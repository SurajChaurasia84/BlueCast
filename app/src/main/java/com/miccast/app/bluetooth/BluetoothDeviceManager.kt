package com.miccast.app.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class BluetoothEndpoint(
    val name: String,
    val address: String,
    val isBonded: Boolean,
    val isConnected: Boolean,
    val isAudioCapable: Boolean
)

data class BluetoothRouteResult(
    val success: Boolean,
    val status: String,
    val supportsAudio: Boolean
)

class BluetoothDeviceManager(
    private val context: Context
) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _devices = MutableStateFlow<List<BluetoothEndpoint>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _disconnectEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val disconnectEvents = _disconnectEvents.asSharedFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isScanning.value = true
                }

                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) mergeDevice(device)
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }

                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> syncAvailableDevices()

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        _disconnectEvents.tryEmit(device.address)
                    }
                    syncAvailableDevices()
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        syncAvailableDevices()
    }

    fun hasBluetoothPermissions(): Boolean {
        val connectGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        val scanGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
        return connectGranted && scanGranted
    }

    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        if (!hasBluetoothPermissions()) {
            _devices.value = emptyList()
            _isScanning.value = false
            return
        }

        syncAvailableDevices()

        if (adapter?.isDiscovering == true) {
            adapter.cancelDiscovery()
        }
        _isScanning.value = adapter?.startDiscovery() == true
    }

    fun getDevice(address: String?): BluetoothEndpoint? {
        if (address == null) return null
        return _devices.value.firstOrNull { it.address == address }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String?): BluetoothRouteResult {
        val endpoint = getDevice(address)
            ?: return BluetoothRouteResult(false, "Select a Bluetooth device first", false)
        if (!endpoint.isAudioCapable) {
            return BluetoothRouteResult(false, "Selected device does not support audio output", false)
        }

        val audioDevice = findMatchingAudioOutput(address)
        if (audioDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
            audioManager.setCommunicationDevice(audioDevice)
        }

        syncAvailableDevices()

        return BluetoothRouteResult(
            success = true,
            status = "Connected to ${endpoint.name}",
            supportsAudio = true
        )
    }

    fun clearAudioRoute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
    }

    fun playTestTone(address: String?): Result<Unit> {
        return runCatching {
            val sampleRate = 16000
            val frameCount = 2048
            val audioDevice = findMatchingAudioOutput(address)
            val track = android.media.AudioTrack.Builder()
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(frameCount * 2)
                .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                .setPerformanceMode(android.media.AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            if (audioDevice != null) {
                track.preferredDevice = audioDevice
            }

            val buffer = ShortArray(frameCount) { index ->
                val angle = 2.0 * Math.PI * index / (sampleRate / 660.0)
                (Math.sin(angle) * Short.MAX_VALUE * 0.18).toInt().toShort()
            }

            track.write(buffer, 0, buffer.size)
            track.reloadStaticData()
            track.play()
            Thread.sleep(250)
            track.stop()
            track.release()
        }
    }

    fun findMatchingAudioOutput(address: String?): AudioDeviceInfo? {
        if (address.isNullOrBlank()) return null
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { info ->
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                        info.address.equals(address, ignoreCase = true) && info.isBluetoothOutput()
                    }

                    else -> info.isBluetoothOutput()
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun syncAvailableDevices() {
        if (!hasBluetoothPermissions()) {
            _devices.value = emptyList()
            _isScanning.value = false
            return
        }

        _devices.value = currentAudioOutputDevices()
    }

    @SuppressLint("MissingPermission")
    private fun currentAudioOutputDevices(): List<BluetoothEndpoint> {
        val connectedAddresses = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isBluetoothOutput() }
            .mapNotNull { deviceInfo ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) deviceInfo.address else null
            }
            .toSet()

        if (connectedAddresses.isEmpty()) {
            return emptyList()
        }

        return adapter?.bondedDevices.orEmpty()
            .filter { it.address in connectedAddresses }
            .map(::toEndpoint)
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    private fun mergeDevice(device: BluetoothDevice) {
        val endpoint = toEndpoint(device)
        _devices.value = (_devices.value + endpoint)
            .filter { it.name.isNotBlank() && it.address.isNotBlank() }
            .distinctBy { it.address }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    @SuppressLint("MissingPermission")
    private fun toEndpoint(device: BluetoothDevice): BluetoothEndpoint {
        val connected = findMatchingAudioOutput(device.address) != null
        return BluetoothEndpoint(
            name = device.name ?: "Unknown Device",
            address = device.address ?: "",
            isBonded = device.bondState == BluetoothDevice.BOND_BONDED,
            isConnected = connected,
            isAudioCapable = supportsAudioOutput(device)
        )
    }

    private fun supportsAudioOutput(device: BluetoothDevice): Boolean {
        val deviceClass = device.bluetoothClass?.deviceClass ?: return false
        return deviceClass in supportedAudioClasses
    }

    private fun AudioDeviceInfo.isBluetoothOutput(): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            type == AudioDeviceInfo.TYPE_BLE_SPEAKER
    }

    companion object {
        private val supportedAudioClasses = setOf(
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
            BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
        )
    }
}

private inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
}

package com.miccast.app.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miccast.app.bluetooth.BluetoothDeviceManager
import com.miccast.app.service.StreamingController
import com.miccast.app.ui.BluetoothDeviceUi
import com.miccast.app.ui.MicCastUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MicCastViewModel(
    application: Application,
    private val bluetoothDeviceManager: BluetoothDeviceManager,
    private val streamingController: StreamingController
) : AndroidViewModel(application) {
    private val permissionState = MutableStateFlow(!hasRequiredPermissions())
    private val splashVisible = MutableStateFlow(true)

    val uiState: StateFlow<MicCastUiState> = combine(
        bluetoothDeviceManager.devices,
        bluetoothDeviceManager.isScanning,
        streamingController.state,
        permissionState,
        splashVisible
    ) { devices, isScanning, streamingState, missingPermissions, showSplash ->
        MicCastUiState(
            showSplash = showSplash,
            isScanning = isScanning,
            devices = devices.map {
                BluetoothDeviceUi(
                    name = it.name,
                    address = it.address,
                    isBonded = it.isBonded,
                    isConnected = it.isConnected,
                    isAudioCapable = it.isAudioCapable
                )
            },
            selectedDeviceAddress = streamingState.selectedDeviceAddress,
            selectedDeviceName = streamingState.selectedDeviceName,
            connectionStatus = streamingState.statusText,
            supportsAudioOutput = streamingState.supportsAudioOutput,
            isDeviceConnected = streamingState.isDeviceConnected,
            isStreaming = streamingState.isStreaming,
            pushToTalkEnabled = streamingState.pushToTalkEnabled,
            outputGain = streamingState.outputGain,
            micLevel = streamingState.micLevel,
            message = streamingState.message,
            missingPermissions = missingPermissions,
            isTalkPressed = streamingState.isTalkPressed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MicCastUiState())

    private var splashInitialized = false

    init {
        viewModelScope.launch {
            bluetoothDeviceManager.devices.collect { devices ->
                syncSelectedDeviceStatus(devices)
            }
        }
    }

    fun onScreenVisible() {
        if (!splashInitialized) {
            splashInitialized = true
            viewModelScope.launch {
                delay(900)
                splashVisible.value = false
            }
        }
        refreshDevices()
    }

    fun handlePermissionResult(granted: Map<String, Boolean>) {
        permissionState.value = !granted.values.all { it }
        if (!permissionState.value) {
            refreshDevices()
        } else {
            streamingController.updateConnection(
                statusText = "Permission required",
                supportsAudio = false,
                message = "Grant microphone and Bluetooth permissions to continue."
            )
        }
    }

    fun refreshDevices() {
        if (!hasRequiredPermissions()) {
            permissionState.value = true
            return
        }
        permissionState.value = false
        bluetoothDeviceManager.refreshDevices()
    }

    fun selectDevice(address: String) {
        val device = bluetoothDeviceManager.getDevice(address) ?: return
        streamingController.updateDeviceSelection(
            address = device.address,
            name = device.name,
            supportsAudio = device.isAudioCapable
        )
        if (!device.isAudioCapable) {
            streamingController.updateConnection(
                statusText = "Not supported",
                supportsAudio = false,
                message = "Selected device does not advertise Bluetooth audio output support."
            )
        } else if (device.isConnected) {
            streamingController.updateConnection(
                statusText = "Connected to ${device.name}",
                supportsAudio = true,
                isConnected = true
            )
        }
    }

    fun connectSelectedDevice() {
        if (!hasRequiredPermissions()) {
            permissionState.value = true
            return
        }

        val result = bluetoothDeviceManager.connect(streamingController.state.value.selectedDeviceAddress)
        streamingController.updateConnection(
            statusText = result.status,
            supportsAudio = result.supportsAudio,
            isConnected = result.success,
            message = if (result.success) null else result.status
        )
        if (result.success) {
            bluetoothDeviceManager.playTestTone(streamingController.state.value.selectedDeviceAddress)
                .onFailure {
                    streamingController.updateConnection(
                        statusText = result.status,
                        supportsAudio = result.supportsAudio,
                        isConnected = true,
                        message = "Connected, but the test tone could not be played."
                    )
                }
        }
    }

    fun disconnectSelectedDevice() {
        val currentState = streamingController.state.value
        if (!currentState.isDeviceConnected) return

        if (currentState.isStreaming) {
            streamingController.stopStreaming()
        }
        bluetoothDeviceManager.clearAudioRoute()
        streamingController.updateConnection(
            statusText = "Not connected",
            supportsAudio = currentState.supportsAudioOutput,
            isConnected = false
        )
        refreshDevices()
    }

    fun toggleStreaming() {
        if (!hasRequiredPermissions()) {
            permissionState.value = true
            return
        }

        if (streamingController.state.value.isStreaming) {
            streamingController.stopStreaming()
        } else {
            streamingController.startStreaming()
        }
    }

    fun setPushToTalkEnabled(enabled: Boolean) {
        streamingController.setPushToTalkEnabled(enabled)
    }

    fun setTalkPressed(pressed: Boolean) {
        streamingController.setTalkPressed(pressed)
    }

    fun setOutputGain(gain: Float) {
        streamingController.setOutputGain(gain)
    }

    fun clearMessage() {
        streamingController.clearMessage()
    }

    private fun syncSelectedDeviceStatus(devices: List<com.miccast.app.bluetooth.BluetoothEndpoint>) {
        val currentState = streamingController.state.value
        val selectedAddress = currentState.selectedDeviceAddress ?: return
        val selectedDevice = devices.firstOrNull { it.address == selectedAddress } ?: return

        if (selectedDevice.isConnected) {
            streamingController.updateConnection(
                statusText = if (currentState.isStreaming) {
                    "Streaming to ${selectedDevice.name}"
                } else {
                    "Connected to ${selectedDevice.name}"
                },
                supportsAudio = selectedDevice.isAudioCapable,
                isConnected = true
            )
        } else if (!currentState.isStreaming && currentState.supportsAudioOutput) {
            streamingController.updateConnection(
                statusText = "Not connected",
                supportsAudio = selectedDevice.isAudioCapable,
                isConnected = false
            )
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(getApplication(), it) == PackageManager.PERMISSION_GRANTED
        }
    }
}

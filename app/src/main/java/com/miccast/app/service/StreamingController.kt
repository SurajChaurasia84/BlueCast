package com.miccast.app.service

import android.content.Context
import android.content.Intent
import com.miccast.app.bluetooth.BluetoothDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StreamingController(
    private val context: Context,
    bluetoothDeviceManager: BluetoothDeviceManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(StreamingState(isTalkPressed = true))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            bluetoothDeviceManager.disconnectEvents.collect { address ->
                if (_state.value.selectedDeviceAddress == address) {
                    updateState {
                        copy(
                            isStreaming = false,
                            isDeviceConnected = false,
                            canStream = false,
                            statusText = "Not connected",
                            message = "Bluetooth device disconnected. Streaming stopped."
                        )
                    }
                    stopStreaming()
                }
            }
        }
    }

    fun updateDeviceSelection(address: String?, name: String?, supportsAudio: Boolean) {
        updateState {
            copy(
                selectedDeviceAddress = address,
                selectedDeviceName = name,
                supportsAudioOutput = supportsAudio,
                isDeviceConnected = false,
                canStream = supportsAudio && !address.isNullOrBlank(),
                statusText = if (supportsAudio) "Not connected" else "Not supported"
            )
        }
    }

    fun updateConnection(
        statusText: String,
        supportsAudio: Boolean,
        isConnected: Boolean = false,
        message: String? = null
    ) {
        updateState {
            copy(
                statusText = statusText,
                supportsAudioOutput = supportsAudio,
                isDeviceConnected = isConnected,
                canStream = supportsAudio && !selectedDeviceAddress.isNullOrBlank(),
                message = message
            )
        }
    }

    fun setStreaming(isStreaming: Boolean, statusText: String? = null, message: String? = null) {
        updateState {
            copy(
                isStreaming = isStreaming,
                isDeviceConnected = if (isStreaming) true else isDeviceConnected,
                statusText = statusText ?: this.statusText,
                message = message,
                isTalkPressed = if (pushToTalkEnabled) false else true
            )
        }
    }

    fun setMicLevel(level: Float) {
        updateState { copy(micLevel = level) }
    }

    fun setOutputGain(gain: Float) {
        updateState { copy(outputGain = gain) }
    }

    fun setPushToTalkEnabled(enabled: Boolean) {
        updateState {
            copy(
                pushToTalkEnabled = enabled,
                isTalkPressed = !enabled
            )
        }
    }

    fun setTalkPressed(pressed: Boolean) {
        updateState { copy(isTalkPressed = pressed) }
    }

    fun clearMessage() {
        updateState { copy(message = null) }
    }

    fun startStreaming() {
        val intent = Intent(context, StreamingService::class.java).apply {
            action = StreamingService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    fun stopStreaming() {
        val intent = Intent(context, StreamingService::class.java).apply {
            action = StreamingService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun updateState(transform: StreamingState.() -> StreamingState) {
        _state.value = _state.value.transform()
    }
}

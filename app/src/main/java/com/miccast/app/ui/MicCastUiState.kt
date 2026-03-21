package com.miccast.app.ui

data class BluetoothDeviceUi(
    val name: String,
    val address: String,
    val isBonded: Boolean,
    val isConnected: Boolean,
    val isAudioCapable: Boolean
)

data class MicCastUiState(
    val showSplash: Boolean = true,
    val isScanning: Boolean = false,
    val devices: List<BluetoothDeviceUi> = emptyList(),
    val selectedDeviceAddress: String? = null,
    val selectedDeviceName: String? = null,
    val connectionStatus: String = "Not connected",
    val supportsAudioOutput: Boolean = false,
    val isDeviceConnected: Boolean = false,
    val isStreaming: Boolean = false,
    val pushToTalkEnabled: Boolean = false,
    val isTalkPressed: Boolean = false,
    val outputGain: Float = 1f,
    val micLevel: Float = 0f,
    val message: String? = null,
    val missingPermissions: Boolean = false
)

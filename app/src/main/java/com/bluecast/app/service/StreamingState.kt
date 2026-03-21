package com.bluecast.app.service

data class StreamingState(
    val isStreaming: Boolean = false,
    val selectedDeviceAddress: String? = null,
    val selectedDeviceName: String? = null,
    val supportsAudioOutput: Boolean = false,
    val isDeviceConnected: Boolean = false,
    val statusText: String = "Not connected",
    val micLevel: Float = 0f,
    val outputGain: Float = 1f,
    val pushToTalkEnabled: Boolean = false,
    val isTalkPressed: Boolean = false,
    val canStream: Boolean = false,
    val message: String? = null
)


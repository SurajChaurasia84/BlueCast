package com.miccast.app

import android.content.Context
import com.miccast.app.audio.AudioStreamingEngine
import com.miccast.app.bluetooth.BluetoothDeviceManager
import com.miccast.app.service.StreamingController

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val bluetoothDeviceManager: BluetoothDeviceManager by lazy {
        BluetoothDeviceManager(appContext)
    }

    val audioStreamingEngine: AudioStreamingEngine by lazy {
        AudioStreamingEngine(appContext)
    }

    val streamingController: StreamingController by lazy {
        StreamingController(appContext, bluetoothDeviceManager)
    }
}

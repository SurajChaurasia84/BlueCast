package com.bluecast.app

import android.content.Context
import com.bluecast.app.audio.AudioStreamingEngine
import com.bluecast.app.bluetooth.BluetoothDeviceManager
import com.bluecast.app.service.StreamingController

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


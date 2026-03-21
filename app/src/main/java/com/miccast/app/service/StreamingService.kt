package com.miccast.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.miccast.app.MicCastApplication
import com.miccast.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class StreamingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val container by lazy { (application as MicCastApplication).container }
    private val controller by lazy { container.streamingController }
    private val bluetoothManager by lazy { container.bluetoothDeviceManager }
    private val audioEngine by lazy { container.audioStreamingEngine }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startStreaming()
            ACTION_STOP -> stopStreaming()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startStreaming() {
        val state = controller.state.value
        if (!state.supportsAudioOutput || state.selectedDeviceAddress.isNullOrBlank()) {
            controller.setStreaming(
                isStreaming = false,
                statusText = "Not supported",
                message = "Connect to a supported Bluetooth audio device first."
            )
            stopSelf()
            return
        }

        val foregroundStarted = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        }

        if (foregroundStarted.isFailure) {
            controller.setStreaming(
                isStreaming = false,
                statusText = "Streaming failed",
                message = foregroundStarted.exceptionOrNull()?.message
                    ?: "Unable to start foreground microphone service."
            )
            stopSelf()
            return
        }

        scope.launch {
            val preferredDevice = bluetoothManager.findMatchingAudioOutput(state.selectedDeviceAddress)
            val result = audioEngine.start(
                preferredDevice = preferredDevice,
                gainProvider = { controller.state.value.outputGain },
                canTransmit = {
                    val liveState = controller.state.value
                    !liveState.pushToTalkEnabled || liveState.isTalkPressed
                },
                onMicLevel = controller::setMicLevel
            )

            result.onSuccess {
                controller.setStreaming(
                    isStreaming = true,
                    statusText = "Streaming to ${state.selectedDeviceName ?: "Bluetooth device"}"
                )
            }.onFailure { throwable ->
                controller.setStreaming(
                    isStreaming = false,
                    statusText = "Streaming failed",
                    message = throwable.message ?: "Unable to start streaming."
                )
                stopSelf()
            }
        }
    }

    private fun stopStreaming() {
        scope.launch {
            audioEngine.stop()
            bluetoothManager.clearAudioRoute()
            controller.setStreaming(isStreaming = false, statusText = "Not connected")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MicCast Streaming",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the active MicCast audio streaming session."
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_miccast_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.miccast.app.action.START_STREAMING"
        const val ACTION_STOP = "com.miccast.app.action.STOP_STREAMING"

        private const val CHANNEL_ID = "miccast_streaming"
        private const val NOTIFICATION_ID = 2001
    }
}

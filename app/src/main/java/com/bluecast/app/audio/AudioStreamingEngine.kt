package com.bluecast.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

class AudioStreamingEngine(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var processingJob: Job? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: AutomaticGainControl? = null

    val isRunning: Boolean
        get() = processingJob?.isActive == true

    suspend fun start(
        preferredDevice: AudioDeviceInfo?,
        gainProvider: () -> Float,
        canTransmit: () -> Boolean,
        onMicLevel: (Float) -> Unit
    ): Result<Unit> = withContext(ioDispatcher) {
        if (!hasRecordPermission()) {
            return@withContext Result.failure(IllegalStateException("Microphone permission denied"))
        }
        if (isRunning) return@withContext Result.success(Unit)

        val minRecordBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AudioFormat.ENCODING_PCM_16BIT)
        val minTrackBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = (maxOf(minRecordBuffer, minTrackBuffer, 2048) / 2) * 2

        val localRecord = runCatching {
            createAudioRecord(bufferSize)
        }.getOrElse { throwable ->
            return@withContext Result.failure(
                IllegalStateException(
                    throwable.message ?: "AudioRecord initialization failed",
                    throwable
                )
            )
        }

        if (localRecord.state != AudioRecord.STATE_INITIALIZED) {
            localRecord.release()
            return@withContext Result.failure(IllegalStateException("AudioRecord initialization failed"))
        }

        val localTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        preferredDevice?.let { localTrack.preferredDevice = it }

        record = localRecord
        track = localTrack
        attachProcessors(localRecord.audioSessionId)

        localRecord.startRecording()
        localTrack.play()

        processingJob = CoroutineScope(ioDispatcher).launch {
            val readBuffer = ShortArray(bufferSize / 2)
            val outputBuffer = ShortArray(readBuffer.size)
            while (isActive) {
                val read = localRecord.read(readBuffer, 0, readBuffer.size)
                if (read <= 0) continue

                onMicLevel(readBuffer.calculateLevel(read))

                if (!canTransmit()) {
                    outputBuffer.fill(0, 0, read)
                } else {
                    val gain = gainProvider().coerceIn(0.5f, 2f)
                    val threshold = 280
                    for (index in 0 until read) {
                        val sample = readBuffer[index].toInt()
                        outputBuffer[index] = if (abs(sample) < threshold) {
                            0
                        } else {
                            (sample * gain).toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                .toShort()
                        }
                    }
                }

                localTrack.write(outputBuffer, 0, read)
            }
        }

        Result.success(Unit)
    }

    suspend fun stop() {
        withContext(ioDispatcher) {
            processingJob?.cancelAndJoin()
            processingJob = null

            record?.runCatching {
                stop()
                release()
            }
            record = null

            track?.runCatching {
                pause()
                flush()
                stop()
                release()
            }
            track = null

            noiseSuppressor?.release()
            echoCanceler?.release()
            automaticGainControl?.release()
            noiseSuppressor = null
            echoCanceler = null
            automaticGainControl = null

            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    private fun attachProcessors(audioSessionId: Int) {
        noiseSuppressor = if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
        } else {
            null
        }
        echoCanceler = if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
        } else {
            null
        }
        automaticGainControl = if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
        } else {
            null
        }

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        disableSpeakerphone()
    }

    private fun disableSpeakerphone() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(bufferSize: Int): AudioRecord {
        check(
            context.checkPermission(
                Manifest.permission.RECORD_AUDIO,
                Process.myPid(),
                Process.myUid()
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            "Microphone permission denied"
        }

        return try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_IN,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (securityException: SecurityException) {
            throw IllegalStateException("Microphone permission denied", securityException)
        }
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    }
}

private fun ShortArray.calculateLevel(count: Int): Float {
    if (count <= 0) return 0f
    var total = 0.0
    for (index in 0 until count) {
        val sample = this[index].toDouble()
        total += sample * sample
    }
    val rms = sqrt(total / count) / Short.MAX_VALUE
    return rms.toFloat().coerceIn(0f, 1f)
}


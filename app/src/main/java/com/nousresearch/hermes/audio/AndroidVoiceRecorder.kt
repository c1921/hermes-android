package com.nousresearch.hermes.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VoiceRecording(
    val file: File,
    val mimeType: String,
    val durationMillis: Long,
)

@Singleton
class AndroidVoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mutableLevel = MutableStateFlow(0f)
    val level = mutableLevel.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMillis = 0L
    private var meterJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null
    private var interruptionCallback: (() -> Unit)? = null

    @Synchronized
    fun start(scope: CoroutineScope, onInterrupted: () -> Unit) {
        check(recorder == null) { "A voice recording is already active" }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener(
                { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        interruptionCallback?.invoke()
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            .build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            throw IOException("Android could not reserve the microphone audio session")
        }
        focusRequest = request
        interruptionCallback = onInterrupted

        val target = File.createTempFile("hermes-voice-", ".m4a", context.cacheDir)
        val next = createRecorder()
        try {
            next.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioSamplingRate(44_100)
            next.setAudioEncodingBitRate(96_000)
            next.setOutputFile(target.absolutePath)
            next.prepare()
            next.start()
        } catch (error: Throwable) {
            runCatching { next.release() }
            target.delete()
            releaseAudioFocus()
            throw IOException("Android could not start voice recording", error)
        }

        recorder = next
        outputFile = target
        startedAtMillis = System.currentTimeMillis()
        mutableLevel.value = 0f
        meterJob = scope.launch {
            while (isActive) {
                val amplitude = runCatching { synchronized(this@AndroidVoiceRecorder) { recorder?.maxAmplitude ?: 0 } }
                    .getOrDefault(0)
                mutableLevel.value = sqrt((amplitude.toFloat() / MAX_AMPLITUDE).coerceIn(0f, 1f))
                delay(METER_INTERVAL_MILLIS)
            }
        }
    }

    @Synchronized
    fun stop(): VoiceRecording {
        val active = recorder ?: throw IOException("No voice recording is active")
        val target = requireNotNull(outputFile)
        val duration = System.currentTimeMillis() - startedAtMillis
        recorder = null
        outputFile = null
        meterJob?.cancel()
        meterJob = null
        mutableLevel.value = 0f
        interruptionCallback = null
        try {
            active.stop()
        } catch (error: RuntimeException) {
            target.delete()
            throw IOException("The recording was too short to process", error)
        } finally {
            active.release()
            releaseAudioFocus()
        }
        return VoiceRecording(target, MIME_TYPE, duration)
    }

    @Synchronized
    fun cancel() {
        val active = recorder
        val target = outputFile
        recorder = null
        outputFile = null
        meterJob?.cancel()
        meterJob = null
        mutableLevel.value = 0f
        interruptionCallback = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        target?.delete()
        releaseAudioFocus()
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        MediaRecorder()
    }

    private fun releaseAudioFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private companion object {
        const val MAX_AMPLITUDE = 32_767f
        const val METER_INTERVAL_MILLIS = 80L
        const val MIME_TYPE = "audio/mp4"
    }
}

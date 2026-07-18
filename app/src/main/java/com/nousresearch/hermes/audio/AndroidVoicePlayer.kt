package com.nousresearch.hermes.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRouter2
import android.os.Build
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class SpokenAudio(
    val bytes: ByteArray,
    val mimeType: String,
) {
    val extension: String
        get() = when (mimeType.lowercase()) {
            "audio/mpeg", "audio/mp3" -> ".mp3"
            "audio/ogg" -> ".ogg"
            "audio/opus" -> ".opus"
            "audio/wav", "audio/x-wav" -> ".wav"
            "audio/flac" -> ".flac"
            else -> ".m4a"
        }
}

enum class VoicePlaybackPhase { PLAYING, PAUSED }

data class VoicePlaybackStatus(
    val phase: VoicePlaybackPhase,
    val outputName: String,
)

@Singleton
class AndroidVoicePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var sourceFile: File? = null
    private var focusRequest: AudioFocusRequest? = null
    private var statusCallback: ((VoicePlaybackStatus) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null
    private var completionCallback: (() -> Unit)? = null
    private var resumeAfterFocusGain = false

    @Synchronized
    fun play(
        audio: SpokenAudio,
        onStatus: (VoicePlaybackStatus) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit,
    ) {
        stop()
        if (!requestAudioFocus()) throw IOException("Android could not reserve the speech audio session")

        val target = try {
            File.createTempFile("hermes-speech-", audio.extension, context.cacheDir)
        } catch (error: Throwable) {
            releaseAudioFocus()
            throw IOException("Android could not prepare temporary spoken audio", error)
        }
        try {
            target.writeBytes(audio.bytes)
            val next = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(target.absolutePath)
            }
            sourceFile = target
            player = next
            statusCallback = onStatus
            errorCallback = onError
            completionCallback = onComplete
            next.setOnPreparedListener { prepared ->
                prepared.start()
                notifyStatus(VoicePlaybackPhase.PLAYING)
            }
            next.setOnCompletionListener {
                val callback = completionCallback
                finish()
                callback?.invoke()
            }
            next.setOnErrorListener { _, _, _ ->
                fail("Android could not play the Hermes spoken reply")
                true
            }
            next.addOnRoutingChangedListener({ notifyCurrentStatus() }, mainHandler)
            next.prepareAsync()
        } catch (error: Throwable) {
            target.delete()
            finish()
            throw IOException("Android could not prepare the Hermes spoken reply", error)
        }
    }

    @Synchronized
    fun pause() {
        val active = player ?: return
        if (active.isPlaying) {
            active.pause()
            notifyStatus(VoicePlaybackPhase.PAUSED)
        }
    }

    @Synchronized
    fun resume() {
        val active = player ?: return
        if (!requestAudioFocus()) {
            fail("Android could not resume the speech audio session")
            return
        }
        runCatching { active.start() }
            .onSuccess { notifyStatus(VoicePlaybackPhase.PLAYING) }
            .onFailure { fail("Android could not resume the Hermes spoken reply") }
    }

    @Synchronized
    fun stop() = finish()

    fun showOutputSwitcher() {
        val shown = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { MediaRouter2.getInstance(context).showSystemOutputSwitcher() }.getOrDefault(false)
        } else {
            false
        }
        if (!shown) audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
    }

    @Synchronized
    private fun requestAudioFocus(): Boolean {
        if (focusRequest != null) return true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener(::onAudioFocusChanged, mainHandler)
            .build()
        return if (audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusRequest = request
            true
        } else {
            false
        }
    }

    private fun onAudioFocusChanged(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> stop()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> synchronized(this) {
                resumeAfterFocusGain = player?.isPlaying == true
                pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> synchronized(this) {
                if (resumeAfterFocusGain) {
                    resumeAfterFocusGain = false
                    resume()
                }
            }
        }
    }

    @Synchronized
    private fun notifyCurrentStatus() {
        val active = player ?: return
        notifyStatus(if (active.isPlaying) VoicePlaybackPhase.PLAYING else VoicePlaybackPhase.PAUSED)
    }

    @Synchronized
    private fun notifyStatus(phase: VoicePlaybackPhase) {
        val route = player?.routedDevice?.productName?.toString()?.takeIf(String::isNotBlank) ?: "Android media output"
        statusCallback?.invoke(VoicePlaybackStatus(phase, route))
    }

    @Synchronized
    private fun fail(message: String) {
        val callback = errorCallback
        finish()
        callback?.invoke(message)
    }

    @Synchronized
    private fun finish() {
        val active = player
        player = null
        runCatching { active?.release() }
        sourceFile?.delete()
        sourceFile = null
        statusCallback = null
        errorCallback = null
        completionCallback = null
        resumeAfterFocusGain = false
        releaseAudioFocus()
    }

    private fun releaseAudioFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }
}

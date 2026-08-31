package com.nousresearch.hermes.audio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.MediaRouter2
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.nousresearch.hermes.MainActivity
import com.nousresearch.hermes.R
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

internal data class VoiceMediaControls(val actions: Long, val toggleAction: String?)

internal fun voiceMediaControls(state: Int): VoiceMediaControls = when (state) {
    PlaybackState.STATE_PLAYING -> VoiceMediaControls(
        PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP,
        AndroidVoicePlayer.ACTION_PAUSE,
    )
    PlaybackState.STATE_PAUSED -> VoiceMediaControls(
        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_STOP,
        AndroidVoicePlayer.ACTION_PLAY,
    )
    else -> VoiceMediaControls(PlaybackState.ACTION_STOP, null)
}

@Singleton
class AndroidVoicePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var pcmTrack: AudioTrack? = null
    private var sourceFile: File? = null
    private var focusRequest: AudioFocusRequest? = null
    private var statusCallback: ((VoicePlaybackStatus) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null
    private var completionCallback: (() -> Unit)? = null
    private var mediaStopCallback: (() -> Unit)? = null
    private var streamFailureCallback: (() -> Unit)? = null
    private var resumeAfterFocusGain = false
    private var mediaSession: MediaSession? = null
    private var playbackGeneration = 0L
    private var pcmDrainRunnable: Runnable? = null

    init {
        context.cacheDir.listFiles { file -> file.name.startsWith(SPEECH_FILE_PREFIX) }
            .orEmpty()
            .forEach(File::delete)
    }

    @Synchronized
    fun play(
        audio: SpokenAudio,
        onStatus: (VoicePlaybackStatus) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit,
        onStop: (() -> Unit)? = null,
    ) {
        stop()
        val generation = playbackGeneration
        if (!requestAudioFocus()) throw IOException("Android could not reserve the speech audio session")

        val target = try {
            File.createTempFile(SPEECH_FILE_PREFIX, audio.extension, context.cacheDir)
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
            mediaStopCallback = onStop
            prepareMediaSession()
            publishMediaState(PlaybackState.STATE_BUFFERING)
            next.setOnPreparedListener { prepared ->
                synchronized(this) {
                    if (player !== prepared || playbackGeneration != generation) return@setOnPreparedListener
                    prepared.start()
                    notifyStatus(VoicePlaybackPhase.PLAYING)
                }
            }
            next.setOnCompletionListener {
                synchronized(this) {
                    if (player !== next || playbackGeneration != generation) return@setOnCompletionListener
                    val callback = completionCallback
                    finish()
                    callback?.invoke()
                }
            }
            next.setOnErrorListener { _, _, _ ->
                synchronized(this) {
                    if (player === next && playbackGeneration == generation) {
                        fail("Android could not play the Hermes spoken reply")
                    }
                }
                true
            }
            next.addOnRoutingChangedListener(
                {
                    synchronized(this) {
                        if (player === next && playbackGeneration == generation) notifyCurrentStatus()
                    }
                },
                mainHandler,
            )
            next.prepareAsync()
        } catch (error: Throwable) {
            target.delete()
            finish()
            throw IOException("Android could not prepare the Hermes spoken reply", error)
        }
    }

    @Synchronized
    fun beginPcmStream(
        format: VoicePcmFormat,
        onStatus: (VoicePlaybackStatus) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit,
        onStop: (() -> Unit)? = null,
        onStreamFailure: (() -> Unit)? = null,
    ): PcmAudioSink {
        stop()
        val generation = playbackGeneration
        if (!requestAudioFocus()) throw IOException("Android could not reserve the speech audio session")

        return try {
            val track = createPcmTrack(format)
            pcmTrack = track
            statusCallback = onStatus
            errorCallback = onError
            completionCallback = onComplete
            mediaStopCallback = onStop
            streamFailureCallback = onStreamFailure
            prepareMediaSession()
            publishMediaState(PlaybackState.STATE_BUFFERING)
            track.addOnRoutingChangedListener(
                {
                    synchronized(this) {
                        if (pcmTrack === track && playbackGeneration == generation) notifyCurrentStatus()
                    }
                },
                mainHandler,
            )
            AudioTrackPcmSink(track, generation)
        } catch (error: Throwable) {
            finish()
            throw IOException("Android could not prepare the Hermes PCM stream", error)
        }
    }

    @Synchronized
    fun pause() {
        val activePcm = pcmTrack
        if (activePcm != null) {
            if (activePcm.playState == AudioTrack.PLAYSTATE_PLAYING) {
                activePcm.pause()
                notifyStatus(VoicePlaybackPhase.PAUSED)
            }
            return
        }
        val active = player ?: return finish()
        if (active.isPlaying) {
            active.pause()
            notifyStatus(VoicePlaybackPhase.PAUSED)
        }
    }

    @Synchronized
    fun resume() {
        val activePcm = pcmTrack
        if (activePcm != null) {
            if (!requestAudioFocus()) {
                fail("Android could not resume the speech audio session")
                return
            }
            runCatching { activePcm.play() }
                .onSuccess {
                    notifyStatus(VoicePlaybackPhase.PLAYING)
                    pcmDrainRunnable?.let { drain ->
                        mainHandler.removeCallbacks(drain)
                        mainHandler.post(drain)
                    }
                }
                .onFailure { fail("Android could not resume the Hermes PCM stream") }
            return
        }
        val active = player ?: return finish()
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

    fun stopFromMediaControl() {
        val callback = synchronized(this) { mediaStopCallback }
        stop()
        callback?.invoke()
    }

    private fun createPcmTrack(format: VoicePcmFormat): AudioTrack {
        val minimumBufferSize = AudioTrack.getMinBufferSize(
            format.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minimumBufferSize > 0) { "Android rejected the Hermes PCM format" }
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(format.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(minimumBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private inner class AudioTrackPcmSink(
        private val track: AudioTrack,
        private val generation: Long,
    ) : PcmAudioSink {
        private val lifecycle = PcmStreamLifecycle()
        private var writtenFrames = 0L

        @Synchronized
        override fun write(pcm: ByteArray) {
            lifecycle.requireWritable()
            validatePcmChunk(pcm)
            val shouldStart: Boolean
            try {
                shouldStart = synchronized(this@AndroidVoicePlayer) {
                    ensurePcmCurrent(track, generation)
                    val start = track.playState == AudioTrack.PLAYSTATE_STOPPED
                    if (start) track.play()
                    start
                }
                var offset = 0
                while (offset < pcm.size) {
                    val written = track.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) throw IOException("Android rejected a Hermes PCM write")
                    offset += written
                }
            } catch (error: Throwable) {
                val failure = if (error is IOException) error else IOException("Android could not write the Hermes PCM stream", error)
                synchronized(this@AndroidVoicePlayer) {
                    if (pcmTrack === track && playbackGeneration == generation) {
                        fail("Android could not write the Hermes PCM stream")
                    }
                }
                throw failure
            }
            writtenFrames += pcm.size / 2L
            synchronized(this@AndroidVoicePlayer) {
                if (shouldStart && pcmTrack === track && playbackGeneration == generation) {
                    notifyStatus(
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            VoicePlaybackPhase.PLAYING
                        } else {
                            VoicePlaybackPhase.PAUSED
                        },
                    )
                }
            }
        }

        @Synchronized
        override fun end() {
            lifecycle.end()
            synchronized(this@AndroidVoicePlayer) {
                ensurePcmCurrent(track, generation)
                val callback = completionCallback
                if (writtenFrames == 0L) {
                    finish()
                    callback?.invoke()
                } else {
                    drainPcm(track, generation, writtenFrames, callback)
                }
            }
        }
    }

    private fun drainPcm(
        track: AudioTrack,
        generation: Long,
        targetFrames: Long,
        callback: (() -> Unit)?,
    ) {
        val drain = object : Runnable {
            override fun run() {
                synchronized(this@AndroidVoicePlayer) {
                    if (pcmTrack !== track || playbackGeneration != generation) return
                    if (track.playState == AudioTrack.PLAYSTATE_PAUSED) return
                    val playedFrames = track.playbackHeadPosition.toLong() and UINT32_MASK
                    if (playedFrames >= targetFrames) {
                        pcmDrainRunnable = null
                        finish()
                        callback?.invoke()
                    } else {
                        mainHandler.postDelayed(this, PCM_DRAIN_POLL_MILLIS)
                    }
                }
            }
        }
        pcmDrainRunnable = drain
        mainHandler.post(drain)
    }

    private fun ensurePcmCurrent(track: AudioTrack, generation: Long) {
        check(pcmTrack === track && playbackGeneration == generation) {
            "PCM stream is no longer active"
        }
    }

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
            AudioManager.AUDIOFOCUS_LOSS -> fail("Playback stopped because Android audio focus changed")
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> synchronized(this) {
                resumeAfterFocusGain = player?.isPlaying == true || pcmTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
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
        player?.let { active ->
            notifyStatus(if (active.isPlaying) VoicePlaybackPhase.PLAYING else VoicePlaybackPhase.PAUSED)
            return
        }
        pcmTrack?.let { active ->
            notifyStatus(if (active.playState == AudioTrack.PLAYSTATE_PLAYING) VoicePlaybackPhase.PLAYING else VoicePlaybackPhase.PAUSED)
        }
    }

    @Synchronized
    private fun notifyStatus(phase: VoicePlaybackPhase) {
        val route = (player?.routedDevice ?: pcmTrack?.routedDevice)?.productName?.toString()?.takeIf(String::isNotBlank)
            ?: "Android media output"
        publishMediaState(
            if (phase == VoicePlaybackPhase.PLAYING) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
        )
        statusCallback?.invoke(VoicePlaybackStatus(phase, route))
    }

    @Synchronized
    private fun fail(message: String) {
        val callback = errorCallback
        val streamFailureCallback = this.streamFailureCallback.takeIf { pcmTrack != null }
        finish()
        streamFailureCallback?.invoke()
        callback?.invoke(message)
    }

    @Synchronized
    private fun finish() {
        playbackGeneration++
        pcmDrainRunnable?.let(mainHandler::removeCallbacks)
        pcmDrainRunnable = null
        val active = player
        player = null
        runCatching { active?.release() }
        val activePcm = pcmTrack
        pcmTrack = null
        runCatching { activePcm?.stop() }
        runCatching { activePcm?.release() }
        sourceFile?.delete()
        sourceFile = null
        statusCallback = null
        errorCallback = null
        completionCallback = null
        mediaStopCallback = null
        streamFailureCallback = null
        resumeAfterFocusGain = false
        releaseAudioFocus()
        mediaSession?.setPlaybackState(
            PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0L, 0f).build(),
        )
        notificationManager.cancel(NOTIFICATION_ID)
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
    }

    private fun releaseAudioFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private fun prepareMediaSession() {
        if (mediaSession != null) return
        createNotificationChannel()
        mediaSession = MediaSession(context, "HermesReadAloud").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() = resume()
                    override fun onPause() = pause()
                    override fun onStop() = stopFromMediaControl()
                },
                mainHandler,
            )
            setSessionActivity(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, localizedContext().getString(R.string.read_aloud))
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, localizedContext().getString(R.string.app_name))
                    .build(),
            )
            isActive = true
        }
    }

    @SuppressLint("NotificationPermission") // Media-session notifications are exempt from the Android 13 runtime grant.
    private fun publishMediaState(state: Int) {
        val session = mediaSession ?: return
        val controls = voiceMediaControls(state)
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(controls.actions)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (state == PlaybackState.STATE_PLAYING) 1f else 0f)
                .build(),
        )
        val playing = state == PlaybackState.STATE_PLAYING
        val strings = localizedContext()
        val stopIntent = mediaActionIntent(ACTION_STOP, 3)
        val builder = Notification.Builder(strings, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hermes)
            .setContentTitle(strings.getString(R.string.read_aloud))
            .setContentText(strings.getString(if (state == PlaybackState.STATE_BUFFERING) R.string.preparing_spoken_reply else R.string.spoken_reply))
            .setContentIntent(session.controller.sessionActivity)
            .setDeleteIntent(stopIntent)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
        controls.toggleAction?.let { action ->
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(
                        context,
                        if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    ),
                    strings.getString(if (playing) R.string.pause else R.string.play),
                    mediaActionIntent(action, if (playing) 1 else 2),
                ).build(),
            )
        }
        builder
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                    strings.getString(R.string.stop),
                    stopIntent,
                ).build(),
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(
                        *(if (controls.toggleAction == null) intArrayOf(0) else intArrayOf(0, 1)),
                    ),
            )
        val notification = builder.build()
        // Media-session notifications are exempt from Android 13's runtime notification permission.
        runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
    }

    private fun mediaActionIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, VoiceMediaActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createNotificationChannel() {
        val strings = localizedContext()
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, strings.getString(R.string.spoken_replies), NotificationManager.IMPORTANCE_LOW).apply {
                description = strings.getString(R.string.spoken_replies_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setSound(null, null)
            },
        )
    }

    private fun localizedContext(): Context = ContextCompat.getContextForLanguage(context)

    internal companion object {
        const val ACTION_PLAY = "com.nousresearch.hermes.voice.PLAY"
        const val ACTION_PAUSE = "com.nousresearch.hermes.voice.PAUSE"
        const val ACTION_STOP = "com.nousresearch.hermes.voice.STOP"
        const val CHANNEL_ID = "hermes_read_aloud"
        const val NOTIFICATION_ID = 3101
        const val SPEECH_FILE_PREFIX = "hermes-speech-"
        const val PCM_DRAIN_POLL_MILLIS = 20L
        const val UINT32_MASK = 0xffff_ffffL
    }
}

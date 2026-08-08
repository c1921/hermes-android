package com.nousresearch.hermes.data

import com.nousresearch.hermes.audio.PcmAudioSink
import com.nousresearch.hermes.audio.VoicePcmFormat
import com.nousresearch.hermes.audio.VoiceRecording
import com.nousresearch.hermes.audio.SpokenAudio
import com.nousresearch.hermes.network.HermesRestClient
import com.nousresearch.hermes.network.HermesVoiceStreamClient
import com.nousresearch.hermes.network.VoiceStreamEvent
import com.nousresearch.hermes.network.VoiceStreamException
import java.io.IOException
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

enum class StreamedSpeechResult { COMPLETED, FALLBACK }

@Singleton
class VoiceRepository @Inject constructor(
    private val rest: HermesRestClient,
    private val stream: HermesVoiceStreamClient,
    private val credentials: SessionCredentialStore,
) {
    suspend fun transcribe(config: BackendConfig, profile: String, recording: VoiceRecording): String {
        try {
            require(recording.durationMillis >= MIN_RECORDING_MILLIS) { "Hold the microphone a little longer before releasing" }
            val bytes = withContext(Dispatchers.IO) {
                val size = recording.file.length()
                require(size in 1..MAX_TRANSCRIPTION_BYTES) { "Voice recording exceeds the Hermes upload limit" }
                recording.file.readBytes()
            }
            val payload = "data:${recording.mimeType};base64,${Base64.getEncoder().encodeToString(bytes)}"
            val response = rest.transcribeAudio(config, credential(config), profile, payload, recording.mimeType)
            if (!response.ok) throw IOException("Hermes could not transcribe the recording")
            return response.transcript.trim()
        } finally {
            recording.file.delete()
        }
    }

    suspend fun speak(config: BackendConfig, profile: String, text: String): SpokenAudio {
        require(text.isNotBlank()) { "There is no Hermes reply to speak" }
        val response = rest.speakText(config, credential(config), profile, text)
        if (!response.ok) throw IOException("Hermes could not generate a spoken reply")
        return decodeSpokenAudio(response.dataUrl, response.mimeType)
    }

    suspend fun streamSpeech(
        config: BackendConfig,
        profile: String,
        text: String,
        openSink: (VoicePcmFormat) -> PcmAudioSink,
    ): StreamedSpeechResult {
        require(text.isNotBlank()) { "There is no Hermes reply to speak" }
        val credential = credentials.get(config.id)
            ?: throw IOException("Reconnect ${config.label} before using Hermes voice")
        val terminal = CompletableDeferred<StreamedSpeechResult>()
        val sink = AtomicReference<PcmAudioSink?>()
        val receivedAudio = AtomicBoolean(false)
        val session = stream.open(
            config = config,
            profile = profile,
            token = credential.takeUnless { config.authMode == AuthMode.DASHBOARD_SESSION }
                ?.headerValue
                ?.substringAfter('=', missingDelimiterValue = ""),
            sessionCookie = credential.takeIf { config.authMode == AuthMode.DASHBOARD_SESSION },
        ) { event ->
            runCatching {
                when (event) {
                    is VoiceStreamEvent.Started -> check(sink.compareAndSet(null, openSink(event.format))) {
                        "Hermes voice stream started more than once"
                    }
                    is VoiceStreamEvent.Audio -> {
                        receivedAudio.set(true)
                        checkNotNull(sink.get()) { "Hermes voice audio arrived before its format" }.write(event.pcm)
                    }
                    VoiceStreamEvent.Ended -> {
                        checkNotNull(sink.get()) { "Hermes voice stream ended before it started" }.end()
                        terminal.complete(StreamedSpeechResult.COMPLETED)
                    }
                    VoiceStreamEvent.Fallback -> {
                        check(!receivedAudio.get()) { "Hermes requested fallback after streaming audio" }
                        terminal.complete(StreamedSpeechResult.FALLBACK)
                    }
                    VoiceStreamEvent.Stopped -> terminal.completeExceptionally(IOException("Hermes voice stream stopped"))
                    is VoiceStreamEvent.Disconnected -> terminal.completeExceptionally(
                        IOException("Hermes voice stream disconnected (${event.code})"),
                    )
                    is VoiceStreamEvent.Failed -> terminal.completeExceptionally(VoiceStreamException(event.reason))
                }
            }.onFailure(terminal::completeExceptionally)
        }
        var completedNormally = false
        try {
            if (!session.append(text) && !terminal.isCompleted) throw IOException("Hermes rejected spoken text")
            if (!session.finish() && !terminal.isCompleted) throw IOException("Hermes rejected spoken completion")
            return withTimeout(STREAM_TIMEOUT_MILLIS) { terminal.await() }.also { completedNormally = true }
        } finally {
            if (!completedNormally) session.stop()
        }
    }

    private fun credential(config: BackendConfig): String = credentials.get(config.id)?.headerValue
        ?: throw IOException("Reconnect ${config.label} before using Hermes voice")

    private companion object {
        const val MIN_RECORDING_MILLIS = 250L
        const val MAX_TRANSCRIPTION_BYTES = 25L * 1024L * 1024L
        const val STREAM_TIMEOUT_MILLIS = 180_000L
    }
}

internal fun decodeSpokenAudio(dataUrl: String, declaredMimeType: String): SpokenAudio {
    val separator = dataUrl.indexOf(',')
    require(separator > 5) { "Hermes returned invalid spoken audio" }
    val metadata = dataUrl.substring(5, separator)
    require(metadata.endsWith(";base64", ignoreCase = true)) { "Hermes returned unsupported spoken audio" }
    val encoded = dataUrl.substring(separator + 1)
    require(encoded.length <= MAX_SPOKEN_AUDIO_BASE64_CHARS) { "Hermes spoken audio exceeds the Android playback limit" }
    val mimeType = metadata.substringBefore(';').takeIf(String::isNotBlank) ?: declaredMimeType
    require(mimeType.startsWith("audio/", ignoreCase = true)) { "Hermes returned a non-audio response" }
    val bytes = runCatching { Base64.getDecoder().decode(encoded) }
        .getOrElse { throw IllegalArgumentException("Hermes returned invalid spoken audio", it) }
    require(bytes.isNotEmpty() && bytes.size <= MAX_SPOKEN_AUDIO_BYTES) { "Hermes returned invalid spoken audio" }
    return SpokenAudio(bytes, mimeType)
}

private const val MAX_SPOKEN_AUDIO_BYTES = 25 * 1024 * 1024
private const val MAX_SPOKEN_AUDIO_BASE64_CHARS = 35 * 1024 * 1024

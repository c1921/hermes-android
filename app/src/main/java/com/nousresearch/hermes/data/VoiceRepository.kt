package com.nousresearch.hermes.data

import com.nousresearch.hermes.audio.VoiceRecording
import com.nousresearch.hermes.audio.SpokenAudio
import com.nousresearch.hermes.network.HermesRestClient
import com.nousresearch.hermes.security.SecureTokenStore
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class VoiceRepository @Inject constructor(
    private val rest: HermesRestClient,
    private val credentials: SecureTokenStore,
) {
    suspend fun transcribe(config: BackendConfig, recording: VoiceRecording): String {
        try {
            require(recording.durationMillis >= MIN_RECORDING_MILLIS) { "Hold the microphone a little longer before releasing" }
            val bytes = withContext(Dispatchers.IO) {
                val size = recording.file.length()
                require(size in 1..MAX_TRANSCRIPTION_BYTES) { "Voice recording exceeds the Hermes upload limit" }
                recording.file.readBytes()
            }
            val payload = "data:${recording.mimeType};base64,${Base64.getEncoder().encodeToString(bytes)}"
            val response = rest.transcribeAudio(config, credential(config), payload, recording.mimeType)
            if (!response.ok) throw IOException("Hermes could not transcribe the recording")
            return response.transcript.trim()
        } finally {
            recording.file.delete()
        }
    }

    suspend fun speak(config: BackendConfig, text: String): SpokenAudio {
        require(text.isNotBlank()) { "There is no Hermes reply to speak" }
        val response = rest.speakText(config, credential(config), text)
        if (!response.ok) throw IOException("Hermes could not generate a spoken reply")
        return decodeSpokenAudio(response.dataUrl, response.mimeType)
    }

    private fun credential(config: BackendConfig): String = credentials.get(config.id)?.headerValue
        ?: throw IOException("Reconnect ${config.label} before using Hermes voice")

    private companion object {
        const val MIN_RECORDING_MILLIS = 250L
        const val MAX_TRANSCRIPTION_BYTES = 25L * 1024L * 1024L
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

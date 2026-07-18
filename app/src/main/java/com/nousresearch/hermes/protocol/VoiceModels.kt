package com.nousresearch.hermes.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AudioTranscriptionResponse(
    val ok: Boolean,
    val transcript: String = "",
    val provider: String? = null,
)

@Serializable
data class AudioSpeakResponse(
    val ok: Boolean,
    @SerialName("data_url") val dataUrl: String,
    @SerialName("mime_type") val mimeType: String,
    val provider: String? = null,
)

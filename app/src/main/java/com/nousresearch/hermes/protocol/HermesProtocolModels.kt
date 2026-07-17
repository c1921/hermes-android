package com.nousresearch.hermes.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ImageAttachResult(
    val attached: Boolean,
    val path: String,
    val text: String? = null,
    val bytes: Long? = null,
)

@Serializable
data class PdfAttachPage(
    val path: String,
    val page: Int,
)

@Serializable
data class PdfAttachResult(
    val attached: Boolean,
    val filename: String,
    @SerialName("pages_attached") val pagesAttached: Int,
    val pages: List<PdfAttachPage>,
    val text: String? = null,
)

@Serializable
data class FileAttachResult(
    val attached: Boolean,
    val name: String,
    val path: String,
    @SerialName("ref_path") val refPath: String,
    @SerialName("ref_text") val refText: String,
    val uploaded: Boolean,
)

@Serializable
data class StatusResponse(
    val status: String = "unknown",
    val version: String? = null,
    @SerialName("hermes_version") val hermesVersion: String? = null,
    @SerialName("auth_required") val authRequired: Boolean = false,
    val capabilities: JsonElement? = null,
)

@Serializable
data class SessionPage(
    val sessions: List<StoredSession> = emptyList(),
    val total: Int = sessions.size,
    val limit: Int = sessions.size,
    val offset: Int = 0,
)

@Serializable
data class StoredSession(
    @SerialName("session_id") val sessionId: String = "",
    val id: String? = null,
    val title: String? = null,
    val profile: String? = null,
    val source: String? = null,
    val model: String? = null,
    val provider: String? = null,
    val archived: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("started_at") val startedAt: Double = 0.0,
    @SerialName("last_active") val lastActive: Double = startedAt,
) {
    val durableId: String get() = sessionId.ifBlank { id.orEmpty() }
    val displayTitle: String get() = title?.takeIf(String::isNotBlank) ?: "Untitled session"
}

@Serializable
data class SessionMessagePage(
    @SerialName("session_id") val sessionId: String,
    val messages: List<ProtocolMessage> = emptyList(),
)

@Serializable
data class ProtocolMessage(
    val id: String? = null,
    val role: String,
    val content: JsonElement? = null,
    val text: String? = null,
    val timestamp: Double? = null,
    @SerialName("tool_calls") val toolCalls: JsonElement? = null,
)

@Serializable
data class SessionCreateResult(
    @SerialName("session_id") val runtimeSessionId: String,
    @SerialName("session_key") val durableSessionId: String? = null,
    val messages: List<ProtocolMessage> = emptyList(),
    val status: String = "idle",
    val running: Boolean = false,
)

@Serializable
data class SessionResumeResult(
    @SerialName("session_id") val runtimeSessionId: String,
    @SerialName("session_key") val durableSessionId: String? = null,
    val resumed: String? = null,
    val messages: List<ProtocolMessage> = emptyList(),
    val status: String = "idle",
    val running: Boolean = false,
    val inflight: JsonElement? = null,
)

@Serializable
data class ModelOptionsResult(
    val providers: List<ModelProvider> = emptyList(),
    val models: List<ModelOption> = emptyList(),
)

@Serializable
data class ModelProvider(
    val id: String,
    val name: String? = null,
    val models: List<ModelOption> = emptyList(),
)

@Serializable
data class ModelOption(
    val id: String,
    val name: String? = null,
    val provider: String? = null,
    val supportsReasoning: Boolean? = null,
)

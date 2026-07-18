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
data class SessionSearchPage(
    val results: List<SessionSearchHit> = emptyList(),
)

@Serializable
data class SessionSearchHit(
    @SerialName("session_id") val sessionId: String,
    val snippet: String = "",
    val role: String? = null,
    val source: String? = null,
    val model: String? = null,
    @SerialName("session_started") val sessionStarted: Double = 0.0,
    val profile: String? = null,
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
    @Serializable(with = NullableFlexibleStringSerializer::class)
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
    @SerialName("stored_session_id") val durableSessionId: String? = null,
    val messages: List<ProtocolMessage> = emptyList(),
    val status: String = "idle",
    val running: Boolean = false,
    val info: SessionRuntimeInfo = SessionRuntimeInfo(),
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
    val info: SessionRuntimeInfo = SessionRuntimeInfo(),
)

@Serializable
data class ModelOptionsResult(
    val model: String? = null,
    val provider: String? = null,
    val providers: List<ModelProvider> = emptyList(),
)

@Serializable
data class ModelProvider(
    val slug: String,
    val name: String,
    @SerialName("is_current") val isCurrent: Boolean = false,
    val models: List<String> = emptyList(),
    @SerialName("total_models") val totalModels: Int = models.size,
    val warning: String? = null,
    val authenticated: Boolean = true,
    @SerialName("auth_type") val authType: String? = null,
    @SerialName("key_env") val keyEnvironment: String? = null,
    @SerialName("is_user_defined") val isUserDefined: Boolean = false,
    val capabilities: Map<String, ModelCapabilities> = emptyMap(),
)

@Serializable
data class ModelCapabilities(
    val fast: Boolean = false,
    val reasoning: Boolean = false,
)

@Serializable
data class SessionRuntimeInfo(
    val cwd: String = "",
    val model: String = "",
    val provider: String = "",
    @SerialName("reasoning_effort") val reasoningEffort: String = "",
    @SerialName("service_tier") val serviceTier: String = "",
    val fast: Boolean = false,
    val yolo: Boolean = false,
    @SerialName("approval_mode") val approvalMode: String = "manual",
    val running: Boolean = false,
    val title: String = "",
    @SerialName("stored_session_id") val storedSessionId: String = "",
    @SerialName("desktop_contract") val desktopContract: Int? = null,
    val usage: JsonElement? = null,
)

@Serializable
data class ConfigSetResult(
    val key: String,
    val value: String,
    val warning: String? = null,
    @SerialName("confirm_required") val confirmRequired: Boolean = false,
    @SerialName("confirm_message") val confirmMessage: String = "",
    val scope: String? = null,
)

@Serializable
data class SessionTitleResult(
    val title: String,
    @SerialName("session_key") val sessionKey: String? = null,
)

@Serializable
data class SessionBranchResult(
    @SerialName("session_id") val runtimeSessionId: String,
    val title: String,
    val parent: String,
)

@Serializable
data class SessionUndoResult(
    val removed: Int,
)

@Serializable
data class SessionDeleteResult(
    val deleted: String,
)

@Serializable
data class SessionCloseResult(
    val closed: Boolean,
)

@Serializable
data class SlashCommandCategory(
    val name: String = "",
    val pairs: List<List<String>> = emptyList(),
)

@Serializable
data class SlashCommandCatalog(
    val pairs: List<List<String>> = emptyList(),
    val categories: List<SlashCommandCategory> = emptyList(),
    @SerialName("skill_count") val skillCount: Int = 0,
    val warning: String = "",
)

@Serializable
data class SlashCompletionItem(
    val text: String,
    val display: String = text,
    val meta: String = "",
)

@Serializable
data class SlashCompletionResult(
    val items: List<SlashCompletionItem> = emptyList(),
    @SerialName("replace_from") val replaceFrom: Int = 1,
)

@Serializable
data class SlashCommandResult(
    val type: String? = null,
    val output: String? = null,
    val warning: String? = null,
    val target: String? = null,
    val message: String? = null,
    val notice: String? = null,
    val name: String? = null,
)

@Serializable
data class SessionHistoryResult(
    val count: Int,
    val messages: List<ProtocolMessage> = emptyList(),
)

@Serializable
data class SessionCompressResult(
    val status: String,
    val removed: Int = 0,
    @SerialName("before_messages") val beforeMessages: Int = 0,
    @SerialName("after_messages") val afterMessages: Int = 0,
    val messages: List<ProtocolMessage> = emptyList(),
    val info: SessionRuntimeInfo? = null,
)

@Serializable
data class SessionSteerResult(
    val status: String,
    val text: String,
)

@Serializable
data class PromptSubmitResult(
    val status: String,
)

package com.nousresearch.hermes.domain

import com.nousresearch.hermes.protocol.GatewayEvent
import com.nousresearch.hermes.protocol.ProtocolMessage
import com.nousresearch.hermes.protocol.SessionInflightProjection
import com.nousresearch.hermes.protocol.SessionQueuedProjection
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

sealed interface TimelineItem {
    val id: String

    data class Message(
        override val id: String,
        val role: MessageRole,
        val text: String,
        val streaming: Boolean = false,
        val failed: Boolean = false,
    ) : TimelineItem

    data class Tool(
        override val id: String,
        val name: String,
        val context: String? = null,
        val state: ToolState,
        val summary: String? = null,
        val detail: String? = null,
        val durationSeconds: Double? = null,
    ) : TimelineItem

    data class Reasoning(
        override val id: String,
        val text: String,
        val streaming: Boolean,
    ) : TimelineItem

    data class Status(
        override val id: String,
        val kind: String,
        val text: String,
    ) : TimelineItem
}

enum class MessageRole { USER, ASSISTANT, SYSTEM, TOOL }
enum class ToolState { RUNNING, COMPLETE, FAILED }

data class ApprovalRequest(
    val sessionId: String,
    val command: String,
    val description: String?,
    val choices: List<String>,
)

data class ClarificationRequest(
    val sessionId: String,
    val requestId: String,
    val question: String,
    val choices: List<String>,
)

enum class SensitiveInputKind { SUDO_PASSWORD, SECRET }

data class SensitiveInputRequest(
    val sessionId: String,
    val requestId: String,
    val kind: SensitiveInputKind,
    val prompt: String,
    val environmentVariable: String? = null,
)

data class TimelineState(
    val items: List<TimelineItem> = emptyList(),
    val approval: ApprovalRequest? = null,
    val clarification: ClarificationRequest? = null,
    val sensitiveInput: SensitiveInputRequest? = null,
    val generation: Long = 0,
)

object TimelineReducer {
    fun hydrate(messages: List<ProtocolMessage>): TimelineState {
        val items = messages.mapIndexed { index, message ->
            TimelineItem.Message(
                id = message.id ?: "history:$index:${message.role}",
                role = message.role.toMessageRole(),
                text = displayText(message.content, message.text),
            )
        }
        return TimelineState(items = items)
    }

    fun reconcileResume(
        messages: List<ProtocolMessage>,
        runtimeSessionId: String,
        inflight: SessionInflightProjection?,
        queued: SessionQueuedProjection?,
        running: Boolean,
        previousRuntimeSessionId: String?,
        previous: TimelineState,
    ): TimelineState {
        val state = hydrate(messages)
        val sameRuntime = previousRuntimeSessionId == runtimeSessionId
        val liveItems = buildList {
            inflight?.user?.trim()?.takeIf(String::isNotEmpty)?.let {
                add(TimelineItem.Message("resume:user:$runtimeSessionId", MessageRole.USER, it))
            }
            if (
                inflight != null &&
                (inflight.assistant.isNotEmpty() || inflight.streaming || (inflight.user.isNotBlank() && queued?.user?.isNotBlank() == true))
            ) {
                add(
                    TimelineItem.Message(
                        "resume:assistant:$runtimeSessionId",
                        MessageRole.ASSISTANT,
                        inflight.assistant,
                        streaming = inflight.streaming,
                    ),
                )
            }
            queued?.user?.trim()?.takeIf(String::isNotEmpty)?.let {
                add(TimelineItem.Message("resume:queued:$runtimeSessionId", MessageRole.USER, it))
            }
        }
        val preserveLiveState = running && sameRuntime
        val reconciled = state.copy(
            items = state.items + liveItems,
            approval = previous.approval.takeIf { preserveLiveState },
            clarification = previous.clarification.takeIf { preserveLiveState },
            sensitiveInput = previous.sensitiveInput.takeIf { preserveLiveState },
            generation = previous.generation.takeIf { sameRuntime } ?: state.generation,
        )
        if (!preserveLiveState) return reconciled

        val authoritativeByRole = mutableMapOf<Pair<MessageRole, Int>, TimelineItem.Message>()
        val authoritativeCounts = mutableMapOf<MessageRole, Int>()
        reconciled.items.filterIsInstance<TimelineItem.Message>().forEach { message ->
            val ordinal = authoritativeCounts.getOrDefault(message.role, 0)
            authoritativeCounts[message.role] = ordinal + 1
            authoritativeByRole[message.role to ordinal] = message
        }

        val previousCounts = mutableMapOf<MessageRole, Int>()
        val pending = previous.items.filterIsInstance<TimelineItem.Message>().mapNotNull { message ->
            val ordinal = previousCounts.getOrDefault(message.role, 0)
            previousCounts[message.role] = ordinal + 1
            val isLocalUser = message.role == MessageRole.USER && message.id.startsWith("local:")
            val isStreamingAssistant = message.role == MessageRole.ASSISTANT && message.streaming
            if (!isLocalUser && !isStreamingAssistant) return@mapNotNull null
            val authoritative = authoritativeByRole[message.role to ordinal]
            when {
                authoritative == null -> message
                isLocalUser && authoritative.text.trim() != message.text.trim() -> message
                else -> null
            }
        }
        val pendingIds = pending.mapTo(mutableSetOf(), TimelineItem::id)
        val authoritativeIds = reconciled.items.mapTo(mutableSetOf(), TimelineItem::id)
        val preserved = previous.items.filter {
            it.id in pendingIds ||
                (it.id !in authoritativeIds &&
                    ((it is TimelineItem.Tool && it.state == ToolState.RUNNING) ||
                        (it is TimelineItem.Reasoning && it.streaming)))
        }
        val insertionIndex = reconciled.items.indexOfFirst {
            it.id == "resume:assistant:$runtimeSessionId" || it.id == "resume:queued:$runtimeSessionId"
        }.takeIf { it >= 0 } ?: reconciled.items.size
        return reconciled.copy(
            items = reconciled.items.toMutableList().apply { addAll(insertionIndex, preserved) },
        )
    }

    fun reduce(state: TimelineState, event: GatewayEvent): TimelineState {
        val payload = event.payload as? JsonObject ?: JsonObject(emptyMap())
        return when (event.type) {
            "message.start" -> {
                val nextGeneration = state.generation + 1
                state.copy(
                    generation = nextGeneration,
                    items = state.items + TimelineItem.Message(
                        id = assistantId(event.sessionId, nextGeneration),
                        role = MessageRole.ASSISTANT,
                        text = "",
                        streaming = true,
                    ),
                )
            }

            "message.delta" -> appendAssistantDelta(state, event.sessionId, payload.text("text"))
            "message.complete" -> completeAssistant(
                state,
                event.sessionId,
                payload.text("text"),
                payload.text("status") == "error",
            )

            "reasoning.delta", "thinking.delta" -> appendReasoning(
                state,
                event.sessionId,
                event.type,
                payload.text("text"),
            )

            "tool.start" -> upsertTool(
                state,
                TimelineItem.Tool(
                    id = payload.text("tool_id").ifBlank { "tool:${state.generation}:${payload.text("name")}" },
                    name = payload.text("name").ifBlank { "tool" },
                    context = payload.text("context").ifBlank { null },
                    detail = payload.text("args_text").ifBlank { null },
                    state = ToolState.RUNNING,
                ),
            )

            "tool.complete" -> upsertTool(
                state,
                TimelineItem.Tool(
                    id = payload.text("tool_id").ifBlank { "tool:${state.generation}:${payload.text("name")}" },
                    name = payload.text("name").ifBlank { "tool" },
                    summary = payload.text("summary").ifBlank { null },
                    detail = payload.text("result_text").ifBlank {
                        payload["result"]?.toString()?.take(20_000)
                    },
                    durationSeconds = (payload["duration_s"] as? JsonPrimitive)?.doubleOrNull,
                    state = if (payload.boolean("failed")) ToolState.FAILED else ToolState.COMPLETE,
                ),
            )

            "status.update" -> updateStatus(state, event.sessionId, payload)

            "approval.request" -> state.copy(
                approval = ApprovalRequest(
                    sessionId = event.sessionId.orEmpty(),
                    command = payload.text("command"),
                    description = payload.text("description").ifBlank { null },
                    choices = payload.stringList("choices").ifEmpty {
                        listOf("once", "session", "deny")
                    },
                ),
            )

            "clarify.request" -> state.copy(
                clarification = ClarificationRequest(
                    sessionId = event.sessionId.orEmpty(),
                    requestId = payload.text("request_id"),
                    question = payload.text("question"),
                    choices = payload.stringList("choices"),
                ),
            )

            "sudo.request" -> payload.text("request_id").takeIf(String::isNotBlank)?.let { requestId ->
                state.copy(
                    sensitiveInput = SensitiveInputRequest(
                        sessionId = event.sessionId.orEmpty(),
                        requestId = requestId,
                        kind = SensitiveInputKind.SUDO_PASSWORD,
                        prompt = "Hermes needs a sudo password to continue this command.",
                    ),
                )
            } ?: state

            "secret.request" -> payload.text("request_id").takeIf(String::isNotBlank)?.let { requestId ->
                state.copy(
                    sensitiveInput = SensitiveInputRequest(
                        sessionId = event.sessionId.orEmpty(),
                        requestId = requestId,
                        kind = SensitiveInputKind.SECRET,
                        prompt = payload.text("prompt").ifBlank { "Hermes needs a secret value to continue." },
                        environmentVariable = payload.text("env_var").ifBlank { null },
                    ),
                )
            } ?: state

            "sudo.expire", "secret.expire" -> if (state.sensitiveInput?.requestId == payload.text("request_id")) {
                state.copy(sensitiveInput = null)
            } else {
                state
            }

            else -> state
        }
    }

    private fun updateStatus(state: TimelineState, sessionId: String?, payload: JsonObject): TimelineState {
        val text = payload.text("text").trim()
        if (text.isBlank()) return state
        val items = state.items.filterNot { it is TimelineItem.Status }
        if (payload.text("kind") == "status" && text.equals("ready", ignoreCase = true)) {
            return state.copy(items = items)
        }
        return state.copy(
            items = items + TimelineItem.Status(
                id = "status:${sessionId.orEmpty()}",
                kind = payload.text("kind").ifBlank { "status" },
                text = text,
            ),
        )
    }

    fun appendUserMessage(state: TimelineState, id: String, text: String): TimelineState = state.copy(
        items = state.items + TimelineItem.Message(id, MessageRole.USER, text),
    )

    fun insertAcceptedUserMessage(state: TimelineState, id: String, text: String): TimelineState {
        if (state.items.any { it.id == id }) return state
        val assistantIndex = state.items.indexOfLast {
            it is TimelineItem.Message && it.role == MessageRole.ASSISTANT && it.streaming
        }
        val user = TimelineItem.Message(id, MessageRole.USER, text)
        return if (assistantIndex < 0) {
            state.copy(items = state.items + user)
        } else {
            state.copy(items = state.items.toMutableList().apply { add(assistantIndex, user) })
        }
    }

    fun appendSystemMessage(state: TimelineState, id: String, text: String): TimelineState = state.copy(
        items = state.items + TimelineItem.Message(id, MessageRole.SYSTEM, text),
    )

    fun removeLastExchange(state: TimelineState): TimelineState {
        val lastUserIndex = state.items.indexOfLast {
            it is TimelineItem.Message && it.role == MessageRole.USER
        }
        return if (lastUserIndex < 0) state else state.copy(
            items = state.items.take(lastUserIndex),
            approval = null,
            clarification = null,
            sensitiveInput = null,
        )
    }

    fun clearApproval(state: TimelineState) = state.copy(approval = null)
    fun clearClarification(state: TimelineState) = state.copy(clarification = null)
    fun clearSensitiveInput(state: TimelineState) = state.copy(sensitiveInput = null)

    private fun appendAssistantDelta(state: TimelineState, sessionId: String?, delta: String): TimelineState {
        val index = state.items.indexOfLast {
            it is TimelineItem.Message && it.role == MessageRole.ASSISTANT && it.streaming
        }
        if (index < 0) {
            val next = state.generation + 1
            return state.copy(
                generation = next,
                items = state.items + TimelineItem.Message(
                    assistantId(sessionId, next),
                    MessageRole.ASSISTANT,
                    delta,
                    streaming = true,
                ),
            )
        }
        val current = state.items[index] as TimelineItem.Message
        return state.copy(items = state.items.replaced(index, current.copy(text = current.text + delta)))
    }

    private fun completeAssistant(
        state: TimelineState,
        sessionId: String?,
        text: String,
        failed: Boolean,
    ): TimelineState {
        val index = state.items.indexOfLast {
            it is TimelineItem.Message && it.role == MessageRole.ASSISTANT && it.streaming
        }
        if (index < 0) {
            return state.copy(
                items = state.items + TimelineItem.Message(
                    assistantId(sessionId, state.generation + 1),
                    MessageRole.ASSISTANT,
                    text,
                    failed = failed,
                ),
                generation = state.generation + 1,
            )
        }
        val current = state.items[index] as TimelineItem.Message
        val finalText = text.ifBlank { current.text }
        return state.copy(items = state.items.replaced(index, current.copy(text = finalText, streaming = false, failed = failed)))
    }

    private fun appendReasoning(
        state: TimelineState,
        sessionId: String?,
        kind: String,
        delta: String,
    ): TimelineState {
        val id = "reasoning:${sessionId.orEmpty()}:${state.generation}:$kind"
        val index = state.items.indexOfLast { it.id == id }
        if (index < 0) {
            return state.copy(items = state.items + TimelineItem.Reasoning(id, delta, streaming = true))
        }
        val current = state.items[index] as TimelineItem.Reasoning
        return state.copy(items = state.items.replaced(index, current.copy(text = current.text + delta)))
    }

    private fun upsertTool(state: TimelineState, tool: TimelineItem.Tool): TimelineState {
        val index = state.items.indexOfFirst { it.id == tool.id }
        if (index < 0) return state.copy(items = state.items + tool)
        val current = state.items[index] as? TimelineItem.Tool
        val merged = tool.copy(
            context = tool.context ?: current?.context,
            detail = tool.detail ?: current?.detail,
        )
        return state.copy(items = state.items.replaced(index, merged))
    }

    private fun assistantId(sessionId: String?, generation: Long) =
        "assistant:${sessionId.orEmpty()}:$generation"
}

fun lastUserPrompt(messages: List<ProtocolMessage>): String? = messages.asReversed()
    .firstOrNull { it.role.equals("user", ignoreCase = true) }
    ?.let { retryText(it.content, it.text).trim() }
    ?.takeIf(String::isNotEmpty)

private fun retryText(content: JsonElement?, fallback: String?): String = when (content) {
    is JsonArray -> content.joinToString(" ") { part ->
        (part as? JsonObject)?.text("text").orEmpty()
    }
    else -> displayText(content, fallback)
}

private fun String.toMessageRole() = when (lowercase()) {
    "user" -> MessageRole.USER
    "assistant" -> MessageRole.ASSISTANT
    "tool" -> MessageRole.TOOL
    else -> MessageRole.SYSTEM
}

private fun displayText(content: JsonElement?, fallback: String?): String = when (content) {
    is JsonPrimitive -> content.contentOrNull.orEmpty()
    is JsonArray -> content.joinToString("\n") { part ->
        (part as? JsonObject)?.text("text").orEmpty()
    }
    is JsonObject -> content.text("text").ifBlank { content.toString() }
    else -> fallback.orEmpty()
}

private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true
private fun JsonObject.stringList(key: String): List<String> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

private fun <T> List<T>.replaced(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }

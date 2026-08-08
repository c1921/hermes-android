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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

enum class TimelineIdentityKind { SERVER, GENERATED_FALLBACK }

data class TimelineIdentity(
    val value: String,
    val kind: TimelineIdentityKind,
    val runtimeSessionId: String? = null,
    val parentServerId: String? = null,
    val source: String? = null,
) {
    companion object {
        fun server(
            value: String,
            runtimeSessionId: String? = null,
            parentServerId: String? = null,
            source: String? = null,
        ) = TimelineIdentity(value, TimelineIdentityKind.SERVER, runtimeSessionId, parentServerId, source)

        fun fallback(
            value: String,
            runtimeSessionId: String? = null,
            parentServerId: String? = null,
            source: String? = null,
        ) = TimelineIdentity(value, TimelineIdentityKind.GENERATED_FALLBACK, runtimeSessionId, parentServerId, source)
    }
}

sealed interface TimelineItem {
    val id: String
    val identity: TimelineIdentity

    data class Message(
        override val id: String,
        val role: MessageRole,
        val text: String,
        val streaming: Boolean = false,
        val failed: Boolean = false,
        override val identity: TimelineIdentity = TimelineIdentity.fallback(id),
    ) : TimelineItem

    data class Tool(
        override val id: String,
        val name: String,
        val context: String? = null,
        val state: ToolState,
        val summary: String? = null,
        val detail: String? = null,
        val durationSeconds: Double? = null,
        override val identity: TimelineIdentity = TimelineIdentity.fallback(id),
    ) : TimelineItem

    data class Reasoning(
        override val id: String,
        val text: String,
        val streaming: Boolean,
        override val identity: TimelineIdentity = TimelineIdentity.fallback(id),
    ) : TimelineItem

    data class Status(
        override val id: String,
        val kind: String,
        val text: String,
        override val identity: TimelineIdentity = TimelineIdentity.fallback(id),
    ) : TimelineItem

    data class Reference(
        override val id: String,
        val label: String,
        val text: String,
        val index: Int? = null,
        val count: Int? = null,
        override val identity: TimelineIdentity = TimelineIdentity.fallback(id),
    ) : TimelineItem

    data class Artifact(
        override val id: String,
        val label: String,
        val mimeType: String? = null,
        val reference: String? = null,
        val description: String? = null,
        override val identity: TimelineIdentity = TimelineIdentity.fallback(id),
    ) : TimelineItem

    data class Error(
        override val id: String,
        val message: String,
        val recoverable: Boolean = false,
        override val identity: TimelineIdentity = TimelineIdentity.fallback(id),
    ) : TimelineItem

    data class BlockingRequest(
        override val id: String,
        val kind: BlockingRequestKind,
        val prompt: String,
        val choices: List<String> = emptyList(),
        override val identity: TimelineIdentity = TimelineIdentity.fallback(id),
    ) : TimelineItem

    data class Unknown(
        override val id: String,
        val type: String,
        val summary: String,
        override val identity: TimelineIdentity = TimelineIdentity.fallback(id),
    ) : TimelineItem
}

internal fun List<TimelineItem>.indexOfServerMessage(messageId: String): Int {
    if (messageId.isBlank()) return -1
    return indexOfFirst { item ->
        item.identity.value == messageId ||
            item.identity.parentServerId == messageId ||
            item.id == messageId
    }
}

enum class MessageRole { USER, ASSISTANT, SYSTEM, TOOL }
enum class ToolState { RUNNING, COMPLETE, FAILED }
enum class BlockingRequestKind { APPROVAL, CLARIFICATION, SUDO_PASSWORD, SECRET }

enum class TimelineSyncMode { AUTHORITATIVE_RESUME, EVENT_REPLAY }

data class TimelineSyncState(
    val mode: TimelineSyncMode = TimelineSyncMode.AUTHORITATIVE_RESUME,
    val resyncRequired: Boolean = false,
    val reason: String? = null,
)

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
    val runtimeSessionId: String? = null,
    val interimBoundary: Boolean = false,
    val sync: TimelineSyncState = TimelineSyncState(),
)

object TimelineReducer {
    fun hydrate(messages: List<ProtocolMessage>): TimelineState {
        val items = messages.flatMapIndexed(::historyMessageParts)
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
            runtimeSessionId = runtimeSessionId,
            sync = TimelineSyncState(
                mode = TimelineSyncMode.AUTHORITATIVE_RESUME,
                resyncRequired = false,
                reason = null,
            ),
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
                        (it is TimelineItem.Reasoning && it.streaming) ||
                        it is TimelineItem.BlockingRequest))
        }
        val insertionIndex = reconciled.items.indexOfFirst {
            it.id == "resume:assistant:$runtimeSessionId" || it.id == "resume:queued:$runtimeSessionId"
        }.takeIf { it >= 0 } ?: reconciled.items.size
        return reconciled.copy(
            items = reconciled.items.toMutableList().apply { addAll(insertionIndex, preserved) },
        )
    }

    fun reduce(previous: TimelineState, event: GatewayEvent): TimelineState {
        if (event.type !in TIMELINE_EVENT_TYPES) return previous
        val eventRuntime = event.sessionId?.takeIf(String::isNotBlank)
        if (
            previous.runtimeSessionId != null &&
            previous.runtimeSessionId != eventRuntime
        ) {
            return previous
        }
        val state = if (previous.runtimeSessionId == null && eventRuntime != null) {
            previous.copy(runtimeSessionId = eventRuntime)
        } else {
            previous
        }
        val payload = event.payload as? JsonObject ?: JsonObject(emptyMap())
        return when (event.type) {
            "message.start" -> {
                val nextGeneration = state.generation + 1
                if (state.hasOpenAssistant() || !state.hasUserAfterLastAssistant()) return state
                val id = assistantId(event.sessionId, nextGeneration)
                state.copy(
                    generation = nextGeneration,
                    interimBoundary = false,
                    items = state.items + TimelineItem.Message(
                        id = id,
                        role = MessageRole.ASSISTANT,
                        text = "",
                        streaming = true,
                        identity = TimelineIdentity.fallback(
                            id,
                            event.sessionId,
                            source = event.type,
                        ),
                    ),
                )
            }

            "message.interim" -> sealInterimAssistant(state, event.sessionId, payload.text("text"))
            "message.delta" -> appendAssistantDelta(state, event.sessionId, payload.text("text"))
            "message.complete" -> completeAssistant(
                state,
                event.sessionId,
                payload.text("text"),
                payload.text("status") == "error",
                payload.text("error").ifBlank { null },
                payload.boolean("partial"),
                payload.boolean("recoverable"),
            )

            "reasoning.delta", "reasoning.available" -> appendReasoning(
                state,
                event.sessionId,
                event.type,
                payload.text("text"),
                replace = event.type == "reasoning.available",
            )

            "thinking.delta" -> state

            "moa.reference" -> upsertReference(state, event.sessionId, payload)

            "tool.start", "tool.progress" -> upsertTool(
                state,
                TimelineItem.Tool(
                    id = toolIdentity(payload, state, event.type).first,
                    name = payload.text("name").ifBlank { "tool" }.take(MAX_TIMELINE_LABEL_CHARACTERS),
                    context = payload.text("context").ifBlank { null }?.take(MAX_TIMELINE_SUMMARY_CHARACTERS),
                    detail = payload.text("args_text").ifBlank {
                        payload["args"]?.toString().orEmpty()
                    }.ifBlank { null }?.take(MAX_TIMELINE_TOOL_DETAIL_CHARACTERS),
                    state = ToolState.RUNNING,
                    identity = toolIdentity(payload, state, event.type).second.copy(
                        runtimeSessionId = event.sessionId,
                        source = event.type,
                    ),
                ),
            )

            "tool.complete" -> upsertTool(
                state,
                TimelineItem.Tool(
                    id = toolIdentity(payload, state, event.type).first,
                    name = payload.text("name").ifBlank { "tool" }.take(MAX_TIMELINE_LABEL_CHARACTERS),
                    summary = payload.text("summary").ifBlank { null }?.take(MAX_TIMELINE_SUMMARY_CHARACTERS),
                    detail = payload.text("result_text").ifBlank {
                        payload["result"]?.toString()
                    }?.take(MAX_TIMELINE_TOOL_DETAIL_CHARACTERS),
                    durationSeconds = (payload["duration_s"] as? JsonPrimitive)?.doubleOrNull,
                    state = if (payload.boolean("failed")) ToolState.FAILED else ToolState.COMPLETE,
                    identity = toolIdentity(payload, state, event.type).second.copy(
                        runtimeSessionId = event.sessionId,
                        source = event.type,
                    ),
                ),
            )

            "status.update" -> updateStatus(state, event.sessionId, payload)

            "approval.request" -> {
                val request = ApprovalRequest(
                    sessionId = event.sessionId.orEmpty(),
                    command = payload.text("command").take(MAX_TIMELINE_SUMMARY_CHARACTERS),
                    description = payload.text("description").ifBlank { null }?.take(MAX_TIMELINE_SUMMARY_CHARACTERS),
                    choices = payload.stringList("choices").ifEmpty {
                        listOf("once", "session", "deny")
                    }.take(MAX_TIMELINE_CHOICES).map { it.take(MAX_TIMELINE_CHOICE_CHARACTERS) },
                )
                state.copy(
                    approval = request,
                    items = upsertBlockingPart(
                        state.items,
                        blockingPart(event.sessionId, payload, BlockingRequestKind.APPROVAL, request.description ?: request.command, request.choices),
                    ),
                )
            }

            "clarify.request" -> {
                val request = ClarificationRequest(
                    sessionId = event.sessionId.orEmpty(),
                    requestId = payload.text("request_id").take(MAX_TIMELINE_LABEL_CHARACTERS),
                    question = payload.text("question").take(MAX_TIMELINE_SUMMARY_CHARACTERS),
                    choices = payload.stringList("choices").take(MAX_TIMELINE_CHOICES)
                        .map { it.take(MAX_TIMELINE_CHOICE_CHARACTERS) },
                )
                if (request.requestId.isBlank() || request.question.isBlank()) return state
                state.copy(
                    clarification = request,
                    items = upsertBlockingPart(
                        state.items,
                        blockingPart(event.sessionId, payload, BlockingRequestKind.CLARIFICATION, request.question, request.choices),
                    ),
                )
            }

            "sudo.request" -> payload.text("request_id").takeIf(String::isNotBlank)?.let { requestId ->
                state.copy(
                    sensitiveInput = SensitiveInputRequest(
                        sessionId = event.sessionId.orEmpty(),
                        requestId = requestId,
                        kind = SensitiveInputKind.SUDO_PASSWORD,
                        prompt = "Hermes needs a sudo password to continue this command.",
                    ),
                    items = upsertBlockingPart(
                        state.items,
                        blockingPart(
                            event.sessionId,
                            payload,
                            BlockingRequestKind.SUDO_PASSWORD,
                            "Hermes needs a sudo password to continue this command.",
                            emptyList(),
                        ),
                    ),
                )
            } ?: state

            "secret.request" -> payload.text("request_id").takeIf(String::isNotBlank)?.let { requestId ->
                state.copy(
                    sensitiveInput = SensitiveInputRequest(
                        sessionId = event.sessionId.orEmpty(),
                        requestId = requestId,
                        kind = SensitiveInputKind.SECRET,
                        prompt = payload.text("prompt").ifBlank { "Hermes needs a secret value to continue." }
                            .take(MAX_TIMELINE_SUMMARY_CHARACTERS),
                        environmentVariable = payload.text("env_var").ifBlank { null }
                            ?.take(MAX_TIMELINE_LABEL_CHARACTERS),
                    ),
                    items = upsertBlockingPart(
                        state.items,
                        blockingPart(
                            event.sessionId,
                            payload,
                            BlockingRequestKind.SECRET,
                            payload.text("prompt").ifBlank { "Hermes needs a secret value to continue." },
                            emptyList(),
                        ),
                    ),
                )
            } ?: state

            "sudo.expire", "secret.expire" -> if (state.sensitiveInput?.requestId == payload.text("request_id")) {
                state.copy(
                    sensitiveInput = null,
                    items = state.items.filterNot { it is TimelineItem.BlockingRequest && it.id == "request:${payload.text("request_id")}" },
                )
            } else {
                state
            }

            "error" -> appendError(state, event.sessionId, payload)

            else -> state
        }
    }

    private fun updateStatus(state: TimelineState, sessionId: String?, payload: JsonObject): TimelineState {
        val text = payload.text("text").trim().take(MAX_TIMELINE_SUMMARY_CHARACTERS)
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
        interimBoundary = false,
        items = state.items + TimelineItem.Message(id, MessageRole.USER, text),
    )

    fun insertAcceptedUserMessage(state: TimelineState, id: String, text: String): TimelineState {
        if (state.items.any { it.id == id }) return state
        val assistantIndex = state.items.indexOfLast {
            it is TimelineItem.Message && it.role == MessageRole.ASSISTANT && it.streaming
        }
        val user = TimelineItem.Message(id, MessageRole.USER, text)
        return if (assistantIndex < 0) {
            state.copy(interimBoundary = false, items = state.items + user)
        } else {
            state.copy(interimBoundary = false, items = state.items.toMutableList().apply { add(assistantIndex, user) })
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

    fun clearApproval(state: TimelineState) = state.copy(
        approval = null,
        items = state.items.filterNot { it is TimelineItem.BlockingRequest && it.kind == BlockingRequestKind.APPROVAL },
    )
    fun clearClarification(state: TimelineState) = state.copy(
        clarification = null,
        items = state.items.filterNot { it is TimelineItem.BlockingRequest && it.kind == BlockingRequestKind.CLARIFICATION },
    )
    fun clearSensitiveInput(state: TimelineState) = state.copy(
        sensitiveInput = null,
        items = state.items.filterNot {
            it is TimelineItem.BlockingRequest &&
                it.kind in setOf(BlockingRequestKind.SUDO_PASSWORD, BlockingRequestKind.SECRET)
        },
    )

    private fun appendAssistantDelta(state: TimelineState, sessionId: String?, delta: String): TimelineState {
        if (delta.isEmpty()) return state
        val index = state.items.indexOfLast {
            it is TimelineItem.Message && it.role == MessageRole.ASSISTANT && it.streaming
        }
        if (index < 0) {
            if (!state.hasUserAfterLastAssistant() && !state.interimBoundary) return state
            val next = state.generation + 1
            return state.copy(
                generation = next,
                interimBoundary = false,
                items = state.items + TimelineItem.Message(
                    assistantId(sessionId, next),
                    MessageRole.ASSISTANT,
                    delta.take(MAX_TIMELINE_TEXT_CHARACTERS),
                    streaming = true,
                    identity = TimelineIdentity.fallback(
                        assistantId(sessionId, next),
                        sessionId,
                        source = "message.delta",
                    ),
                ),
            )
        }
        val current = state.items[index] as TimelineItem.Message
        return state.copy(
            interimBoundary = false,
            items = state.items.replaced(index, current.copy(text = current.text.boundedAppend(delta))),
        )
    }

    private fun sealInterimAssistant(
        state: TimelineState,
        sessionId: String?,
        text: String,
    ): TimelineState {
        val interimText = text.take(MAX_TIMELINE_TEXT_CHARACTERS)
        if (interimText.isBlank()) return state
        if (state.interimBoundary && state.items.any {
                it is TimelineItem.Message &&
                    it.role == MessageRole.ASSISTANT &&
                    it.identity.source == "message.interim" &&
                    it.text == interimText
            }) {
            return state
        }
        val streamingIndex = state.items.indexOfLast {
            it is TimelineItem.Message && it.role == MessageRole.ASSISTANT && it.streaming
        }
        if (streamingIndex >= 0) {
            val current = state.items[streamingIndex] as TimelineItem.Message
            return state.copy(
                interimBoundary = true,
                items = state.items.replaced(
                    streamingIndex,
                    current.copy(
                        text = interimText,
                        streaming = false,
                        identity = current.identity.copy(source = "message.interim"),
                    ),
                ),
            )
        }
        val nextGeneration = state.generation + 1
        val id = "${assistantId(sessionId, nextGeneration)}:interim"
        return state.copy(
            generation = nextGeneration,
            interimBoundary = true,
            items = state.items + TimelineItem.Message(
                id = id,
                role = MessageRole.ASSISTANT,
                text = interimText,
                identity = TimelineIdentity.fallback(id, sessionId, source = "message.interim"),
            ),
        )
    }

    private fun completeAssistant(
        state: TimelineState,
        sessionId: String?,
        text: String,
        failed: Boolean,
        errorMessage: String?,
        partial: Boolean,
        recoverable: Boolean,
    ): TimelineState {
        val streamingIndex = state.items.indexOfLast {
            it is TimelineItem.Message && it.role == MessageRole.ASSISTANT && it.streaming
        }
        val index = if (streamingIndex >= 0) {
            streamingIndex
        } else {
            state.lastAssistantIndex().takeIf { it >= 0 && !state.hasUserAfterLastAssistant() } ?: -1
        }
        if (index < 0) {
            val nextGeneration = state.generation + 1
            val id = assistantId(sessionId, nextGeneration)
            val completed = state.items.filterNot { it is TimelineItem.BlockingRequest } + TimelineItem.Message(
                    id,
                    MessageRole.ASSISTANT,
                    text.take(MAX_TIMELINE_TEXT_CHARACTERS),
                    failed = failed,
                    identity = TimelineIdentity.fallback(
                        id,
                        sessionId,
                        source = "message.complete",
                    ),
                )
            return state.copy(
                items = completed.withTerminalError(sessionId, nextGeneration, failed, errorMessage, text, partial, recoverable),
                generation = nextGeneration,
                interimBoundary = false,
                approval = null,
                clarification = null,
                sensitiveInput = null,
            )
        }
        val current = state.items[index] as TimelineItem.Message
        val finalText = text.ifBlank { current.text }.take(MAX_TIMELINE_TEXT_CHARACTERS)
        val completed = state.items.replaced(
            index,
            current.copy(
                text = finalText,
                streaming = false,
                failed = failed,
            ),
        ).filterNot { it is TimelineItem.BlockingRequest }
        return state.copy(
            items = completed.withTerminalError(
                sessionId,
                state.generation,
                failed,
                errorMessage,
                finalText,
                partial,
                recoverable,
            ),
            interimBoundary = false,
            approval = null,
            clarification = null,
            sensitiveInput = null,
        )
    }

    private fun appendReasoning(
        state: TimelineState,
        sessionId: String?,
        kind: String,
        delta: String,
        replace: Boolean = false,
    ): TimelineState {
        if (delta.isEmpty()) return state
        val id = "reasoning:${sessionId.orEmpty()}:${state.generation}:$kind"
        val index = state.items.indexOfLast { it.id == id }
        if (index < 0) {
            return state.copy(
                items = state.items + TimelineItem.Reasoning(
                    id,
                    delta,
                    streaming = kind == "reasoning.delta",
                    identity = TimelineIdentity.fallback(
                        id,
                        sessionId,
                        source = kind,
                    ),
                ),
            )
        }
        val current = state.items[index] as TimelineItem.Reasoning
        return state.copy(
            items = state.items.replaced(
                index,
                current.copy(text = if (replace) delta.take(MAX_TIMELINE_TEXT_CHARACTERS) else current.text.boundedAppend(delta)),
            ),
        )
    }

    private fun upsertReference(state: TimelineState, sessionId: String?, payload: JsonObject): TimelineState {
        val label = payload.text("label").ifBlank { "Reference" }.take(MAX_TIMELINE_LABEL_CHARACTERS)
        val index = (payload["index"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        val count = (payload["count"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        val id = "reference:${sessionId.orEmpty()}:${state.generation}:${index ?: state.items.size}"
        val item = TimelineItem.Reference(
            id = id,
            label = label,
            text = payload.text("text").take(MAX_TIMELINE_SUMMARY_CHARACTERS),
            index = index,
            count = count,
            identity = TimelineIdentity.fallback(
                id,
                sessionId,
                source = "moa.reference",
            ),
        )
        val existing = state.items.indexOfFirst { it.id == id }
        return if (existing < 0) state.copy(items = state.items + item) else state.copy(items = state.items.replaced(existing, item))
    }

    private fun appendError(state: TimelineState, sessionId: String?, payload: JsonObject): TimelineState {
        val message = payload.text("message").ifBlank { payload.text("error") }.ifBlank { "Hermes reported an error" }
            .take(MAX_TIMELINE_SUMMARY_CHARACTERS)
        val id = "error:${sessionId.orEmpty()}:${state.generation}:event"
        val item = TimelineItem.Error(
            id = id,
            message = message,
            recoverable = payload.boolean("recoverable"),
            identity = TimelineIdentity.fallback(
                id,
                sessionId,
                source = "error",
            ),
        )
        return state.copy(
            items = state.items.filterNot { it is TimelineItem.BlockingRequest }.upsertById(item),
            approval = null,
            clarification = null,
            sensitiveInput = null,
        )
    }

    private fun upsertTool(state: TimelineState, tool: TimelineItem.Tool): TimelineState {
        val index = state.items.indexOfFirst { it.id == tool.id }
        if (index < 0) return state.copy(items = state.items + tool)
        val current = state.items[index] as? TimelineItem.Tool
        if (current?.state in setOf(ToolState.COMPLETE, ToolState.FAILED) && tool.state == ToolState.RUNNING) return state
        val merged = tool.copy(
            context = tool.context ?: current?.context,
            detail = tool.detail ?: current?.detail,
        )
        return state.copy(items = state.items.replaced(index, merged))
    }

    private fun assistantId(sessionId: String?, generation: Long) =
        "assistant:${sessionId.orEmpty()}:$generation"
}

private fun TimelineState.lastAssistantIndex(): Int = items.indexOfLast {
    it is TimelineItem.Message && it.role == MessageRole.ASSISTANT
}

private fun TimelineState.hasOpenAssistant(): Boolean = items.any {
    it is TimelineItem.Message && it.role == MessageRole.ASSISTANT && it.streaming
}

private fun TimelineState.hasUserAfterLastAssistant(): Boolean {
    val assistantIndex = lastAssistantIndex()
    if (assistantIndex < 0) return true
    return items.indexOfLast { it is TimelineItem.Message && it.role == MessageRole.USER } > assistantIndex
}

private fun List<TimelineItem>.withTerminalError(
    sessionId: String?,
    generation: Long,
    failed: Boolean,
    errorMessage: String?,
    finalText: String,
    partial: Boolean,
    recoverable: Boolean,
): List<TimelineItem> {
    if (!failed) return this
    val message = errorMessage?.takeIf(String::isNotBlank)
        ?: finalText.takeIf(String::isNotBlank)
        ?: "Hermes reported an error"
    val id = "error:${sessionId.orEmpty()}:$generation:complete"
    return upsertById(
        TimelineItem.Error(
            id = id,
            message = message.take(MAX_TIMELINE_SUMMARY_CHARACTERS),
            recoverable = recoverable,
            identity = TimelineIdentity.fallback(
                id,
                sessionId,
                source = if (partial) "message.complete:error:partial" else "message.complete:error",
            ),
        ),
    )
}

private fun List<TimelineItem>.upsertById(item: TimelineItem): List<TimelineItem> {
    val index = indexOfFirst { it.id == item.id }
    return if (index < 0) this + item else replaced(index, item)
}

private fun String.boundedAppend(value: String): String {
    if (length >= MAX_TIMELINE_TEXT_CHARACTERS) return this
    return this + value.take(MAX_TIMELINE_TEXT_CHARACTERS - length)
}

private fun toolIdentity(
    payload: JsonObject,
    state: TimelineState,
    eventType: String,
): Pair<String, TimelineIdentity> {
    val serverId = payload.text("tool_id").ifBlank { payload.text("tool_call_id") }.ifBlank { payload.text("id") }
        .takeIf(String::isNotBlank)
    if (serverId != null) return serverId to TimelineIdentity.server(serverId)
    val name = payload.text("name").ifBlank { "tool" }.take(MAX_TIMELINE_LABEL_CHARACTERS)
    val prefix = "tool:${state.generation}:$name:"
    val existing = state.items.asReversed().filterIsInstance<TimelineItem.Tool>().firstOrNull {
        it.id.startsWith(prefix) && (
            it.state == ToolState.RUNNING ||
                (eventType == "tool.complete" && it.state in setOf(ToolState.COMPLETE, ToolState.FAILED))
            )
    }
    val id = existing?.id ?: "$prefix${state.items.count { it.id.startsWith(prefix) }}"
    return id to TimelineIdentity.fallback(id)
}

private fun blockingPart(
    sessionId: String?,
    payload: JsonObject,
    kind: BlockingRequestKind,
    prompt: String,
    choices: List<String>,
): TimelineItem.BlockingRequest {
    val serverId = payload.text("request_id").ifBlank { payload.text("id") }.takeIf(String::isNotBlank)
    val id = serverId?.let { "request:$it" } ?: "request:${sessionId.orEmpty()}:${kind.name.lowercase()}"
    return TimelineItem.BlockingRequest(
        id = id,
        kind = kind,
        prompt = prompt.take(MAX_TIMELINE_SUMMARY_CHARACTERS),
        choices = choices.take(MAX_TIMELINE_CHOICES).map { it.take(MAX_TIMELINE_CHOICE_CHARACTERS) },
        identity = serverId?.let {
            TimelineIdentity.server(it, sessionId, source = "${kind.name.lowercase()}.request")
        } ?: TimelineIdentity.fallback(id, sessionId, source = "${kind.name.lowercase()}.request"),
    )
}

private fun upsertBlockingPart(
    items: List<TimelineItem>,
    request: TimelineItem.BlockingRequest,
): List<TimelineItem> {
    val index = items.indexOfFirst { it.id == request.id }
    return if (index < 0) items + request else items.replaced(index, request)
}

private fun historyMessageParts(index: Int, message: ProtocolMessage): List<TimelineItem> {
    val messageId = message.id?.takeIf(String::isNotBlank)
        ?: message.toolCallId?.takeIf(String::isNotBlank)
    val baseId = messageId ?: "history:$index:${message.role}"
    val role = message.role.toMessageRole()
    val baseIdentity = messageId?.let {
        TimelineIdentity.server(it, source = "history")
    } ?: TimelineIdentity.fallback(baseId, source = "history")
    if (message.role.equals("tool", ignoreCase = true)) {
        val content = message.content as? JsonObject
        val toolName = message.toolName.orEmpty()
            .ifBlank { content?.text("name").orEmpty() }
            .ifBlank { content?.text("tool_name").orEmpty() }
            .ifBlank { "tool" }
        return listOf(
            TimelineItem.Tool(
                id = baseId,
                name = toolName.take(MAX_TIMELINE_LABEL_CHARACTERS),
                state = ToolState.COMPLETE,
                summary = content?.text("summary")?.ifBlank { null }
                    ?.take(MAX_TIMELINE_SUMMARY_CHARACTERS),
                detail = toolHistoryDetail(message.content, message.text),
                identity = baseIdentity,
            ),
        )
    }
    val parts = buildList<TimelineItem> {
        val content = message.content
        val contentParts = when (content) {
            is JsonArray -> content
            is JsonObject -> JsonArray(listOf(content))
            else -> null
        }
        if (contentParts != null) {
            contentParts.forEachIndexed { partIndex, element ->
                val part = element as? JsonObject ?: return@forEachIndexed
                val partType = part.text("type").ifBlank { "text" }
                val id = "$baseId:part:$partIndex"
                val identity = TimelineIdentity.fallback(
                    id,
                    parentServerId = messageId,
                    source = "history:$partType",
                )
                when (partType) {
                    "text", "output_text", "input_text" -> part.text("text").takeIf(String::isNotBlank)?.let {
                        add(TimelineItem.Message(id, role, it, identity = identity))
                    }
                    "reasoning", "thinking" -> part.text("text").takeIf(String::isNotBlank)?.let {
                        add(TimelineItem.Reasoning(id, it, streaming = false, identity = identity))
                    }
                    "tool-call", "tool_call", "tool" -> add(historyToolPart(id, part, identity))
                    "artifact", "image", "image_url", "file", "audio", "video", "media" ->
                        add(historyArtifactPart(id, part, identity))
                    "citation", "reference", "source" -> add(historyReferencePart(id, part, identity))
                    "error" -> add(
                        TimelineItem.Error(
                            id,
                            part.text("message").ifBlank { part.text("text") }.ifBlank { "Hermes reported an error" }
                                .take(MAX_TIMELINE_SUMMARY_CHARACTERS),
                            identity = identity,
                        ),
                    )
                    else -> add(
                        TimelineItem.Unknown(
                            id = id,
                            type = partType.take(MAX_TIMELINE_LABEL_CHARACTERS),
                            summary = "Unsupported ${partType.take(MAX_TIMELINE_LABEL_CHARACTERS)} part",
                            identity = identity,
                        ),
                    )
                }
            }
        } else {
            val text = displayText(content, message.text)
            if (text.isNotBlank()) add(TimelineItem.Message(baseId, role, text, identity = baseIdentity))
        }
        addAll(historyToolCalls(baseId, message.toolCalls, messageId))
    }
    return parts
}

private fun historyReferencePart(
    id: String,
    part: JsonObject,
    identity: TimelineIdentity,
): TimelineItem.Reference = TimelineItem.Reference(
    id = id,
    label = listOf(part.text("label"), part.text("title"), part.text("name"))
        .firstOrNull(String::isNotBlank)
        ?.take(MAX_TIMELINE_LABEL_CHARACTERS)
        ?: "Reference",
    text = listOf(part.text("text"), part.text("description"), part.text("snippet"), part.text("url"))
        .firstOrNull(String::isNotBlank)
        ?.take(MAX_TIMELINE_SUMMARY_CHARACTERS)
        .orEmpty(),
    index = (part["index"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
    count = (part["count"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
    identity = identity,
)

private fun historyArtifactPart(
    id: String,
    part: JsonObject,
    identity: TimelineIdentity,
): TimelineItem.Artifact {
    val type = part.text("type").ifBlank { "artifact" }
    val nestedImage = part["image_url"] as? JsonObject
    val serverId = part.text("artifact_id")
        .ifBlank { part.text("artifactId") }
        .ifBlank { part.text("id") }
        .takeIf(String::isNotBlank)
    val reference = listOf(
        part.text("reference"),
        part.text("url"),
        part.text("href"),
        part.text("src"),
        nestedImage?.text("url").orEmpty(),
        part.text("data"),
        part.text("image"),
        part.text("file"),
        part.text("path"),
        part.text("uri"),
        serverId.orEmpty(),
    ).firstOrNull(String::isNotBlank)?.take(MAX_TIMELINE_REFERENCE_CHARACTERS)
    val label = listOf(
        part.text("label"),
        part.text("name"),
        part.text("filename"),
        part.text("file_name"),
        part.text("title"),
    ).firstOrNull(String::isNotBlank)
        ?: reference?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf(String::isNotBlank)
        ?: type.replaceFirstChar { it.uppercase() }
    val mimeType = listOf(
        part.text("mime"),
        part.text("mimeType"),
        part.text("mime_type"),
        part.text("mediaType"),
        part.text("media_type"),
        nestedImage?.text("mime_type").orEmpty(),
    ).firstOrNull(String::isNotBlank)
        ?: reference?.substringAfter("data:", "")?.substringBefore(';')?.takeIf(String::isNotBlank)
    val itemId = serverId ?: id
    return TimelineItem.Artifact(
        id = itemId,
        label = label.take(MAX_TIMELINE_LABEL_CHARACTERS),
        mimeType = mimeType?.take(MAX_TIMELINE_LABEL_CHARACTERS),
        reference = reference,
        description = part.text("description").ifBlank { part.text("alt") }
            .ifBlank { part.text("text") }.ifBlank { null }
            ?.take(MAX_TIMELINE_SUMMARY_CHARACTERS),
        identity = serverId?.let {
            TimelineIdentity.server(it, parentServerId = identity.parentServerId, source = "history:$type")
        } ?: identity,
    )
}

private fun historyToolPart(
    id: String,
    part: JsonObject,
    identity: TimelineIdentity,
): TimelineItem.Tool {
    val name = part.text("toolName").ifBlank { part.text("name") }.ifBlank { part.text("tool_name") }.ifBlank { "tool" }
    val arguments = part["args"] ?: part["arguments"] ?: part["input"] ?: part["argsText"]
    val result = part["result"] ?: part["resultText"]
    val detail = when {
        arguments != null && result != null -> buildJsonObject {
            put("arguments", arguments)
            put("result", result)
        }
        result != null -> result
        else -> arguments
    }
    val serverId = part.text("toolCallId")
        .ifBlank { part.text("tool_id") }
        .ifBlank { part.text("id") }
        .takeIf(String::isNotBlank)
    val itemId = serverId ?: id
    return TimelineItem.Tool(
        id = itemId,
        name = name.take(MAX_TIMELINE_LABEL_CHARACTERS),
        state = if (part.boolean("failed") || part.boolean("isError")) ToolState.FAILED else ToolState.COMPLETE,
        summary = part.text("summary").ifBlank { null }?.take(MAX_TIMELINE_SUMMARY_CHARACTERS),
        detail = when (detail) {
            is JsonPrimitive -> detail.contentOrNull
            null -> null
            else -> detail.toString()
        }?.take(MAX_TIMELINE_TOOL_DETAIL_CHARACTERS),
        identity = serverId?.let {
            TimelineIdentity.server(it, parentServerId = identity.parentServerId, source = "history:tool")
        } ?: identity,
    )
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

private fun toolHistoryDetail(content: JsonElement?, fallback: String?): String? = when (content) {
    is JsonPrimitive -> content.contentOrNull
    is JsonObject, is JsonArray -> content.toString()
    else -> fallback
}?.takeIf(String::isNotBlank)?.take(MAX_TIMELINE_TOOL_DETAIL_CHARACTERS)

private fun historyToolCalls(
    messageId: String,
    toolCalls: JsonElement?,
    parentServerId: String?,
): List<TimelineItem.Tool> {
    val calls = when (toolCalls) {
        is JsonArray -> toolCalls
        is JsonObject -> JsonArray(listOf(toolCalls))
        else -> return emptyList()
    }
    return calls.mapIndexedNotNull { index, element ->
        val call = element as? JsonObject ?: return@mapIndexedNotNull null
        val function = call["function"] as? JsonObject
        val name = call.text("name").ifBlank { function?.text("name").orEmpty() }.ifBlank { "tool" }
        val detail = function?.get("arguments") ?: call["arguments"] ?: call["input"]
        val serverId = call.text("id").takeIf(String::isNotBlank)
        val id = serverId ?: "$messageId:tool:$index"
        TimelineItem.Tool(
            id = id,
            name = name.take(MAX_TIMELINE_LABEL_CHARACTERS),
            state = ToolState.COMPLETE,
            detail = when (detail) {
                is JsonPrimitive -> detail.contentOrNull
                null -> null
                else -> detail.toString()
            }?.take(MAX_TIMELINE_TOOL_DETAIL_CHARACTERS),
            identity = serverId?.let {
                TimelineIdentity.server(it, parentServerId = parentServerId, source = "history:tool-call")
            } ?: TimelineIdentity.fallback(id, parentServerId = parentServerId, source = "history:tool-call"),
        )
    }
}

private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true
private fun JsonObject.stringList(key: String): List<String> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

private fun <T> List<T>.replaced(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }

private const val MAX_TIMELINE_CHOICES = 16
private const val MAX_TIMELINE_CHOICE_CHARACTERS = 160
private const val MAX_TIMELINE_LABEL_CHARACTERS = 160
private const val MAX_TIMELINE_REFERENCE_CHARACTERS = 2_048
private const val MAX_TIMELINE_SUMMARY_CHARACTERS = 4_096
private const val MAX_TIMELINE_TOOL_DETAIL_CHARACTERS = 65_536
private const val MAX_TIMELINE_TEXT_CHARACTERS = 1_048_576

private val TIMELINE_EVENT_TYPES = setOf(
    "message.start",
    "message.interim",
    "message.delta",
    "message.complete",
    "reasoning.delta",
    "reasoning.available",
    "thinking.delta",
    "moa.reference",
    "tool.start",
    "tool.progress",
    "tool.complete",
    "status.update",
    "approval.request",
    "clarify.request",
    "sudo.request",
    "secret.request",
    "sudo.expire",
    "secret.expire",
    "error",
)

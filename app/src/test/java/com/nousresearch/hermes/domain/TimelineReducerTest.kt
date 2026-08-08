package com.nousresearch.hermes.domain

import com.nousresearch.hermes.protocol.GatewayEvent
import com.nousresearch.hermes.protocol.ProtocolMessage
import com.nousresearch.hermes.protocol.SessionInflightProjection
import com.nousresearch.hermes.protocol.SessionQueuedProjection
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineReducerTest {
    @Test
    fun `resume restores the authoritative live and queued turns without stale local text`() {
        val previous = TimelineState(
            items = listOf(
                TimelineItem.Message("history-user", MessageRole.USER, "Earlier"),
                TimelineItem.Message("history-assistant", MessageRole.ASSISTANT, "Done"),
                TimelineItem.Message("local:current", MessageRole.USER, "Current question"),
                TimelineItem.Message("assistant:runtime-1:1", MessageRole.ASSISTANT, "Stale partial", streaming = true),
            ),
        )

        val result = TimelineReducer.reconcileResume(
            messages = listOf(
                ProtocolMessage(id = "history-user", role = "user", text = "Earlier"),
                ProtocolMessage(id = "history-assistant", role = "assistant", text = "Done"),
            ),
            runtimeSessionId = "runtime-1",
            inflight = SessionInflightProjection("Current question", "Fresh partial", streaming = true),
            queued = SessionQueuedProjection("Next question"),
            running = true,
            previousRuntimeSessionId = "runtime-1",
            previous = previous,
        )

        val messages = result.items.filterIsInstance<TimelineItem.Message>()
        assertEquals(listOf("Earlier", "Done", "Current question", "Fresh partial", "Next question"), messages.map { it.text })
        assertEquals(
            listOf(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.USER, MessageRole.ASSISTANT, MessageRole.USER),
            messages.map { it.role },
        )
        assertTrue(messages[3].streaming)
    }

    @Test
    fun `resume keeps the local pending turn when an older Hermes has no live projection`() {
        val previous = TimelineState(
            items = listOf(
                TimelineItem.Message("history-user", MessageRole.USER, "Earlier"),
                TimelineItem.Message("history-assistant", MessageRole.ASSISTANT, "Done"),
                TimelineItem.Message("local:current", MessageRole.USER, "Current question"),
                TimelineItem.Message("assistant:runtime-1:1", MessageRole.ASSISTANT, "Partial answer", streaming = true),
            ),
        )

        val result = TimelineReducer.reconcileResume(
            messages = listOf(
                ProtocolMessage(id = "history-user", role = "user", text = "Earlier"),
                ProtocolMessage(id = "history-assistant", role = "assistant", text = "Done"),
            ),
            runtimeSessionId = "runtime-1",
            inflight = null,
            queued = null,
            running = true,
            previousRuntimeSessionId = "runtime-1",
            previous = previous,
        )

        val messages = result.items.filterIsInstance<TimelineItem.Message>()
        assertEquals(listOf("Earlier", "Done", "Current question", "Partial answer"), messages.map { it.text })
        assertTrue(messages.last().streaming)
    }

    @Test
    fun `resume keeps blocking requests and stable live activity for the same running session`() {
        val previous = TimelineState(
            items = listOf(
                TimelineItem.Reasoning("reasoning:runtime-1:3:reasoning.delta", "Checking", streaming = true),
                TimelineItem.Tool("tool-7", "terminal", context = "workspace", state = ToolState.RUNNING),
                TimelineItem.BlockingRequest(
                    "request:clarify-8",
                    BlockingRequestKind.CLARIFICATION,
                    "Which branch?",
                ),
            ),
            approval = ApprovalRequest("runtime-1", "git status", "Inspect worktree", listOf("once", "deny")),
            clarification = ClarificationRequest("runtime-1", "clarify-8", "Which branch?", listOf("dev", "main")),
            sensitiveInput = SensitiveInputRequest(
                "runtime-1",
                "secret-9",
                SensitiveInputKind.SECRET,
                "Token required",
                "TEST_TOKEN",
            ),
            generation = 3,
        )

        val result = TimelineReducer.reconcileResume(
            messages = emptyList(),
            runtimeSessionId = "runtime-1",
            inflight = SessionInflightProjection("Current question", streaming = true),
            queued = null,
            running = true,
            previousRuntimeSessionId = "runtime-1",
            previous = previous,
        )

        assertEquals(previous.approval, result.approval)
        assertEquals(previous.clarification, result.clarification)
        assertEquals(previous.sensitiveInput, result.sensitiveInput)
        assertEquals(3, result.generation)
        assertTrue(result.items.any { it.id == "reasoning:runtime-1:3:reasoning.delta" })
        assertTrue(result.items.any { it.id == "tool-7" })
        assertTrue(result.items.any { it.id == "request:clarify-8" })
    }

    @Test
    fun `resume never carries pending state into a replacement runtime`() {
        val previous = TimelineState(
            items = listOf(
                TimelineItem.Message("local:current", MessageRole.USER, "Current question"),
                TimelineItem.Message("assistant:old-runtime:1", MessageRole.ASSISTANT, "Partial", streaming = true),
            ),
            approval = ApprovalRequest("old-runtime", "git status", null, listOf("once", "deny")),
            generation = 1,
        )

        val result = TimelineReducer.reconcileResume(
            messages = emptyList(),
            runtimeSessionId = "replacement-runtime",
            inflight = null,
            queued = null,
            running = true,
            previousRuntimeSessionId = "old-runtime",
            previous = previous,
        )

        assertTrue(result.items.isEmpty())
        assertTrue(result.approval == null)
        assertEquals(0, result.generation)
    }

    @Test
    fun `accepted queued prompt is inserted before an already streaming assistant`() {
        val streaming = TimelineReducer.reduce(TimelineState(), GatewayEvent("message.start", "runtime-1"))

        val result = TimelineReducer.insertAcceptedUserMessage(streaming, "local:queued-1", "Next turn")

        assertEquals(
            listOf(MessageRole.USER, MessageRole.ASSISTANT),
            result.items.filterIsInstance<TimelineItem.Message>().map(TimelineItem.Message::role),
        )
    }

    @Test
    fun `stream deltas settle into one assistant message`() {
        var state = TimelineReducer.reduce(TimelineState(), GatewayEvent("message.start", "runtime-1"))
        state = TimelineReducer.reduce(state, event("message.delta", "runtime-1", "text", "Hello "))
        state = TimelineReducer.reduce(state, event("message.delta", "runtime-1", "text", "world"))
        state = TimelineReducer.reduce(state, event("message.complete", "runtime-1", "text", "Hello world"))

        val message = state.items.single() as TimelineItem.Message
        assertEquals("Hello world", message.text)
        assertFalse(message.streaming)
    }

    @Test
    fun `tool completion updates stable tool identity`() {
        val start = GatewayEvent(
            "tool.start",
            "runtime-1",
            buildJsonObject { put("tool_id", "call-7"); put("name", "terminal") },
        )
        val complete = GatewayEvent(
            "tool.complete",
            "runtime-1",
            buildJsonObject { put("tool_id", "call-7"); put("name", "terminal"); put("summary", "Completed") },
        )
        var state = TimelineReducer.reduce(TimelineState(), start)
        state = TimelineReducer.reduce(state, complete)

        val tool = state.items.single() as TimelineItem.Tool
        assertEquals(ToolState.COMPLETE, tool.state)
        assertEquals("Completed", tool.summary)
    }

    @Test
    fun `history tool messages remain folded tool activity instead of raw chat messages`() {
        val detail = """{"output":"first line\nsecond line","exit_code":0,"error":null}"""

        val state = TimelineReducer.hydrate(
            listOf(ProtocolMessage(id = "history-tool", role = "tool", text = detail)),
        )

        val tool = state.items.single() as TimelineItem.Tool
        assertEquals("history-tool", tool.id)
        assertEquals(ToolState.COMPLETE, tool.state)
        assertEquals(detail, tool.detail)
        assertTrue(state.items.none { it is TimelineItem.Message && it.role == MessageRole.TOOL })
    }

    @Test
    fun `history tool objects preserve complete structured transcript data`() {
        val content = buildJsonObject {
            put("name", "terminal")
            put("output", buildJsonObject { put("path", "/workspace/report.txt") })
            put("exit_code", 0)
        }

        val tool = TimelineReducer.hydrate(
            listOf(ProtocolMessage(id = "history-object", role = "tool", content = content)),
        ).items.single() as TimelineItem.Tool

        assertTrue(tool.detail.orEmpty().contains("/workspace/report.txt"))
        assertTrue(tool.presentation().transcript.contains("/workspace/report.txt"))
    }

    @Test
    fun `history assistant tool calls become folded tool activity`() {
        val toolCalls = buildJsonArray {
            add(buildJsonObject {
                put("id", "call-history")
                put("function", buildJsonObject {
                    put("name", "terminal")
                    put("arguments", """{"command":"git status"}""")
                })
            })
        }

        val state = TimelineReducer.hydrate(
            listOf(ProtocolMessage(role = "assistant", toolCalls = toolCalls)),
        )

        val tool = state.items.single() as TimelineItem.Tool
        assertEquals("call-history", tool.id)
        assertEquals("terminal", tool.name)
        assertTrue(tool.detail.orEmpty().contains("git status"))
    }

    @Test
    fun `tool completion decodes structured result without truncating the transcript`() {
        val output = "x".repeat(25_000)
        val state = TimelineReducer.reduce(
            TimelineState(),
            GatewayEvent(
                "tool.complete",
                "runtime-1",
                buildJsonObject {
                    put("tool_id", "call-large")
                    put("name", "terminal")
                    put("result", buildJsonObject {
                        put("output", output)
                        put("exit_code", 0)
                    })
                },
            ),
        )

        val tool = state.items.single() as TimelineItem.Tool
        assertTrue(tool.detail.orEmpty().contains(output))
        assertEquals(output, tool.presentation().transcript.substringAfter("Output\n").substringBefore("\n\nExit code"))
    }

    @Test
    fun `unknown future events are ignored without losing state`() {
        val original = TimelineState(items = listOf(TimelineItem.Status("x", "ready", "Ready")))
        val result = TimelineReducer.reduce(original, GatewayEvent("future.event", "runtime-1"))
        assertEquals(original, result)
    }

    @Test
    fun `structured history preserves ordered typed parts and explicit identity provenance`() {
        val state = TimelineReducer.hydrate(
            listOf(
                ProtocolMessage(
                    id = "server-turn-7",
                    role = "assistant",
                    content = buildJsonArray {
                        add(buildJsonObject { put("type", "text"); put("text", "Answer") })
                        add(buildJsonObject {
                            put("type", "reference")
                            put("label", "Model B")
                            put("text", "Alternative")
                        })
                        add(buildJsonObject {
                            put("type", "artifact")
                            put("artifact_id", "artifact-1")
                            put("name", "report.md")
                            put("mime", "text/markdown")
                        })
                        add(buildJsonObject {
                            put("type", "tool-call")
                            put("tool_id", "tool-8")
                            put("name", "terminal")
                            put("arguments", buildJsonObject { put("command", "git status") })
                        })
                        add(buildJsonObject { put("type", "error"); put("message", "Partial failure") })
                    },
                ),
            ),
        )

        assertEquals(
            listOf(
                TimelineItem.Message::class,
                TimelineItem.Reference::class,
                TimelineItem.Unknown::class,
                TimelineItem.Tool::class,
                TimelineItem.Error::class,
            ),
            state.items.map { it::class },
        )
        assertTrue(state.items.all { it.identity.parentServerId == "server-turn-7" })
        assertEquals(TimelineIdentityKind.GENERATED_FALLBACK, state.items.first().identity.kind)
        assertEquals(TimelineIdentityKind.SERVER, state.items.filterIsInstance<TimelineItem.Tool>().single().identity.kind)
    }

    @Test
    fun `server ids are preferred while legacy history ids remain explicit fallbacks`() {
        val state = TimelineReducer.hydrate(
            listOf(
                ProtocolMessage(id = "server-message", role = "user", text = "Stable"),
                ProtocolMessage(role = "assistant", text = "Legacy"),
            ),
        )

        assertEquals(TimelineIdentityKind.SERVER, state.items[0].identity.kind)
        assertEquals("server-message", state.items[0].identity.value)
        assertEquals(TimelineIdentityKind.GENERATED_FALLBACK, state.items[1].identity.kind)
    }

    @Test
    fun `fallback message start and stable blocking request ids make replay idempotent`() {
        val start = GatewayEvent(
            "message.start",
            "runtime-1",
            buildJsonObject { put("id", "message-7") },
        )
        val clarify = GatewayEvent(
            "clarify.request",
            "runtime-1",
            buildJsonObject {
                put("request_id", "clarify-8")
                put("question", "Which branch?")
            },
        )
        var state = TimelineReducer.reduce(TimelineState(), start)
        state = TimelineReducer.reduce(state, start)
        state = TimelineReducer.reduce(state, clarify)
        state = TimelineReducer.reduce(state, clarify)

        assertEquals(1, state.items.filterIsInstance<TimelineItem.Message>().size)
        assertEquals(1, state.items.filterIsInstance<TimelineItem.BlockingRequest>().size)
        assertEquals(TimelineIdentityKind.GENERATED_FALLBACK, state.items.first().identity.kind)
        assertEquals(
            TimelineIdentityKind.SERVER,
            state.items.filterIsInstance<TimelineItem.BlockingRequest>().single().identity.kind,
        )
    }

    @Test
    fun `events from another runtime cannot mutate the active projection`() {
        val active = TimelineReducer.reduce(TimelineState(), GatewayEvent("message.start", "runtime-a"))

        val raced = TimelineReducer.reduce(
            active,
            event("message.delta", "runtime-b", "text", "wrong session"),
        )

        assertEquals(active, raced)
    }

    @Test
    fun `unscoped events cannot mutate a runtime bound projection`() {
        val active = TimelineReducer.reduce(TimelineState(), GatewayEvent("message.start", "runtime-a"))

        val raced = TimelineReducer.reduce(active, event("message.delta", null, "text", "unscoped"))

        assertEquals(active, raced)
    }

    @Test
    fun `replayed fallback terminal frames do not fork a completed turn`() {
        var state = TimelineReducer.appendUserMessage(TimelineState(), "local:1", "Question")
        state = TimelineReducer.reduce(state, GatewayEvent("message.start", "runtime-1"))
        state = TimelineReducer.reduce(state, event("message.delta", "runtime-1", "text", "Answer"))
        state = TimelineReducer.reduce(state, event("message.complete", "runtime-1", "text", "Answer"))
        val completed = state

        state = TimelineReducer.reduce(state, GatewayEvent("message.start", "runtime-1"))
        state = TimelineReducer.reduce(state, event("message.delta", "runtime-1", "text", "Answer"))
        state = TimelineReducer.reduce(state, event("message.complete", "runtime-1", "text", "Answer"))

        assertEquals(completed, state)
    }

    @Test
    fun `terminal tool state cannot be reopened by late progress`() {
        val complete = GatewayEvent(
            "tool.complete",
            "runtime-1",
            buildJsonObject { put("tool_id", "tool-1"); put("name", "terminal"); put("summary", "Done") },
        )
        val progress = GatewayEvent(
            "tool.progress",
            "runtime-1",
            buildJsonObject { put("tool_id", "tool-1"); put("name", "terminal"); put("args_text", "late") },
        )

        val completed = TimelineReducer.reduce(TimelineState(), complete)
        val raced = TimelineReducer.reduce(completed, progress)

        assertEquals(completed, raced)
    }

    @Test
    fun `terminal message error keeps partial output and structured failure`() {
        var state = TimelineReducer.appendUserMessage(TimelineState(), "local:1", "Question")
        state = TimelineReducer.reduce(state, GatewayEvent("message.start", "runtime-1"))
        state = TimelineReducer.reduce(state, event("message.delta", "runtime-1", "text", "Half an answer"))
        state = TimelineReducer.reduce(
            state,
            GatewayEvent(
                "message.complete",
                "runtime-1",
                buildJsonObject {
                    put("status", "error")
                    put("text", "Half an answer")
                    put("error", "connection reset")
                    put("partial", true)
                    put("recoverable", true)
                },
            ),
        )

        val assistant = state.items.filterIsInstance<TimelineItem.Message>().last()
        val error = state.items.filterIsInstance<TimelineItem.Error>().single()
        assertEquals("Half an answer", assistant.text)
        assertTrue(assistant.failed)
        assertEquals("connection reset", error.message)
        assertTrue(error.recoverable)
    }

    @Test
    fun `malformed deltas and unadvertised replay envelopes are inert fallbacks`() {
        val original = TimelineState()
        val malformed = TimelineReducer.reduce(original, GatewayEvent("message.delta", "runtime-1"))
        val replay = TimelineReducer.reduce(original, GatewayEvent("resync.required", "runtime-1"))

        assertEquals(original.copy(runtimeSessionId = "runtime-1"), malformed)
        assertEquals(original, replay)
        assertEquals(TimelineSyncMode.AUTHORITATIVE_RESUME, malformed.sync.mode)
    }

    @Test
    fun `message completion clears stale blocking parts and keeps recoverable error typed`() {
        var state = TimelineReducer.reduce(
            TimelineState(),
            GatewayEvent(
                "secret.request",
                "runtime-1",
                buildJsonObject { put("request_id", "secret-1"); put("prompt", "Token") },
            ),
        )
        state = TimelineReducer.reduce(
            state,
            GatewayEvent(
                "error",
                "runtime-1",
                buildJsonObject { put("message", "Provider failed"); put("recoverable", true) },
            ),
        )

        assertTrue(state.sensitiveInput == null)
        assertTrue(state.items.none { it is TimelineItem.BlockingRequest })
        assertTrue((state.items.last() as TimelineItem.Error).recoverable)
    }

    @Test
    fun `transient status replaces its predecessor and ready clears it`() {
        var state = TimelineState(items = listOf(TimelineItem.Message("u1", MessageRole.USER, "Keep me")))
        state = TimelineReducer.reduce(
            state,
            GatewayEvent("status.update", "runtime-1", buildJsonObject { put("kind", "compacting"); put("text", "Summarizing") }),
        )
        state = TimelineReducer.reduce(
            state,
            GatewayEvent("status.update", "runtime-1", buildJsonObject { put("kind", "context_pressure"); put("text", "85% to compaction") }),
        )

        assertEquals(1, state.items.filterIsInstance<TimelineItem.Status>().size)
        assertEquals("85% to compaction", state.items.filterIsInstance<TimelineItem.Status>().single().text)

        state = TimelineReducer.reduce(
            state,
            GatewayEvent("status.update", "runtime-1", buildJsonObject { put("kind", "status"); put("text", "ready") }),
        )
        assertTrue(state.items.none { it is TimelineItem.Status })
        assertEquals("Keep me", (state.items.single() as TimelineItem.Message).text)
    }

    @Test
    fun `approval remains blocking until explicitly cleared`() {
        val event = GatewayEvent(
            "approval.request",
            "runtime-1",
            buildJsonObject { put("command", "git status") },
        )
        val state = TimelineReducer.reduce(TimelineState(), event)
        assertTrue(state.approval != null)
        assertTrue(TimelineReducer.clearApproval(state).approval == null)
    }

    @Test
    fun `sudo and named secret prompts remain masked typed requests`() {
        val sudo = TimelineReducer.reduce(
            TimelineState(),
            GatewayEvent("sudo.request", "runtime-1", buildJsonObject { put("request_id", "sudo-7") }),
        )
        assertEquals(SensitiveInputKind.SUDO_PASSWORD, sudo.sensitiveInput?.kind)
        assertEquals("sudo-7", sudo.sensitiveInput?.requestId)

        val secret = TimelineReducer.reduce(
            sudo,
            GatewayEvent(
                "secret.request",
                "runtime-1",
                buildJsonObject {
                    put("request_id", "secret-8")
                    put("env_var", "DEPLOY_TOKEN")
                    put("prompt", "Token for the isolated test target")
                },
            ),
        )
        assertEquals(SensitiveInputKind.SECRET, secret.sensitiveInput?.kind)
        assertEquals("DEPLOY_TOKEN", secret.sensitiveInput?.environmentVariable)
        assertEquals("Token for the isolated test target", secret.sensitiveInput?.prompt)
    }

    @Test
    fun `sensitive prompt expiry only clears the matching request`() {
        val state = TimelineReducer.reduce(
            TimelineState(),
            GatewayEvent("sudo.request", "runtime-1", buildJsonObject { put("request_id", "sudo-7") }),
        )
        val unrelated = TimelineReducer.reduce(
            state,
            GatewayEvent("sudo.expire", "runtime-1", buildJsonObject { put("request_id", "sudo-old") }),
        )
        val expired = TimelineReducer.reduce(
            unrelated,
            GatewayEvent("sudo.expire", "runtime-1", buildJsonObject { put("request_id", "sudo-7") }),
        )

        assertEquals("sudo-7", unrelated.sensitiveInput?.requestId)
        assertTrue(expired.sensitiveInput == null)
    }

    @Test
    fun `removing the last exchange keeps earlier turns and drops tool output`() {
        val state = TimelineState(
            items = listOf(
                TimelineItem.Message("u1", MessageRole.USER, "First"),
                TimelineItem.Message("a1", MessageRole.ASSISTANT, "Done"),
                TimelineItem.Message("u2", MessageRole.USER, "Retry this"),
                TimelineItem.Tool("tool", "terminal", state = ToolState.COMPLETE),
                TimelineItem.Message("a2", MessageRole.ASSISTANT, "Failed", failed = true),
            ),
        )

        val trimmed = TimelineReducer.removeLastExchange(state)

        assertEquals(listOf("u1", "a1"), trimmed.items.map(TimelineItem::id))
    }

    @Test
    fun `retry text uses the last authoritative user message including text parts`() {
        val messages = listOf(
            ProtocolMessage(role = "user", text = "Old"),
            ProtocolMessage(role = "assistant", text = "Answer"),
            ProtocolMessage(
                role = "user",
                content = buildJsonArray {
                    add(buildJsonObject { put("type", "text"); put("text", "Retry this") })
                    add(buildJsonObject { put("type", "text"); put("text", "@file:notes.txt") })
                },
            ),
        )

        assertEquals("Retry this @file:notes.txt", lastUserPrompt(messages))
    }

    private fun event(type: String, sessionId: String?, key: String, value: String) = GatewayEvent(
        type,
        sessionId,
        buildJsonObject { put(key, value) },
    )
}

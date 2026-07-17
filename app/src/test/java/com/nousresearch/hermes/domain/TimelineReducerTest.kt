package com.nousresearch.hermes.domain

import com.nousresearch.hermes.protocol.GatewayEvent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineReducerTest {
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
    fun `unknown future events are ignored without losing state`() {
        val original = TimelineState(items = listOf(TimelineItem.Status("x", "ready", "Ready")))
        val result = TimelineReducer.reduce(original, GatewayEvent("future.event", "runtime-1"))
        assertEquals(original, result)
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

    private fun event(type: String, sessionId: String, key: String, value: String) = GatewayEvent(
        type,
        sessionId,
        buildJsonObject { put(key, value) },
    )
}

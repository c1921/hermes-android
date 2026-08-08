package com.nousresearch.hermes.data

import com.nousresearch.hermes.protocol.ProtocolMessage
import com.nousresearch.hermes.protocol.SessionMessagePage
import com.nousresearch.hermes.protocol.SessionResumeResult
import com.nousresearch.hermes.protocol.StoredSession
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionResumeReconciliationTest {
    @Test
    fun `rotated resume projection wins over the requested parent transcript`() {
        val prefetch = SessionMessagePage(
            sessionId = "parent-session",
            messages = listOf(ProtocolMessage(role = "user", text = "Before compression")),
        )
        val resumed = SessionResumeResult(
            runtimeSessionId = "live-child",
            durableSessionId = "child-session",
            resumed = "child-session",
            messages = listOf(
                ProtocolMessage(role = "system", text = "Compressed context"),
                ProtocolMessage(role = "assistant", text = "After compression"),
            ),
        )

        assertEquals(
            listOf("Compressed context", "After compression"),
            selectResumeMessages(prefetch, resumed).map { it.text },
        )
    }

    @Test
    fun `rotated resume promotes the returned durable identity`() {
        val requested = StoredSession(sessionId = "parent-session", title = "Long chat", profile = "work")
        val resumed = SessionResumeResult(
            runtimeSessionId = "live-child",
            durableSessionId = "child-session",
            resumed = "child-session",
        )

        val active = resumedStoredSession(requested, resumed)

        assertEquals("child-session", active.durableId)
        assertEquals("Long chat", active.title)
        assertEquals("work", active.profile)
    }
}

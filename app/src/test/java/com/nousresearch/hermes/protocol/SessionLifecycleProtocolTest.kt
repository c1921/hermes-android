package com.nousresearch.hermes.protocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLifecycleProtocolTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes branch identity returned by Hermes 0 18 2`() {
        val result = json.decodeFromString<SessionBranchResult>(
            """{"session_id":"live-branch","title":"Investigation (branch)","parent":"stored-parent"}""",
        )

        assertEquals("live-branch", result.runtimeSessionId)
        assertEquals("stored-parent", result.parent)
    }

    @Test
    fun `decodes compression with refreshed transcript and runtime info`() {
        val result = json.decodeFromString<SessionCompressResult>(
            """{"status":"compressed","removed":8,"before_messages":12,"after_messages":4,"messages":[{"role":"system","content":"summary"}],"info":{"model":"hermes-4","provider":"nous","running":false}}""",
        )

        assertEquals(4, result.afterMessages)
        assertEquals("summary", result.messages.single().content.toString().trim('"'))
        assertEquals("hermes-4", result.info?.model)
    }

    @Test
    fun `accepts compute-host compression acknowledgement without transcript`() {
        val result = json.decodeFromString<SessionCompressResult>(
            """{"status":"compressed","turn_isolation":true,"host_ack":{"type":"control.ok"}}""",
        )

        assertTrue(result.messages.isEmpty())
    }

    @Test
    fun `title response remains compatible before and after first persisted turn`() {
        val pendingRow = json.decodeFromString<SessionTitleResult>("""{"pending":false,"title":"Mobile plan"}""")
        val existingRow = json.decodeFromString<SessionTitleResult>(
            """{"title":"Mobile plan","session_key":"stored-1"}""",
        )

        assertEquals("Mobile plan", pendingRow.title)
        assertEquals("stored-1", existingRow.sessionKey)
    }
}

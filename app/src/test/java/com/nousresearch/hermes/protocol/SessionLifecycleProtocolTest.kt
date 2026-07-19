package com.nousresearch.hermes.protocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLifecycleProtocolTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes live turn projection returned by current Hermes`() {
        val result = json.decodeFromString<SessionResumeResult>(
            checkNotNull(javaClass.getResource("/fixtures/session-resume-live-614dc194.json")).readText(),
        )

        assertEquals("Current question", result.inflight?.user)
        assertEquals("Partial answer", result.inflight?.assistant)
        assertTrue(result.inflight?.streaming == true)
        assertEquals("Next question", result.queued?.user)
    }

    @Test
    fun `decodes branch identity returned by Hermes 0 18 2`() {
        val result = json.decodeFromString<SessionBranchResult>(
            """{"session_id":"live-branch","title":"Investigation (branch)","parent":"stored-parent"}""",
        )

        assertEquals("live-branch", result.runtimeSessionId)
        assertEquals("stored-parent", result.parent)
    }

    @Test
    fun `decodes lazy session create identities returned by Hermes 0 18 2`() {
        val result = json.decodeFromString<SessionCreateResult>(
            """{"session_id":"live-new","stored_session_id":"stored-new","messages":[],"info":{"cwd":"/srv/hermes/workspace","model":"hermes-4","desktop_contract":4}}""",
        )

        assertEquals("live-new", result.runtimeSessionId)
        assertEquals("stored-new", result.durableSessionId)
        assertEquals("/srv/hermes/workspace", result.info.cwd)
        assertEquals(4, result.info.desktopContract)
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

    @Test
    fun `decodes the exact session deleted by the gateway`() {
        val result = json.decodeFromString<SessionDeleteResult>("""{"deleted":"stored-1"}""")

        assertEquals("stored-1", result.deleted)
    }

    @Test
    fun `decodes idempotent live session close result`() {
        assertTrue(json.decodeFromString<SessionCloseResult>("""{"closed":true}""").closed)
    }

    @Test
    fun `decodes command dispatch result variants`() {
        val skill = json.decodeFromString<SlashCommandResult>(
            """{"type":"skill","name":"codex","message":"Use the Codex workflow"}""",
        )
        val prefill = json.decodeFromString<SlashCommandResult>(
            """{"type":"prefill","message":"Edit this before sending","notice":"Turn restored"}""",
        )

        assertEquals("codex", skill.name)
        assertEquals("Use the Codex workflow", skill.message)
        assertEquals("Turn restored", prefill.notice)
    }
}

package com.nousresearch.hermes.protocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointProtocolModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes checkpoint list from pinned gateway contract`() {
        val result = json.decodeFromString<RollbackListResult>(
            """{
                "enabled": true,
                "checkpoints": [{
                    "hash": "0123456789abcdef0123456789abcdef01234567",
                    "timestamp": "2026-07-18T10:20:30+00:00",
                    "message": "before terminal mutation",
                    "future": "ignored"
                }]
            }""",
        )

        assertTrue(result.enabled)
        assertEquals(
            RollbackCheckpoint(
                hash = "0123456789abcdef0123456789abcdef01234567",
                timestamp = "2026-07-18T10:20:30+00:00",
                message = "before terminal mutation",
            ),
            result.checkpoints.single(),
        )
    }

    @Test
    fun `decodes disabled checkpoint manager`() {
        val result = json.decodeFromString<RollbackListResult>(
            """{"enabled":false,"checkpoints":[]}""",
        )

        assertTrue(!result.enabled)
        assertTrue(result.checkpoints.isEmpty())
    }

    @Test
    fun `decodes bounded diff payload without relying on rendered terminal output`() {
        val result = json.decodeFromString<RollbackDiffResult>(
            """{
                "stat": " app.kt | 2 +-",
                "diff": "diff --git a/app.kt b/app.kt\\n-old\\n+new",
                "rendered": [{"text":"terminal-only"}]
            }""",
        )

        assertTrue(result.stat.contains("app.kt"))
        assertTrue(result.diff.contains("-old"))
    }

    @Test
    fun `decodes full restore result and history mutation count`() {
        val result = json.decodeFromString<RollbackRestoreResult>(
            """{
                "success": true,
                "restored_to": "01234567",
                "reason": "before terminal mutation",
                "directory": "/workspace/project",
                "history_removed": 4,
                "debug": "ignored"
            }""",
        )

        assertTrue(result.success)
        assertEquals("01234567", result.restoredTo)
        assertEquals(4, result.historyRemoved)
    }
}

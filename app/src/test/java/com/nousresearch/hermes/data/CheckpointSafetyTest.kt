package com.nousresearch.hermes.data

import com.google.common.truth.Truth.assertThat
import com.nousresearch.hermes.protocol.RollbackCheckpoint
import com.nousresearch.hermes.protocol.RollbackDiffResult
import org.junit.Test
import org.junit.Assert.assertThrows

class CheckpointSafetyTest {
    private val checkpoint = RollbackCheckpoint(
        hash = "0123456789abcdef0123456789abcdef01234567",
        timestamp = "2026-07-18T10:20:30+00:00",
    )

    @Test
    fun `accepts only full hashes advertised by the open session`() {
        val selected = CheckpointSafety.requireAdvertised(
            checkpoints = listOf(checkpoint),
            requestedHash = checkpoint.hash,
        )

        assertThat(selected).isEqualTo(checkpoint)
    }

    @Test
    fun `rejects prefixes and stale checkpoint identities`() {
        assertThrows(IllegalArgumentException::class.java) {
            CheckpointSafety.requireAdvertised(listOf(checkpoint), "01234567")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CheckpointSafety.requireAdvertised(listOf(checkpoint), "f".repeat(40))
        }
    }

    @Test
    fun `restore requires matching preview and idle session`() {
        CheckpointSafety.requireRestorable(
            checkpoints = listOf(checkpoint),
            requestedHash = checkpoint.hash,
            previewedHash = checkpoint.hash,
            running = false,
        )

        assertThrows(IllegalStateException::class.java) {
            CheckpointSafety.requireRestorable(listOf(checkpoint), checkpoint.hash, null, false)
        }
        assertThrows(IllegalStateException::class.java) {
            CheckpointSafety.requireRestorable(listOf(checkpoint), checkpoint.hash, checkpoint.hash, true)
        }
    }

    @Test
    fun `restore rejects a workspace diff changed since user review`() {
        val preview = CheckpointPreview(checkpoint.hash, "app.kt | 1 +", "+first")

        CheckpointSafety.requireUnchangedPreview(
            preview,
            RollbackDiffResult(stat = "app.kt | 1 +", diff = "+first"),
        )
        assertThrows(IllegalStateException::class.java) {
            CheckpointSafety.requireUnchangedPreview(
                preview,
                RollbackDiffResult(stat = "app.kt | 2 +", diff = "+first\n+second"),
            )
        }
    }
}

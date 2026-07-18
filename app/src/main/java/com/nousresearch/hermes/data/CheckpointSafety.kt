package com.nousresearch.hermes.data

import com.nousresearch.hermes.protocol.RollbackCheckpoint
import com.nousresearch.hermes.protocol.RollbackDiffResult
import java.security.MessageDigest

internal object CheckpointSafety {
    const val MAX_STAT_CHARACTERS = 2_000
    const val MAX_DIFF_CHARACTERS = 4_000

    fun isValidIdentity(hash: String): Boolean = hash.matches(FULL_GIT_HASH)

    fun boundedPreview(hash: String, result: RollbackDiffResult) = CheckpointPreview(
        hash = hash,
        stat = result.stat.take(MAX_STAT_CHARACTERS),
        diff = result.diff.take(MAX_DIFF_CHARACTERS),
        fingerprint = result.fingerprint(),
    )

    fun requireAdvertised(
        checkpoints: List<RollbackCheckpoint>,
        requestedHash: String,
    ): RollbackCheckpoint {
        require(isValidIdentity(requestedHash)) { "Hermes returned an invalid checkpoint identity" }
        return requireNotNull(checkpoints.firstOrNull { it.hash == requestedHash }) {
            "That checkpoint is no longer advertised by the open Hermes session"
        }
    }

    fun requireRestorable(
        checkpoints: List<RollbackCheckpoint>,
        requestedHash: String,
        previewedHash: String?,
        running: Boolean,
    ): RollbackCheckpoint {
        check(!running) { "Interrupt the current Hermes run before restoring a checkpoint" }
        check(previewedHash == requestedHash) { "Preview this checkpoint before restoring it" }
        return requireAdvertised(checkpoints, requestedHash)
    }

    fun requireUnchangedPreview(preview: CheckpointPreview, latest: RollbackDiffResult) {
        check(preview.fingerprint == latest.fingerprint()) {
            "The server workspace changed after the preview. Review the updated diff before restoring."
        }
    }

    private fun RollbackDiffResult.fingerprint(): String = MessageDigest.getInstance("SHA-256")
        .digest("$stat\u0000$diff".toByteArray())
        .joinToString("") { "%02x".format(it) }

    private val FULL_GIT_HASH = Regex("^[0-9a-fA-F]{40,64}$")
}

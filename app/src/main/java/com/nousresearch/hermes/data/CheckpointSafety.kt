package com.nousresearch.hermes.data

import com.nousresearch.hermes.protocol.RollbackCheckpoint
import com.nousresearch.hermes.protocol.RollbackDiffResult

internal object CheckpointSafety {
    fun isValidIdentity(hash: String): Boolean = hash.matches(FULL_GIT_HASH)

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
        check(preview.stat == latest.stat && preview.diff == latest.diff) {
            "The server workspace changed after the preview. Review the updated diff before restoring."
        }
    }

    private val FULL_GIT_HASH = Regex("^[0-9a-fA-F]{40,64}$")
}

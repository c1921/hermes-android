package com.nousresearch.hermes.data

import com.nousresearch.hermes.protocol.ActionStatusResponse
import com.nousresearch.hermes.protocol.BackupActionResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostBackupPolicyTest {
    private val started = BackupActionResponse(true, 91, "backup", "/srv/backups/archive.zip")

    @Test
    fun `completion requires the exact successful backup process receipt`() {
        assertNull(backupReceiptError(started, status(pid = 91, exitCode = 0)))
        assertEquals(
            "Hermes reported a different backup process",
            backupReceiptError(started, status(pid = 92, exitCode = 0)),
        )
        assertEquals(
            "Hermes backup failed with exit 2",
            backupReceiptError(started, status(pid = 91, exitCode = 2)),
        )
    }

    private fun status(pid: Long, exitCode: Int) = ActionStatusResponse(
        exitCode = exitCode,
        name = "backup",
        pid = pid,
        running = false,
    )
}

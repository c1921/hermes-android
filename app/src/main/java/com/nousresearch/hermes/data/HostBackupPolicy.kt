package com.nousresearch.hermes.data

import com.nousresearch.hermes.protocol.ActionStatusResponse
import com.nousresearch.hermes.protocol.BackupActionResponse

internal fun backupReceiptError(started: BackupActionResponse, status: ActionStatusResponse): String? = when {
    status.name != started.name || status.pid != started.pid -> "Hermes reported a different backup process"
    status.running -> null
    status.exitCode == 0 -> null
    else -> "Hermes backup failed with exit ${status.exitCode ?: "unknown"}"
}

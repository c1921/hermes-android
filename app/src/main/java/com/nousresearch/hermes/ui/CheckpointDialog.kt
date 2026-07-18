package com.nousresearch.hermes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.RollbackCheckpoint
import com.nousresearch.hermes.ui.theme.Danger

@Composable
internal fun CheckpointDialog(
    state: HermesState,
    onRefresh: () -> Unit,
    onPreview: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingRestore by rememberSaveable { mutableStateOf<String?>(null) }
    val preview = state.checkpointPreview
    val running = state.runtimeInfo.running || state.sending

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WORKSPACE CHECKPOINTS") },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Hermes checkpoints are server-side workspace snapshots created before file-changing tools. Preview a checkpoint before restoring it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.checkpointNotice?.let { CheckpointMessage(it, danger = false) }
                state.checkpointError?.let { CheckpointMessage(it, danger = true) }
                when {
                    state.checkpointsLoading && state.checkpointsEnabled == null ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Spacer(Modifier.width(12.dp))
                            Text("Loading checkpoints from the open Hermes session…")
                        }
                    state.checkpointsEnabled == false -> Text(
                        "Checkpoints are disabled for this Hermes session. Enable Hermes checkpoints on the server, then start a new session.",
                    )
                    state.checkpointsEnabled == true && state.checkpoints.isEmpty() -> Text(
                        "No checkpoints exist for this session workspace yet.",
                    )
                    else -> state.checkpoints.forEachIndexed { index, checkpoint ->
                        CheckpointRow(
                            checkpoint = checkpoint,
                            index = index + 1,
                            selected = preview?.hash == checkpoint.hash,
                            enabled = !state.checkpointsLoading,
                            onPreview = { onPreview(checkpoint.hash) },
                        )
                    }
                }
                if (state.checkpointsLoading && state.checkpointsEnabled != null) {
                    Text("Hermes is checking the selected checkpoint…", style = MaterialTheme.typography.bodySmall)
                }
                preview?.let {
                    HorizontalDivider()
                    Text("PREVIEW ${it.hash.take(8)}", style = MaterialTheme.typography.labelLarge)
                    if (it.stat.isNotBlank()) {
                        SelectionContainer { Text(it.stat, fontFamily = FontFamily.Monospace) }
                    }
                    SelectionContainer {
                        Text(
                            it.diff.ifBlank { "No workspace changes differ from this checkpoint." },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (running) {
                        CheckpointMessage("Interrupt the current Hermes run before restoring.", danger = true)
                    }
                    OutlinedButton(
                        onClick = { pendingRestore = it.hash },
                        enabled = !running && !state.checkpointsLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Review restore") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onRefresh, enabled = !state.checkpointsLoading) { Text("Refresh") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    pendingRestore?.let { hash ->
        val checkpoint = state.checkpoints.firstOrNull { it.hash == hash }
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("RESTORE ${hash.take(8)}?") },
            text = {
                Text(
                    "Hermes will change files in the server workspace to this checkpoint and remove the latest user turn with its assistant/tool output from the live session. Hermes creates a pre-rollback safety snapshot first.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestore = null
                        onRestore(hash)
                    },
                    enabled = checkpoint != null && state.checkpointPreview?.hash == hash && !running,
                ) { Text("Restore workspace", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CheckpointRow(
    checkpoint: RollbackCheckpoint,
    index: Int,
    selected: Boolean,
    enabled: Boolean,
    onPreview: () -> Unit,
) {
    Surface(
        tonalElevation = if (selected) 3.dp else 0.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$index · ${checkpoint.hash.take(8)}", fontFamily = FontFamily.Monospace)
                TextButton(onClick = onPreview, enabled = enabled) { Text(if (selected) "Previewed" else "Preview") }
            }
            checkpoint.timestamp.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            checkpoint.message.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CheckpointMessage(message: String, danger: Boolean) {
    Text(
        text = message,
        color = if (danger) Danger else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodySmall,
    )
}

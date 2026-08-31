package com.nousresearch.hermes.ui

import androidx.compose.ui.res.stringResource
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.RollbackCheckpoint
import com.nousresearch.hermes.R

@Composable
internal fun CheckpointDialog(
    state: HermesState,
    onRefresh: () -> Unit,
    onPreview: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    val preview = state.checkpointPreview
    val running = state.runtimeInfo.running || state.sending

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_workspace_checkpoints_0e2ad3)) },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.ui_hermes_checkpoints_are_server_side_workspace_snapshots__03e9b3),
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.checkpointNotice?.let { CheckpointMessage(it, danger = false) }
                state.checkpointError?.let { CheckpointMessage(it, danger = true) }
                when {
                    state.checkpointsLoading && state.checkpointsEnabled == null ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.ui_loading_checkpoints_from_the_open_hermes_session_7a7052))
                        }
                    state.checkpointsEnabled == false -> Text(stringResource(R.string.ui_checkpoints_are_disabled_for_this_hermes_session_enable_5c6ca3),
                    )
                    state.checkpointsEnabled == true && state.checkpoints.isEmpty() -> Text(stringResource(R.string.ui_no_checkpoints_exist_for_this_session_workspace_yet_012df4),
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
                    Text(stringResource(R.string.ui_hermes_is_checking_the_selected_checkpoint_e23d39), style = MaterialTheme.typography.bodySmall)
                }
                preview?.let {
                    HorizontalDivider()
                    Text(stringResource(R.string.checkpoint_preview, it.hash.take(8)), style = MaterialTheme.typography.labelLarge)
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
                    ) { Text(stringResource(R.string.ui_review_restore_fcf3c9)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onRefresh, enabled = !state.checkpointsLoading) { Text(stringResource(R.string.ui_refresh_56e3ba)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_close_bbfa77)) } },
    )

    pendingRestore?.let { hash ->
        val checkpoint = state.checkpoints.firstOrNull { it.hash == hash }
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.checkpoint_restore_question, hash.take(8))) },
            text = {
                Text(stringResource(R.string.ui_hermes_will_change_files_in_the_server_workspace_to_thi_06038c),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestore = null
                        onRestore(hash)
                    },
                    enabled = checkpoint != null && state.checkpointPreview?.hash == hash && !running,
                ) { Text(stringResource(R.string.ui_restore_workspace_1b1c90), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
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
                TextButton(onClick = onPreview, enabled = enabled) { Text(stringResource(if (selected) R.string.previewed else R.string.preview)) }
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
        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodySmall,
    )
}

package com.nousresearch.hermes.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.R

private enum class SessionDialog { RENAME, BRANCH, COMPRESS, RETRY, RESET, UNDO, CHECKPOINTS }

@Composable
internal fun SessionActions(
    state: HermesState,
    onRename: (String) -> Unit,
    onBranch: (String) -> Unit,
    onRetry: () -> Unit,
    onUndo: () -> Unit,
    onCompress: (String) -> Unit,
    onReset: () -> Unit,
    onArchive: () -> Unit,
    onRefreshCheckpoints: () -> Unit,
    onPreviewCheckpoint: (String) -> Unit,
    onRestoreCheckpoint: (String) -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<SessionDialog?>(null) }
    var input by remember { mutableStateOf("") }
    val running = state.runtimeInfo.running || state.sending
    val hasHistory = state.timeline.items.isNotEmpty()

    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.a11y_session_actions_b086ca)) }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ui_rename_d3f4cb)) },
            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
            onClick = {
                input = state.activeStoredSession?.displayTitle ?: state.runtimeInfo.title
                menuOpen = false
                dialog = SessionDialog.RENAME
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ui_branch_conversation_681a60)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.CallSplit, null) },
            enabled = hasHistory && !running,
            onClick = {
                input = ""
                menuOpen = false
                dialog = SessionDialog.BRANCH
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ui_retry_last_message_823a52)) },
            leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
            enabled = hasHistory && !running,
            onClick = {
                menuOpen = false
                dialog = SessionDialog.RETRY
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ui_undo_last_turn_38a83f)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Undo, null) },
            enabled = hasHistory && !running,
            onClick = {
                menuOpen = false
                dialog = SessionDialog.UNDO
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ui_compress_context_20c9e8)) },
            leadingIcon = { Icon(Icons.Outlined.Compress, null) },
            enabled = hasHistory && !running,
            onClick = {
                input = ""
                menuOpen = false
                dialog = SessionDialog.COMPRESS
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ui_checkpoints_a2b3a5)) },
            leadingIcon = { Icon(Icons.Outlined.History, null) },
            onClick = {
                menuOpen = false
                dialog = SessionDialog.CHECKPOINTS
                onRefreshCheckpoints()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ui_start_fresh_session_73b0b5)) },
            leadingIcon = { Icon(Icons.Outlined.RestartAlt, null) },
            enabled = !running,
            onClick = {
                menuOpen = false
                dialog = SessionDialog.RESET
            },
        )
        if (!state.activeStoredSession?.durableId.isNullOrBlank()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ui_archive_2621c6)) },
                leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                onClick = {
                    menuOpen = false
                    onArchive()
                },
            )
        }
    }

    when (dialog) {
        SessionDialog.RENAME -> TextInputDialog(
            title = "RENAME SESSION",
            value = input,
            label = "Title",
            confirmLabel = "Rename",
            confirmEnabled = input.isNotBlank() && input.length <= 200,
            onValue = { input = it.take(200) },
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                onRename(input)
            },
        )
        SessionDialog.BRANCH -> TextInputDialog(
            title = "BRANCH CONVERSATION",
            value = input,
            label = "Optional branch name",
            confirmLabel = "Create branch",
            confirmEnabled = true,
            onValue = { input = it.take(200) },
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                onBranch(input)
            },
        )
        SessionDialog.COMPRESS -> TextInputDialog(
            title = "COMPRESS CONTEXT",
            value = input,
            label = "Optional focus topic",
            confirmLabel = "Compress",
            confirmEnabled = true,
            onValue = { input = it.take(500) },
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                onCompress(input)
            },
        )
        SessionDialog.UNDO -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.ui_undo_last_turn_7bf3d0)) },
            text = { Text(stringResource(R.string.ui_hermes_will_remove_the_most_recent_user_turn_and_its_as_44dba9)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        onUndo()
                    },
                ) { Text(stringResource(R.string.ui_undo_turn_29ab7c)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
        )
        SessionDialog.RETRY -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.ui_retry_last_message_246ae3)) },
            text = { Text(stringResource(R.string.ui_hermes_will_remove_the_latest_completed_exchange_and_su_ed94c2)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        onRetry()
                    },
                ) { Text(stringResource(R.string.ui_retry_message_349059)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
        )
        SessionDialog.RESET -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.ui_start_fresh_449169)) },
            text = { Text(stringResource(R.string.ui_hermes_will_end_the_current_live_conversation_and_open__176bb1)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        onReset()
                    },
                ) { Text(stringResource(R.string.ui_start_new_session_6cbc1c)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
        )
        SessionDialog.CHECKPOINTS -> CheckpointDialog(
            state = state,
            onRefresh = onRefreshCheckpoints,
            onPreview = onPreviewCheckpoint,
            onRestore = onRestoreCheckpoint,
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    value: String,
    label: String,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onValue: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValue,
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
    )
}

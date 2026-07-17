package com.nousresearch.hermes.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nousresearch.hermes.data.HermesState

private enum class SessionDialog { RENAME, BRANCH, COMPRESS, UNDO }

@Composable
internal fun SessionActions(
    state: HermesState,
    onRename: (String) -> Unit,
    onBranch: (String) -> Unit,
    onUndo: () -> Unit,
    onCompress: (String) -> Unit,
    onArchive: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<SessionDialog?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    val running = state.runtimeInfo.running || state.sending
    val hasHistory = state.timeline.items.isNotEmpty()

    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "Session actions") }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            text = { Text("Rename") },
            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
            onClick = {
                input = state.activeStoredSession?.displayTitle ?: state.runtimeInfo.title
                menuOpen = false
                dialog = SessionDialog.RENAME
            },
        )
        DropdownMenuItem(
            text = { Text("Branch conversation") },
            leadingIcon = { Icon(Icons.Outlined.CallSplit, null) },
            enabled = hasHistory && !running,
            onClick = {
                input = ""
                menuOpen = false
                dialog = SessionDialog.BRANCH
            },
        )
        DropdownMenuItem(
            text = { Text("Undo last turn") },
            leadingIcon = { Icon(Icons.Outlined.Undo, null) },
            enabled = hasHistory && !running,
            onClick = {
                menuOpen = false
                dialog = SessionDialog.UNDO
            },
        )
        DropdownMenuItem(
            text = { Text("Compress context") },
            leadingIcon = { Icon(Icons.Outlined.Compress, null) },
            enabled = hasHistory && !running,
            onClick = {
                input = ""
                menuOpen = false
                dialog = SessionDialog.COMPRESS
            },
        )
        if (!state.activeStoredSession?.durableId.isNullOrBlank()) {
            DropdownMenuItem(
                text = { Text("Archive") },
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
            title = { Text("UNDO LAST TURN?") },
            text = { Text("Hermes will remove the most recent user turn and its assistant/tool output from this live session.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        onUndo()
                    },
                ) { Text("Undo turn") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
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
                TextField(
                    value = value,
                    onValueChange = onValue,
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

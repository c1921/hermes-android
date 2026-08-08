package com.nousresearch.hermes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.StarmapNode

@Composable
internal fun StarmapScreen(
    state: HermesState,
    profile: String,
    onRefresh: (String) -> Unit,
    onOpenNode: (String, String) -> Unit,
    onCloseNode: () -> Unit,
    onUpdateNode: (String, String, String) -> Unit,
    onDeleteNode: (String, String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable(profile) { mutableStateOf("") }
    var deleteNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(profile) { onRefresh(profile) }

    val graph = state.starmap.takeIf { state.starmapProfile == profile }
    val nodes = remember(graph, query) {
        graph?.nodes.orEmpty().filter { starmapMatches(it, query) }
            .sortedWith(compareByDescending<StarmapNode> { it.pinned }.thenByDescending { it.useCount }.thenBy { it.label })
    }
    val memories = remember(graph, query) {
        val term = query.trim()
        graph?.memory.orEmpty().filter { memory ->
            term.isEmpty() || memory.title.contains(term, ignoreCase = true) || memory.body.contains(term, ignoreCase = true)
        }
    }

    Column(modifier.fillMaxSize()) {
        ManagementHeader("STARMAP", "Remote learning graph / profile $profile", state.starmapLoading, { onRefresh(profile) }, onBack)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Text(
                "Skills and memory remain on the selected Hermes profile. Editing or removal changes that remote profile only.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(200) },
            label = { Text("Search learning") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        graph?.clusters?.takeIf { it.isNotEmpty() }?.let { clusters ->
            Text(
                clusters.joinToString(" · ") { "${it.category.ifBlank { "uncategorised" }} ${it.count}" },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        state.starmapNotice?.let {
            Text(it, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary)
        }
        state.starmapError?.let { ManagementError(it) }
        if (state.starmapLoading && graph == null) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.padding(32.dp))
            }
        } else if (nodes.isEmpty() && memories.isEmpty()) {
            Text(
                if (query.isBlank()) "This Hermes profile has no learning nodes." else "No learning nodes match this search.",
                modifier = Modifier.padding(32.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(nodes, key = StarmapNode::id) { node ->
                    val connections = graph?.edges.orEmpty().count { it.source == node.id || it.target == node.id }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable {
                            confirmDiscard = false
                            onOpenNode(profile, node.id)
                        },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (node.pinned) Icons.Outlined.Star else Icons.Outlined.Memory, null)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(node.label.take(200), fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOf(node.kind, node.category, node.state, "${node.useCount} uses", "$connections links")
                                        .filter(String::isNotBlank).joinToString(" / "),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                items(memories, key = { "memory:${it.source}:${it.title}:${it.timestamp}" }) { memory ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(memory.title.take(200), fontWeight = FontWeight.SemiBold)
                            Text(memory.source.uppercase(), style = MaterialTheme.typography.labelSmall)
                            Text(memory.body.take(4_096), style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    val selectedId = state.starmapNodeId
    val detail = state.starmapNode.takeIf { selectedId != null }
    if (selectedId != null) {
        var draft by remember(selectedId, detail?.content) { mutableStateOf(detail?.content.orEmpty()) }
        val dirty = detail != null && draft != detail.content
        fun requestClose() {
            if (dirty) confirmDiscard = true else onCloseNode()
        }
        AlertDialog(
            onDismissRequest = ::requestClose,
            title = { Text(detail?.label?.take(200) ?: "LEARNING NODE") },
            text = {
                if (detail == null && state.starmapLoading) {
                    CircularProgressIndicator()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${detail?.kind.orEmpty().uppercase()} / profile $profile",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it.take(262_144) },
                            label = { Text("Content") },
                            minLines = 9,
                            maxLines = 18,
                            enabled = detail != null && !state.starmapLoading,
                            supportingText = { Text("${draft.length}/262144 characters") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        state.starmapError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onUpdateNode(profile, selectedId, draft) },
                    enabled = detail != null && dirty && !state.starmapLoading,
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { deleteNodeId = selectedId }, enabled = detail != null && !state.starmapLoading) {
                        Icon(Icons.Outlined.Delete, null)
                        Text("Remove")
                    }
                    TextButton(onClick = ::requestClose) { Text("Close") }
                }
            },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("DISCARD LEARNING EDITS?") },
            text = { Text("Unsaved changes to this remote learning node will be lost.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onCloseNode() }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
    deleteNodeId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteNodeId = null },
            title = { Text("REMOVE LEARNING NODE?") },
            text = { Text("Hermes will archive a skill or remove a memory node from profile $profile. This cannot be undone from Android.") },
            confirmButton = {
                TextButton(onClick = { deleteNodeId = null; onDeleteNode(profile, id) }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deleteNodeId = null }) { Text("Cancel") } },
        )
    }
}

internal fun starmapMatches(node: StarmapNode, query: String): Boolean {
    val term = query.trim()
    return term.isEmpty() || listOf(node.label, node.kind, node.category, node.state, node.createdBy.orEmpty())
        .any { it.contains(term, ignoreCase = true) }
}

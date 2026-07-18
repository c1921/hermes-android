package com.nousresearch.hermes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.domain.SubagentProgress
import com.nousresearch.hermes.domain.SubagentReducer
import com.nousresearch.hermes.domain.SubagentRow
import com.nousresearch.hermes.domain.SubagentStatus
import com.nousresearch.hermes.protocol.BackgroundProcess
import com.nousresearch.hermes.protocol.StoredSession
import com.nousresearch.hermes.protocol.SpawnTreeListEntry
import com.nousresearch.hermes.ui.theme.Warning
import kotlinx.coroutines.delay

@Composable
internal fun AgentsScreen(
    state: HermesState,
    onRefresh: () -> Unit,
    onRefreshArchives: () -> Unit,
    onLoadArchive: (String) -> Unit,
    onSetPaused: (Boolean) -> Unit,
    onInterrupt: (String) -> Unit,
    onStopProcess: (String) -> Unit,
    onOpenSession: (StoredSession) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var pendingInterrupt by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingProcessStop by rememberSaveable { mutableStateOf<String?>(null) }
    val activeIds = state.activeSubagents.mapTo(mutableSetOf(), SubagentProgress::id)
    val recent = state.subagentsBySession.entries.flatMap { (sessionId, agents) ->
        agents.filterNot { it.id in activeIds }.map { sessionId to it }
    }.sortedByDescending { it.second.updatedAtMillis }

    LaunchedEffect(Unit) {
        onRefreshArchives()
        while (true) {
            onRefresh()
            delay(4_000)
        }
    }

    Column(modifier.fillMaxSize()) {
        ManagementHeader(
            "COMMAND CENTER",
            "Agents, delegation and current-session processes",
            state.agentsLoading || state.spawnTreesLoading,
            {
                onRefresh()
                onRefreshArchives()
            },
            onBack,
        )
        state.agentsNotice?.let { AgentNotice(it) }
        state.agentsError?.let { ManagementError(it) }
        state.spawnTreesError?.let { ManagementError(it) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                DelegationSummary(
                    state = state,
                    onSetPaused = onSetPaused,
                )
            }
            section("ACTIVE WORKSTREAMS")
            if (state.activeSubagents.isEmpty() && !state.agentsLoading) {
                item { EmptyAgentCard("Hermes reports no active subagents. Live delegated work will appear here automatically.") }
            } else {
                items(SubagentReducer.rows(state.activeSubagents), key = { "active:${it.progress.id}" }) { row ->
                    SubagentCard(
                        row = row,
                        sessionLabel = null,
                        matchingSession = state.sessions.firstOrNull { it.durableId == row.progress.sessionId },
                        onOpenSession = onOpenSession,
                        onInterrupt = { pendingInterrupt = row.progress.id },
                    )
                }
            }
            section("BACKGROUND PROCESSES")
            if (state.runtimeSessionId == null) {
                item { EmptyAgentCard("Open a session to inspect the background processes owned by that Hermes run.") }
            } else if (state.backgroundProcesses.isEmpty() && !state.agentsLoading) {
                item { EmptyAgentCard("Hermes reports no background processes for the open session.") }
            } else {
                items(state.backgroundProcesses, key = { "process:${it.id}" }) { process ->
                    BackgroundProcessCard(process) {
                        pendingProcessStop = process.id
                    }
                }
            }
            section("RECENT AGENT ACTIVITY")
            if (recent.isEmpty() && !state.agentsLoading) {
                item { EmptyAgentCard("No subagent events have been observed since this Android connection opened.") }
            } else {
                items(recent.take(100), key = { (session, agent) -> "recent:$session:${agent.id}" }) { (session, agent) ->
                    SubagentCard(
                        row = SubagentRow(agent, 0),
                        sessionLabel = session,
                        matchingSession = state.sessions.firstOrNull { it.durableId == agent.sessionId },
                        onOpenSession = onOpenSession,
                        onInterrupt = null,
                    )
                }
            }
            section("ARCHIVED SPAWN TREES")
            if (state.spawnTreeArchives.isEmpty() && !state.spawnTreesLoading) {
                item { EmptyAgentCard("No TUI-persisted spawn trees were returned by this Hermes profile.") }
            } else {
                itemsIndexed(
                    state.spawnTreeArchives,
                    key = { index, archive -> "archive:${archive.finishedAt}:${archive.sessionId}:$index" },
                ) { _, archive ->
                    SpawnTreeArchiveCard(
                        archive = archive,
                        selected = state.spawnTreeReplay?.archive?.path == archive.path,
                        loading = state.spawnTreesLoading,
                        onLoad = { onLoadArchive(archive.path) },
                    )
                }
            }
            state.spawnTreeReplay?.let { replay ->
                section("ARCHIVE REPLAY / ${replay.archive.label.ifBlank { "${replay.subagents.size} SUBAGENTS" }.uppercase()}")
                items(SubagentReducer.rows(replay.subagents), key = { "replay:${replay.archive.finishedAt}:${it.progress.id}" }) { row ->
                    SubagentCard(
                        row = row,
                        sessionLabel = replay.archive.sessionId,
                        matchingSession = state.sessions.firstOrNull { it.durableId == replay.archive.sessionId },
                        onOpenSession = onOpenSession,
                        onInterrupt = null,
                    )
                }
            }
        }
    }

    pendingInterrupt?.let { id ->
        val agent = state.activeSubagents.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { pendingInterrupt = null },
            title = { Text("INTERRUPT SUBAGENT?") },
            text = {
                Text("Hermes will ask ${agent?.goal ?: id} to stop at its next safe iteration boundary. Other workstreams continue.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingInterrupt = null
                        onInterrupt(id)
                    },
                ) { Text("Interrupt") }
            },
            dismissButton = { TextButton(onClick = { pendingInterrupt = null }) { Text("Cancel") } },
        )
    }

    pendingProcessStop?.let { id ->
        val process = state.backgroundProcesses.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { pendingProcessStop = null },
            title = { Text("STOP BACKGROUND PROCESS?") },
            text = {
                Text("Hermes will terminate ${process?.command?.lineSequence()?.firstOrNull().orEmpty().ifBlank { id }}. This can stop a server, watcher, or long-running tool started by the open session.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingProcessStop = null
                        onStopProcess(id)
                    },
                ) { Text("Stop process") }
            },
            dismissButton = { TextButton(onClick = { pendingProcessStop = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SpawnTreeArchiveCard(
    archive: SpawnTreeListEntry,
    selected: Boolean,
    loading: Boolean,
    onLoad: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(archive.label.ifBlank { "${archive.count} subagents" }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        "${archive.count} subagents",
                        archive.sessionId?.takeIf(String::isNotBlank)?.let { "session ${it.take(12)}" },
                    ).joinToString(" / "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onLoad, enabled = !loading) {
                Text(if (selected) "Reload" else "Replay")
            }
        }
    }
}

@Composable
private fun DelegationSummary(state: HermesState, onSetPaused: (Boolean) -> Unit) {
    val status = state.delegationStatus
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DELEGATION", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (status?.paused == true) "New spawns paused" else "New spawns enabled",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (status?.paused == true) Warning else MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(if (status?.paused == true) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = status?.paused == true,
                    onCheckedChange = onSetPaused,
                    enabled = status != null && !state.agentsLoading,
                    modifier = Modifier.semantics {
                        contentDescription = if (status?.paused == true) "Resume new subagent spawns" else "Pause new subagent spawns"
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("ACTIVE", state.activeSubagents.size.toString(), Modifier.weight(1f))
                Metric("MAX PARALLEL", status?.maxConcurrentChildren?.takeIf { it > 0 }?.toString() ?: "?", Modifier.weight(1f))
                Metric("MAX DEPTH", status?.maxSpawnDepth?.takeIf { it > 0 }?.toString() ?: "?", Modifier.weight(1f))
            }
            Text(
                "Pausing affects only future delegate_task calls. Running children keep working until completion or an explicit interrupt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun SubagentCard(
    row: SubagentRow,
    sessionLabel: String?,
    matchingSession: StoredSession?,
    onOpenSession: (StoredSession) -> Unit,
    onInterrupt: (() -> Unit)?,
) {
    val agent = row.progress
    var expanded by rememberSaveable(agent.id) { mutableStateOf(false) }
    val statusColor = agent.status.color()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = (row.depth * 14).dp).clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(agent.goal, fontWeight = FontWeight.SemiBold, maxLines = if (expanded) 4 else 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(agent.model, sessionLabel?.let { "session ${it.take(8)}" }).joinToString(" / ").ifBlank { agent.id.take(12) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(agent.status.label(), style = MaterialTheme.typography.labelMedium, color = statusColor)
            }
            val detail = listOfNotNull(
                agent.currentTool?.let { "Tool: $it" },
                agent.toolCount?.let { "$it tools" },
                agent.inputTokens?.let { "$it in" },
                agent.outputTokens?.let { "$it out" },
                agent.durationSeconds?.let { "${it.toInt()}s" },
                agent.costUsd?.let { "$" + "%.4f".format(it) },
            ).joinToString(" / ")
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            agent.summary?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) 8 else 2, overflow = TextOverflow.Ellipsis)
            }
            if (expanded) {
                agent.stream.takeLast(8).forEach { entry ->
                    Text(
                        "${entry.kind.name.lowercase()} / ${entry.text}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (entry.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (agent.filesRead.isNotEmpty()) Text("Read / ${agent.filesRead.joinToString()}", style = MaterialTheme.typography.bodySmall)
                if (agent.filesWritten.isNotEmpty()) Text("Wrote / ${agent.filesWritten.joinToString()}", style = MaterialTheme.typography.bodySmall)
            }
            if (matchingSession != null || onInterrupt != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    matchingSession?.let { session ->
                        OutlinedButton(onClick = { onOpenSession(session) }) {
                            Icon(Icons.Outlined.OpenInNew, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Open session")
                        }
                    }
                    onInterrupt?.let {
                        Button(onClick = it) {
                            Icon(Icons.Outlined.StopCircle, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Interrupt")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundProcessCard(process: BackgroundProcess, onStop: () -> Unit) {
    var expanded by rememberSaveable(process.id) { mutableStateOf(false) }
    val running = process.status == "running"
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Terminal, null, tint = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(process.command.lineSequence().firstOrNull().orEmpty().ifBlank { "Background process" }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(process.id, "${process.uptimeSeconds}s", process.status, process.exitCode?.let { "exit $it" }).filterNotNull().joinToString(" / "),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val output = process.outputTail.ifBlank { process.outputPreview }
            if (expanded && output.isNotBlank()) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface) {
                    Text(output.takeLast(4_000), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (running) {
                OutlinedButton(onClick = onStop) {
                    Icon(Icons.Outlined.StopCircle, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop process")
                }
            }
        }
    }
}

@Composable
private fun EmptyAgentCard(text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AgentNotice(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String) {
    item(key = "section:$title") {
        Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
    }
}

private fun SubagentStatus.label(): String = name.replace('_', ' ')

@Composable
private fun SubagentStatus.color(): Color = when (this) {
    SubagentStatus.QUEUED -> MaterialTheme.colorScheme.onSurfaceVariant
    SubagentStatus.RUNNING -> MaterialTheme.colorScheme.primary
    SubagentStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
    SubagentStatus.FAILED -> MaterialTheme.colorScheme.error
    SubagentStatus.INTERRUPTED -> Warning
}

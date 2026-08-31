package com.nousresearch.hermes.ui

import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
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
import com.nousresearch.hermes.R

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
    var pendingInterrupt by remember { mutableStateOf<String?>(null) }
    var pendingProcessStop by remember { mutableStateOf<String?>(null) }
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

    val sectionActive = stringResource(R.string.ui_agents_section_active_be810b)
    val sectionBg = stringResource(R.string.ui_agents_section_bg_7705a5)
    val sectionRecent = stringResource(R.string.ui_agents_section_recent_7f8fbe)
    val sectionArchived = stringResource(R.string.ui_agents_section_archived_58f99d)
    val replaySectionTitle = state.spawnTreeReplay?.let { replay ->
        stringResource(R.string.ui_agents_section_archive_replay_063118, replay.archive.label.ifBlank { stringResource(R.string.ui_agents_n_subagents_caps_734876, replay.subagents.size) }.uppercase())
    }
    Column(modifier.fillMaxSize()) {
        ManagementHeader(
            stringResource(R.string.ui_agents_command_center_title_122b59),
            stringResource(R.string.ui_agents_subtitle_8615dc),
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
            section(sectionActive)
            if (state.activeSubagents.isEmpty() && !state.agentsLoading) {
                item { EmptyAgentCard(stringResource(R.string.ui_agents_empty_active_eacd55)) }
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
            section(sectionBg)
            if (state.runtimeSessionId == null) {
                item { EmptyAgentCard(stringResource(R.string.ui_agents_empty_open_session_4b5edb)) }
            } else if (state.backgroundProcesses.isEmpty() && !state.agentsLoading) {
                item { EmptyAgentCard(stringResource(R.string.ui_agents_empty_no_bg_bbe5ce)) }
            } else {
                items(state.backgroundProcesses, key = { "process:${it.id}" }) { process ->
                    BackgroundProcessCard(process) {
                        pendingProcessStop = process.id
                    }
                }
            }
            section(sectionRecent)
            if (recent.isEmpty() && !state.agentsLoading) {
                item { EmptyAgentCard(stringResource(R.string.ui_agents_empty_no_events_ebef7a)) }
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
            section(sectionArchived)
            if (state.spawnTreeArchives.isEmpty() && !state.spawnTreesLoading) {
                item { EmptyAgentCard(stringResource(R.string.ui_agents_empty_no_spawn_96fb2a)) }
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
                replaySectionTitle?.let { section(it) }
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
            title = { Text(stringResource(R.string.ui_interrupt_subagent_47f8fc)) },
            text = {
                Text(stringResource(R.string.interrupt_subagent_description, agent?.goal ?: id))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingInterrupt = null
                        onInterrupt(id)
                    },
                ) { Text(stringResource(R.string.ui_interrupt_d5db45)) }
            },
            dismissButton = { TextButton(onClick = { pendingInterrupt = null }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
        )
    }

    pendingProcessStop?.let { id ->
        val process = state.backgroundProcesses.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { pendingProcessStop = null },
            title = { Text(stringResource(R.string.ui_stop_background_process_95afa4)) },
            text = {
                Text(stringResource(R.string.stop_process_description, process?.command?.lineSequence()?.firstOrNull().orEmpty().ifBlank { id }))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingProcessStop = null
                        onStopProcess(id)
                    },
                ) { Text(stringResource(R.string.ui_stop_process_48cb30)) }
            },
            dismissButton = { TextButton(onClick = { pendingProcessStop = null }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
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
                Text(archive.label.ifBlank { stringResource(R.string.ui_agents_n_subagents_1d11bb, archive.count) }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        stringResource(R.string.ui_agents_n_subagents_1d11bb, archive.count),
                        archive.sessionId?.takeIf(String::isNotBlank)?.let { stringResource(R.string.ui_agents_session_570232, it.take(12)) },
                    ).joinToString(" / "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onLoad, enabled = !loading) {
                Text(stringResource(if (selected) R.string.reload else R.string.replay))
            }
        }
    }
}

@Composable
private fun DelegationSummary(state: HermesState, onSetPaused: (Boolean) -> Unit) {
    val status = state.delegationStatus
    val spawnsDesc = stringResource(if (status?.paused == true) R.string.ui_agents_a11y_resume_spawns_410b39 else R.string.ui_agents_a11y_pause_spawns_af09e1)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_delegation_6d3fa6), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (status?.paused == true) R.string.ui_agents_spawns_paused_010b84 else R.string.ui_agents_spawns_enabled_550c17),
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
                        contentDescription = spawnsDesc
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(stringResource(R.string.ui_agents_metric_active_8c4f72), state.activeSubagents.size.toString(), Modifier.weight(1f))
                Metric(stringResource(R.string.ui_agents_metric_max_parallel_a30e4c), status?.maxConcurrentChildren?.takeIf { it > 0 }?.toString() ?: "?", Modifier.weight(1f))
                Metric(stringResource(R.string.ui_agents_metric_max_depth_fa4a88), status?.maxSpawnDepth?.takeIf { it > 0 }?.toString() ?: "?", Modifier.weight(1f))
            }
            Text(stringResource(R.string.ui_pausing_affects_only_future_delegate_task_calls_running_899136),
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
                        listOfNotNull(agent.model, sessionLabel?.let { stringResource(R.string.ui_agents_session_570232, it.take(8)) }).joinToString(" / ").ifBlank { agent.id.take(12) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(agent.status.label(), style = MaterialTheme.typography.labelMedium, color = statusColor)
            }
            val detail = listOfNotNull(
                agent.currentTool?.let { stringResource(R.string.ui_agents_tool_d04181, it) },
                agent.toolCount?.let { stringResource(R.string.ui_agents_n_tools_4553e7, it) },
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
                if (agent.filesRead.isNotEmpty()) Text(stringResource(R.string.files_read, agent.filesRead.joinToString()), style = MaterialTheme.typography.bodySmall)
                if (agent.filesWritten.isNotEmpty()) Text(stringResource(R.string.files_written, agent.filesWritten.joinToString()), style = MaterialTheme.typography.bodySmall)
            }
            if (matchingSession != null || onInterrupt != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    matchingSession?.let { session ->
                        OutlinedButton(onClick = { onOpenSession(session) }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ui_open_session_777092))
                        }
                    }
                    onInterrupt?.let {
                        Button(onClick = it) {
                            Icon(Icons.Outlined.StopCircle, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ui_interrupt_d5db45))
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
                    Text(process.command.lineSequence().firstOrNull().orEmpty().ifBlank { stringResource(R.string.ui_agents_background_process_177e28) }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(process.id, "${process.uptimeSeconds}s", process.status, process.exitCode?.let { stringResource(R.string.ui_agents_exit_27b8d8, it) }).filterNotNull().joinToString(" / "),
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
                    Text(stringResource(R.string.ui_stop_process_48cb30))
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

package com.nousresearch.hermes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.CronJob
import com.nousresearch.hermes.protocol.SkillInfo

@Composable
internal fun SkillsScreen(
    state: HermesState,
    onRefresh: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { onRefresh() }
    val visible = state.skills.filter {
        query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true) || it.category.contains(query, true)
    }
    Column(modifier.fillMaxSize()) {
        ManagementHeader("SKILLS", "Installed capabilities", state.managementLoading, onRefresh, onBack)
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search installed skills") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        if (state.error != null) ManagementError(state.error)
        if (!state.managementLoading && visible.isEmpty()) {
            ManagementEmpty(if (state.skills.isEmpty()) "No installed skills were returned by Hermes." else "No matching skills.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = SkillInfo::name) { skill ->
                    SkillRow(skill, onToggle, Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    }
}

@Composable
internal fun CronScreen(
    state: HermesState,
    onRefresh: () -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onTrigger: (String) -> Unit,
    onCreate: (String, String, String, String) -> Unit,
    onUpdate: (String, String, String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var editorJobId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleteJobId by rememberSaveable { mutableStateOf<String?>(null) }
    val editorJob = state.cronJobs.firstOrNull { it.id == editorJobId }
    val deleteJob = state.cronJobs.firstOrNull { it.id == deleteJobId }
    LaunchedEffect(Unit) { onRefresh() }
    Column(modifier.fillMaxSize()) {
        ManagementHeader("AUTOMATIONS", "Server-side Hermes cron", state.managementLoading, onRefresh, onBack)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Text(
                "Schedules execute on the Hermes backend, not on this Android device. Android notifications are a separate delivery surface.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("Create job")
        }
        if (state.error != null) ManagementError(state.error)
        if (!state.managementLoading && state.cronJobs.isEmpty()) {
            ManagementEmpty("No cron jobs are configured on this Hermes backend.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.cronJobs, key = CronJob::id) { job ->
                    CronRow(
                        job,
                        onSetEnabled,
                        onTrigger,
                        onEdit = { editorJobId = job.id },
                        onDelete = { deleteJobId = job.id },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }

    if (creating) {
        CronEditorDialog(
            job = null,
            onDismiss = { creating = false },
            onSave = { name, prompt, schedule, deliver ->
                creating = false
                onCreate(name, prompt, schedule, deliver)
            },
        )
    }
    editorJob?.let { job ->
        CronEditorDialog(
            job = job,
            onDismiss = { editorJobId = null },
            onSave = { name, prompt, schedule, deliver ->
                editorJobId = null
                onUpdate(job.id, name, prompt, schedule, deliver)
            },
        )
    }
    deleteJob?.let { job ->
        AlertDialog(
            onDismissRequest = { deleteJobId = null },
            title = { Text("DELETE CRON JOB?") },
            text = { Text("${job.name ?: job.id} will stop running on the Hermes backend. Existing run sessions are not deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteJobId = null
                        onDelete(job.id)
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteJobId = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ManagementHeader(
    title: String,
    subtitle: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onBack: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onBack?.let { IconButton(onClick = it) { Icon(Icons.Outlined.ArrowBack, "Back") } }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Outlined.Refresh, "Refresh $title") }
    }
    HorizontalDivider()
}

@Composable
private fun SkillRow(skill: SkillInfo, onToggle: (String, Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(skill.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text(skill.provenance ?: skill.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(skill.description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                skill.usage?.let { Text("$it observed actions", style = MaterialTheme.typography.labelSmall) }
            }
            Switch(checked = skill.enabled, onCheckedChange = { onToggle(skill.name, it) })
        }
    }
}

@Composable
private fun CronRow(
    job: CronJob,
    onSetEnabled: (String, Boolean) -> Unit,
    onTrigger: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(job.name?.takeIf(String::isNotBlank) ?: job.id, fontWeight = FontWeight.SemiBold)
                    Text(
                        job.scheduleDisplay ?: job.schedule?.display ?: job.schedule?.expr ?: "Schedule unavailable",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { onSetEnabled(job.id, !job.enabled) }) {
                    Icon(if (job.enabled) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (job.enabled) "Pause job" else "Resume job")
                }
                IconButton(onClick = { onTrigger(job.id) }) { Icon(Icons.Outlined.PlayArrow, "Run job now") }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit job") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete job") }
            }
            job.prompt?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (job.enabled) "ENABLED" else "PAUSED", style = MaterialTheme.typography.labelSmall)
                job.nextRunAt?.let { Text("NEXT $it", style = MaterialTheme.typography.labelSmall) }
                job.deliver?.let { Text("DELIVER $it", style = MaterialTheme.typography.labelSmall) }
            }
            job.lastError?.let { Text("Last failure: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun CronEditorDialog(
    job: CronJob?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var name by rememberSaveable(job?.id) { mutableStateOf(job?.name.orEmpty()) }
    var prompt by rememberSaveable(job?.id) { mutableStateOf(job?.prompt.orEmpty()) }
    var schedule by rememberSaveable(job?.id) {
        mutableStateOf(job?.schedule?.expr ?: job?.scheduleDisplay.orEmpty())
    }
    var deliver by rememberSaveable(job?.id) { mutableStateOf(job?.deliver.orEmpty()) }
    val valid = prompt.isNotBlank() && schedule.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (job == null) "CREATE CRON JOB" else "EDIT CRON JOB") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(name, { name = it.take(200) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TextField(prompt, { prompt = it }, label = { Text("Hermes prompt") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 7)
                TextField(
                    schedule,
                    { schedule = it },
                    label = { Text("Exact schedule") },
                    supportingText = { Text("Cron expression or schedule form accepted by this Hermes server; timezone is evaluated server-side.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                TextField(deliver, { deliver = it }, label = { Text("Delivery destination (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, prompt, schedule, deliver) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ManagementError(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun ManagementEmpty(message: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

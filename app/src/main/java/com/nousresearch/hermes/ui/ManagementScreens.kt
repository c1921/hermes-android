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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.CronJob
import com.nousresearch.hermes.protocol.ProfileInfo
import com.nousresearch.hermes.protocol.SkillInfo
import com.nousresearch.hermes.protocol.SkillHubResult
import com.nousresearch.hermes.protocol.StoredSession

@Composable
internal fun BackendsScreen(
    state: HermesState,
    onConnect: (String, String, String, Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onForget: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var forgetId by rememberSaveable { mutableStateOf<String?>(null) }
    val forgetBackend = state.savedBackends.firstOrNull { it.id == forgetId }
    Column(modifier.fillMaxSize()) {
        ManagementHeader("BACKENDS", "Saved Hermes installations", state.loading, null, onBack)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Text(
                "Connection metadata is stored in app-private preferences. Tokens are encrypted separately with Android Keystore and are never displayed here.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("Add backend")
        }
        state.error?.let { ManagementError(it) }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.savedBackends, key = BackendConfig::id) { backend ->
                val selected = backend.id == state.backend?.id
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(backend.label, fontWeight = FontWeight.SemiBold)
                                Text(backend.baseUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (selected) {
                                Text("CONNECTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            } else {
                                TextButton(onClick = { onSelect(backend.id) }) { Text("Connect") }
                            }
                            IconButton(onClick = { forgetId = backend.id }) { Icon(Icons.Outlined.Delete, "Forget ${backend.label}") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            backend.lastHermesVersion?.let { Text("HERMES $it", style = MaterialTheme.typography.labelSmall) }
                            if (backend.baseUrl.startsWith("http://")) {
                                Text("PRIVATE HTTP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("TLS", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        BackendConnectionDialog(
            onDismiss = { adding = false },
            onConnect = { label, url, token, allowPrivate ->
                adding = false
                onConnect(label, url, token, allowPrivate)
            },
        )
    }
    forgetBackend?.let { backend ->
        AlertDialog(
            onDismissRequest = { forgetId = null },
            title = { Text("FORGET BACKEND?") },
            text = { Text("${backend.label} will be removed from this device and its Keystore token deleted. Nothing is deleted from the Hermes server.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        forgetId = null
                        onForget(backend.id)
                    },
                ) { Text("Forget") }
            },
            dismissButton = { TextButton(onClick = { forgetId = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BackendConnectionDialog(
    onDismiss: () -> Unit,
    onConnect: (String, String, String, Boolean) -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var allowPrivate by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ADD HERMES BACKEND") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(label, { label = it.take(100) }, label = { Text("Connection name") }, singleLine = true)
                TextField(url, { url = it }, label = { Text("HTTPS URL") }, singleLine = true)
                TextField(
                    token,
                    { token = it },
                    label = { Text("Dashboard token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow private-network HTTP")
                        Text("Only literal LAN, loopback, or Tailscale IPs.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(allowPrivate, { allowPrivate = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConnect(label, url, token, allowPrivate) },
                enabled = url.isNotBlank() && token.isNotBlank(),
            ) { Text("Test and save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun SkillsScreen(
    state: HermesState,
    onRefresh: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onLoadHub: (String) -> Unit,
    onReview: (String) -> Unit,
    onCloseReview: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onUpdate: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var browsing by rememberSaveable { mutableStateOf(false) }
    var uninstallName by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { onRefresh() }
    val visible = state.skills.filter {
        query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true) || it.category.contains(query, true)
    }
    Column(modifier.fillMaxSize()) {
        ManagementHeader("SKILLS", if (browsing) "Review before installing" else "Installed capabilities", state.managementLoading || state.skillHubLoading, onRefresh, onBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { browsing = false }, modifier = Modifier.weight(1f)) { Text("Installed") }
            OutlinedButton(
                onClick = { browsing = true; onLoadHub("") },
                modifier = Modifier.weight(1f),
            ) { Text("Browse hub") }
            if (!browsing) TextButton(onClick = onUpdate, enabled = state.skillAction?.running != true) { Text("Update") }
        }
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(if (browsing) "Search the Hermes skills hub" else "Search installed skills") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        if (browsing) {
            Button(
                onClick = { onLoadHub(query) },
                enabled = query.isNotBlank() && !state.skillHubLoading,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) { Text("Search hub") }
        }
        state.skillAction?.let { action ->
            Text(
                when {
                    action.running -> "Skill operation running on Hermes${action.pid?.let { " / PID $it" }.orEmpty()}"
                    action.exitCode == 0 -> "Skill operation completed"
                    action.error != null -> action.error
                    else -> "Skill operation exited ${action.exitCode ?: "without status"}"
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (action.exitCode != null && action.exitCode != 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        if (state.error != null) ManagementError(state.error)
        if (browsing) {
            if (!state.skillHubLoading && state.skillHubResults.isEmpty()) {
                ManagementEmpty("No hub results. Search by capability, tool or workflow.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.skillHubResults, key = SkillHubResult::identifier) { skill ->
                        SkillHubRow(skill, onReview, Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        } else if (!state.managementLoading && visible.isEmpty()) {
            ManagementEmpty(if (state.skills.isEmpty()) "No installed skills were returned by Hermes." else "No matching skills.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = SkillInfo::name) { skill ->
                    SkillRow(skill, onToggle, { uninstallName = it }, Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    }

    state.skillHubReview?.let { review ->
        val blocked = review.scan.policy == "block"
        AlertDialog(
            onDismissRequest = onCloseReview,
            title = { Text("REVIEW ${review.preview.name.uppercase()}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${review.preview.trustLevel.uppercase()} / ${review.preview.source} / ${review.scan.verdict.uppercase()}")
                    Text(review.scan.summary.ifBlank { review.preview.description }, style = MaterialTheme.typography.bodySmall)
                    review.scan.policyReason?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (review.scan.findings.isNotEmpty()) {
                        Text(
                            review.scan.findings.take(8).joinToString("\n") { "${it.severity.uppercase()} · ${it.file}${it.line?.let { line -> ":$line" }.orEmpty()} · ${it.description}" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("FILES / ${review.preview.files.joinToString().ifBlank { "SKILL.md only" }}", style = MaterialTheme.typography.labelSmall)
                    Text(review.preview.skillMarkdown.take(4_000), style = MaterialTheme.typography.bodySmall, maxLines = 14, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = {
                Button(onClick = onInstall, enabled = !blocked) {
                    Text(if (review.scan.policy == "ask") "Accept risk and install" else if (blocked) "Blocked by Hermes" else "Install")
                }
            },
            dismissButton = { TextButton(onClick = onCloseReview) { Text("Cancel") } },
        )
    }
    uninstallName?.let { name ->
        AlertDialog(
            onDismissRequest = { uninstallName = null },
            title = { Text("REMOVE SKILL") },
            text = { Text("Uninstall $name from the selected Hermes profile?") },
            confirmButton = { TextButton(onClick = { uninstallName = null; onUninstall(name) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { uninstallName = null }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun CronScreen(
    state: HermesState,
    onRefresh: () -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onTrigger: (String) -> Unit,
    onLoadRuns: (String) -> Unit,
    onOpenRun: (StoredSession) -> Unit,
    onCreate: (String, String, String, String) -> Unit,
    onUpdate: (String, String, String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var editorJobId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleteJobId by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedJobId by rememberSaveable { mutableStateOf<String?>(null) }
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
                        runs = state.cronRuns[job.id],
                        expanded = expandedJobId == job.id,
                        onHistory = {
                            val expanding = expandedJobId != job.id
                            expandedJobId = if (expanding) job.id else null
                            if (expanding) onLoadRuns(job.id)
                        },
                        onOpenRun = onOpenRun,
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
internal fun ProfilesScreen(
    state: HermesState,
    onRefresh: () -> Unit,
    onStartSession: (String) -> Unit,
    onCreate: (String, String, Boolean, Boolean) -> Unit,
    onRename: (String, String) -> Unit,
    onSetActive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var renameProfileName by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteProfileName by rememberSaveable { mutableStateOf<String?>(null) }
    val renameProfile = state.profiles.firstOrNull { it.name == renameProfileName }
    val deleteProfile = state.profiles.firstOrNull { it.name == deleteProfileName }
    LaunchedEffect(Unit) { onRefresh() }
    Column(modifier.fillMaxSize()) {
        ManagementHeader("PROFILES", "Isolated Hermes workspaces", state.managementLoading, onRefresh, onBack)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Text(
                "Running profile: ${state.currentProfile}. Sticky default: ${state.activeProfile}. The sticky default affects future Hermes CLI processes; starting a session here scopes this live connection explicitly.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("Create profile")
        }
        if (state.error != null) ManagementError(state.error)
        if (!state.managementLoading && state.profiles.isEmpty()) {
            ManagementEmpty("No profiles were returned by this Hermes backend.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.profiles, key = ProfileInfo::name) { profile ->
                    ProfileRow(
                        profile = profile,
                        isActive = profile.name == state.activeProfile,
                        isCurrent = profile.name == state.currentProfile,
                        onStartSession = { onStartSession(profile.name) },
                        onRename = { renameProfileName = profile.name },
                        onSetActive = { onSetActive(profile.name) },
                        onDelete = { deleteProfileName = profile.name },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }

    if (creating) {
        ProfileCreateDialog(
            onDismiss = { creating = false },
            onCreate = { name, cloneFrom, cloneAll, noSkills ->
                creating = false
                onCreate(name, cloneFrom, cloneAll, noSkills)
            },
        )
    }
    renameProfile?.let { profile ->
        ProfileRenameDialog(
            profile = profile,
            onDismiss = { renameProfileName = null },
            onRename = { newName ->
                renameProfileName = null
                onRename(profile.name, newName)
            },
        )
    }
    deleteProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteProfileName = null },
            title = { Text("DELETE PROFILE?") },
            text = { Text("${profile.name} and its isolated config, sessions, skills, and memory will be deleted from the Hermes server. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteProfileName = null
                        onDelete(profile.name)
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteProfileName = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: ProfileInfo,
    isActive: Boolean,
    isCurrent: Boolean,
    onStartSession: () -> Unit,
    onRename: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val protected = profile.isDefault || isCurrent
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(profile.provider, profile.model).joinToString(" / ").ifBlank { "Model not assigned" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onStartSession) { Icon(Icons.Outlined.PlayArrow, "Start session in ${profile.name}") }
                if (!profile.isDefault) IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, "Rename ${profile.name}") }
                if (!protected) IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete ${profile.name}") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (profile.isDefault) Text("DEFAULT ROOT", style = MaterialTheme.typography.labelSmall)
                if (isCurrent) Text("SERVER PROCESS", style = MaterialTheme.typography.labelSmall)
                Text("${profile.skillCount} SKILLS", style = MaterialTheme.typography.labelSmall)
                if (isActive) {
                    Text("STICKY DEFAULT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    TextButton(onClick = onSetActive) {
                        Icon(Icons.Outlined.Star, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Make default")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Boolean, Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var cloneFrom by rememberSaveable { mutableStateOf("") }
    var cloneAll by rememberSaveable { mutableStateOf(false) }
    var noSkills by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CREATE PROFILE") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(name, { name = it.take(100) }, label = { Text("Profile name") }, singleLine = true)
                TextField(
                    cloneFrom,
                    { cloneFrom = it.take(100) },
                    label = { Text("Clone source (optional)") },
                    supportingText = { Text("Leave empty for a fresh profile. Use an exact existing profile name.") },
                    singleLine = true,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Clone sessions and full state", Modifier.weight(1f))
                    Switch(cloneAll, { cloneAll = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Start without bundled skills", Modifier.weight(1f))
                    Switch(noSkills, { noSkills = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, cloneFrom, cloneAll, noSkills) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ProfileRenameDialog(
    profile: ProfileInfo,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by rememberSaveable(profile.name) { mutableStateOf(profile.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("RENAME PROFILE") },
        text = { TextField(name, { name = it.take(100) }, label = { Text("New name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank() && name != profile.name) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ManagementHeader(
    title: String,
    subtitle: String,
    loading: Boolean,
    onRefresh: (() -> Unit)?,
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
        onRefresh?.let {
            IconButton(onClick = it, enabled = !loading) { Icon(Icons.Outlined.Refresh, "Refresh $title") }
        }
    }
    HorizontalDivider()
}

@Composable
private fun SkillRow(
    skill: SkillInfo,
    onToggle: (String, Boolean) -> Unit,
    onUninstall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            IconButton(onClick = { onUninstall(skill.name) }) { Icon(Icons.Outlined.Delete, "Uninstall ${skill.name}") }
        }
    }
}

@Composable
private fun SkillHubRow(skill: SkillHubResult, onReview: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(skill.name, fontWeight = FontWeight.SemiBold)
                    Text("${skill.trustLevel.uppercase()} / ${skill.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Button(onClick = { onReview(skill.identifier) }) { Text("Review") }
            }
            Text(skill.description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (skill.tags.isNotEmpty()) Text(skill.tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CronRow(
    job: CronJob,
    onSetEnabled: (String, Boolean) -> Unit,
    onTrigger: (String) -> Unit,
    runs: List<StoredSession>?,
    expanded: Boolean,
    onHistory: () -> Unit,
    onOpenRun: (StoredSession) -> Unit,
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
            TextButton(onClick = onHistory) {
                Icon(Icons.Outlined.History, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (expanded) "Hide runs" else "Recent runs")
            }
            if (expanded) {
                when {
                    runs == null -> Text("Loading run history…", style = MaterialTheme.typography.bodySmall)
                    runs.isEmpty() -> Text("No executions have produced sessions for this job.", style = MaterialTheme.typography.bodySmall)
                    else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        runs.forEach { run ->
                            Surface(
                                onClick = { onOpenRun(run) },
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(run.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(run.profile, run.model).joinToString(" / ").ifBlank { run.durableId },
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (run.isActive) Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
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

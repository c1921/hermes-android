package com.nousresearch.hermes.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.data.ProfileIdentityDraft
import com.nousresearch.hermes.network.DashboardAuthProvider
import com.nousresearch.hermes.protocol.CronJob
import com.nousresearch.hermes.protocol.ProfileInfo
import com.nousresearch.hermes.protocol.SkillInfo
import com.nousresearch.hermes.protocol.SkillHubResult
import com.nousresearch.hermes.protocol.StoredSession
import com.nousresearch.hermes.protocol.ToolsetInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.nousresearch.hermes.R

private enum class CapabilityView { SKILLS, HUB, TOOLSETS }

@Composable
internal fun BackendsScreen(
    state: HermesState,
    onDiscoverPasswordProviders: suspend (String, Boolean) -> List<DashboardAuthProvider>,
    onConnect: (String, String, String, String, Boolean, String) -> Unit,
    onSelect: (String) -> Unit,
    onForget: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var reconnectId by remember { mutableStateOf<String?>(null) }
    var forgetId by remember { mutableStateOf<String?>(null) }
    val forgetBackend = state.savedBackends.firstOrNull { it.id == forgetId }
    Column(modifier.fillMaxSize()) {
        ManagementHeader(stringResource(R.string.ui_mgmt_backends_title_4283fe), stringResource(R.string.ui_mgmt_saved_hermes_installations_fc63f0), state.loading, null, onBack)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Text(stringResource(R.string.ui_connection_metadata_is_stored_in_app_private_preference_401ebb),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(onClick = { reconnectId = null; adding = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ui_add_backend_88b685))
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
                                Text(stringResource(R.string.ui_connected_073afc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            } else {
                                val reconnectRequired = backend.authMode != com.nousresearch.hermes.data.AuthMode.DASHBOARD_SESSION ||
                                    state.reconnectRequiredBackendId == backend.id
                                TextButton(
                                    onClick = {
                                        if (reconnectRequired) {
                                            reconnectId = backend.id
                                            adding = true
                                        } else {
                                            onSelect(backend.id)
                                        }
                                    },
                                ) { Text(stringResource(if (reconnectRequired) R.string.reconnect else R.string.ui_connect_b65463)) }
                            }
                            IconButton(onClick = { forgetId = backend.id }) { Icon(Icons.Outlined.Delete, stringResource(R.string.forget_named, backend.label)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            backend.lastHermesVersion?.let { Text(stringResource(R.string.ui_mgmt_hermes_version_cd0e87, it), style = MaterialTheme.typography.labelSmall) }
                            if (backend.baseUrl.startsWith("http://")) {
                                Text(stringResource(R.string.ui_private_http_e0aab5), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text(stringResource(R.string.ui_tls_d91e18), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        BackendConnectionDialog(
            initial = state.savedBackends.firstOrNull { it.id == reconnectId },
            onDiscoverPasswordProviders = onDiscoverPasswordProviders,
            onDismiss = { adding = false; reconnectId = null },
            onConnect = { label, url, username, password, allowPrivate, provider ->
                adding = false
                reconnectId = null
                onConnect(label, url, username, password, allowPrivate, provider)
            },
        )
    }
    forgetBackend?.let { backend ->
        AlertDialog(
            onDismissRequest = { forgetId = null },
            title = { Text(stringResource(R.string.ui_forget_backend_ed8798)) },
            text = { Text(stringResource(R.string.remove_backend_description, backend.label)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        forgetId = null
                        onForget(backend.id)
                    },
                ) { Text(stringResource(R.string.ui_forget_03d5d8)) }
            },
            dismissButton = { TextButton(onClick = { forgetId = null }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
        )
    }
}

@Composable
private fun BackendConnectionDialog(
    initial: BackendConfig?,
    onDiscoverPasswordProviders: suspend (String, Boolean) -> List<DashboardAuthProvider>,
    onDismiss: () -> Unit,
    onConnect: (String, String, String, String, Boolean, String) -> Unit,
) {
    val context = LocalContext.current
    var label by remember(initial?.id) { mutableStateOf(initial?.label.orEmpty()) }
    var url by remember(initial?.id) { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var allowPrivate by rememberSaveable(initial?.id) { mutableStateOf(initial?.allowInsecurePrivateNetwork == true) }
    var passwordProviders by remember { mutableStateOf(emptyList<DashboardAuthProvider>()) }
    var selectedProvider by remember { mutableStateOf<String?>(null) }
    var providerSource by remember { mutableStateOf<String?>(null) }
    var providerError by remember { mutableStateOf<String?>(null) }
    var discoveringProviders by remember { mutableStateOf(false) }
    val providerDiscoveryGate = remember { DashboardProviderDiscoveryGate() }
    val providerScope = rememberCoroutineScope()
    val providerKey = "${url.trim().trimEnd('/')}|$allowPrivate"

    fun clearProviderSelection() {
        providerDiscoveryGate.invalidate()
        discoveringProviders = false
        passwordProviders = emptyList()
        selectedProvider = null
        providerSource = null
        providerError = null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.add_backend_title else R.string.reconnect_backend_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(label, { label = it.take(100) }, label = { Text(stringResource(R.string.ui_connection_name_5584ef)) }, singleLine = true)
                OutlinedTextField(
                    url,
                    { value -> url = value; clearProviderSelection() },
                    label = { Text(stringResource(R.string.ui_https_url_93e9be)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    username,
                    { username = it },
                    label = { Text(stringResource(R.string.ui_dashboard_username_6e522d)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text(stringResource(R.string.ui_dashboard_password_ade7f2)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui_allow_private_network_http_2be5ea))
                        Text(stringResource(R.string.ui_only_literal_lan_loopback_or_tailscale_ips_f7e430), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = allowPrivate,
                        onCheckedChange = { allowPrivate = it; clearProviderSelection() },
                        modifier = Modifier.localizedContentDescription(R.string.a11y_allow_private_network_http_2be5ea),
                    )
                }
                if (passwordProviders.size > 1 && providerSource == providerKey) {
                    DashboardPasswordProviderSelector(
                        providers = passwordProviders,
                        selectedProvider = selectedProvider,
                        onSelected = { selectedProvider = it; providerError = null },
                    )
                }
                DashboardOAuthAvailabilityNotice()
                providerError?.let { ManagementError(it) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val submit: (String) -> Unit = { provider ->
                        val submittedPassword = password
                        password = ""
                        onConnect(label, url, username, submittedPassword, allowPrivate, provider)
                    }
                    if (providerSource == providerKey) {
                        selectedProvider?.let(submit)
                    } else {
                        val requestToken = providerDiscoveryGate.begin()
                        if (requestToken != null) {
                            val requestedUrl = url
                            val requestedAllowPrivate = allowPrivate
                            discoveringProviders = true
                            providerError = null
                            providerScope.launch {
                                try {
                                    val providers = onDiscoverPasswordProviders(requestedUrl, requestedAllowPrivate)
                                    if (providerDiscoveryGate.isCurrent(requestToken)) {
                                        providerSource = "${requestedUrl.trim().trimEnd('/')}|$requestedAllowPrivate"
                                        passwordProviders = providers
                                        selectedProvider = providers.singleOrNull()?.name
                                        if (providers.size == 1) submit(providers.single().name)
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Throwable) {
                                    if (providerDiscoveryGate.isCurrent(requestToken)) {
                                        clearProviderSelection()
                                        providerError = failure.message ?: context.getString(R.string.ui_mgmt_could_not_load_dashboard_providers_3a0000)
                                    }
                                } finally {
                                    val current = providerDiscoveryGate.isCurrent(requestToken)
                                    providerDiscoveryGate.finish(requestToken)
                                    if (current) discoveringProviders = false
                                }
                            }
                        }
                    }
                },
                enabled = !discoveringProviders && url.isNotBlank() && username.isNotBlank() && password.isNotEmpty() &&
                    (providerSource != providerKey || passwordProviders.size == 1 || selectedProvider != null),
            ) {
                if (discoveringProviders) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(if (providerSource == providerKey) R.string.test_save else R.string.check_signin_options))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
    )
}

@Composable
internal fun SkillsScreen(
    state: HermesState,
    onRefresh: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onRefreshToolsets: () -> Unit,
    onToggleToolset: (String, Boolean) -> Unit,
    onLoadHub: (String) -> Unit,
    onReview: (String) -> Unit,
    onCloseReview: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    onUpdate: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var view by rememberSaveable { mutableStateOf(CapabilityView.SKILLS) }
    var uninstallName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { onRefresh() }
    val visible = state.skills.filter {
        query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true) || it.category.orEmpty().contains(query, true)
    }
    val visibleToolsets = state.toolsets.filter {
        query.isBlank() || it.name.contains(query, true) || it.label.contains(query, true) ||
            it.description.contains(query, true) || it.tools.any { tool -> tool.contains(query, true) }
    }
    val refresh: () -> Unit = {
        when (view) {
            CapabilityView.SKILLS -> onRefresh()
            CapabilityView.HUB -> onLoadHub(query)
            CapabilityView.TOOLSETS -> onRefreshToolsets()
        }
    }
    Column(modifier.fillMaxSize()) {
        ManagementHeader(
            stringResource(R.string.ui_mgmt_capabilities_title_c6d07e),
            when (view) {
                CapabilityView.SKILLS -> stringResource(R.string.ui_mgmt_installed_skills_f7b77e)
                CapabilityView.HUB -> stringResource(R.string.ui_mgmt_review_before_installing_90d359)
                CapabilityView.TOOLSETS -> stringResource(R.string.ui_mgmt_server_tools_for_3ad699, state.activeProfile)
            },
            state.managementLoading || state.skillHubLoading || state.toolsetsLoading,
            refresh,
            onBack,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CapabilityTab(stringResource(R.string.ui_mgmt_tab_skills_fc1f7c), view == CapabilityView.SKILLS, Modifier.weight(1f)) {
                view = CapabilityView.SKILLS
            }
            CapabilityTab(stringResource(R.string.ui_mgmt_tab_hub_d5bb2e), view == CapabilityView.HUB, Modifier.weight(1f)) {
                view = CapabilityView.HUB
                onLoadHub("")
            }
            CapabilityTab(stringResource(R.string.ui_mgmt_tab_tools_35cbf0), view == CapabilityView.TOOLSETS, Modifier.weight(1f)) {
                view = CapabilityView.TOOLSETS
                onRefreshToolsets()
            }
        }
        if (view == CapabilityView.SKILLS) {
            OutlinedButton(
                onClick = onUpdate,
                enabled = state.skillAction?.running != true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) { Text(stringResource(R.string.ui_update_all_skills_ebb6f9), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(200) },
            placeholder = {
                Text(
                    when (view) {
                        CapabilityView.SKILLS -> stringResource(R.string.ui_mgmt_search_installed_skills_f19c55)
                        CapabilityView.HUB -> stringResource(R.string.ui_mgmt_search_hermes_skills_hub_7067cb)
                        CapabilityView.TOOLSETS -> stringResource(R.string.ui_mgmt_search_toolsets_tools_be80c4)
                    },
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        if (view == CapabilityView.HUB) {
            Button(
                onClick = { onLoadHub(query) },
                enabled = query.isNotBlank() && !state.skillHubLoading,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) { Text(stringResource(R.string.ui_search_hub_d3545c)) }
        }
        if (view != CapabilityView.TOOLSETS) state.skillAction?.let { action ->
            Text(
                when {
                    action.running -> stringResource(R.string.ui_mgmt_skill_op_running_6d1fb6, action.pid?.let { stringResource(R.string.ui_mgmt_pid_suffix_dfbdb0, it) }.orEmpty())
                    action.exitCode == 0 -> stringResource(R.string.ui_mgmt_skill_op_completed_ae0755)
                    action.error != null -> action.error
                    else -> stringResource(R.string.ui_mgmt_skill_op_exited_281220, action.exitCode?.toString() ?: stringResource(R.string.ui_mgmt_without_status_e08783))
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (action.exitCode != null && action.exitCode != 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        if (state.error != null) ManagementError(state.error)
        if (view == CapabilityView.TOOLSETS) {
            state.toolsetNotice?.let {
                Text(it, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            state.toolsetError?.let { ManagementError(it) }
            if (!state.toolsetsLoading && visibleToolsets.isEmpty()) {
                ManagementEmpty(if (state.toolsets.isEmpty()) stringResource(R.string.ui_mgmt_no_configurable_toolsets_5b9255) else stringResource(R.string.ui_mgmt_no_matching_toolsets_8589ea))
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibleToolsets, key = ToolsetInfo::name) { toolset ->
                        ToolsetRow(toolset, state.toolsetsLoading, onToggleToolset, Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        } else if (view == CapabilityView.HUB) {
            if (!state.skillHubLoading && state.skillHubResults.isEmpty()) {
                ManagementEmpty(stringResource(R.string.ui_mgmt_no_hub_results_ff309c))
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.skillHubResults, key = SkillHubResult::identifier) { skill ->
                        SkillHubRow(skill, onReview, Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        } else if (!state.managementLoading && visible.isEmpty()) {
            ManagementEmpty(if (state.skills.isEmpty()) stringResource(R.string.ui_mgmt_no_installed_skills_ad6838) else stringResource(R.string.ui_mgmt_no_matching_skills_6d070c))
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
            title = { Text(stringResource(R.string.review_named, review.preview.name.uppercase())) },
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
                    Text(stringResource(R.string.files_named, review.preview.files.joinToString().ifBlank { "SKILL.md only" }), style = MaterialTheme.typography.labelSmall)
                    Text(review.preview.skillMarkdown.take(4_000), style = MaterialTheme.typography.bodySmall, maxLines = 14, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = {
                Button(onClick = onInstall, enabled = !blocked) {
                    Text(if (review.scan.policy == "ask") stringResource(R.string.ui_mgmt_accept_risk_and_install_415ed7) else if (blocked) stringResource(R.string.ui_mgmt_blocked_by_hermes_e70cad) else stringResource(R.string.ui_mgmt_install_194619))
                }
            },
            dismissButton = { TextButton(onClick = onCloseReview) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
        )
    }
    uninstallName?.let { name ->
        AlertDialog(
            onDismissRequest = { uninstallName = null },
            title = { Text(stringResource(R.string.ui_remove_skill_9ad745)) },
            text = { Text(stringResource(R.string.uninstall_skill_question, name)) },
            confirmButton = { TextButton(onClick = { uninstallName = null; onUninstall(name) }) { Text(stringResource(R.string.ui_remove_e96390)) } },
            dismissButton = { TextButton(onClick = { uninstallName = null }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
        )
    }
}

@Composable
private fun CapabilityTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
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
    var editorJobId by remember { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleteJobId by remember { mutableStateOf<String?>(null) }
    var expandedJobId by remember { mutableStateOf<String?>(null) }
    var pendingToggle by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var pendingRunJobId by remember { mutableStateOf<String?>(null) }
    val editorJob = state.cronJobs.firstOrNull { it.id == editorJobId }
    val deleteJob = state.cronJobs.firstOrNull { it.id == deleteJobId }
    LaunchedEffect(Unit) { onRefresh() }
    Column(modifier.fillMaxSize()) {
        ManagementHeader(stringResource(R.string.ui_mgmt_automations_title_22995d), stringResource(R.string.ui_mgmt_server_side_hermes_cron_84016e), state.managementLoading, onRefresh, onBack)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Text(stringResource(R.string.ui_schedules_execute_on_the_hermes_backend_not_on_this_and_3497ba),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ui_create_job_b9a738))
        }
        if (state.error != null) ManagementError(state.error)
        if (!state.managementLoading && state.cronJobs.isEmpty()) {
            ManagementEmpty(stringResource(R.string.ui_mgmt_no_cron_jobs_51c6b3))
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.cronJobs, key = CronJob::id) { job ->
                    CronRow(
                        job,
                        onSetEnabled = { id, enabled -> pendingToggle = id to enabled },
                        onTrigger = { pendingRunJobId = it },
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
            title = { Text(stringResource(R.string.ui_delete_cron_job_3c9650)) },
            text = { Text(stringResource(R.string.delete_cron_description, job.name ?: job.id)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteJobId = null
                        onDelete(job.id)
                    },
                ) { Text(stringResource(R.string.ui_delete_f6fdbe)) }
            },
            dismissButton = { TextButton(onClick = { deleteJobId = null }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
        )
    }
    pendingToggle?.let { (jobId, enabled) ->
        val job = state.cronJobs.firstOrNull { it.id == jobId }
        AlertDialog(
            onDismissRequest = { pendingToggle = null },
            title = { Text(stringResource(if (enabled) R.string.ui_mgmt_resume_cron_job_6d6f3c else R.string.ui_mgmt_pause_cron_job_9f0ac9)) },
            text = {
                Text(
                    stringResource(
                        R.string.ui_mgmt_cron_toggle_body_f72124,
                        stringResource(if (enabled) R.string.ui_mgmt_resume_056a4a else R.string.ui_mgmt_pause_d683cb),
                        job?.name?.takeIf(String::isNotBlank) ?: jobId,
                        state.activeProfile,
                        stringResource(if (enabled) R.string.ui_mgmt_future_runs_resume_5568a8 else R.string.ui_mgmt_future_runs_stop_baa397),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingToggle = null; onSetEnabled(jobId, enabled) }) {
                    Text(stringResource(if (enabled) R.string.ui_mgmt_resume_056a4a else R.string.ui_mgmt_pause_d683cb))
                }
            },
            dismissButton = { TextButton(onClick = { pendingToggle = null }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
        )
    }
    pendingRunJobId?.let { jobId ->
        val job = state.cronJobs.firstOrNull { it.id == jobId }
        AlertDialog(
            onDismissRequest = { pendingRunJobId = null },
            title = { Text(stringResource(R.string.ui_run_cron_job_now_565765)) },
            text = {
                Text(
                    stringResource(R.string.ui_mgmt_cron_run_body_f43a1d, job?.name?.takeIf(String::isNotBlank) ?: jobId, state.activeProfile),
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingRunJobId = null; onTrigger(jobId) }) { Text(stringResource(R.string.ui_run_now_2af00e)) }
            },
            dismissButton = { TextButton(onClick = { pendingRunJobId = null }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
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
    onLoadIdentity: suspend (String) -> ProfileIdentityDraft,
    onSaveSoul: suspend (String, String) -> Unit,
    onSaveModel: suspend (String, String, String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(false) }
    var renameProfileName by remember { mutableStateOf<String?>(null) }
    var deleteProfileName by remember { mutableStateOf<String?>(null) }
    var identityName by remember { mutableStateOf<String?>(null) }
    var identityLoaded by rememberSaveable { mutableStateOf(false) }
    var identityLoading by remember { mutableStateOf(false) }
    var originalSoul by remember { mutableStateOf("") }
    var soulDraft by remember { mutableStateOf("") }
    var setupCommand by remember { mutableStateOf("") }
    var originalProvider by remember { mutableStateOf("") }
    var providerDraft by remember { mutableStateOf("") }
    var originalModel by remember { mutableStateOf("") }
    var modelDraft by remember { mutableStateOf("") }
    var identityError by remember { mutableStateOf<String?>(null) }
    var identityNotice by remember { mutableStateOf<String?>(null) }
    var confirmDiscardIdentity by rememberSaveable { mutableStateOf(false) }
    val renameProfile = state.profiles.firstOrNull { it.name == renameProfileName }
    val deleteProfile = state.profiles.firstOrNull { it.name == deleteProfileName }
    LaunchedEffect(Unit) { onRefresh() }
    LaunchedEffect(identityName, identityLoaded) {
        val name = identityName ?: return@LaunchedEffect
        if (identityLoaded) return@LaunchedEffect
        identityLoading = true
        identityError = null
        try {
            val identity = onLoadIdentity(name)
            if (identityName == name) {
                originalSoul = identity.soul
                soulDraft = identity.soul
                setupCommand = identity.setupCommand
                originalProvider = identity.provider
                providerDraft = identity.provider
                originalModel = identity.model
                modelDraft = identity.model
                identityLoaded = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            identityError = error.message ?: context.getString(R.string.ui_mgmt_could_not_load_profile_identity_2c44a3)
        } finally {
            identityLoading = false
        }
    }

    fun closeIdentity() {
        identityName = null
        identityLoaded = false
        identityLoading = false
        originalSoul = ""
        soulDraft = ""
        setupCommand = ""
        originalProvider = ""
        providerDraft = ""
        originalModel = ""
        modelDraft = ""
        identityError = null
        identityNotice = null
        confirmDiscardIdentity = false
    }

    fun requestCloseIdentity() {
        if (profileIdentityDirty(originalSoul, soulDraft, originalProvider, providerDraft, originalModel, modelDraft)) {
            confirmDiscardIdentity = true
        } else {
            closeIdentity()
        }
    }
    Column(modifier.fillMaxSize()) {
        ManagementHeader(stringResource(R.string.ui_mgmt_profiles_title_c2728b), stringResource(R.string.ui_mgmt_isolated_hermes_workspaces_93e831), state.managementLoading, onRefresh, onBack)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Text(
                stringResource(R.string.ui_mgmt_profiles_intro_9002d3, state.currentProfile, state.activeProfile),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ui_create_profile_96b8ee))
        }
        if (state.error != null) ManagementError(state.error)
        if (!state.managementLoading && state.profiles.isEmpty()) {
            ManagementEmpty(stringResource(R.string.ui_mgmt_no_profiles_167d53))
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.profiles, key = ProfileInfo::name) { profile ->
                    ProfileRow(
                        profile = profile,
                        isActive = profile.name == state.activeProfile,
                        isCurrent = profile.name == state.currentProfile,
                        onStartSession = { onStartSession(profile.name) },
                        onRename = { renameProfileName = profile.name },
                        onEditIdentity = {
                            identityName = profile.name
                            identityLoaded = false
                            identityError = null
                            identityNotice = null
                        },
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
            title = { Text(stringResource(R.string.ui_delete_profile_99163f)) },
            text = { Text(stringResource(R.string.delete_profile_description, profile.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteProfileName = null
                        onDelete(profile.name)
                    },
                ) { Text(stringResource(R.string.ui_delete_f6fdbe)) }
            },
            dismissButton = { TextButton(onClick = { deleteProfileName = null }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
        )
    }
    identityName?.let { name ->
        AlertDialog(
            onDismissRequest = ::requestCloseIdentity,
            title = { Text(stringResource(R.string.profile_identity, name)) },
            text = {
                if (identityLoading && !identityLoaded) {
                    CircularProgressIndicator()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.ui_mgmt_stored_on_for_profile_e5753d, state.backend?.label.orEmpty(), name),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (setupCommand.isNotBlank()) {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(stringResource(R.string.ui_host_setup_command_077939), style = MaterialTheme.typography.labelMedium)
                                    Text(setupCommand, style = MaterialTheme.typography.bodySmall)
                                    TextButton(
                                        onClick = {
                                            context.getSystemService(ClipboardManager::class.java)
                                                .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ui_hermes_profile_setup_command_07e096), setupCommand))
                                            identityNotice = context.getString(R.string.ui_mgmt_setup_command_copied_c98fa3)
                                        },
                                    ) { Text(stringResource(R.string.ui_copy_command_a8e104)) }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = soulDraft,
                            onValueChange = { soulDraft = it.take(131_072); identityNotice = null },
                            label = { Text(stringResource(R.string.ui_soul_md_808c7e)) },
                            minLines = 7,
                            maxLines = 14,
                            enabled = identityLoaded && !identityLoading,
                            supportingText = { Text(stringResource(R.string.persona_characters, soulDraft.length)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = providerDraft,
                                onValueChange = { providerDraft = it.take(200); identityNotice = null },
                                label = { Text(stringResource(R.string.ui_provider_7ceee3)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = modelDraft,
                                onValueChange = { modelDraft = it.take(200); identityNotice = null },
                                label = { Text(stringResource(R.string.ui_model_68c2cc)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    identityLoading = true
                                    identityError = null
                                    scope.launch {
                                        try {
                                            onSaveSoul(name, soulDraft)
                                            originalSoul = soulDraft
                                            identityNotice = context.getString(R.string.ui_mgmt_soul_saved_6a1009)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Throwable) {
                                            identityError = error.message ?: context.getString(R.string.ui_mgmt_could_not_save_soul_aecac2)
                                        } finally {
                                            identityLoading = false
                                        }
                                    }
                                },
                                enabled = identityLoaded && !identityLoading && soulDraft != originalSoul,
                            ) { Text(stringResource(R.string.ui_save_soul_dea0b3)) }
                            Button(
                                onClick = {
                                    identityLoading = true
                                    identityError = null
                                    scope.launch {
                                        try {
                                            onSaveModel(name, providerDraft, modelDraft)
                                            originalProvider = providerDraft.trim()
                                            originalModel = modelDraft.trim()
                                            providerDraft = originalProvider
                                            modelDraft = originalModel
                                            identityNotice = context.getString(R.string.ui_mgmt_profile_model_saved_2ae386)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Throwable) {
                                            identityError = error.message ?: context.getString(R.string.ui_mgmt_could_not_save_profile_model_fb8569)
                                        } finally {
                                            identityLoading = false
                                        }
                                    }
                                },
                                enabled = identityLoaded && !identityLoading && providerDraft.isNotBlank() && modelDraft.isNotBlank() &&
                                    (providerDraft != originalProvider || modelDraft != originalModel),
                            ) { Text(stringResource(R.string.ui_save_model_390868)) }
                        }
                        identityNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        identityError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = ::requestCloseIdentity) { Text(stringResource(R.string.ui_close_bbfa77)) } },
        )
    }
    if (confirmDiscardIdentity) {
        AlertDialog(
            onDismissRequest = { confirmDiscardIdentity = false },
            title = { Text(stringResource(R.string.ui_discard_profile_changes_575ef6)) },
            text = { Text(stringResource(R.string.ui_unsaved_soul_provider_or_model_edits_will_be_lost_3c721e)) },
            confirmButton = { TextButton(onClick = ::closeIdentity) { Text(stringResource(R.string.ui_discard_36fff6)) } },
            dismissButton = { TextButton(onClick = { confirmDiscardIdentity = false }) { Text(stringResource(R.string.ui_keep_editing_d4d9e3)) } },
        )
    }
}

internal fun profileIdentityDirty(
    originalSoul: String,
    soul: String,
    originalProvider: String,
    provider: String,
    originalModel: String,
    model: String,
): Boolean = soul != originalSoul || provider != originalProvider || model != originalModel

@Composable
private fun ProfileRow(
    profile: ProfileInfo,
    isActive: Boolean,
    isCurrent: Boolean,
    onStartSession: () -> Unit,
    onRename: () -> Unit,
    onEditIdentity: () -> Unit,
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
                        listOfNotNull(profile.provider, profile.model).joinToString(" / ").ifBlank { stringResource(R.string.ui_mgmt_model_not_assigned_67b282) },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onStartSession) { Icon(Icons.Outlined.PlayArrow, stringResource(R.string.start_session_named, profile.name)) }
                IconButton(onClick = onEditIdentity) { Icon(Icons.Outlined.Description, stringResource(R.string.edit_identity_named, profile.name)) }
                if (!profile.isDefault) IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, stringResource(R.string.rename_named, profile.name)) }
                if (!protected) IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete_named, profile.name)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (profile.isDefault) Text(stringResource(R.string.ui_default_root_5062b2), style = MaterialTheme.typography.labelSmall)
                if (isCurrent) Text(stringResource(R.string.ui_server_process_85ac55), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.skill_count, profile.skillCount), style = MaterialTheme.typography.labelSmall)
                if (isActive) {
                    Text(stringResource(R.string.ui_sticky_default_a734d7), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    TextButton(onClick = onSetActive) {
                        Icon(Icons.Outlined.Star, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_make_default_7b2628))
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
    var name by remember { mutableStateOf("") }
    var cloneFrom by remember { mutableStateOf("") }
    var cloneAll by rememberSaveable { mutableStateOf(false) }
    var noSkills by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_create_profile_ba1c80)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(100) }, label = { Text(stringResource(R.string.ui_profile_name_775747)) }, singleLine = true)
                OutlinedTextField(
                    cloneFrom,
                    { cloneFrom = it.take(100) },
                    label = { Text(stringResource(R.string.ui_clone_source_optional_cf5105)) },
                    supportingText = { Text(stringResource(R.string.ui_leave_empty_for_a_fresh_profile_use_an_exact_existing_p_d9486a)) },
                    singleLine = true,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_clone_sessions_and_full_state_a38a73), Modifier.weight(1f))
                    Switch(
                        checked = cloneAll,
                        onCheckedChange = { cloneAll = it },
                        modifier = Modifier.localizedContentDescription(R.string.a11y_clone_sessions_and_full_state_a38a73),
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_start_without_bundled_skills_ce6cb3), Modifier.weight(1f))
                    Switch(
                        checked = noSkills,
                        onCheckedChange = { noSkills = it },
                        modifier = Modifier.localizedContentDescription(R.string.a11y_start_without_bundled_skills_ce6cb3),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, cloneFrom, cloneAll, noSkills) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.ui_create_6e157c)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
    )
}

@Composable
private fun ProfileRenameDialog(
    profile: ProfileInfo,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(profile.name) { mutableStateOf(profile.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_rename_profile_8cb84e)) },
        text = { OutlinedTextField(name, { name = it.take(100) }, label = { Text(stringResource(R.string.ui_new_name_9e627c)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank() && name != profile.name) { Text(stringResource(R.string.ui_rename_d3f4cb)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
    )
}

@Composable
internal fun ManagementHeader(
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
        onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.a11y_back_b52b36)) } }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        onRefresh?.let {
            IconButton(onClick = it, enabled = !loading) { Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh_named, title)) }
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
                Text(skill.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    skill.provenance ?: skill.category ?: stringResource(R.string.ui_mgmt_general_e2a7c9),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(skill.description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                skill.usage?.let { Text(stringResource(R.string.observed_actions, it), style = MaterialTheme.typography.labelSmall) }
            }
            Switch(
                checked = skill.enabled,
                onCheckedChange = { onToggle(skill.name, it) },
                modifier = Modifier.localizedContentDescription(R.string.enable_named, skill.name),
            )
            IconButton(onClick = { onUninstall(skill.name) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.uninstall_named, skill.name)) }
        }
    }
}

@Composable
private fun ToolsetRow(
    toolset: ToolsetInfo,
    loading: Boolean,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(toolset.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${toolset.platformLabel.uppercase()} / ${stringResource(if (toolset.configured) R.string.ui_mgmt_configured_1a5a97 else R.string.ui_mgmt_setup_may_be_required_a32770)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(toolset.description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (toolset.tools.isNotEmpty()) {
                    Text(
                        toolset.tools.take(8).joinToString(" · ") + if (toolset.tools.size > 8) " · +${toolset.tools.size - 8}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(
                checked = toolset.enabled,
                onCheckedChange = { onToggle(toolset.name, it) },
                enabled = !loading,
                modifier = Modifier.localizedContentDescription(R.string.enable_named, toolset.label),
            )
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
                Button(onClick = { onReview(skill.identifier) }) { Text(stringResource(R.string.ui_review_e29a79)) }
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
                        job.scheduleDisplay ?: job.schedule?.display ?: job.schedule?.expr ?: stringResource(R.string.ui_mgmt_schedule_unavailable_7c2822),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { onSetEnabled(job.id, !job.enabled) }) {
                    Icon(if (job.enabled) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, stringResource(if (job.enabled) R.string.ui_mgmt_pause_job_009bff else R.string.ui_mgmt_resume_job_43a255))
                }
                IconButton(onClick = { onTrigger(job.id) }) { Icon(Icons.Outlined.PlayArrow, stringResource(R.string.a11y_run_job_now_e80ea6)) }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.a11y_edit_job_4b27b5)) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.a11y_delete_job_ede40d)) }
            }
            job.prompt?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (job.enabled) R.string.enabled else R.string.paused), style = MaterialTheme.typography.labelSmall)
                job.nextRunAt?.let { Text(stringResource(R.string.next_run, it), style = MaterialTheme.typography.labelSmall) }
                job.deliver?.let { Text(stringResource(R.string.deliver_to, it), style = MaterialTheme.typography.labelSmall) }
            }
            job.lastError?.let { Text(stringResource(R.string.last_failure, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onHistory) {
                Icon(Icons.Outlined.History, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (expanded) R.string.hide_runs else R.string.recent_runs))
            }
            if (expanded) {
                when {
                    runs == null -> Text(stringResource(R.string.ui_loading_run_history_7aba72), style = MaterialTheme.typography.bodySmall)
                    runs.isEmpty() -> Text(stringResource(R.string.ui_no_executions_have_produced_sessions_for_this_job_33c7f3), style = MaterialTheme.typography.bodySmall)
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
                                    if (run.isActive) Text(stringResource(R.string.ui_active_c72633), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
    var name by remember(job?.id) { mutableStateOf(job?.name.orEmpty()) }
    var prompt by remember(job?.id) { mutableStateOf(job?.prompt.orEmpty()) }
    var schedule by remember(job?.id) {
        mutableStateOf(job?.schedule?.expr ?: job?.scheduleDisplay.orEmpty())
    }
    var deliver by remember(job?.id) { mutableStateOf(job?.deliver.orEmpty()) }
    val valid = prompt.isNotBlank() && schedule.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (job == null) R.string.create_cron_job else R.string.edit_cron_job)) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(name, { name = it.take(200) }, label = { Text(stringResource(R.string.ui_name_709a23)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(prompt, { prompt = it }, label = { Text(stringResource(R.string.ui_hermes_prompt_7dc987)) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 7)
                OutlinedTextField(
                    schedule,
                    { schedule = it },
                    label = { Text(stringResource(R.string.ui_exact_schedule_a2d1d3)) },
                    supportingText = { Text(stringResource(R.string.ui_cron_expression_or_schedule_form_accepted_by_this_herme_81b79d)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(deliver, { deliver = it }, label = { Text(stringResource(R.string.ui_delivery_destination_optional_d55fe3)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, prompt, schedule, deliver) }, enabled = valid) { Text(stringResource(R.string.ui_save_efc007)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
    )
}

@Composable
internal fun ManagementError(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun ManagementEmpty(message: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

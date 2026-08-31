package com.nousresearch.hermes.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.DiagnosticAction
import com.nousresearch.hermes.R
import com.nousresearch.hermes.data.DiagnosticRunState
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.GatewayConnectionState
import com.nousresearch.hermes.provenance.BuildProvenance
import com.nousresearch.hermes.provenance.BuildProvenanceSource
import com.nousresearch.hermes.security.DiagnosticRedactor
import com.nousresearch.hermes.security.DiagnosticReportInput
import com.nousresearch.hermes.security.DiagnosticReportSection
import com.nousresearch.hermes.security.buildDiagnosticReport
import com.nousresearch.hermes.ui.theme.HermesSkin
import com.nousresearch.hermes.ui.theme.colorScheme
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DiagnosticsScreen(
    state: HermesState,
    connection: GatewayConnectionState,
    onRun: (DiagnosticAction) -> Unit,
    onRefreshHost: (Boolean) -> Unit,
    backup: HostBackupUiState,
    onPrepareBackup: () -> Unit,
    onSaveBackup: (android.net.Uri) -> Unit,
    onCancelBackup: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportInProgress by rememberSaveable { mutableStateOf(false) }
    var exportNotice by remember { mutableStateOf<String?>(null) }
    var confirmBackup by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.backend?.id) { if (state.backend != null) onRefreshHost(false) }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(onSaveBackup)
    }
    val createReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) {
            exportInProgress = false
            exportNotice = "Diagnostic export cancelled."
        } else {
            scope.launch {
                val report = state.buildDiagnosticReport(connection)
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                            writer.write(report)
                        } ?: error("Android could not open the selected document")
                    }
                }
                exportInProgress = false
                exportNotice = result.fold(
                    onSuccess = { "Redacted diagnostic report saved." },
                    onFailure = { "Export failed: ${DiagnosticRedactor.redact(it.message ?: "write failed").take(1_000)}" },
                )
            }
        }
    }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.a11y_back_to_sessions_e7bfae)) } }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.ui_diagnostics_7563ce), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.ui_compatibility_doctor_and_security_audit_26bfa1), style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DiagnosticInfoCard(state, connection, BuildProvenanceSource.current)
            }
            item {
                Text(stringResource(R.string.ui_these_commands_run_on_the_selected_hermes_server_host_l_f51794),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                HostMaintenanceCard(
                    state = state,
                    backup = backup,
                    onRefresh = onRefreshHost,
                    onPrepareBackup = { confirmBackup = true },
                    onSaveBackup = { createBackup.launch(backup.suggestedName) },
                    onCancelBackup = onCancelBackup,
                )
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ui_redacted_support_report_02e644), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.ui_save_an_allowlisted_text_report_through_android_s_docum_8cc5df),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = {
                                exportNotice = null
                                exportInProgress = true
                                createReport.launch("hermes-diagnostics-${System.currentTimeMillis()}.txt")
                            },
                            enabled = !exportInProgress,
                        ) {
                            Text(stringResource(if (exportInProgress) R.string.exporting else R.string.save_report))
                        }
                        exportNotice?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                DiagnosticActionCard(
                    title = "Hermes doctor",
                    description = "Checks configuration, providers, tools and runtime health using the server's official doctor command.",
                    icon = { Icon(Icons.Outlined.MedicalServices, null) },
                    run = state.diagnostics[DiagnosticAction.DOCTOR],
                    onRun = { onRun(DiagnosticAction.DOCTOR) },
                )
            }
            item {
                DiagnosticActionCard(
                    title = "Security audit",
                    description = "Runs the official Hermes security audit on the server. Findings are informational until reviewed in context.",
                    icon = { Icon(Icons.Outlined.HealthAndSafety, null) },
                    run = state.diagnostics[DiagnosticAction.SECURITY_AUDIT],
                    onRun = { onRun(DiagnosticAction.SECURITY_AUDIT) },
                )
            }
        }
    }
    if (confirmBackup) {
        AlertDialog(
            onDismissRequest = { confirmBackup = false },
            title = { Text(stringResource(R.string.ui_create_hermes_host_backup_0a4e1f)) },
            text = {
                Text(
                    "This runs backup on the whole authenticated Hermes installation, not only profile ${state.activeProfile}. " +
                        "After Hermes confirms the exact process succeeded, Android will ask where to save the ZIP. " +
                        "The archive is never stored in this app or Android backup.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmBackup = false; onPrepareBackup() }) { Text(stringResource(R.string.ui_create_backup_60e77f)) }
            },
            dismissButton = { TextButton(onClick = { confirmBackup = false }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
        )
    }
}

@Composable
private fun HostMaintenanceCard(
    state: HermesState,
    backup: HostBackupUiState,
    onRefresh: (Boolean) -> Unit,
    onPrepareBackup: () -> Unit,
    onSaveBackup: () -> Unit,
    onCancelBackup: () -> Unit,
) {
    val update = state.hostUpdate
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ui_hermes_host_f9ccdc), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.hostMaintenanceLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Text(stringResource(R.string.ui_read_only_status_for_the_authenticated_hermes_host_andr_4c1903),
                style = MaterialTheme.typography.bodySmall,
            )
            if (update != null) {
                DiagnosticValue("Installed version", update.currentVersion)
                DiagnosticValue("Install method", update.installMethod)
                DiagnosticValue(
                    "Update state",
                    when {
                        update.behind == 0 -> "Current"
                        update.behind != null && update.behind > 0 -> "${update.behind} commits behind"
                        update.updateAvailable -> "Update available; count unavailable"
                        else -> "Unavailable"
                    },
                )
                update.message?.takeIf(String::isNotBlank)?.let { DiagnosticValue("Host guidance", it) }
                update.commits.take(20).takeIf { it.isNotEmpty() }?.let { commits ->
                    SelectionContainer {
                        Text(
                            commits.joinToString("\n") { "${it.sha.take(8)}  ${it.summary.take(300)}" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            state.hostMaintenanceError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (state.hostLogs.isNotEmpty()) {
                Text(stringResource(R.string.ui_recent_redacted_agent_log_4305fe), style = MaterialTheme.typography.labelMedium)
                SelectionContainer {
                    Text(
                        state.hostLogs.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Button(onClick = { onRefresh(true) }, enabled = !state.hostMaintenanceLoading) { Text(stringResource(R.string.ui_check_now_2ce5dd)) }
            HorizontalDivider()
            Text(stringResource(R.string.ui_host_backup_a4daac), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.ui_hermes_creates_the_archive_on_its_host_android_download_fbc70d),
                style = MaterialTheme.typography.bodySmall,
            )
            backup.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            backup.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (backup.preparing || backup.saving) {
                backup.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                    ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                TextButton(onClick = onCancelBackup) { Text(stringResource(if (backup.preparing) R.string.stop_waiting else R.string.cancel_export)) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPrepareBackup) { Text(stringResource(if (backup.archive == null) R.string.ui_create_backup_60e77f else R.string.create_another)) }
                    if (backup.archive != null) Button(onClick = onSaveBackup) { Text(stringResource(R.string.ui_save_zip_f9a419)) }
                }
            }
        }
    }
}

@Composable
internal fun AppearancePicker(
    selected: HermesSkin,
    onSelected: (HermesSkin) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(
                stringResource(R.string.appearance_description),
                style = MaterialTheme.typography.bodySmall,
            )
            HermesSkin.entries.chunked(2).forEach { rowSkins ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowSkins.forEach { skin ->
                        val preview = skin.colorScheme(dark)
                        val active = skin == selected
                        Surface(
                            color = preview.surface,
                            contentColor = preview.onSurface,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(
                                if (active) 2.dp else 1.dp,
                                if (active) preview.primary else preview.outline,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 104.dp)
                                .selectable(
                                    selected = active,
                                    role = Role.RadioButton,
                                    onClick = { onSelected(skin) },
                                ),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().height(18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    listOf(preview.background, preview.primary, preview.tertiary).forEach { color ->
                                        Box(
                                            Modifier
                                                .weight(1f)
                                                .height(18.dp),
                                        ) {
                                            Surface(
                                                modifier = Modifier.fillMaxSize(),
                                                color = color,
                                                shape = MaterialTheme.shapes.extraSmall,
                                                border = BorderStroke(1.dp, preview.outline),
                                            ) {}
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(skin.labelResource), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                    if (active) Icon(Icons.Outlined.Check, stringResource(R.string.selected), modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    stringResource(skin.descriptionResource),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = preview.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (rowSkins.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private val HermesSkin.labelResource: Int
    get() = when (this) {
        HermesSkin.NOUS -> R.string.skin_nous
        HermesSkin.MIDNIGHT -> R.string.skin_midnight
        HermesSkin.EMBER -> R.string.skin_ember
        HermesSkin.MONO -> R.string.skin_mono
        HermesSkin.CYBERPUNK -> R.string.skin_cyberpunk
        HermesSkin.SLATE -> R.string.skin_slate
    }

private val HermesSkin.descriptionResource: Int
    get() = when (this) {
        HermesSkin.NOUS -> R.string.skin_nous_description
        HermesSkin.MIDNIGHT -> R.string.skin_midnight_description
        HermesSkin.EMBER -> R.string.skin_ember_description
        HermesSkin.MONO -> R.string.skin_mono_description
        HermesSkin.CYBERPUNK -> R.string.skin_cyberpunk_description
        HermesSkin.SLATE -> R.string.skin_slate_description
    }

@Composable
private fun DiagnosticInfoCard(
    state: HermesState,
    connection: GatewayConnectionState,
    provenance: BuildProvenance,
) {
    val status = state.status
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DiagnosticValue("Android app", provenance.androidVersion)
            DiagnosticValue("Build channel", provenance.channel)
            DiagnosticValue("Android commit", provenance.androidCommit)
            DiagnosticValue("Backend", state.backend?.label.orEmpty())
            DiagnosticValue("Endpoint", state.backend?.baseUrl.orEmpty())
            DiagnosticValue("Connection", DiagnosticRedactor.redact(connection.displayName()))
            DiagnosticValue("Hermes version", status?.hermesVersion ?: status?.version ?: "Not reported")
            DiagnosticValue("Server state", status?.status ?: "Unavailable")
            DiagnosticValue("Authentication", if (status?.authRequired == true) "Required" else "Not reported as required")
            DiagnosticValue("Desktop contract", state.runtimeInfo.desktopContract?.let { "v$it" } ?: "Not reported")
            DiagnosticValue(
                "Capabilities",
                status?.capabilities?.toString()?.let(DiagnosticRedactor::redact)?.take(1_000) ?: "Not reported",
            )
            DiagnosticValue("Audited upstream", provenance.auditedHermesCommit)
            DiagnosticValue("Hermes Agent", "${provenance.hermesAgentVersion} (${provenance.hermesAgentVersionRange})")
            DiagnosticValue("Hermes Desktop", "${provenance.hermesDesktopVersion} (${provenance.hermesDesktopVersionRange})")
            DiagnosticValue("Toolchain digest", provenance.toolchainDigest)
            DiagnosticValue("Build identity", provenance.buildIdentity)
            DiagnosticValue("Package", provenance.packageName)
            DiagnosticValue("Signing fingerprint", provenance.signingFingerprint)
            DiagnosticValue("Build author", provenance.author)
        }
    }
}

@Composable
private fun DiagnosticValue(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        SelectionContainer {
            Text(value.ifBlank { "Not reported" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun DiagnosticActionCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    run: DiagnosticRunState?,
    onRun: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (run?.running == true) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Text(description, style = MaterialTheme.typography.bodySmall)
            if (run != null) {
                val result = run.displayStatus()
                Text(result, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                run.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (run.lines.isNotEmpty()) {
                    SelectionContainer {
                        Text(
                            run.lines.joinToString("\n"),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            Button(onClick = onRun, enabled = run?.running != true) {
                Text(stringResource(if (run == null) R.string.run else R.string.run_again))
            }
        }
    }
}

private fun HermesState.buildDiagnosticReport(connection: GatewayConnectionState): String {
    val status = status
    val provenance = BuildProvenanceSource.current
    return buildDiagnosticReport(
        DiagnosticReportInput(
            generatedAt = Instant.now().toString(),
            appVersion = provenance.androidVersion,
            auditedCommit = provenance.auditedHermesCommit,
            backendLabel = backend?.label,
            endpoint = backend?.baseUrl,
            connection = connection.displayName(),
            hermesVersion = status?.hermesVersion ?: status?.version,
            serverState = status?.status,
            authRequired = status?.authRequired,
            desktopContract = runtimeInfo.desktopContract,
            capabilities = status?.capabilities?.toString(),
            provenance = provenance,
            sections = DiagnosticAction.entries.mapNotNull { action ->
                diagnostics[action]?.let { run ->
                    DiagnosticReportSection(
                        title = when (action) {
                            DiagnosticAction.DOCTOR -> "Hermes doctor"
                            DiagnosticAction.SECURITY_AUDIT -> "Security audit"
                        },
                        status = run.displayStatus(),
                        lines = run.error?.let { run.lines + it } ?: run.lines,
                    )
                }
            } + hostLogs.takeIf { it.isNotEmpty() }?.let {
                listOf(
                    DiagnosticReportSection(
                        title = "Recent redacted Hermes host log",
                        status = "${it.size.coerceAtMost(200)} lines",
                        lines = it.takeLast(200),
                    ),
                )
            }.orEmpty(),
        ),
    )
}

private fun DiagnosticRunState.displayStatus(): String = when {
    running -> "RUNNING${pid?.let { " / PID $it" }.orEmpty()}"
    timedOut -> "POLLING TIMED OUT"
    exitCode == 0 -> "COMPLETED / EXIT 0"
    exitCode != null -> "FAILED / EXIT $exitCode"
    error != null -> "FAILED"
    else -> "STATUS UNKNOWN"
}

private fun GatewayConnectionState.displayName(): String = when (this) {
    GatewayConnectionState.Idle -> "Idle"
    is GatewayConnectionState.Connecting -> "Connecting / attempt $attempt"
    GatewayConnectionState.Open -> "Live / JSON-RPC"
    is GatewayConnectionState.Reconnecting -> "Reconnecting / attempt $attempt"
    is GatewayConnectionState.Closed -> "Offline${reason?.let { " / $it" }.orEmpty()}"
    is GatewayConnectionState.Failed -> "Failed / $message"
}

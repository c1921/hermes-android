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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.DiagnosticAction
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
    secureScreen: Boolean,
    onSecureScreenChange: (Boolean) -> Unit,
    skin: HermesSkin,
    onSkinChange: (HermesSkin) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportInProgress by rememberSaveable { mutableStateOf(false) }
    var exportNotice by rememberSaveable { mutableStateOf<String?>(null) }
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
            onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to sessions") } }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text("DIAGNOSTICS", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text("Compatibility, doctor and security audit", style = MaterialTheme.typography.bodySmall)
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
                AppearancePicker(skin, onSkinChange)
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("SECURE SCREEN", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Block screenshots, screen recording and the recent-app thumbnail for Hermes content on this device.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = secureScreen,
                            onCheckedChange = onSecureScreenChange,
                            modifier = Modifier.semantics { contentDescription = "Secure screen" },
                        )
                    }
                }
            }
            item {
                Text(
                    "These commands run on the selected Hermes server. Output is bounded and redacted again on Android before display; it is not uploaded by the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("REDACTED SUPPORT REPORT", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Save an allowlisted text report through Android's document picker. It excludes credentials, conversations, provider settings and raw configuration.",
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
                            Text(if (exportInProgress) "Exporting…" else "Save report")
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
}

@Composable
private fun AppearancePicker(
    selected: HermesSkin,
    onSelected: (HermesSkin) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("APPEARANCE", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(
                "Official Hermes Desktop presets. Android follows the system light or dark setting.",
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
                                    Text(skin.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                    if (active) Icon(Icons.Outlined.Check, "Selected", modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    skin.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = preview.onSurfaceVariant,
                                    maxLines = 2,
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
                Text(if (run == null) "Run" else "Run again")
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
            },
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

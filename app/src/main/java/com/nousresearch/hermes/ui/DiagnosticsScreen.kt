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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.BuildConfig
import com.nousresearch.hermes.data.DiagnosticAction
import com.nousresearch.hermes.data.DiagnosticRunState
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.GatewayConnectionState
import com.nousresearch.hermes.security.DiagnosticRedactor

@Composable
internal fun DiagnosticsScreen(
    state: HermesState,
    connection: GatewayConnectionState,
    onRun: (DiagnosticAction) -> Unit,
    secureScreen: Boolean,
    onSecureScreenChange: (Boolean) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let { IconButton(onClick = it) { Icon(Icons.Outlined.ArrowBack, "Back to sessions") } }
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
                DiagnosticInfoCard(state, connection)
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
private fun DiagnosticInfoCard(state: HermesState, connection: GatewayConnectionState) {
    val status = state.status
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DiagnosticValue("Android app", BuildConfig.VERSION_NAME)
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
            DiagnosticValue("Audited upstream", BuildConfig.AUDITED_HERMES_COMMIT)
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
                val result = when {
                    run.running -> "RUNNING${run.pid?.let { " / PID $it" }.orEmpty()}"
                    run.timedOut -> "POLLING TIMED OUT"
                    run.exitCode == 0 -> "COMPLETED / EXIT 0"
                    run.exitCode != null -> "FAILED / EXIT ${run.exitCode}"
                    run.error != null -> "FAILED"
                    else -> "STATUS UNKNOWN"
                }
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

private fun GatewayConnectionState.displayName(): String = when (this) {
    GatewayConnectionState.Idle -> "Idle"
    is GatewayConnectionState.Connecting -> "Connecting / attempt $attempt"
    GatewayConnectionState.Open -> "Live / JSON-RPC"
    is GatewayConnectionState.Reconnecting -> "Reconnecting / attempt $attempt"
    is GatewayConnectionState.Closed -> "Offline${reason?.let { " / $it" }.orEmpty()}"
    is GatewayConnectionState.Failed -> "Failed / $message"
}

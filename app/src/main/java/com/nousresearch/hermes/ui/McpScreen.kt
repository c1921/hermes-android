package com.nousresearch.hermes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.McpCatalogEntry
import com.nousresearch.hermes.protocol.McpServerSummary

private enum class McpView { CONFIGURED, CATALOG }

@Composable
internal fun McpScreen(
    state: HermesState,
    onRefresh: () -> Unit,
    onTest: (String) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onInstall: (String, Map<String, String>) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var view by rememberSaveable { mutableStateOf(McpView.CONFIGURED) }
    var query by rememberSaveable { mutableStateOf("") }
    var pendingToggleName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingToggleEnabled by rememberSaveable { mutableStateOf(false) }
    var pendingRemoveName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingInstall by remember { mutableStateOf<McpCatalogEntry?>(null) }
    var installEnv by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) { onRefresh() }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let {
                IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            }
            Column(Modifier.weight(1f)) {
                Text("MCP SERVERS", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text("Hermes runtime / ${state.activeProfile}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRefresh, enabled = !state.mcpLoading) {
                if (state.mcpLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Refresh, "Refresh MCP servers")
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            Text(
                "Configured servers and catalog metadata come from this Hermes profile. Connection tests run on Hermes; Android never executes MCP processes or receives their credentials.",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            McpView.entries.forEach { candidate ->
                val label = if (candidate == McpView.CONFIGURED) "Configured" else "Nous catalog"
                if (view == candidate) {
                    Button(onClick = { view = candidate }, modifier = Modifier.weight(1f)) { Text(label) }
                } else {
                    OutlinedButton(onClick = { view = candidate }, modifier = Modifier.weight(1f)) { Text(label) }
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(120) },
            placeholder = { Text(if (view == McpView.CONFIGURED) "Search configured servers" else "Search catalog") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        state.mcpNotice?.let { McpNotice(it, error = false) }
        state.mcpError?.let { McpNotice(it, error = true) }

        when (view) {
            McpView.CONFIGURED -> ConfiguredMcpList(
                state,
                query,
                onTest,
                onSetEnabled = { name, enabled ->
                    pendingToggleName = name
                    pendingToggleEnabled = enabled
                },
                onRemove = { pendingRemoveName = it },
            )
            McpView.CATALOG -> McpCatalogList(
                state,
                query,
                onInstall = { entry ->
                    installEnv = emptyMap()
                    pendingInstall = entry
                },
            )
        }
    }

    pendingToggleName?.let { name ->
        val runtimeProfile = state.activeStoredSession?.profile
            ?: state.activeProfile.takeIf { state.runtimeSessionId != null }
        val reloadsNow = state.currentProfile == state.activeProfile || runtimeProfile == state.activeProfile
        AlertDialog(
            onDismissRequest = { pendingToggleName = null },
            title = { Text("${if (pendingToggleEnabled) "ENABLE" else "DISABLE"} MCP SERVER?") },
            text = {
                Text(
                    if (reloadsNow) {
                        "Hermes will ${if (pendingToggleEnabled) "enable" else "disable"} $name and reload the live MCP runtime. Reloading invalidates the prompt cache, so the next message may resend full input tokens."
                    } else {
                        "Hermes will ${if (pendingToggleEnabled) "enable" else "disable"} $name for ${state.activeProfile}. This profile is not the current runtime, so the setting takes effect when it next starts."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingToggleName = null
                        onSetEnabled(name, pendingToggleEnabled)
                    },
                ) {
                    Text(
                        if (reloadsNow) {
                            if (pendingToggleEnabled) "Enable and reload" else "Disable and reload"
                        } else if (pendingToggleEnabled) {
                            "Enable"
                        } else {
                            "Disable"
                        },
                    )
                }
            },
            dismissButton = { TextButton(onClick = { pendingToggleName = null }) { Text("Cancel") } },
        )
    }
    pendingRemoveName?.let { name ->
        val runtimeProfile = state.activeStoredSession?.profile
            ?: state.activeProfile.takeIf { state.runtimeSessionId != null }
        val reloadsNow = state.currentProfile == state.activeProfile || runtimeProfile == state.activeProfile
        AlertDialog(
            onDismissRequest = { pendingRemoveName = null },
            title = { Text("REMOVE MCP SERVER?") },
            text = {
                Text(
                    if (reloadsNow) {
                        "Hermes will remove $name from ${state.activeProfile} and reload the live MCP runtime. Stored server credentials and OAuth state may also become unusable."
                    } else {
                        "Hermes will remove $name from ${state.activeProfile}. The change applies when that profile next starts."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoveName = null
                        onRemove(name)
                    },
                ) { Text(if (reloadsNow) "Remove and reload" else "Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoveName = null }) { Text("Cancel") } },
        )
    }
    pendingInstall?.let { entry ->
        val requiredComplete = entry.requiredEnv.filter { it.required }.all { installEnv[it.name]?.isNotBlank() == true }
        AlertDialog(
            onDismissRequest = {
                pendingInstall = null
                installEnv = emptyMap()
            },
            title = { Text("INSTALL ${entry.name.uppercase()}?") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Source: ${entry.source.take(MAX_MCP_DISPLAY_CHARACTERS)}", style = MaterialTheme.typography.bodySmall)
                    Text(entry.targetSummary(), style = MaterialTheme.typography.bodySmall)
                    if (entry.bootstrap.isNotEmpty()) {
                        Text(
                            "Server bootstrap: ${entry.bootstrap.joinToString(" ").take(MAX_MCP_DISPLAY_CHARACTERS)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "Hermes will install and enable this server for ${state.activeProfile}. Review the source and command before continuing. Credential values are sent once to Hermes and are not saved by Android.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    entry.requiredEnv.forEach { requirement ->
                        OutlinedTextField(
                            value = installEnv[requirement.name].orEmpty(),
                            onValueChange = { value ->
                                installEnv = installEnv + (requirement.name to value.take(MAX_MCP_ENV_VALUE_CHARACTERS))
                            },
                            label = {
                                Text(
                                    requirement.prompt.ifBlank { requirement.name } +
                                        if (requirement.required) " (required)" else " (optional)",
                                )
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val values = installEnv
                        pendingInstall = null
                        installEnv = emptyMap()
                        onInstall(entry.name, values)
                    },
                    enabled = requiredComplete && !state.mcpLoading,
                ) { Text("Install and enable") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingInstall = null
                        installEnv = emptyMap()
                    },
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ConfiguredMcpList(
    state: HermesState,
    query: String,
    onTest: (String) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
) {
    val visible = state.mcpServers.filter { server ->
        query.isBlank() || listOf(server.name, server.transport, server.command, server.url, server.auth)
            .filterNotNull().any { it.contains(query.trim(), ignoreCase = true) }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(visible, key = McpServerSummary::name) { server ->
            val probe = state.mcpTests[server.name]
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(server.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                server.connectionSummary(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOf(server.transport, server.auth?.let { "auth: $it" }).filterNotNull().joinToString(" / "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Switch(
                            checked = server.enabled,
                            onCheckedChange = { onSetEnabled(server.name, it) },
                            enabled = !state.mcpLoading,
                        )
                    }
                    probe?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (it.ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                if (it.ok) "Connection test passed" else "Connection test failed",
                                tint = if (it.ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (it.ok) "${it.tools.size} tools / ${it.prompts} prompts / ${it.resources} resources"
                                else it.error ?: "Hermes could not connect to this MCP server.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (it.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { onTest(server.name) },
                        enabled = server.enabled && !state.mcpLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Test on Hermes") }
                    TextButton(
                        onClick = { onRemove(server.name) },
                        enabled = !state.mcpLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove from Hermes")
                    }
                }
            }
        }
        if (visible.isEmpty() && !state.mcpLoading) {
            item {
                Text(
                    if (state.mcpServers.isEmpty()) "No MCP servers are configured for this Hermes profile."
                    else "No configured MCP servers match this search.",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun McpCatalogList(
    state: HermesState,
    query: String,
    onInstall: (McpCatalogEntry) -> Unit,
) {
    val visible = state.mcpCatalog.filter { entry ->
        query.isBlank() || listOf(entry.name, entry.description, entry.source, entry.transport, entry.authType)
            .any { it.contains(query.trim(), ignoreCase = true) }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(visible, key = McpCatalogEntry::name) { entry ->
            McpCatalogCard(entry, state.mcpLoading, onInstall)
        }
        if (visible.isEmpty() && !state.mcpLoading) {
            item {
                Text(
                    if (state.mcpCatalog.isEmpty()) "Hermes returned no approved MCP catalog entries."
                    else "No catalog entries match this search.",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun McpCatalogCard(
    entry: McpCatalogEntry,
    loading: Boolean,
    onInstall: (McpCatalogEntry) -> Unit,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        entry.description.take(MAX_MCP_DISPLAY_CHARACTERS),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    when {
                        entry.enabled -> "ENABLED"
                        entry.installed -> "INSTALLED"
                        else -> "AVAILABLE"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text("${entry.transport} / auth: ${entry.authType}", style = MaterialTheme.typography.labelMedium)
            Text(entry.targetSummary(), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (entry.requiredEnv.isNotEmpty()) {
                Text(
                    "Required setup: ${entry.requiredEnv.take(20).joinToString { it.name.take(120) + if (it.required) " (required)" else "" }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (entry.needsInstall) {
                Text("Requires a server-side clone/bootstrap step. Android waits for Hermes to finish before reloading MCP.", style = MaterialTheme.typography.bodySmall)
            }
            if (entry.postInstall.isNotBlank()) {
                Text(entry.postInstall.take(MAX_MCP_DISPLAY_CHARACTERS), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Source: ${entry.source.take(MAX_MCP_DISPLAY_CHARACTERS)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                entry.installed -> Unit
                entry.authType == "oauth" -> Text(
                    "Install and authentication require Hermes Desktop or CLI because the current flow opens a browser on the server host.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Button(
                    onClick = { onInstall(entry) },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Review and install") }
            }
        }
    }
}

@Composable
private fun McpNotice(message: String, error: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private fun McpServerSummary.connectionSummary(): String = when (transport) {
    "http" -> url?.safeEndpoint() ?: "Hermes did not report an HTTP URL"
    "stdio" -> command?.let { "$it / ${args.size} argument${if (args.size == 1) "" else "s"}" }
        ?: "Hermes did not report a command"
    else -> url?.safeEndpoint() ?: command ?: "Unknown transport"
}

private fun McpCatalogEntry.targetSummary(): String = when (transport) {
    "http" -> url?.safeEndpoint() ?: "No URL reported"
    "stdio" -> listOfNotNull(command, args.takeIf { it.isNotEmpty() }?.joinToString(" ")).joinToString(" ")
    else -> url?.safeEndpoint() ?: command ?: "Unknown transport"
}.take(MAX_MCP_DISPLAY_CHARACTERS)

private fun String.safeEndpoint(): String = substringBefore('?').substringBefore('#')
    .replace(Regex("(?<=://)[^/@]+@"), "redacted@")
    .take(MAX_MCP_DISPLAY_CHARACTERS)

private const val MAX_MCP_DISPLAY_CHARACTERS = 1_000
private const val MAX_MCP_ENV_VALUE_CHARACTERS = 32_768

package com.nousresearch.hermes.ui

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.data.ProviderOAuthSession
import com.nousresearch.hermes.protocol.EnvVarInfo
import com.nousresearch.hermes.protocol.ModelProvider
import com.nousresearch.hermes.protocol.OAuthProvider
import kotlinx.coroutines.delay

@Composable
internal fun ProvidersScreen(
    state: HermesState,
    onRefresh: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onStartOAuth: (String) -> Unit = {},
    onSubmitOAuth: (String) -> Unit = {},
    onCancelOAuth: () -> Unit = {},
    onDisconnectOAuth: (String) -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onCopy: (String, String) -> Unit = { _, _ -> },
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var editingKey by remember { mutableStateOf<String?>(null) }
    var deletingKey by remember { mutableStateOf<String?>(null) }
    var externalProvider by remember { mutableStateOf<OAuthProvider?>(null) }
    var disconnectingProvider by remember { mutableStateOf<OAuthProvider?>(null) }
    var accountsSelected by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (state.providerOptions == null) onRefresh()
    }
    LaunchedEffect(state.providerOAuthSession?.sessionId) {
        state.providerOAuthSession?.browserUrl?.takeIf(String::isNotBlank)?.let(onOpenUrl)
    }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to sessions") } }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text("PROVIDERS", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text("Dynamic Hermes model accounts / ${state.activeProfile}", style = MaterialTheme.typography.bodySmall)
            }
            if (state.providersLoading) CircularProgressIndicator(Modifier.padding(12.dp), strokeWidth = 2.dp)
            else IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Refresh providers") }
        }
        HorizontalDivider()
        if (state.providerAccountsSupported) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (accountsSelected) {
                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("ACCOUNTS") }
                    OutlinedButton(onClick = { accountsSelected = false }, modifier = Modifier.weight(1f)) { Text("API KEYS") }
                } else {
                    OutlinedButton(onClick = { accountsSelected = true }, modifier = Modifier.weight(1f)) { Text("ACCOUNTS") }
                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("API KEYS") }
                }
            }
        }
        state.providerNotice?.let {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.providerAccountsSupported && accountsSelected) {
                items(state.oauthProviders, key = OAuthProvider::id) { provider ->
                    OAuthProviderCard(
                        provider = provider,
                        onConnect = {
                            if (provider.flow == "external") externalProvider = provider
                            else onStartOAuth(provider.id)
                        },
                        onDisconnect = { disconnectingProvider = provider },
                        enabled = !state.providersLoading,
                    )
                }
                if (state.oauthProviders.isEmpty() && !state.providersLoading) {
                    item {
                        Text(
                            "Hermes returned no provider accounts for this profile.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            } else {
                val providers = state.providerOptions?.providers.orEmpty()
                items(providers, key = ModelProvider::slug) { provider ->
                    val settings = state.providerEnv.entries.filter { it.value.provider == provider.slug }
                    ProviderCard(
                        provider = provider,
                        settings = settings,
                        onEdit = { editingKey = it },
                        onDelete = { deletingKey = it },
                    )
                }
                if (providers.isEmpty() && !state.providersLoading) {
                    item {
                        Text(
                            "Hermes returned no providers for this profile. Configure a provider on the server or refresh its catalogue.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
        }
    }

    editingKey?.let { key ->
        state.providerEnv[key]?.let { info ->
            ProviderSettingDialog(
                key = key,
                info = info,
                onDismiss = { editingKey = null },
                onSave = { value, apiKey ->
                    editingKey = null
                    onSave(key, value, apiKey)
                },
            )
        }
    }
    deletingKey?.let { key ->
        AlertDialog(
            onDismissRequest = { deletingKey = null },
            title = { Text("REMOVE PROVIDER SETTING") },
            text = { Text("Remove $key from the selected Hermes profile? Existing sessions may stop working.") },
            confirmButton = {
                TextButton(onClick = { deletingKey = null; onDelete(key) }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deletingKey = null }) { Text("Cancel") } },
        )
    }
    state.providerOAuthSession?.let { session ->
        ProviderOAuthSessionDialog(
            session = session,
            onSubmit = onSubmitOAuth,
            onCancel = onCancelOAuth,
            onOpenUrl = onOpenUrl,
            onCopy = onCopy,
            busy = state.providersLoading,
        )
    }
    externalProvider?.let { provider ->
        ExternalProviderDialog(
            provider = provider,
            onDismiss = { externalProvider = null },
            onRefresh = {
                externalProvider = null
                onRefresh()
            },
            onOpenUrl = onOpenUrl,
            onCopy = onCopy,
        )
    }
    disconnectingProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { disconnectingProvider = null },
            title = { Text("Disconnect ${provider.name}?") },
            text = { Text("Hermes will remove this provider account from ${state.activeProfile}.") },
            confirmButton = {
                TextButton(
                    modifier = Modifier.semantics { contentDescription = "Confirm disconnect" },
                    onClick = {
                        disconnectingProvider = null
                        onDisconnectOAuth(provider.id)
                    },
                ) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { disconnectingProvider = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun OAuthProviderCard(
    provider: OAuthProvider,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    enabled: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(provider.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (provider.status.loggedIn) "Connected" else "Not connected / ${provider.flow.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (provider.status.loggedIn) {
                    Icon(Icons.Outlined.CheckCircle, "Connected", tint = MaterialTheme.colorScheme.primary)
                    if (provider.disconnectable) {
                        OutlinedButton(onClick = onDisconnect, enabled = enabled) { Text("Disconnect") }
                    }
                } else if (provider.flow !in setOf("pkce", "device_code", "external")) {
                    Text("Unsupported", style = MaterialTheme.typography.labelMedium)
                } else {
                    OutlinedButton(onClick = onConnect, enabled = enabled) { Text("Connect") }
                }
            }
            provider.status.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (provider.status.loggedIn && !provider.disconnectable) {
                provider.disconnectHint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun ProviderOAuthSessionDialog(
    session: ProviderOAuthSession,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopy: (String, String) -> Unit,
    busy: Boolean,
) {
    var authorizationCode by remember(session.sessionId) { mutableStateOf("") }
    var remainingSeconds by remember(session.sessionId) {
        mutableStateOf(((session.expiresAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L) / 1_000L))
    }
    LaunchedEffect(session.sessionId, session.expiresAtEpochMillis) {
        while (remainingSeconds > 0L) {
            delay(1_000L)
            remainingSeconds = (session.expiresAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L) / 1_000L
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("CONNECT ${session.providerName.uppercase()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (session.flow == "device_code") {
                        "Open the provider page, enter this code, then return here. Hermes will check the result automatically."
                    } else {
                        "Complete sign-in in your browser, then paste the returned authorization code."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Expires in ${remainingSeconds / 60}:${(remainingSeconds % 60).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                session.userCode?.let { code ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(code, Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(onClick = { onCopy("Authorization code", code) }) { Text("Copy code") }
                }
                if (session.browserUrl.isNotBlank()) {
                    OutlinedButton(onClick = { onOpenUrl(session.browserUrl) }) { Text("Open browser") }
                }
                if (session.flow == "pkce") {
                    OutlinedTextField(
                        value = authorizationCode,
                        onValueChange = { authorizationCode = it.take(8192) },
                        label = { Text("Authorization code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text("Waiting for Hermes", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (session.flow == "pkce") {
                TextButton(
                    enabled = authorizationCode.isNotBlank() && !busy,
                    onClick = { onSubmit(authorizationCode.trim()) },
                ) { Text("Complete sign-in") }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun ExternalProviderDialog(
    provider: OAuthProvider,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopy: (String, String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CONNECT ${provider.name.uppercase()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Run this command on the machine hosting Hermes, then recheck the account status here.")
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(provider.cliCommand, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = { onCopy("Hermes login command", provider.cliCommand) }) { Text("Copy command") }
                if (provider.docsUrl.isNotBlank()) {
                    OutlinedButton(onClick = { onOpenUrl(provider.docsUrl) }) { Text("Learn more") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onRefresh) { Text("Recheck") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ProviderCard(
    provider: ModelProvider,
    settings: List<Map.Entry<String, EnvVarInfo>>,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${provider.models.size} shown / ${provider.totalModels} models · ${provider.authType ?: "server managed"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (provider.authenticated) Icon(Icons.Outlined.CheckCircle, "Configured", tint = MaterialTheme.colorScheme.primary)
            }
            provider.warning?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (provider.models.isNotEmpty()) {
                Text(provider.models.take(5).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            if (settings.isEmpty()) {
                Text(
                    if (provider.authenticated) "Credentials are managed by Hermes or an external OAuth/SDK flow."
                    else "This provider requires ${provider.authType ?: "server-side setup"}; Hermes did not advertise an editable setting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            settings.forEach { (key, info) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(info.providerLabel.ifBlank { key }, style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (info.isSet) "$key · ${info.redactedValue ?: "set"}" else "$key · not set",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { onEdit(key) }) { Icon(Icons.Outlined.Edit, "Edit $key") }
                    if (info.isSet) IconButton(onClick = { onDelete(key) }) { Icon(Icons.Outlined.Delete, "Remove $key") }
                }
            }
        }
    }
}

@Composable
private fun ProviderSettingDialog(
    key: String,
    info: EnvVarInfo,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    // Secrets deliberately use remember, not rememberSaveable: Android must not persist them in restored UI state.
    var value by remember(key) { mutableStateOf("") }
    var apiKey by remember(key) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (info.isSet) "REPLACE PROVIDER SETTING" else "SET UP PROVIDER") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(info.description.ifBlank { key }, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(key) },
                    singleLine = true,
                    visualTransformation = if (info.isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (key == "OPENAI_BASE_URL") {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Endpoint API key (optional, validation only)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "The value is sent directly to Hermes for validation and server-side storage. Android never writes it to app state, logs or preferences.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val submittedValue = value
                    val submittedKey = apiKey
                    value = ""
                    apiKey = ""
                    onSave(submittedValue, submittedKey)
                },
                enabled = value.isNotBlank(),
            ) { Text("Validate and save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

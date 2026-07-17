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
import androidx.compose.material.icons.outlined.ArrowBack
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
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.EnvVarInfo
import com.nousresearch.hermes.protocol.ModelProvider

@Composable
internal fun ProvidersScreen(
    state: HermesState,
    onRefresh: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var editingKey by remember { mutableStateOf<String?>(null) }
    var deletingKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (state.providerOptions == null) onRefresh()
    }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let { IconButton(onClick = it) { Icon(Icons.Outlined.ArrowBack, "Back to sessions") } }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text("PROVIDERS", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text("Dynamic Hermes model accounts / ${state.activeProfile}", style = MaterialTheme.typography.bodySmall)
            }
            if (state.providersLoading) CircularProgressIndicator(Modifier.padding(12.dp), strokeWidth = 2.dp)
            else IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Refresh providers") }
        }
        HorizontalDivider()
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
                TextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(key) },
                    singleLine = true,
                    visualTransformation = if (info.isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (key == "OPENAI_BASE_URL") {
                    TextField(
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

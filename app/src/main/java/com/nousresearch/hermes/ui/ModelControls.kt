package com.nousresearch.hermes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.ModelProvider

private val reasoningEfforts = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModelControls(
    state: HermesState,
    onRefresh: () -> Unit,
    onSelect: (String, String) -> Unit,
    onConfirmModel: () -> Unit,
    onCancelModel: () -> Unit,
    onReasoning: (String) -> Unit,
    onFast: (Boolean) -> Unit,
    onYolo: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var effortOpen by rememberSaveable { mutableStateOf(false) }
    var yoloConfirmation by rememberSaveable { mutableStateOf(false) }
    val runtime = state.runtimeInfo
    val provider = state.modelOptions?.providers?.firstOrNull { it.slug == runtime.provider }
    val capabilities = provider?.capabilities?.get(runtime.model)

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedButton(onClick = { pickerOpen = true }, enabled = !state.modelsLoading) {
            if (state.modelsLoading) {
                CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Memory, null, modifier = Modifier.padding(end = 8.dp))
            }
            Text(
                listOf(runtime.provider, runtime.model).filter(String::isNotBlank).joinToString(" / ").ifBlank { "Choose model" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (capabilities?.reasoning == true) {
            OutlinedButton(onClick = { effortOpen = true }) {
                Icon(Icons.Outlined.Psychology, null, modifier = Modifier.padding(end = 6.dp))
                Text(runtime.reasoningEffort.ifBlank { "default" })
            }
        }
        if (capabilities?.fast == true) {
            FilterChip(
                selected = runtime.fast,
                onClick = { onFast(!runtime.fast) },
                label = { Text("Fast") },
                leadingIcon = { Icon(Icons.Outlined.Bolt, null) },
            )
        }
        if (state.supportsSessionYolo) {
            FilterChip(
                selected = runtime.yolo,
                onClick = {
                    if (runtime.yolo) onYolo(false) else yoloConfirmation = true
                },
                label = { Text("YOLO") },
                leadingIcon = { Icon(Icons.Outlined.Warning, null) },
                modifier = Modifier.semantics {
                    stateDescription = if (runtime.yolo) "Approval bypass enabled for this session" else "Approval bypass disabled"
                },
            )
        }
    }

    if (pickerOpen) {
        ModelPickerDialog(
            providers = state.modelOptions?.providers.orEmpty(),
            currentProvider = runtime.provider,
            currentModel = runtime.model,
            loading = state.modelsLoading,
            onDismiss = { pickerOpen = false },
            onRefresh = onRefresh,
            onSelect = { providerSlug, model ->
                pickerOpen = false
                onSelect(providerSlug, model)
            },
        )
    }

    if (effortOpen) {
        AlertDialog(
            onDismissRequest = { effortOpen = false },
            title = { Text("REASONING EFFORT") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reasoningEfforts.forEach { effort ->
                        FilterChip(
                            selected = runtime.reasoningEffort == effort,
                            onClick = {
                                effortOpen = false
                                onReasoning(effort)
                            },
                            label = { Text(effort) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { effortOpen = false }) { Text("Close") } },
        )
    }

    if (yoloConfirmation) {
        AlertDialog(
            onDismissRequest = { yoloConfirmation = false },
            icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("BYPASS APPROVALS?") },
            text = {
                Text("Hermes will run dangerous commands in this session without asking. This does not change other sessions or the server-wide policy.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        yoloConfirmation = false
                        onYolo(true)
                    },
                ) { Text("Enable for session", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { yoloConfirmation = false }) { Text("Cancel") } },
        )
    }

    state.pendingModelConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = onCancelModel,
            icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("CONFIRM MODEL COST") },
            text = { Text(pending.message) },
            confirmButton = { TextButton(onClick = onConfirmModel) { Text("Use model") } },
            dismissButton = { TextButton(onClick = onCancelModel) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModelPickerDialog(
    providers: List<ModelProvider>,
    currentProvider: String,
    currentModel: String,
    loading: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (String, String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = providers.mapNotNull { provider ->
        val models = provider.models.filter { model ->
            query.isBlank() || model.contains(query, ignoreCase = true) || provider.name.contains(query, ignoreCase = true)
        }
        provider.takeIf { models.isNotEmpty() }?.let { it to models }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MODEL CATALOGUE", modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Outlined.Refresh, "Refresh model catalogue") }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search provider or model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    filtered.forEach { (provider, models) ->
                        stickyHeader(key = "provider:${provider.slug}") {
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                    Text(provider.name, fontWeight = FontWeight.SemiBold)
                                    provider.warning?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        items(models, key = { "${provider.slug}:$it" }) { model ->
                            val selected = provider.slug == currentProvider && model == currentModel
                            Surface(
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onSelect(provider.slug, model) },
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(model, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (selected) Text("Active", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                if (!loading && filtered.isEmpty()) {
                    Text(
                        if (providers.isEmpty()) "Hermes returned no configured models." else "No matching models.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

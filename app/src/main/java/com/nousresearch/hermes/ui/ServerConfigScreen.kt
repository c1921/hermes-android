package com.nousresearch.hermes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.data.ServerConfigField
import com.nousresearch.hermes.data.ServerConfigType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

@Composable
internal fun ServerConfigScreen(
    state: HermesState,
    onRefresh: () -> Unit,
    onUpdate: (String, JsonElement) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ServerConfigField?>(null) }

    LaunchedEffect(state.activeProfile) {
        editing = null
        selectedCategory = null
        onRefresh()
    }

    val categories = state.serverConfig.categories
    val activeCategory = selectedCategory?.takeIf { it in categories }
    val visible = state.serverConfig.fields.filter { field ->
        (activeCategory == null || field.category == activeCategory) &&
            (query.isBlank() || listOf(field.key, field.description, field.category).any {
                it.contains(query.trim(), ignoreCase = true)
            })
    }

    Column(modifier.fillMaxSize()) {
        ManagementHeader(
            title = "SERVER SETTINGS",
            subtitle = "Profile ${state.serverConfigProfile ?: state.activeProfile}",
            loading = state.serverConfigLoading,
            onRefresh = onRefresh,
            onBack = onBack,
        )
        Text(
            "Schema-driven Hermes configuration. Android sends one advertised field at a time; running sessions may retain their current values.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(120) },
            label = { Text("Search settings") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        )
        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = activeCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All") },
                    )
                }
                items(categories, key = { it }) { category ->
                    FilterChip(
                        selected = activeCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.humanizeConfigName()) },
                    )
                }
            }
        }
        state.serverConfigNotice?.let { ConfigNotice(it, error = false) }
        state.serverConfigError?.let { ConfigNotice(it, error = true) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!state.serverConfigLoading && visible.isEmpty()) {
                item {
                    Text(
                        if (state.serverConfig.fields.isEmpty()) {
                            "Hermes returned no safely editable scalar settings for this profile."
                        } else {
                            "No settings match this filter."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            items(visible, key = ServerConfigField::key) { field ->
                ServerConfigRow(
                    field = field,
                    enabled = !state.serverConfigLoading,
                    onBoolean = { onUpdate(field.key, JsonPrimitive(it)) },
                    onEdit = { editing = field },
                )
            }
            item {
                Text(
                    "Provider secrets, global approval bypass, private-network relaxations, model selection and toolsets stay in their dedicated guarded surfaces.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(4.dp, 12.dp, 4.dp, 24.dp),
                )
            }
        }
    }

    editing?.let { field ->
        ServerConfigEditDialog(
            field = field,
            onDismiss = { editing = null },
            onSave = { value ->
                onUpdate(field.key, value)
                editing = null
            },
        )
    }
}

@Composable
private fun ServerConfigRow(
    field: ServerConfigField,
    enabled: Boolean,
    onBoolean: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val isBoolean = field.type == ServerConfigType.BOOLEAN
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().then(
            if (isBoolean) Modifier else Modifier.clickable(enabled = enabled, onClick = onEdit),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(field.key.substringAfterLast('.').humanizeConfigName(), fontWeight = FontWeight.SemiBold)
                Text(field.key, style = MaterialTheme.typography.labelSmall)
                if (field.description.isNotBlank()) {
                    Text(field.description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (!isBoolean) {
                    Text(field.displayValue(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (isBoolean) {
                Switch(
                    checked = field.value.jsonPrimitive.boolean,
                    onCheckedChange = onBoolean,
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "Toggle ${field.key}" },
                )
            } else {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit ${field.key}")
            }
        }
    }
}

@Composable
private fun ServerConfigEditDialog(
    field: ServerConfigField,
    onDismiss: () -> Unit,
    onSave: (JsonElement) -> Unit,
) {
    var draft by remember(field.key) { mutableStateOf(field.editValue()) }
    var validationError by remember(field.key) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.key.humanizeConfigName(), modifier = Modifier.semantics { heading() }) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (field.description.isNotBlank()) Text(field.description, style = MaterialTheme.typography.bodySmall)
                if (field.type == ServerConfigType.SELECT) {
                    field.options.forEach { option ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (draft == option) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().clickable { draft = option; validationError = null },
                        ) {
                            Text(option.ifBlank { "None" }, modifier = Modifier.padding(12.dp))
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(4_096); validationError = null },
                        label = { Text("Value") },
                        minLines = if (field.type == ServerConfigType.TEXT) 3 else 1,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { field.parseDraft(draft) }
                    .onSuccess(onSave)
                    .onFailure { validationError = it.message ?: "Invalid value" }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfigNotice(message: String, error: Boolean) {
    Text(
        message,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

private fun ServerConfigField.displayValue(): String = value.jsonPrimitive.content.ifBlank { "Not set" }

private fun ServerConfigField.editValue(): String = value.jsonPrimitive.content

private fun ServerConfigField.parseDraft(draft: String): JsonElement = when (type) {
    ServerConfigType.NUMBER -> draft.trim().toLongOrNull()?.let(::JsonPrimitive)
        ?: draft.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.let(::JsonPrimitive)
        ?: throw IllegalArgumentException("Enter a finite number")
    ServerConfigType.SELECT, ServerConfigType.STRING, ServerConfigType.TEXT -> JsonPrimitive(draft)
    ServerConfigType.BOOLEAN -> throw IllegalArgumentException("Boolean settings use their switch")
}

private fun String.humanizeConfigName(): String = split('_', '-').joinToString(" ") { part ->
    part.replaceFirstChar { character -> character.uppercase() }
}

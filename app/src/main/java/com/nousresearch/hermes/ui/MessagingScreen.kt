package com.nousresearch.hermes.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.MessagingEnvVarInfo
import com.nousresearch.hermes.protocol.MessagingPlatformInfo
import com.nousresearch.hermes.R

@Composable
internal fun MessagingScreen(
    state: HermesState,
    onRefresh: () -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onSave: (String, Map<String, String>) -> Unit,
    onClear: (String, String) -> Unit,
    onTest: (String) -> Unit,
    onRestartGateway: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { onRefresh() }
    val selected = state.messagingPlatforms.firstOrNull { it.id == selectedId }

    if (selected == null) {
        MessagingList(
            state = state,
            query = query,
            onQuery = { query = it.take(120) },
            onSelect = { selectedId = it },
            onRefresh = onRefresh,
            onBack = onBack,
            modifier = modifier,
        )
    } else {
        MessagingDetail(
            state = state,
            platform = selected,
            onSetEnabled = onSetEnabled,
            onSave = onSave,
            onClear = onClear,
            onTest = onTest,
            onRestartGateway = onRestartGateway,
            onBack = { selectedId = null },
            modifier = modifier,
        )
    }
}

@Composable
private fun MessagingList(
    state: HermesState,
    query: String,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier,
) {
    val visible = state.messagingPlatforms.filter { platform ->
        query.isBlank() || listOf(platform.name, platform.id, platform.description, platform.state.orEmpty())
            .any { it.contains(query.trim(), ignoreCase = true) }
    }
    Column(modifier.fillMaxSize()) {
        MessagingHeader("MESSAGING", "Hermes gateway platforms / ${state.activeProfile}", state.messagingLoading, onRefresh, onBack)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Text(stringResource(R.string.ui_these_are_server_side_telegram_discord_slack_and_other__66fc87),
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text(stringResource(R.string.ui_search_messaging_platforms_774838)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        state.messagingNotice?.let { MessagingNotice(it) }
        state.error?.let { MessagingError(it) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(visible, key = MessagingPlatformInfo::id) { platform ->
                MessagingPlatformRow(platform, onClick = { onSelect(platform.id) })
            }
            if (visible.isEmpty() && !state.messagingLoading) {
                item {
                    Text(
                        if (state.messagingPlatforms.isEmpty()) "Hermes returned no messaging platforms for this profile."
                        else "No messaging platforms match this search.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessagingPlatformRow(platform: MessagingPlatformInfo, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(platform.name, style = MaterialTheme.typography.titleMedium)
                Text(platform.description.ifBlank { platform.id }, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(platform.stateLabel(), style = MaterialTheme.typography.labelMedium, color = platform.stateColor())
            }
            Icon(
                when {
                    !platform.enabled -> Icons.Outlined.RadioButtonUnchecked
                    platform.state == "connected" -> Icons.Outlined.CheckCircle
                    else -> Icons.Outlined.ErrorOutline
                },
                platform.stateLabel(),
                tint = platform.stateColor(),
            )
        }
    }
}

@Composable
private fun MessagingDetail(
    state: HermesState,
    platform: MessagingPlatformInfo,
    onSetEnabled: (String, Boolean) -> Unit,
    onSave: (String, Map<String, String>) -> Unit,
    onClear: (String, String) -> Unit,
    onTest: (String) -> Unit,
    onRestartGateway: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    // Credentials deliberately never use rememberSaveable. Process recreation and navigation clear every entered value.
    val edits = remember(platform.id) { mutableStateMapOf<String, String>() }
    var pendingClear by remember { mutableStateOf<MessagingEnvVarInfo?>(null) }
    var pendingEnabled by remember { mutableStateOf<Boolean?>(null) }
    var confirmRestart by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Column(modifier.fillMaxSize()) {
        MessagingHeader(platform.name.uppercase(), platform.id, state.messagingLoading || state.gatewayRestarting, null, onBack)
        state.messagingNotice?.let { MessagingNotice(it) }
        state.error?.let { MessagingError(it) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(platform.stateLabel(), style = MaterialTheme.typography.titleMedium, color = platform.stateColor())
                                Text(
                                    "${if (platform.configured) "Configured" else "Setup incomplete"} / ${if (platform.gatewayRunning) "gateway running" else "gateway stopped"}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(
                                checked = platform.enabled,
                                onCheckedChange = { pendingEnabled = it },
                                enabled = !state.messagingLoading && !state.gatewayRestarting,
                                modifier = Modifier.localizedContentDescription(R.string.enable_named, platform.id),
                            )
                        }
                        Text(platform.description.ifBlank { "Hermes messaging platform ${platform.id}." }, style = MaterialTheme.typography.bodySmall)
                        platform.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        platform.homeChannel?.let {
                            Text(stringResource(R.string.home_channel, it.name, it.chatId), style = MaterialTheme.typography.bodySmall)
                        }
                        if (platform.docsUrl.startsWith("https://") || platform.docsUrl.startsWith("http://")) {
                            TextButton(
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(platform.docsUrl))) },
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.ui_open_official_setup_guide_6b45c1))
                            }
                        }
                    }
                }
            }
            items(platform.envVars, key = MessagingEnvVarInfo::key) { field ->
                MessagingSettingField(
                    field = field,
                    value = edits[field.key].orEmpty(),
                    onValue = { edits[field.key] = it.take(MAX_MESSAGING_VALUE_CHARACTERS) },
                    onClear = if (field.isSet) ({ pendingClear = field }) else null,
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onTest(platform.id) },
                        enabled = !state.messagingLoading && !state.gatewayRestarting,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.ui_test_640ab2)) }
                    Button(
                        onClick = {
                            val submitted = edits.toMap()
                            edits.keys.forEach { edits[it] = "" }
                            onSave(platform.id, submitted)
                        },
                        enabled = edits.values.any(String::isNotBlank) && !state.messagingLoading && !state.gatewayRestarting,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.ui_save_setup_1a0067)) }
                }
            }
            state.messagingTests[platform.id]?.let { result ->
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (result.ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(if (result.ok) "CONNECTION TEST PASSED" else "CONNECTION TEST NEEDS ATTENTION", style = MaterialTheme.typography.labelMedium)
                            Text(result.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { confirmRestart = true },
                    enabled = !state.gatewayRestarting && !state.messagingLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.gatewayRestarting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Sync, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.gatewayRestarting) "Restarting gateway" else "Restart messaging gateway")
                }
            }
        }
    }

    pendingClear?.let { field ->
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text(stringResource(R.string.ui_remove_messaging_setting_d9a6fb)) },
            text = { Text(stringResource(R.string.remove_messaging_setting_description, field.key, platform.name)) },
            confirmButton = {
                TextButton(onClick = { pendingClear = null; onClear(platform.id, field.key) }) { Text(stringResource(R.string.ui_remove_e96390)) }
            },
            dismissButton = { TextButton(onClick = { pendingClear = null }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
        )
    }
    pendingEnabled?.let { enabled ->
        AlertDialog(
            onDismissRequest = { pendingEnabled = null },
            title = { Text(if (enabled) "ENABLE MESSAGING PLATFORM?" else "DISABLE MESSAGING PLATFORM?") },
            text = {
                Text(
                    "${if (enabled) "Enable" else "Disable"} ${platform.name} for profile ${state.activeProfile}? " +
                        if (enabled) "Hermes may begin receiving and sending messages through this platform."
                        else "Hermes will stop handling new messages through this platform.",
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingEnabled = null; onSetEnabled(platform.id, enabled) }) {
                    Text(if (enabled) "Enable" else "Disable")
                }
            },
            dismissButton = { TextButton(onClick = { pendingEnabled = null }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
        )
    }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text(stringResource(R.string.ui_restart_messaging_gateway_76d6d5)) },
            text = { Text(stringResource(R.string.restart_gateway_description, state.activeProfile)) },
            confirmButton = {
                TextButton(onClick = { confirmRestart = false; onRestartGateway() }) { Text(stringResource(R.string.ui_restart_b134bd)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text(stringResource(R.string.ui_cancel_77dfd2)) } },
        )
    }
}

@Composable
private fun MessagingSettingField(
    field: MessagingEnvVarInfo,
    value: String,
    onValue: (String) -> Unit,
    onClear: (() -> Unit)?,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(field.prompt.ifBlank { field.key }, style = MaterialTheme.typography.labelLarge)
                    Text(
                        buildString {
                            append(field.key)
                            if (field.required) append(" / required")
                            if (field.advanced) append(" / advanced")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                onClear?.let {
                    IconButton(onClick = it) { Icon(Icons.Outlined.Delete, stringResource(R.string.remove_named, field.key)) }
                }
            }
            field.description.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                label = { Text(if (field.isSet) stringResource(R.string.replace_saved_value, field.redactedValue ?: "saved value") else stringResource(R.string.enter_value)) },
                visualTransformation = if (field.isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.ui_sent_directly_to_hermes_and_never_saved_by_android_843266),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessagingHeader(
    title: String,
    subtitle: String,
    loading: Boolean,
    onRefresh: (() -> Unit)?,
    onBack: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.a11y_back_b52b36)) } }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (loading) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
        else onRefresh?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Refresh, stringResource(R.string.a11y_refresh_messaging_platforms_e555a5)) } }
    }
    HorizontalDivider()
}

@Composable
private fun MessagingNotice(message: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun MessagingError(message: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun MessagingPlatformInfo.stateColor(): Color = when {
    !enabled -> MaterialTheme.colorScheme.outline
    state == "connected" -> MaterialTheme.colorScheme.tertiary
    state == "fatal" || state == "startup_failed" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

private fun MessagingPlatformInfo.stateLabel(): String = when {
    !enabled -> "DISABLED"
    state.isNullOrBlank() -> "UNKNOWN"
    else -> state.replace('_', ' ').uppercase()
}

private const val MAX_MESSAGING_VALUE_CHARACTERS = 32_768

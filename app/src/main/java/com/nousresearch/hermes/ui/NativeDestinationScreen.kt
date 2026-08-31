package com.nousresearch.hermes.ui

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.R

/** The native product homes introduced around the legacy management destinations. */
internal enum class NativeDestination {
    ARTIFACTS,
    AUTOMATIONS,
    MANAGE,
}

/** Manage categories are deliberately separate from the server's legacy screen names. */
internal enum class NativeManageSection {
    CAPABILITIES,
    PROFILES_AND_MODELS,
    CONNECTIONS_AND_DELIVERY,
    MEMORY_AND_LEARNING,
    SERVER_AND_ACCOUNT,
}

/** Whether Android can open an existing remote destination from this entry. */
internal enum class NativeEntryAvailability {
    AVAILABLE,
    REMOTE_STATUS,
    UNAVAILABLE,
}

internal enum class NativeDestinationAction {
    REMOTE_FILES,
    CRON,
    COMMAND_CENTER,
    AGENTS,
    SKILLS,
    MCP,
    PROFILES,
    PROVIDERS,
    MESSAGING,
    BACKENDS,
    SERVER_SETTINGS,
    USAGE,
    BILLING,
    REMOTE_DIAGNOSTICS,
    STARMAP,
}

internal data class NativeDestinationEntry(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val availability: NativeEntryAvailability,
    @StringRes val status: Int,
    val icon: ImageVector,
    val action: NativeDestinationAction? = null,
)

internal data class NativeManageSectionModel(
    val section: NativeManageSection,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val entries: List<NativeDestinationEntry>,
)

internal data class NativeArtifactsModel(
    val entries: List<NativeDestinationEntry> = defaultNativeArtifactsEntries(),
)

internal data class NativeAutomationsModel(
    val entries: List<NativeDestinationEntry> = defaultNativeAutomationsEntries(),
)

/**
 * Standalone category UI for destinations that are not themselves protocol operations.
 *
 * The host owns navigation and supplies [onOpenEntry]. Status-only and unavailable entries
 * intentionally never invoke that callback, so this screen cannot invent an Android-local
 * implementation for a Hermes capability that is not exposed by the backend.
 */
@Composable
internal fun NativeDestinationScreen(
    destination: NativeDestination,
    artifacts: NativeArtifactsModel = NativeArtifactsModel(),
    automations: NativeAutomationsModel = NativeAutomationsModel(),
    manageSections: List<NativeManageSectionModel> = defaultNativeManageSections(),
    onBack: (() -> Unit)? = null,
    onOpenEntry: ((NativeDestinationEntry) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val title = when (destination) {
        NativeDestination.ARTIFACTS -> stringResource(R.string.ui_native_artifacts_title_793cf4)
        NativeDestination.AUTOMATIONS -> stringResource(R.string.ui_native_automations_title_220c91)
        NativeDestination.MANAGE -> stringResource(R.string.ui_native_manage_title_7b8538)
    }
    val subtitle = when (destination) {
        NativeDestination.ARTIFACTS -> stringResource(R.string.ui_native_artifacts_subtitle_608865)
        NativeDestination.AUTOMATIONS -> stringResource(R.string.ui_native_automations_subtitle_99b2ac)
        NativeDestination.MANAGE -> stringResource(R.string.ui_native_manage_subtitle_757f82)
    }

    val entries = when (destination) {
        NativeDestination.ARTIFACTS -> artifacts.entries
        NativeDestination.AUTOMATIONS -> automations.entries
        NativeDestination.MANAGE -> emptyList()
    }

    Column(modifier.fillMaxSize()) {
        NativeDestinationHeader(title, subtitle, onBack)
        when (destination) {
            NativeDestination.MANAGE -> ManageSections(
                sections = manageSections,
                onOpenEntry = onOpenEntry,
                modifier = Modifier.fillMaxSize(),
            )
            NativeDestination.ARTIFACTS,
            NativeDestination.AUTOMATIONS,
            -> DestinationEntries(
                entries = entries,
                onOpenEntry = onOpenEntry,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun ScopedDestinationScreen(
    title: String,
    resourceId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        NativeDestinationHeader(title.uppercase(), stringResource(R.string.ui_scoped_hermes_resource_90996d), onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CategorySummary(
                    stringResource(R.string.ui_scoped_destination_intro_d9de57),
                )
            }
            item {
                Text(stringResource(R.string.ui_resource_id_067bbd), style = MaterialTheme.typography.labelMedium)
                SelectionContainer {
                    Text(resourceId, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun NativeDestinationHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.localizedContentDescription(R.string.a11y_back_b52b36),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DestinationEntries(
    entries: List<NativeDestinationEntry>,
    onOpenEntry: ((NativeDestinationEntry) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CategorySummary(
                text = stringResource(R.string.ui_entries_intro_98121e),
            )
        }
        items(entries, key = NativeDestinationEntry::id) { entry ->
            NativeEntryCard(entry, onOpenEntry)
        }
    }
}

@Composable
private fun ManageSections(
    sections: List<NativeManageSectionModel>,
    onOpenEntry: ((NativeDestinationEntry) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CategorySummary(
                text = stringResource(R.string.ui_manage_sections_intro_8fa462),
            )
        }
        items(sections, key = NativeManageSectionModel::section) { section ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(section.title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(section.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                section.entries.forEach { entry ->
                    NativeEntryCard(entry, onOpenEntry)
                }
                if (section != sections.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySummary(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun NativeEntryCard(
    entry: NativeDestinationEntry,
    onOpenEntry: ((NativeDestinationEntry) -> Unit)?,
) {
    val canOpen = entry.availability == NativeEntryAvailability.AVAILABLE && onOpenEntry != null
    Card(
        onClick = { onOpenEntry?.invoke(entry) },
        enabled = canOpen,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .localizedContentDescription(R.string.native_entry_description, stringResource(entry.title), stringResource(entry.status)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(entry.title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(entry.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(entry.status),
                style = MaterialTheme.typography.labelSmall,
                color = availabilityColor(entry.availability),
                maxLines = 2,
                modifier = Modifier.width(92.dp),
            )
        }
    }
}

@Composable
private fun availabilityColor(availability: NativeEntryAvailability) = when (availability) {
    NativeEntryAvailability.AVAILABLE -> MaterialTheme.colorScheme.primary
    NativeEntryAvailability.REMOTE_STATUS -> MaterialTheme.colorScheme.tertiary
    NativeEntryAvailability.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun defaultNativeArtifactsEntries(): List<NativeDestinationEntry> = listOf(
    NativeDestinationEntry(
        id = "remote-files",
        title = R.string.ui_entry_remote_files_title_0b1e5b,
        description = R.string.ui_entry_remote_files_desc_b93e6d,
        availability = NativeEntryAvailability.AVAILABLE,
        status = R.string.ui_status_remote_files_4d296e,
        icon = Icons.Outlined.Folder,
        action = NativeDestinationAction.REMOTE_FILES,
    ),
    NativeDestinationEntry(
        id = "artifact-index",
        title = R.string.ui_entry_artifact_index_title_631115,
        description = R.string.ui_entry_artifact_index_desc_41f7a1,
        availability = NativeEntryAvailability.UNAVAILABLE,
        status = R.string.ui_status_not_exposed_9a0a9a,
        icon = Icons.Outlined.Archive,
    ),
)

internal fun defaultNativeAutomationsEntries(): List<NativeDestinationEntry> = listOf(
    NativeDestinationEntry(
        id = "cron",
        title = R.string.ui_entry_cron_title_39d6f6,
        description = R.string.ui_entry_cron_desc_7edf17,
        availability = NativeEntryAvailability.AVAILABLE,
        status = R.string.ui_status_remote_jobs_9e01b2,
        icon = Icons.Outlined.Schedule,
        action = NativeDestinationAction.CRON,
    ),
    NativeDestinationEntry(
        id = "command-center",
        title = R.string.ui_entry_command_center_title_de7d6f,
        description = R.string.ui_entry_command_center_desc_611887,
        availability = NativeEntryAvailability.AVAILABLE,
        status = R.string.ui_status_remote_status_7873a5,
        icon = Icons.Outlined.Terminal,
        action = NativeDestinationAction.COMMAND_CENTER,
    ),
    NativeDestinationEntry(
        id = "agents",
        title = R.string.ui_entry_agents_title_67d84a,
        description = R.string.ui_entry_agents_desc_1a3c1f,
        availability = NativeEntryAvailability.AVAILABLE,
        status = R.string.ui_status_remote_status_7873a5,
        icon = Icons.Outlined.Terminal,
        action = NativeDestinationAction.AGENTS,
    ),
    NativeDestinationEntry(
        id = "webhooks",
        title = R.string.ui_entry_webhooks_title_c2d65a,
        description = R.string.ui_entry_webhooks_desc_9fad68,
        availability = NativeEntryAvailability.REMOTE_STATUS,
        status = R.string.ui_status_status_only_da6a31,
        icon = Icons.AutoMirrored.Outlined.Send,
    ),
)

internal fun defaultNativeManageSections(): List<NativeManageSectionModel> = listOf(
    NativeManageSectionModel(
        section = NativeManageSection.CAPABILITIES,
        title = R.string.ui_sec_capabilities_title_acf286,
        description = R.string.ui_sec_capabilities_desc_1c4815,
        entries = listOf(
            entry("skills-and-hub", R.string.ui_entry_skills_hub_title_de269f, R.string.ui_entry_skills_hub_desc_d35b55, NativeEntryAvailability.AVAILABLE, R.string.ui_status_remote_d6e85b, Icons.Outlined.Psychology, NativeDestinationAction.SKILLS),
            entry("mcp", R.string.ui_entry_mcp_title_4afa80, R.string.ui_entry_mcp_desc_50933f, NativeEntryAvailability.AVAILABLE, R.string.ui_status_remote_d6e85b, Icons.Outlined.Hub, NativeDestinationAction.MCP),
            entry("host-capabilities", R.string.ui_entry_host_capabilities_title_f27d9b, R.string.ui_entry_host_capabilities_desc_7501ae, NativeEntryAvailability.REMOTE_STATUS, R.string.ui_status_status_only_da6a31, Icons.Outlined.HealthAndSafety),
        ),
    ),
    NativeManageSectionModel(
        section = NativeManageSection.PROFILES_AND_MODELS,
        title = R.string.ui_sec_profiles_models_title_efcc60,
        description = R.string.ui_sec_profiles_models_desc_65083c,
        entries = listOf(
            entry("profiles", R.string.ui_entry_profiles_title_9eec48, R.string.ui_entry_profiles_desc_ba4d24, NativeEntryAvailability.AVAILABLE, R.string.ui_status_remote_d6e85b, Icons.Outlined.Person, NativeDestinationAction.PROFILES),
            entry("providers", R.string.ui_entry_providers_title_59b6b2, R.string.ui_entry_providers_desc_2c5aba, NativeEntryAvailability.AVAILABLE, R.string.ui_status_remote_d6e85b, Icons.Outlined.Key, NativeDestinationAction.PROVIDERS),
            entry("model-catalogue", R.string.ui_entry_model_catalogue_title_59002b, R.string.ui_entry_model_catalogue_desc_355f23, NativeEntryAvailability.REMOTE_STATUS, R.string.ui_status_status_only_da6a31, Icons.Outlined.Memory),
        ),
    ),
    NativeManageSectionModel(
        section = NativeManageSection.CONNECTIONS_AND_DELIVERY,
        title = R.string.ui_sec_connections_title_c7165a,
        description = R.string.ui_sec_connections_desc_fe0213,
        entries = listOf(
            entry("backends", R.string.ui_entry_backends_title_5f79c7, R.string.ui_entry_backends_desc_f79b7a, NativeEntryAvailability.AVAILABLE, R.string.ui_status_device_remote_948c5a, Icons.Outlined.Tune, NativeDestinationAction.BACKENDS),
            entry("messaging", R.string.ui_entry_messaging_title_ecca9e, R.string.ui_entry_messaging_desc_d085eb, NativeEntryAvailability.AVAILABLE, R.string.ui_status_remote_d6e85b, Icons.AutoMirrored.Outlined.Send, NativeDestinationAction.MESSAGING),
        ),
    ),
    NativeManageSectionModel(
        section = NativeManageSection.MEMORY_AND_LEARNING,
        title = R.string.ui_sec_memory_title_f841b2,
        description = R.string.ui_sec_memory_desc_09b22e,
        entries = listOf(
            entry("starmap-memory-graph", R.string.ui_entry_starmap_title_cc042e, R.string.ui_entry_starmap_desc_b928f7, NativeEntryAvailability.AVAILABLE, R.string.ui_status_remote_d6e85b, Icons.Outlined.Memory, NativeDestinationAction.STARMAP),
        ),
    ),
    NativeManageSectionModel(
        section = NativeManageSection.SERVER_AND_ACCOUNT,
        title = R.string.ui_sec_server_account_title_c69c7f,
        description = R.string.ui_sec_server_account_desc_c323bd,
        entries = listOf(
            entry("server-settings", R.string.ui_entry_server_settings_title_f24729, R.string.ui_entry_server_settings_desc_415c80, NativeEntryAvailability.AVAILABLE, R.string.ui_status_remote_d6e85b, Icons.Outlined.Tune, NativeDestinationAction.SERVER_SETTINGS),
            entry("usage", R.string.ui_entry_usage_title_75361a, R.string.ui_entry_usage_desc_238195, NativeEntryAvailability.AVAILABLE, R.string.ui_status_remote_d6e85b, Icons.Outlined.BarChart, NativeDestinationAction.USAGE),
            entry("billing", R.string.ui_entry_billing_title_4d42b0, R.string.ui_entry_billing_desc_4d469f, NativeEntryAvailability.AVAILABLE, R.string.ui_status_remote_d6e85b, Icons.Outlined.Key, NativeDestinationAction.BILLING),
            entry("remote-diagnostics", R.string.ui_entry_remote_diagnostics_title_2c558c, R.string.ui_entry_remote_diagnostics_desc_9ca5c1, NativeEntryAvailability.AVAILABLE, R.string.ui_status_remote_d6e85b, Icons.Outlined.HealthAndSafety, NativeDestinationAction.REMOTE_DIAGNOSTICS),
        ),
    ),
)

private fun entry(
    id: String,
    @StringRes title: Int,
    @StringRes description: Int,
    availability: NativeEntryAvailability,
    @StringRes status: Int,
    icon: ImageVector,
    action: NativeDestinationAction? = null,
) = NativeDestinationEntry(id, title, description, availability, status, icon, action)

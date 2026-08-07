package com.nousresearch.hermes.ui

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
}

internal data class NativeDestinationEntry(
    val id: String,
    val title: String,
    val description: String,
    val availability: NativeEntryAvailability,
    val status: String,
    val icon: ImageVector,
    val action: NativeDestinationAction? = null,
)

internal data class NativeManageSectionModel(
    val section: NativeManageSection,
    val title: String,
    val description: String,
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
        NativeDestination.ARTIFACTS -> "ARTIFACTS"
        NativeDestination.AUTOMATIONS -> "AUTOMATIONS"
        NativeDestination.MANAGE -> "MANAGE"
    }
    val subtitle = when (destination) {
        NativeDestination.ARTIFACTS -> "Remote files and artifact status"
        NativeDestination.AUTOMATIONS -> "Cron, agents, and delivery status"
        NativeDestination.MANAGE -> "Hermes capabilities and account"
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
        NativeDestinationHeader(title.uppercase(), "Scoped Hermes resource", onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CategorySummary(
                    "This destination preserves the backend, profile, and stable resource identity. Actions remain unavailable until the connected Hermes contract exposes this detail surface.",
                )
            }
            item {
                Text("RESOURCE ID", style = MaterialTheme.typography.labelMedium)
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
                modifier = Modifier.semantics { contentDescription = "Back" },
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
                text = "Choose a remote Hermes destination. Entries marked status-only are reported by the server and have no Android operation yet.",
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
                text = "Manage Hermes remotely. Device-local preferences stay in App settings and never inherit backend or profile scope.",
            )
        }
        items(sections, key = NativeManageSectionModel::section) { section ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    section.description,
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
            .semantics {
                contentDescription = "${entry.title}. ${entry.status}"
            },
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
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = entry.status,
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
        title = "Remote Files",
        description = "Browse, preview, download, and share files managed by Hermes.",
        availability = NativeEntryAvailability.AVAILABLE,
        status = "REMOTE FILES",
        icon = Icons.Outlined.Folder,
        action = NativeDestinationAction.REMOTE_FILES,
    ),
    NativeDestinationEntry(
        id = "artifact-index",
        title = "Artifact index",
        description = "A canonical artifact catalogue is not exposed by the current Hermes contract.",
        availability = NativeEntryAvailability.UNAVAILABLE,
        status = "NOT EXPOSED",
        icon = Icons.Outlined.Archive,
    ),
)

internal fun defaultNativeAutomationsEntries(): List<NativeDestinationEntry> = listOf(
    NativeDestinationEntry(
        id = "cron",
        title = "Cron",
        description = "Create and monitor scheduled Hermes jobs.",
        availability = NativeEntryAvailability.AVAILABLE,
        status = "REMOTE JOBS",
        icon = Icons.Outlined.Schedule,
        action = NativeDestinationAction.CRON,
    ),
    NativeDestinationEntry(
        id = "command-center",
        title = "Command Center",
        description = "Inspect agents, delegation, and background process status.",
        availability = NativeEntryAvailability.AVAILABLE,
        status = "REMOTE STATUS",
        icon = Icons.Outlined.Terminal,
        action = NativeDestinationAction.COMMAND_CENTER,
    ),
    NativeDestinationEntry(
        id = "agents",
        title = "Agents",
        description = "Review the agents exposed by the connected Hermes backend.",
        availability = NativeEntryAvailability.AVAILABLE,
        status = "REMOTE STATUS",
        icon = Icons.Outlined.Terminal,
        action = NativeDestinationAction.AGENTS,
    ),
    NativeDestinationEntry(
        id = "webhooks",
        title = "Webhooks",
        description = "Webhook delivery status is reported here when Hermes exposes its contract.",
        availability = NativeEntryAvailability.REMOTE_STATUS,
        status = "STATUS ONLY",
        icon = Icons.AutoMirrored.Outlined.Send,
    ),
)

internal fun defaultNativeManageSections(): List<NativeManageSectionModel> = listOf(
    NativeManageSectionModel(
        section = NativeManageSection.CAPABILITIES,
        title = "Capabilities",
        description = "Remote skills, tools, and host capability reporting.",
        entries = listOf(
            entry("skills-and-hub", "Skills & Hub", "Browse Hermes skills and the capability hub.", NativeEntryAvailability.AVAILABLE, "REMOTE", Icons.Outlined.Psychology, NativeDestinationAction.SKILLS),
            entry("mcp", "MCP", "Manage remote Model Context Protocol servers.", NativeEntryAvailability.AVAILABLE, "REMOTE", Icons.Outlined.Hub, NativeDestinationAction.MCP),
            entry("host-capabilities", "Host capabilities", "Desktop-only capabilities are reported by Hermes and never executed locally on Android.", NativeEntryAvailability.REMOTE_STATUS, "STATUS ONLY", Icons.Outlined.HealthAndSafety),
        ),
    ),
    NativeManageSectionModel(
        section = NativeManageSection.PROFILES_AND_MODELS,
        title = "Profiles & models",
        description = "Remote profiles, provider accounts, and model availability.",
        entries = listOf(
            entry("profiles", "Profiles", "Select and manage Hermes profiles.", NativeEntryAvailability.AVAILABLE, "REMOTE", Icons.Outlined.Person, NativeDestinationAction.PROFILES),
            entry("providers", "Providers & API keys", "Manage provider accounts and their remote credentials.", NativeEntryAvailability.AVAILABLE, "REMOTE", Icons.Outlined.Key, NativeDestinationAction.PROVIDERS),
            entry("model-catalogue", "Model catalogue", "Model availability follows the connected Hermes backend.", NativeEntryAvailability.REMOTE_STATUS, "STATUS ONLY", Icons.Outlined.Memory),
        ),
    ),
    NativeManageSectionModel(
        section = NativeManageSection.CONNECTIONS_AND_DELIVERY,
        title = "Connections & delivery",
        description = "Backends, messaging channels, and delivery status.",
        entries = listOf(
            entry("backends", "Backends", "Switch between saved Hermes installations.", NativeEntryAvailability.AVAILABLE, "DEVICE + REMOTE", Icons.Outlined.Tune, NativeDestinationAction.BACKENDS),
            entry("messaging", "Messaging", "Configure remote messaging channels.", NativeEntryAvailability.AVAILABLE, "REMOTE", Icons.AutoMirrored.Outlined.Send, NativeDestinationAction.MESSAGING),
        ),
    ),
    NativeManageSectionModel(
        section = NativeManageSection.MEMORY_AND_LEARNING,
        title = "Memory & learning",
        description = "Remote memory status without local execution or invented storage.",
        entries = listOf(
            entry("starmap-memory-graph", "Starmap / Memory Graph", "No dedicated Hermes route is currently exposed for this view.", NativeEntryAvailability.UNAVAILABLE, "NOT EXPOSED", Icons.Outlined.Memory),
        ),
    ),
    NativeManageSectionModel(
        section = NativeManageSection.SERVER_AND_ACCOUNT,
        title = "Server & account",
        description = "Connected Hermes installations, settings, health, and account records.",
        entries = listOf(
            entry("server-settings", "Server settings", "Review configuration exposed by the connected Hermes server.", NativeEntryAvailability.AVAILABLE, "REMOTE", Icons.Outlined.Tune, NativeDestinationAction.SERVER_SETTINGS),
            entry("usage", "Usage", "Review remote usage and quota information.", NativeEntryAvailability.AVAILABLE, "REMOTE", Icons.Outlined.BarChart, NativeDestinationAction.USAGE),
            entry("billing", "Billing", "Review account billing information held by Hermes.", NativeEntryAvailability.AVAILABLE, "REMOTE", Icons.Outlined.Key, NativeDestinationAction.BILLING),
            entry("remote-diagnostics", "Remote diagnostics", "Run Hermes health and security checks on the connected backend.", NativeEntryAvailability.AVAILABLE, "REMOTE", Icons.Outlined.HealthAndSafety, NativeDestinationAction.REMOTE_DIAGNOSTICS),
        ),
    ),
)

private fun entry(
    id: String,
    title: String,
    description: String,
    availability: NativeEntryAvailability,
    status: String,
    icon: ImageVector,
    action: NativeDestinationAction? = null,
) = NativeDestinationEntry(id, title, description, availability, status, icon, action)

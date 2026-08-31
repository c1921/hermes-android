package com.nousresearch.hermes.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.R

private data class HostCapability(
    val title: String,
    val status: String,
    val explanation: String,
)

private val hostCapabilities = listOf(
    HostCapability(
        "Remote workspace files",
        "AVAILABLE THROUGH HERMES",
        "Android browses and downloads files through the authenticated Hermes backend. It does not read the desktop host filesystem directly.",
    ),
    HostCapability(
        "Terminal and PTY takeover",
        "DESKTOP HOST ONLY",
        "Interactive terminal ownership remains on Hermes Desktop or the server host. Android never starts a local shell.",
    ),
    HostCapability(
        "Git, worktrees and project discovery",
        "DESKTOP HOST ONLY",
        "Repository scanning and worktree mutation require the desktop host. Android shows only state returned by Hermes.",
    ),
    HostCapability(
        "Backend install, update and repair",
        "DESKTOP HOST ONLY",
        "Android can report backend health but cannot install, launch, update or repair the desktop process.",
    ),
    HostCapability(
        "Desktop windows, quick entry and plugins",
        "DESKTOP HOST ONLY",
        "Electron windows, quick entry, pet overlays and desktop plugin execution are intentionally unavailable on Android.",
    ),
)

@Composable
internal fun HostCapabilitiesScreen(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let {
                IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.a11y_back_b52b36)) }
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.ui_host_capabilities_30d569),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.ui_what_android_can_request_remotely_and_what_stays_on_des_c7c461), style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(hostCapabilities, key = HostCapability::title) { capability ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(capability.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            capability.status,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(capability.explanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

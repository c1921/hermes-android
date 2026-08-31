package com.nousresearch.hermes.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.network.DashboardAuthProvider
import com.nousresearch.hermes.R

internal class DashboardProviderDiscoveryGate {
    private var generation = 0L
    private var activeRequest: Long? = null

    fun begin(): Long? {
        if (activeRequest != null) return null
        generation += 1
        return generation.also { activeRequest = it }
    }

    fun invalidate() {
        generation += 1
        activeRequest = null
    }

    fun isCurrent(request: Long): Boolean = activeRequest == request && generation == request

    fun finish(request: Long) {
        if (activeRequest == request) activeRequest = null
    }
}

@Composable
internal fun DashboardPasswordProviderSelector(
    providers: List<DashboardAuthProvider>,
    selectedProvider: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics { selectableGroup() },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.ui_password_provider_963784), style = MaterialTheme.typography.labelMedium)
        providers.forEach { provider ->
            val selected = provider.name == selectedProvider
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password-provider-${provider.name}")
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelected(provider.name) },
                    ),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Column(Modifier.padding(start = 6.dp)) {
                        Text(provider.displayName, style = MaterialTheme.typography.bodyMedium)
                        if (provider.displayName != provider.name) {
                            Text(provider.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DashboardOAuthAvailabilityNotice(modifier: Modifier = Modifier) {
    Text(stringResource(R.string.ui_android_uses_password_providers_advertised_by_this_dash_58816b) +
            "advertises a registered Android redirect contract; complete OAuth-only sign-in in the web Dashboard. " +
            "Hermes Android never reads browser or Custom Tab cookies.",
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

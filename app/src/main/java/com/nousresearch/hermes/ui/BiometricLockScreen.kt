package com.nousresearch.hermes.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.R

@Composable
internal fun BiometricLockScreen(
    error: String?,
    onUnlock: () -> Unit,
    onUseDeviceCredential: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.ui_hermes_locked_3c096f),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() },
            )
            Text(stringResource(R.string.ui_authenticate_with_biometrics_or_your_device_credential__fdb9e0),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp).semantics {
                        liveRegion = LiveRegionMode.Assertive
                    },
                )
            }
            Button(onClick = onUnlock, modifier = Modifier.padding(top = 20.dp)) {
                Text(stringResource(R.string.ui_unlock_908843))
            }
            Button(onClick = onUseDeviceCredential, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.ui_use_device_credential_37ae7d))
            }
        }
    }
}

package com.nousresearch.hermes.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.GatewayConnectionState
import com.nousresearch.hermes.ui.theme.HermesSkin
import com.nousresearch.hermes.ui.theme.HermesTheme
import org.junit.Rule
import org.junit.Test

class SettingsSeparationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun appSettingsOwnDevicePreferences() {
        compose.setContent {
            HermesTheme {
                AppSettingsScreen(
                    secureScreen = true,
                    onSecureScreenChange = {},
                    skin = HermesSkin.NOUS,
                    onSkinChange = {},
                    onBack = null,
                )
            }
        }

        compose.onNodeWithText("APP SETTINGS").assertExists()
        compose.onNodeWithText("APPEARANCE").assertExists()
        compose.onNode(hasScrollAction()).performScrollToIndex(1)
        compose.onNodeWithText("SECURE SCREEN").assertExists()
    }

    @Test
    fun remoteDiagnosticsDoNotExposeDevicePreferences() {
        compose.setContent {
            HermesTheme {
                DiagnosticsScreen(
                    state = HermesState(),
                    connection = GatewayConnectionState.Idle,
                    onRun = {},
                    onRefreshHost = {},
                    onBack = null,
                )
            }
        }

        compose.onNodeWithText("DIAGNOSTICS").assertExists()
        compose.onNodeWithText("APPEARANCE").assertDoesNotExist()
        compose.onNodeWithText("SECURE SCREEN").assertDoesNotExist()
    }

    @Test
    fun scopedDestinationKeepsStableResourceIdentityVisible() {
        compose.setContent {
            HermesTheme {
                ScopedDestinationScreen(
                    title = "Cron",
                    resourceId = "daily-review",
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Scoped Hermes resource").assertExists()
        compose.onNodeWithText("daily-review").assertExists()
    }
}

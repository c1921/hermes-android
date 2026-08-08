package com.nousresearch.hermes.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.CronJob
import com.nousresearch.hermes.protocol.MessagingPlatformInfo
import com.nousresearch.hermes.ui.theme.HermesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ManagementMutationConfirmationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun messagingToggleNamesProfileAndWaitsForConfirmation() {
        var changed: Pair<String, Boolean>? = null
        compose.setContent {
            HermesTheme {
                MessagingScreen(
                    state = HermesState(
                        activeProfile = "work",
                        messagingPlatforms = listOf(
                            MessagingPlatformInfo(
                                id = "telegram",
                                name = "Telegram",
                                enabled = true,
                                configured = true,
                                gatewayRunning = true,
                            ),
                        ),
                    ),
                    onRefresh = {},
                    onSetEnabled = { id, enabled -> changed = id to enabled },
                    onSave = { _, _ -> },
                    onClear = { _, _ -> },
                    onTest = {},
                    onRestartGateway = {},
                    onBack = null,
                )
            }
        }

        compose.onNodeWithText("Telegram").performClick()
        compose.onNodeWithContentDescription("Enable telegram").performClick()
        assertNull(changed)
        compose.onNodeWithText("Disable Telegram for profile work?", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertNull(changed)

        compose.onNodeWithContentDescription("Enable telegram").performClick()
        compose.onNodeWithText("Disable").performClick()
        assertEquals("telegram" to false, changed)
    }

    @Test
    fun messagingEnableWaitsForConfirmation() {
        var changed: Pair<String, Boolean>? = null
        compose.setContent {
            HermesTheme {
                MessagingScreen(
                    state = HermesState(
                        activeProfile = "work",
                        messagingPlatforms = listOf(MessagingPlatformInfo(id = "telegram", name = "Telegram")),
                    ),
                    onRefresh = {},
                    onSetEnabled = { id, enabled -> changed = id to enabled },
                    onSave = { _, _ -> },
                    onClear = { _, _ -> },
                    onTest = {},
                    onRestartGateway = {},
                    onBack = null,
                )
            }
        }

        compose.onNodeWithText("Telegram").performClick()
        compose.onNodeWithContentDescription("Enable telegram").performClick()
        assertNull(changed)
        compose.onNodeWithText("Enable Telegram for profile work?", substring = true).assertExists()
        compose.onNodeWithText("Enable").performClick()
        assertEquals("telegram" to true, changed)
    }

    @Test
    fun cronMutationsNameProfileAndWaitForConfirmation() {
        var toggled: Pair<String, Boolean>? = null
        var triggered: String? = null
        compose.setContent {
            HermesTheme {
                CronScreen(
                    state = HermesState(
                        activeProfile = "work",
                        cronJobs = listOf(
                            CronJob(
                                id = "daily",
                                name = "Daily brief",
                                enabled = true,
                                scheduleDisplay = "0 9 * * *",
                                prompt = "Prepare brief",
                            ),
                        ),
                    ),
                    onRefresh = {},
                    onSetEnabled = { id, enabled -> toggled = id to enabled },
                    onTrigger = { triggered = it },
                    onLoadRuns = {},
                    onOpenRun = {},
                    onCreate = { _, _, _, _ -> },
                    onUpdate = { _, _, _, _, _ -> },
                    onDelete = {},
                    onBack = null,
                )
            }
        }

        compose.onNodeWithContentDescription("Pause job").performClick()
        assertNull(toggled)
        compose.onNodeWithText("Pause Daily brief for profile work?", substring = true).assertExists()
        compose.onNodeWithText("Pause").performClick()
        assertEquals("daily" to false, toggled)

        compose.onNodeWithContentDescription("Run job now").performClick()
        assertNull(triggered)
        compose.onNodeWithText("Run Daily brief now for profile work?", substring = true).assertExists()
        compose.onNodeWithText("Run now").performClick()
        assertEquals("daily", triggered)
    }

    @Test
    fun cronResumeWaitsForConfirmation() {
        var toggled: Pair<String, Boolean>? = null
        compose.setContent {
            HermesTheme {
                CronScreen(
                    state = HermesState(
                        activeProfile = "work",
                        cronJobs = listOf(CronJob(id = "daily", name = "Daily brief", enabled = false)),
                    ),
                    onRefresh = {},
                    onSetEnabled = { id, enabled -> toggled = id to enabled },
                    onTrigger = {},
                    onLoadRuns = {},
                    onOpenRun = {},
                    onCreate = { _, _, _, _ -> },
                    onUpdate = { _, _, _, _, _ -> },
                    onDelete = {},
                    onBack = null,
                )
            }
        }

        compose.onNodeWithContentDescription("Resume job").performClick()
        assertNull(toggled)
        compose.onNodeWithText("Resume Daily brief for profile work?", substring = true).assertExists()
        compose.onNodeWithText("Resume").performClick()
        assertEquals("daily" to true, toggled)
    }
}

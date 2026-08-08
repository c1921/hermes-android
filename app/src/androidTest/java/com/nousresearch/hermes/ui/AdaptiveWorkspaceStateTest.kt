package com.nousresearch.hermes.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.ui.navigation.HermesDestinationRoute
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AdaptiveWorkspaceStateTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun productionShellPreservesDestinationDraftFocusAndSupportingPaneAcrossMoves() {
        lateinit var expanded: MutableState<Boolean>
        val destination = HermesDestinationRoute.Chats(
            backendId = "backend-intended",
            profileId = "research",
            sessionId = "session-42",
        )
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            expanded = remember { mutableStateOf(false) }
            var draft by rememberSaveable { mutableStateOf("") }
            val focusState = rememberAdaptiveFocusState()
            AdaptiveWorkspaceShell(
                configuration = AdaptiveWorkspaceConfiguration(
                    layout = if (expanded.value) AdaptiveWorkspaceLayout.EXPANDED else AdaptiveWorkspaceLayout.COMPACT,
                    supportsSupportingPane = expanded.value,
                ),
                destination = destination,
                destinations = listOf(destination),
                isListDestination = { false },
                paneModifier = { _, _ -> Modifier.fillMaxSize() },
                expandedNavigation = { Spacer(Modifier.width(120.dp)) },
                modifier = Modifier.fillMaxSize(),
                supportingPaneKey = "tool-output/tool-7",
                supportingPane = {
                    Text("TOOL OUTPUT", Modifier.testTag("supporting-pane"))
                },
            ) { activeDestination, compact ->
                Text(
                    "${activeDestination.backendId}/${activeDestination.profileId}/${activeDestination.sessionId}",
                    Modifier.testTag("route"),
                )
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.testTag("draft").preserveFocusAcrossAdaptiveMove(compact, focusState),
                )
            }
        }

        compose.onNodeWithTag("supporting-pane").assertDoesNotExist()
        compose.onNodeWithTag("draft").performClick().performTextInput("preserved")
        compose.onNodeWithTag("draft").assertIsFocused()

        compose.runOnIdle { expanded.value = true }
        compose.waitUntilAtLeastOneExists(hasTestTag("draft"), 5_000)

        compose.onNodeWithTag("draft").assertTextContains("preserved").assertIsFocused()
        compose.onNodeWithTag("supporting-pane").assertExists()
        compose.onNodeWithTag("route").assertTextContains("backend-intended/research/session-42")

        compose.runOnIdle { expanded.value = false }
        compose.waitUntilAtLeastOneExists(hasTestTag("draft"), 5_000)

        compose.onNodeWithTag("draft").assertTextContains("preserved").assertIsFocused()
        compose.onNodeWithTag("supporting-pane").assertDoesNotExist()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntilAtLeastOneExists(hasTestTag("draft"), 5_000)
        compose.onNodeWithTag("draft").assertTextContains("preserved").assertIsFocused()
        compose.onNodeWithTag("route").assertTextContains("backend-intended/research/session-42")
    }
}

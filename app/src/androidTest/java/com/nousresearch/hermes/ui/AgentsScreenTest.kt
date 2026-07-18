package com.nousresearch.hermes.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.domain.SubagentProgress
import com.nousresearch.hermes.domain.SubagentStatus
import com.nousresearch.hermes.protocol.BackgroundProcess
import com.nousresearch.hermes.protocol.DelegationStatusResponse
import com.nousresearch.hermes.ui.theme.HermesTheme
import org.junit.Rule
import org.junit.Test

class AgentsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun interventionControlsRequireConfirmation() {
        val state = HermesState(
            runtimeSessionId = "runtime-1",
            delegationStatus = DelegationStatusResponse(maxSpawnDepth = 2, maxConcurrentChildren = 4),
            activeSubagents = listOf(
                SubagentProgress(
                    id = "agent-1",
                    goal = "Native Android QA test that Hermes may ignore",
                    status = SubagentStatus.RUNNING,
                    startedAtMillis = 1_000,
                    updatedAtMillis = 2_000,
                ),
            ),
            backgroundProcesses = listOf(
                BackgroundProcess(id = "proc-1", command = "test-only preview server", status = "running"),
            ),
        )
        compose.setContent {
            HermesTheme {
                AgentsScreen(
                    state = state,
                    onRefresh = {},
                    onSetPaused = {},
                    onInterrupt = {},
                    onStopProcess = {},
                    onOpenSession = {},
                    onBack = null,
                )
            }
        }

        compose.onNodeWithText("Interrupt").performClick()
        compose.onNodeWithText("INTERRUPT SUBAGENT?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Stop process").performClick()
        compose.onNodeWithText("STOP BACKGROUND PROCESS?").assertExists()
    }
}

package com.nousresearch.hermes.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.ModelCapabilities
import com.nousresearch.hermes.protocol.ModelOptionsResult
import com.nousresearch.hermes.protocol.ModelProvider
import com.nousresearch.hermes.protocol.SessionRuntimeInfo
import com.nousresearch.hermes.ui.theme.HermesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ModelControlsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun reasoningFastAndYoloUseAdvertisedControlsAndConfirmation() {
        var reasoning: String? = null
        var fast: Boolean? = null
        var yolo: Boolean? = null
        val state = HermesState(
            runtimeSessionId = "runtime-1",
            runtimeInfo = SessionRuntimeInfo(
                model = "hermes-4",
                provider = "nous",
                reasoningEffort = "medium",
                desktopContract = 3,
            ),
            modelOptions = ModelOptionsResult(
                providers = listOf(
                    ModelProvider(
                        slug = "nous",
                        name = "Nous Portal",
                        models = listOf("hermes-4"),
                        capabilities = mapOf("hermes-4" to ModelCapabilities(fast = true, reasoning = true)),
                    ),
                ),
            ),
        )
        compose.setContent {
            HermesTheme {
                ModelControls(
                    state = state,
                    onRefresh = {},
                    onSelect = { _, _ -> },
                    onConfirmModel = {},
                    onCancelModel = {},
                    onReasoning = { reasoning = it },
                    onFast = { fast = it },
                    onYolo = { yolo = it },
                )
            }
        }

        compose.onNodeWithText("medium").performClick()
        compose.onNodeWithText("high").performClick()
        compose.onNodeWithText("Fast").performClick()
        compose.onNodeWithText("YOLO").performClick()
        compose.onNodeWithText("BYPASS APPROVALS?").assertExists()
        compose.runOnIdle {
            assertEquals("high", reasoning)
            assertEquals(true, fast)
            assertNull(yolo)
        }
        compose.onNodeWithText("Enable for session").performClick()
        compose.runOnIdle { assertEquals(true, yolo) }
    }
}

package com.nousresearch.hermes.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import com.nousresearch.hermes.domain.BlockingRequestKind
import com.nousresearch.hermes.domain.TimelineItem
import com.nousresearch.hermes.domain.ToolState
import com.nousresearch.hermes.ui.theme.HermesTheme
import org.junit.Rule
import org.junit.Test

class ToolBlockTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun transcriptIsCollapsedByDefaultAndBeautifiedWhenExpanded() {
        compose.setContent {
            HermesTheme {
                ToolBlock(
                    TimelineItem.Tool(
                        id = "tool-test",
                        name = "terminal",
                        state = ToolState.COMPLETE,
                        detail = """{"output":"first line\nsecond line","exit_code":0}""",
                    ),
                )
            }
        }

        compose.onNodeWithText("TRANSCRIPT").assertDoesNotExist()
        compose.onNodeWithText("first line\nsecond line").assertDoesNotExist()

        compose.onNodeWithContentDescription("Tool usage, Terminal, Terminal completed").performClick()

        compose.onNodeWithText("TRANSCRIPT").assertExists()
        compose.onNodeWithText("first line\nsecond line", substring = true).assertExists()
    }

    @Test
    fun expandedToolCanDriveTheProductionSupportingPane() {
        val tool = TimelineItem.Tool(
            id = "tool-support",
            name = "terminal",
            state = ToolState.COMPLETE,
            detail = """{"output":"support transcript","exit_code":0}""",
        )
        compose.setContent {
            HermesTheme {
                var expanded by remember { mutableStateOf(false) }
                ToolBlock(
                    tool = tool,
                    expanded = expanded,
                    disclosureKey = scopedToolPaneKey("backend", "profile", "session", tool.id),
                    onExpandedChange = { _, nextExpanded -> expanded = nextExpanded },
                )
                if (expanded) {
                    ToolSupportingPane(tool = tool, onClose = { expanded = false })
                }
            }
        }

        compose.onNodeWithContentDescription("Tool usage, Terminal, Terminal completed").performClick()
        compose.onAllNodesWithText("support transcript", substring = true).assertCountEquals(2)
        compose.onNodeWithContentDescription("Close tool transcript").performClick()
        compose.onNodeWithContentDescription("Tool usage, Terminal, Terminal completed").assertExists()
        compose.onAllNodesWithText("support transcript", substring = true).assertCountEquals(0)
    }

    @Test
    fun registryPreservesToolDisclosureAndRendersTypedReference() {
        compose.setContent {
            HermesTheme {
                var expanded by remember { mutableStateOf(false) }
                val context = TimelineRenderContext(
                    speechState = SpeechUiState(),
                    onSpeak = { _, _ -> },
                    onStopSpeaking = {},
                    expandedToolIds = if (expanded) setOf("registry-tool") else emptySet(),
                    toolDisclosureKey = { it.id },
                    onToolExpandedChange = { _, next -> expanded = next },
                )
                Column {
                    TimelineRendererRegistry.Render(
                        TimelineItem.Tool(
                            id = "registry-tool",
                            name = "terminal",
                            state = ToolState.COMPLETE,
                            detail = """{"output":"registry transcript","exit_code":0}""",
                        ),
                        context,
                    )
                    TimelineRendererRegistry.Render(
                        TimelineItem.Reference("registry-reference", "Model B", "Alternative answer"),
                        context,
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Tool usage, Terminal, Terminal completed").performClick()
        compose.onNodeWithText("registry transcript", substring = true).assertExists()
        compose.onNodeWithText("REFERENCE · Model B").assertExists()
        compose.onNodeWithText("Alternative answer").assertExists()
    }

    @Test
    fun registryRendersMixedTypedPartsWithAccessibleFallbacks() {
        compose.setContent {
            HermesTheme {
                val context = TimelineRenderContext(
                    speechState = SpeechUiState(),
                    onSpeak = { _, _ -> },
                    onStopSpeaking = {},
                    expandedToolIds = emptySet(),
                    toolDisclosureKey = { it.id },
                    onToolExpandedChange = null,
                )
                Column {
                    TimelineRendererRegistry.Render(
                        TimelineItem.Artifact("artifact", "report.md", "text/markdown", "artifact-7", "Report"),
                        context,
                    )
                    TimelineRendererRegistry.Render(TimelineItem.Error("error", "Connection reset", true), context)
                    TimelineRendererRegistry.Render(
                        TimelineItem.BlockingRequest(
                            "request",
                            BlockingRequestKind.CLARIFICATION,
                            "Which branch?",
                        ),
                        context,
                    )
                    TimelineRendererRegistry.Render(
                        TimelineItem.Unknown("unknown", "future", "Unsupported future part"),
                        context,
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Artifact, report.md").assertExists()
        compose.onNodeWithText("Report · text/markdown · artifact-7").assertExists()
        compose.onNodeWithContentDescription("Hermes error").assertExists()
        compose.onNodeWithContentDescription("Action required, clarification").assertExists()
        compose.onNodeWithContentDescription("Unsupported Hermes conversation part").assertExists()
        compose.onNodeWithText("Unsupported future part").assertExists()
    }
}

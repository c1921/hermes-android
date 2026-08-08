package com.nousresearch.hermes.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                var supportingTool by remember { mutableStateOf<TimelineItem.Tool?>(null) }
                if (supportingTool == null) {
                    ToolBlock(tool, onSupportingToolChange = { supportingTool = it })
                } else {
                    ToolSupportingPane(tool = requireNotNull(supportingTool), onClose = { supportingTool = null })
                }
            }
        }

        compose.onNodeWithContentDescription("Tool usage, Terminal, Terminal completed").performClick()
        compose.onNodeWithText("support transcript", substring = true).assertExists()
        compose.onNodeWithContentDescription("Close tool transcript").performClick()
        compose.onNodeWithContentDescription("Tool usage, Terminal, Terminal completed").assertExists()
    }
}

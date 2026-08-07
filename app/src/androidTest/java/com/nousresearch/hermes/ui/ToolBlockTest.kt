package com.nousresearch.hermes.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}

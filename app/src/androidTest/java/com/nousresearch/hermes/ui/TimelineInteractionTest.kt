package com.nousresearch.hermes.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.nousresearch.hermes.domain.MessageRole
import com.nousresearch.hermes.domain.TimelineItem
import com.nousresearch.hermes.ui.theme.HermesTheme
import org.junit.Rule
import org.junit.Test

class TimelineInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun jumpToLatestReturnsToTheNewestMessageAfterReadingEarlierContent() {
        val items = List(30) { index ->
            TimelineItem.Message(
                id = "message-$index",
                role = MessageRole.ASSISTANT,
                text = "message-$index",
            )
        }

        compose.setContent {
            HermesTheme {
                Timeline(
                    items = items,
                    speechState = SpeechUiState(),
                    onSpeak = { _, _ -> },
                    onStopSpeaking = {},
                    expandedToolIds = emptySet(),
                    toolDisclosureKey = { it.id },
                    onToolExpandedChange = null,
                    focusMessageId = null,
                )
            }
        }

        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("Jump to latest message").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Jump to latest message").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("message-29").assertIsDisplayed()
    }
}

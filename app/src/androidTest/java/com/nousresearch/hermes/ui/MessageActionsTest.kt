package com.nousresearch.hermes.ui

import android.content.ClipboardManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.nousresearch.hermes.domain.MessageRole
import com.nousresearch.hermes.domain.TimelineItem
import com.nousresearch.hermes.ui.theme.HermesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MessageActionsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun completedMessageCanBeCopiedAndAdvertisesShare() {
        val text = "A completed Hermes reply"
        compose.setContent {
            HermesTheme {
                MessageBlock(
                    message = TimelineItem.Message("reply-1", MessageRole.ASSISTANT, text),
                    speechState = SpeechUiState(),
                    onSpeak = {},
                    onStopSpeaking = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Copy message").performClick()
        compose.onNodeWithContentDescription("Copied message").assertExists()
        compose.onNodeWithContentDescription("Share message").assertExists()
        val clipboard = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(ClipboardManager::class.java)
        assertEquals(text, clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test
    fun completedAssistantMessageRendersMarkdown() {
        compose.setContent {
            HermesTheme {
                RichText(
                    "# Rendered heading\n\n- First item\n- Second item\n\n```kotlin\nval answer = 42\n```",
                    markdown = true,
                )
            }
        }

        compose.waitForIdle()
        assertRenderedText("Rendered heading")
        assertRenderedText("First item")
        assertRenderedText("val answer = 42", substring = true)
    }

    private fun assertRenderedText(text: String, substring: Boolean = false) {
        compose.waitUntil(timeoutMillis = 10_000) {
            val merged = compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes()
            val unmerged = compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes()
            merged.isNotEmpty() || unmerged.isNotEmpty()
        }
        val merged = compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes()
        val unmerged = compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("Expected rendered text '$text'", merged.isNotEmpty() || unmerged.isNotEmpty())
    }
}

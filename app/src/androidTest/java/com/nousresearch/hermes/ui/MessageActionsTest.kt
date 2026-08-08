package com.nousresearch.hermes.ui

import android.content.ClipboardManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.nousresearch.hermes.domain.MessageRole
import com.nousresearch.hermes.domain.TimelineItem
import com.nousresearch.hermes.ui.theme.HermesTheme
import org.junit.Assert.assertEquals
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
        compose.onNodeWithText("Rendered heading", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("First item", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("val answer = 42", substring = true, useUnmergedTree = true).assertExists()
    }
}

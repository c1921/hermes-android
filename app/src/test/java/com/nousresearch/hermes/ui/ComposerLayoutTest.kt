package com.nousresearch.hermes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.ui.theme.HermesTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComposerLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sendingComposerKeepsUsableTextWidthWithAttachmentsEnabled() {
        compose.setContent {
            HermesTheme {
                Surface(Modifier.width(412.dp).testTag("composer-root")) {
                    Box(Modifier.fillMaxWidth().padding(12.dp)) {
                        ComposerInputLayout(
                            sending = true,
                            input = {
                                repeat(3) { Box(Modifier.size(48.dp)) }
                                OutlinedTextField(
                                    value = "Draft message",
                                    onValueChange = {},
                                    modifier = Modifier.weight(1f).testTag("composer-input"),
                                    trailingIcon = { VoiceInputTestIcon() },
                                )
                            },
                            actions = {
                                repeat(3) { index -> Box(Modifier.size(48.dp).testTag("composer-action-$index")) }
                            },
                        )
                    }
                }
            }
        }

        compose.waitForIdle()
        val rootWidth = bounds("composer-root").width
        val inputWidth = bounds("composer-input").width

        assertTrue(
            "Expected at least 120dp of text input width, got ${inputWidth / rootWidth * 412f}dp",
            inputWidth >= rootWidth * 120f / 412f,
        )
        repeat(3) { index ->
            val action = bounds("composer-action-$index")
            assertTrue("Composer action $index is clipped", action.left >= 0f && action.right <= rootWidth)
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Composable
    private fun VoiceInputTestIcon() {
        Box(Modifier.size(48.dp))
    }
}

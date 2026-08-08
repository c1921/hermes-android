package com.nousresearch.hermes.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerKeyPolicyTest {
    @Test
    fun escapeDismissesWithoutReplacingHistoryNavigation() {
        assertEquals(
            ComposerKeyAction.ESCAPE,
            composerKeyAction(KeyEventType.KeyDown, Key.Escape, ctrlPressed = false),
        )
        assertEquals(
            ComposerKeyAction.ESCAPE,
            composerKeyAction(KeyEventType.KeyDown, Key.Escape, ctrlPressed = true),
        )
        assertEquals(
            ComposerKeyAction.NONE,
            composerKeyAction(KeyEventType.KeyUp, Key.Escape, ctrlPressed = false),
        )
    }

    @Test
    fun ctrlArrowsContinueToBrowseHistory() {
        assertEquals(
            ComposerKeyAction.HISTORY_BACK,
            composerKeyAction(KeyEventType.KeyDown, Key.DirectionUp, ctrlPressed = true),
        )
        assertEquals(
            ComposerKeyAction.HISTORY_FORWARD,
            composerKeyAction(KeyEventType.KeyDown, Key.DirectionDown, ctrlPressed = true),
        )
        assertEquals(
            ComposerKeyAction.NONE,
            composerKeyAction(KeyEventType.KeyDown, Key.DirectionUp, ctrlPressed = false),
        )
    }
}

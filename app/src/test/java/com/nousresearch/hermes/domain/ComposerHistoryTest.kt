package com.nousresearch.hermes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerHistoryTest {
    @Test
    fun `derives newest first nonblank user messages without a parallel store`() {
        val timeline = TimelineState(
            items = listOf(
                TimelineItem.Message("u1", MessageRole.USER, " first "),
                TimelineItem.Message("a1", MessageRole.ASSISTANT, "answer"),
                TimelineItem.Tool("t1", "terminal", state = ToolState.COMPLETE),
                TimelineItem.Message("u2", MessageRole.USER, "second"),
                TimelineItem.Message("u3", MessageRole.USER, "   "),
            ),
        )

        assertEquals(listOf("second", "first"), ComposerHistory.derive(timeline))
    }

    @Test
    fun `backward browsing snapshots draft and stops at oldest`() {
        val history = listOf("newest", "older")
        val first = requireNotNull(ComposerHistory.backward(ComposerBrowseState(), "unfinished draft", history))
        val second = requireNotNull(ComposerHistory.backward(first.state, first.text, history))
        val exhausted = ComposerHistory.backward(second.state, second.text, history)

        assertEquals("newest", first.text)
        assertEquals("older", second.text)
        assertFalse(first.returnedToPresent)
        assertNull(exhausted)
    }

    @Test
    fun `forward browsing restores the exact draft at present`() {
        val history = listOf("newest", "older")
        val first = requireNotNull(ComposerHistory.backward(ComposerBrowseState(), "draft  ", history))
        val second = requireNotNull(ComposerHistory.backward(first.state, first.text, history))
        val newer = requireNotNull(ComposerHistory.forward(second.state, history))
        val present = requireNotNull(ComposerHistory.forward(newer.state, history))

        assertEquals("newest", newer.text)
        assertEquals("draft  ", present.text)
        assertTrue(present.returnedToPresent)
        assertEquals(-1, present.state.cursor)
    }

    @Test
    fun `invalid session state and empty history are no ops`() {
        assertNull(ComposerHistory.backward(ComposerBrowseState(), "draft", emptyList()))
        assertNull(ComposerHistory.forward(ComposerBrowseState(), listOf("message")))
    }
}

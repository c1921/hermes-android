package com.nousresearch.hermes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerQueueTest {
    @Test
    fun `enqueue preserves fifo and bounds text`() {
        val first = QueuedPrompt("one", " first ", 10)
        val second = QueuedPrompt("two", "second", 20)

        val queue = ComposerQueue.enqueue(emptyList(), first)
            .let { ComposerQueue.enqueue(it, second) }

        assertEquals(listOf("first", "second"), queue.map(QueuedPrompt::text))
    }

    @Test
    fun `queue refuses blank duplicate ids and overflow`() {
        val existing = (0 until ComposerQueue.MAX_ENTRIES).map {
            QueuedPrompt("id-$it", "message $it", it.toLong())
        }

        assertNull(ComposerQueue.tryEnqueue(emptyList(), QueuedPrompt("blank", "  ", 50)))
        assertNull(ComposerQueue.tryEnqueue(existing.take(1), QueuedPrompt("id-0", "duplicate", 51)))
        assertNull(ComposerQueue.tryEnqueue(existing, QueuedPrompt("overflow", "full", 52)))
    }

    @Test
    fun `edit resets failures without changing fifo position`() {
        val queue = listOf(
            QueuedPrompt("one", "first", 10, autoDrainFailures = 3),
            QueuedPrompt("two", "second", 20),
        )

        val edited = ComposerQueue.updateText(queue, "one", " revised ")

        assertEquals(listOf("one", "two"), edited.map(QueuedPrompt::id))
        assertEquals("revised", edited.first().text)
        assertEquals(0, edited.first().autoDrainFailures)
    }

    @Test
    fun `remove and promote preserve every other entry`() {
        val queue = listOf(
            QueuedPrompt("one", "first", 10),
            QueuedPrompt("two", "second", 20),
            QueuedPrompt("three", "third", 30),
        )

        val promoted = ComposerQueue.promote(queue, "three")
        val removed = ComposerQueue.remove(promoted, "two")

        assertEquals(listOf("three", "one"), removed.map(QueuedPrompt::id))
    }

    @Test
    fun `failed head stops automatic drain after bounded attempts`() {
        var queue = listOf(QueuedPrompt("one", "first", 10))
        repeat(ComposerQueue.MAX_AUTO_DRAIN_ATTEMPTS) {
            queue = ComposerQueue.markAutoDrainFailure(queue, "one")
        }

        assertFalse(ComposerQueue.shouldAutoDrain(isBusy = false, queue = queue))
        assertTrue(ComposerQueue.shouldAutoDrain(isBusy = false, queue = ComposerQueue.resetFailures(queue, "one")))
        assertFalse(ComposerQueue.shouldAutoDrain(isBusy = true, queue = queue))
        assertFalse(ComposerQueue.shouldAutoDrain(isBusy = false, queue = emptyList()))
    }
}

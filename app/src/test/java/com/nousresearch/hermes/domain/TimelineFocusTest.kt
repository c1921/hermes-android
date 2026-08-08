package com.nousresearch.hermes.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineFocusTest {
    @Test
    fun `message focus resolves direct and child timeline identities`() {
        val items = listOf(
            TimelineItem.Message(
                id = "message-1",
                role = MessageRole.ASSISTANT,
                text = "Direct",
                identity = TimelineIdentity.server("message-1"),
            ),
            TimelineItem.Message(
                id = "message-2:part:0",
                role = MessageRole.ASSISTANT,
                text = "Child",
                identity = TimelineIdentity.fallback("message-2:part:0", parentServerId = "message-2"),
            ),
        )

        assertEquals(0, items.indexOfServerMessage("message-1"))
        assertEquals(1, items.indexOfServerMessage("message-2"))
        assertEquals(-1, items.indexOfServerMessage("missing"))
    }
}

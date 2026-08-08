package com.nousresearch.hermes.ui

import com.nousresearch.hermes.protocol.StarmapNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarmapPolicyTest {
    private val node = StarmapNode(
        id = "skill:review",
        label = "Review changes",
        kind = "skill",
        category = "workflow",
        useCount = 4,
        state = "active",
        createdBy = "agent",
        pinned = true,
    )

    @Test
    fun `starmap search matches visible bounded metadata`() {
        assertTrue(starmapMatches(node, ""))
        assertTrue(starmapMatches(node, "review"))
        assertTrue(starmapMatches(node, "WORKFLOW"))
        assertTrue(starmapMatches(node, "agent"))
        assertFalse(starmapMatches(node, "memory"))
    }
}

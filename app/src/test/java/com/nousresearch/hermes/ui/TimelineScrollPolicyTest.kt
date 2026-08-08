package com.nousresearch.hermes.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineScrollPolicyTest {
    @Test
    fun `timeline follows only when the reader is near the latest item`() {
        assertTrue(timelineIsNearLatest(lastVisibleIndex = null, totalItems = 0))
        assertTrue(timelineIsNearLatest(lastVisibleIndex = 8, totalItems = 10))
        assertTrue(timelineIsNearLatest(lastVisibleIndex = 9, totalItems = 10))
        assertFalse(timelineIsNearLatest(lastVisibleIndex = 7, totalItems = 10))
    }
}

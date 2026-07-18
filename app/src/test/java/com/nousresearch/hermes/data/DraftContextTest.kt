package com.nousresearch.hermes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftContextTest {
    @Test
    fun `storage key is stable without exposing backend profile or session names`() {
        val first = DraftContext("private-backend", "work-profile", "sensitive-session").storageKey
        val repeated = DraftContext("private-backend", "work-profile", "sensitive-session").storageKey

        assertEquals(first, repeated)
        assertTrue(first.startsWith("draft.v1."))
        assertTrue("private-backend" !in first)
        assertTrue("work-profile" !in first)
        assertTrue("sensitive-session" !in first)
    }

    @Test
    fun `new and persisted session drafts remain isolated`() {
        val fresh = DraftContext("backend", "default", null).storageKey
        val sessionNamedNew = DraftContext("backend", "default", "new").storageKey
        val stored = DraftContext("backend", "default", "session-1").storageKey
        val otherProfile = DraftContext("backend", "research", null).storageKey

        assertNotEquals(fresh, stored)
        assertNotEquals(fresh, sessionNamedNew)
        assertNotEquals(fresh, otherProfile)
    }
}

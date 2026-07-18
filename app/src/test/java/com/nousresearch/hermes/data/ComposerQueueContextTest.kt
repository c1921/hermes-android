package com.nousresearch.hermes.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerQueueContextTest {
    @Test
    fun `storage key isolates backend profile and session without exposing identifiers`() {
        val base = ComposerQueueContext("backend-secret", "default", "session-alpha")

        assertNotEquals(base.storageKey, ComposerQueueContext("other-backend", "default", "session-alpha").storageKey)
        assertNotEquals(base.storageKey, ComposerQueueContext("backend-secret", "work", "session-alpha").storageKey)
        assertNotEquals(base.storageKey, ComposerQueueContext("backend-secret", "default", "session-beta").storageKey)
        assertTrue(base.storageKey.startsWith(base.backendPrefix))
        assertFalse(base.storageKey.contains("backend-secret"))
        assertFalse(base.storageKey.contains("session-alpha"))
    }
}

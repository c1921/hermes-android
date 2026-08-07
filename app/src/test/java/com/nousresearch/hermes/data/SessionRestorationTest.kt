package com.nousresearch.hermes.data

import com.nousresearch.hermes.protocol.StoredSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRestorationTest {
    @Test
    fun `durable target resolves only when backend profile and session are authoritative`() {
        val target = SessionTarget("backend-1", "work", "stored-1")

        val result = resolveSessionTarget(
            target = target,
            availableBackendIds = setOf("backend-1"),
            authenticatedBackendId = "backend-1",
            sessions = listOf(StoredSession(sessionId = "stored-1", profile = "work")),
        )

        assertEquals(SessionRestorationStatus.READY, result.status)
        assertEquals("stored-1", result.session?.durableId)
        assertTrue(result.mutationsEnabled)
    }

    @Test
    fun `missing backend and expired auth are deterministic read only recovery states`() {
        val target = SessionTarget("backend-1", "default", "stored-1")

        val missingBackend = resolveSessionTarget(target, emptySet(), null, emptyList())
        assertEquals(SessionRestorationStatus.BACKEND_UNAVAILABLE, missingBackend.status)
        assertFalse(missingBackend.mutationsEnabled)

        val expired = resolveSessionTarget(target, setOf("backend-1"), null, emptyList())
        assertEquals(SessionRestorationStatus.AUTHENTICATION_REQUIRED, expired.status)
        assertFalse(expired.mutationsEnabled)
    }

    @Test
    fun `deleted and profile mismatched sessions never become active`() {
        val deleted = resolveSessionTarget(
            SessionTarget("backend-1", "work", "missing"),
            setOf("backend-1"),
            "backend-1",
            listOf(StoredSession(sessionId = "other", profile = "work")),
        )
        assertEquals(SessionRestorationStatus.SESSION_UNAVAILABLE, deleted.status)

        val mismatched = resolveSessionTarget(
            SessionTarget("backend-1", "work", "stored-1"),
            setOf("backend-1"),
            "backend-1",
            listOf(StoredSession(sessionId = "stored-1", profile = "default")),
        )
        assertEquals(SessionRestorationStatus.PROFILE_MISMATCH, mismatched.status)
        assertFalse(mismatched.mutationsEnabled)
    }

    @Test
    fun `rehydration rejects stale and unscoped runtime events`() {
        assertFalse(shouldAcceptRuntimeEvent(SessionRestorationStatus.IDLE, null, "runtime-old"))
        assertFalse(shouldAcceptRuntimeEvent(SessionRestorationStatus.AUTHENTICATING, null, "runtime-old"))
        assertFalse(shouldAcceptRuntimeEvent(SessionRestorationStatus.AUTHENTICATION_REQUIRED, null, "runtime-old"))
        assertFalse(shouldAcceptRuntimeEvent(SessionRestorationStatus.BACKEND_UNAVAILABLE, null, "runtime-old"))
        assertFalse(shouldAcceptRuntimeEvent(SessionRestorationStatus.RECOVERY_REQUIRED, "runtime-old", "runtime-old"))
        assertFalse(shouldAcceptRuntimeEvent(SessionRestorationStatus.SESSION_UNAVAILABLE, "runtime-old", "runtime-old"))
        assertFalse(shouldAcceptRuntimeEvent(SessionRestorationStatus.PROFILE_MISMATCH, "runtime-old", "runtime-old"))
        assertFalse(shouldAcceptRuntimeEvent(SessionRestorationStatus.REHYDRATING, null, "runtime-old"))
        assertFalse(shouldAcceptRuntimeEvent(SessionRestorationStatus.REHYDRATING, "runtime-new", "runtime-old"))
        assertFalse(shouldAcceptRuntimeEvent(SessionRestorationStatus.REHYDRATING, "runtime-new", null))
        assertTrue(shouldAcceptRuntimeEvent(SessionRestorationStatus.REHYDRATING, "runtime-new", "runtime-new"))
        assertFalse(shouldAcceptRuntimeEvent(SessionRestorationStatus.READY, null, "runtime-old"))
        assertFalse(shouldAcceptRuntimeEvent(SessionRestorationStatus.READY, "runtime-new", null))
        assertTrue(shouldAcceptRuntimeEvent(SessionRestorationStatus.READY, "runtime-new", "runtime-new"))
    }

    @Test
    fun `ready state keeps session scoped subagent activity without accepting it into the active timeline`() {
        assertTrue(shouldAcceptSubagentEvent(SessionRestorationStatus.READY, "runtime-active", "runtime-background"))
        assertFalse(shouldAcceptRuntimeEvent(SessionRestorationStatus.READY, "runtime-active", "runtime-background"))
        assertFalse(shouldAcceptSubagentEvent(SessionRestorationStatus.RECOVERY_REQUIRED, null, "runtime-old"))
    }

    @Test
    fun `only authenticated restoration and recovery states allow recovery requests`() {
        assertFalse(SessionRestorationStatus.AUTHENTICATING.allowsRecoveryRequest())
        assertFalse(SessionRestorationStatus.AUTHENTICATION_REQUIRED.allowsRecoveryRequest())
        assertFalse(SessionRestorationStatus.BACKEND_UNAVAILABLE.allowsRecoveryRequest())
        assertFalse(SessionRestorationStatus.REHYDRATING.allowsRecoveryRequest())
        assertTrue(SessionRestorationStatus.SESSION_UNAVAILABLE.allowsRecoveryRequest())
        assertTrue(SessionRestorationStatus.PROFILE_MISMATCH.allowsRecoveryRequest())
    }
}

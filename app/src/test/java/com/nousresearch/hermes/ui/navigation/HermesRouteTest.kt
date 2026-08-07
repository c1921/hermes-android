package com.nousresearch.hermes.ui.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesRouteTest {
    @Test
    fun `conversation restoration contains durable identity and no runtime or private payload`() {
        val encoded = Json.encodeToString<HermesRoute>(
            HermesRoute.Conversation(
                backendId = "backend-1",
                profileId = "research",
                sessionId = "stored-session-1",
            ),
        )

        assertTrue(encoded.contains("backend-1"))
        assertTrue(encoded.contains("stored-session-1"))
        listOf("token", "password", "runtime", "transcript", "attachment", "share").forEach { forbidden ->
            assertFalse(encoded.lowercase().contains(forbidden))
        }
    }

    @Test
    fun `files route retains only backend and genuine resource path`() {
        val encoded = Json.encodeToString<HermesRoute>(
            HermesRoute.Files(backendId = "backend-1", profileId = "default", path = "/workspace/report.md"),
        )

        assertTrue(encoded.contains("backend-1"))
        assertTrue(encoded.contains("report.md"))
        assertFalse(encoded.contains("runtime"))
    }

    @Test
    fun `missing backend restoration recovers to backend picker with explanation`() {
        val result = resolveRestoredRoute(
            route = HermesRoute.Conversation("missing", "default", "session-1"),
            availableBackendIds = setOf("backend-1"),
            authenticatedBackendId = "backend-1",
            authoritativeSessions = setOf(SessionIdentity("backend-1", "default", "session-1")),
        )

        assertEquals(HermesRoute.BackendPicker(), result.route)
        assertTrue(result.explanation.orEmpty().contains("no longer available"))
        assertFalse(result.mutationsEnabled)
    }

    @Test
    fun `expired authentication restores to backend picker without enabling mutations`() {
        val result = resolveRestoredRoute(
            route = HermesRoute.Files("backend-1", "default", "/workspace"),
            availableBackendIds = setOf("backend-1"),
            authenticatedBackendId = null,
            authoritativeSessions = emptySet(),
        )

        assertEquals(
            HermesRoute.BackendPicker(returnBackendId = "backend-1", profileId = "default"),
            result.route,
        )
        assertTrue(result.explanation.orEmpty().contains("Reconnect"))
        assertFalse(result.mutationsEnabled)
    }

    @Test
    fun `authentication recovery keeps the intended backend and profile`() {
        val result = resolveRestoredRoute(
            route = HermesRoute.Conversation("backend-intended", "research", "stored-session"),
            availableBackendIds = setOf("backend-intended", "backend-current"),
            authenticatedBackendId = "backend-current",
            authoritativeSessions = emptySet(),
        )

        assertEquals(
            HermesRoute.BackendPicker(returnBackendId = "backend-intended", profileId = "research"),
            result.route,
        )
        assertFalse(result.mutationsEnabled)
    }

    @Test
    fun `missing conversation restores to atlas with explanation`() {
        val result = resolveRestoredRoute(
            route = HermesRoute.Conversation("backend-1", "default", "missing-session"),
            availableBackendIds = setOf("backend-1"),
            authenticatedBackendId = "backend-1",
            authoritativeSessions = emptySet(),
        )

        assertEquals(HermesRoute.SessionAtlas("backend-1", "default"), result.route)
        assertTrue(result.explanation.orEmpty().contains("could not be found"))
        assertFalse(result.mutationsEnabled)
    }

    @Test
    fun `rehydrated conversation enables mutations only after durable session is authoritative`() {
        val route = HermesRoute.Conversation("backend-1", "default", "session-1")
        val result = resolveRestoredRoute(
            route = route,
            availableBackendIds = setOf("backend-1"),
            authenticatedBackendId = "backend-1",
            authoritativeSessions = setOf(SessionIdentity("backend-1", "default", "session-1")),
        )

        assertEquals(route, result.route)
        assertTrue(result.mutationsEnabled)
    }

    @Test
    fun `restored conversation stays read only while runtime belongs to another session`() {
        val route = HermesRoute.Conversation("backend-1", "default", "session-1")

        assertFalse(
            conversationMutationsEnabled(
                route = route,
                activeBackendId = "backend-1",
                activeSession = SessionIdentity("backend-1", "default", "session-1"),
                runtimeStoredSessionId = "previous-session",
                runtimeSessionId = "runtime-previous",
            ),
        )
        assertTrue(
            conversationMutationsEnabled(
                route = route,
                activeBackendId = "backend-1",
                activeSession = SessionIdentity("backend-1", "default", "session-1"),
                runtimeStoredSessionId = "session-1",
                runtimeSessionId = "runtime-current",
            ),
        )
    }
}

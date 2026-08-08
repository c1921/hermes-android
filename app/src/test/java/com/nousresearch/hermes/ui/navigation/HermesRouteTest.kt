package com.nousresearch.hermes.ui.navigation

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesRouteTest {
    @Test
    fun `product destinations are authoritative Hermes routes`() {
        val routes = listOf<HermesRoute>(
            HermesDestinationRoute.Chats("backend-1", "default"),
            HermesDestinationRoute.Artifacts("backend-1", "default"),
            HermesDestinationRoute.Automations("backend-1", "default"),
            HermesDestinationRoute.Manage("backend-1", "default"),
            HermesDestinationRoute.AppSettings(),
        )

        routes.forEach { route ->
            val encoded = Json.encodeToString<HermesRoute>(route)
            assertTrue(encoded.contains(route::class.simpleName.orEmpty()))
        }
    }

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
    fun `chat message origin survives process restoration serialization`() {
        val route: HermesRoute = HermesDestinationRoute.Chats(
            backendId = "backend-1",
            profileId = "research",
            sessionId = "session-1",
            messageId = "message-42",
        )
        val encoded = Json.encodeToString<HermesRoute>(route)

        assertEquals(route, Json.decodeFromString<HermesRoute>(encoded))
    }

    @Test
    fun `selected artifact survives process restoration serialization`() {
        val route: HermesRoute = HermesDestinationRoute.Artifacts(
            backendId = "backend-1",
            profileId = "research",
            artifactId = "artifact-42",
        )

        assertEquals(route, Json.decodeFromString<HermesRoute>(Json.encodeToString(route)))
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
    fun `missing product chat restores to chats home with explanation`() {
        val result = resolveRestoredRoute(
            route = HermesDestinationRoute.Chats("backend-1", "default", "missing-session"),
            availableBackendIds = setOf("backend-1"),
            authenticatedBackendId = "backend-1",
            authoritativeSessions = emptySet(),
        )

        assertEquals(HermesDestinationRoute.Chats("backend-1", "default"), result.route)
        assertTrue(result.explanation.orEmpty().contains("could not be found"))
        assertFalse(result.mutationsEnabled)
    }

    @Test
    fun `every remote product route preserves backend and profile through authentication recovery`() {
        val routes = listOf<HermesRoute>(
            HermesDestinationRoute.Chats("backend-intended", "research"),
            HermesDestinationRoute.Artifacts("backend-intended", "research", artifactId = "artifact-1"),
            HermesDestinationRoute.Automations(
                "backend-intended",
                "research",
                AutomationDestination.COMMAND_CENTER,
                "run-1",
            ),
            HermesDestinationRoute.Manage(
                "backend-intended",
                "research",
                ManageSection.MEMORY_AND_LEARNING,
                ManageDestination.STARMAP,
                "node-1",
            ),
        )

        routes.forEach { route ->
            val result = resolveRestoredRoute(
                route = route,
                availableBackendIds = setOf("backend-intended"),
                authenticatedBackendId = null,
                authoritativeSessions = emptySet(),
            )
            assertEquals(
                HermesRoute.BackendPicker("backend-intended", "research"),
                result.route,
            )
            assertFalse(result.mutationsEnabled)
        }
    }

    @Test
    fun `device local settings do not require backend restoration`() {
        val route = HermesDestinationRoute.AppSettings(AppSettingsSection.APPEARANCE)

        val result = resolveRestoredRoute(
            route = route,
            availableBackendIds = emptySet(),
            authenticatedBackendId = null,
            authoritativeSessions = emptySet(),
        )

        assertEquals(route, result.route)
        assertFalse(result.mutationsEnabled)
    }

    @Test
    fun `system entry with unknown profile falls back without enabling mutations`() {
        val result = resolveEntryDestination(
            route = HermesDestinationRoute.Automations(
                backendId = "backend-1",
                profileId = "forged-profile",
                destination = AutomationDestination.CRON,
                resourceId = "job-1",
            ),
            availableBackendIds = setOf("backend-1"),
            authenticatedBackendId = "backend-1",
            authoritativeSessions = emptySet(),
            authoritativeProfileIds = setOf("default"),
            fallbackProfileId = "default",
            authoritativeAutomationResources = setOf(
                AutomationResourceIdentity(AutomationDestination.CRON, "job-1"),
            ),
        )

        assertEquals(HermesDestinationRoute.Chats("backend-1", "default"), result.route)
        assertTrue(result.explanation.orEmpty().contains("profile"))
        assertFalse(result.mutationsEnabled)
    }

    @Test
    fun `system entry accepts only an automation resource owned by its destination`() {
        val known = setOf(AutomationResourceIdentity(AutomationDestination.CRON, "job-1"))
        val valid = resolveEntryDestination(
            route = HermesDestinationRoute.Automations(
                "backend-1",
                "default",
                AutomationDestination.CRON,
                "job-1",
            ),
            availableBackendIds = setOf("backend-1"),
            authenticatedBackendId = "backend-1",
            authoritativeSessions = emptySet(),
            authoritativeProfileIds = setOf("default"),
            fallbackProfileId = "default",
            authoritativeAutomationResources = known,
        )
        val forgedDestination = resolveEntryDestination(
            route = HermesDestinationRoute.Automations(
                "backend-1",
                "default",
                AutomationDestination.COMMAND_CENTER,
                "job-1",
            ),
            availableBackendIds = setOf("backend-1"),
            authenticatedBackendId = "backend-1",
            authoritativeSessions = emptySet(),
            authoritativeProfileIds = setOf("default"),
            fallbackProfileId = "default",
            authoritativeAutomationResources = known,
        )

        assertEquals(
            HermesDestinationRoute.Automations(
                "backend-1",
                "default",
                AutomationDestination.CRON,
                "job-1",
            ),
            valid.route,
        )
        assertTrue(valid.mutationsEnabled)
        assertEquals(
            HermesDestinationRoute.Automations(
                "backend-1",
                "default",
                AutomationDestination.COMMAND_CENTER,
            ),
            forgedDestination.route,
        )
        assertFalse(forgedDestination.mutationsEnabled)
    }

    @Test
    fun `unverifiable artifact and management resources are removed`() {
        val routes = listOf(
            HermesDestinationRoute.Artifacts("backend-1", "default", artifactId = "artifact-1"),
            HermesDestinationRoute.Manage(
                "backend-1",
                "default",
                ManageSection.MEMORY_AND_LEARNING,
                ManageDestination.STARMAP,
                "node-1",
            ),
        )

        routes.forEach { route ->
            val result = resolveEntryDestination(
                route = route,
                availableBackendIds = setOf("backend-1"),
                authenticatedBackendId = "backend-1",
                authoritativeSessions = emptySet(),
                authoritativeProfileIds = setOf("default"),
                fallbackProfileId = "default",
                authoritativeAutomationResources = emptySet(),
            )

            assertFalse(result.mutationsEnabled)
            assertTrue(result.explanation.orEmpty().contains("could not be verified"))
            assertEquals(null, (result.route as HermesDestinationRoute).resourceIdForTest())
        }
    }

    @Test
    fun `external chat message reference is stripped while verified session remains`() {
        val result = resolveEntryDestination(
            route = HermesDestinationRoute.Chats(
                backendId = "backend-1",
                profileId = "default",
                sessionId = "session-1",
                messageId = "message-42",
            ),
            availableBackendIds = setOf("backend-1"),
            authenticatedBackendId = "backend-1",
            authoritativeSessions = setOf(SessionIdentity("backend-1", "default", "session-1")),
            authoritativeProfileIds = setOf("default"),
            fallbackProfileId = "default",
            authoritativeAutomationResources = emptySet(),
        )

        assertEquals(
            HermesDestinationRoute.Chats("backend-1", "default", "session-1"),
            result.route,
        )
        assertTrue(result.explanation.orEmpty().contains("message"))
        assertTrue(result.mutationsEnabled)
    }

    @Test
    fun `verified internal chat route preserves its message origin`() {
        val route = HermesDestinationRoute.Chats(
            backendId = "backend-1",
            profileId = "default",
            sessionId = "session-1",
            messageId = "message-42",
        )

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

private fun HermesDestinationRoute.resourceIdForTest(): String? = when (this) {
    is HermesDestinationRoute.Artifacts -> artifactId ?: filePath
    is HermesDestinationRoute.Automations -> resourceId
    is HermesDestinationRoute.Manage -> resourceId
    is HermesDestinationRoute.Chats,
    is HermesDestinationRoute.AppSettings,
    -> null
}

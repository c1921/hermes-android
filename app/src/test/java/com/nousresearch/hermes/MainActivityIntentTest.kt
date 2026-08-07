package com.nousresearch.hermes

import com.nousresearch.hermes.ui.navigation.HermesDestinationRoute
import com.nousresearch.hermes.ui.navigation.AutomationDestination
import com.nousresearch.hermes.ui.navigation.ManageSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityIntentTest {
    @Test
    fun `only ACTION_VIEW accepts a valid Hermes destination`() {
        assertEquals(
            HermesDestinationRoute.Chats("personal", "default", "session-1"),
            parseHermesDestination(
                "android.intent.action.VIEW",
                "hermes://chats?backend=personal&profile=default&session=session-1",
            ),
        )
    }

    @Test
    fun `share intents remain outside deep link intake`() {
        assertNull(
            parseHermesDestination(
                "android.intent.action.SEND",
                "hermes://chats?backend=personal&profile=default",
            ),
        )
    }

    @Test
    fun `deep link intake rejects non Hermes and malformed destinations`() {
        assertNull(parseHermesDestination("android.intent.action.VIEW", "https://example.test"))
        assertNull(
            parseHermesDestination(
                "android.intent.action.VIEW",
                "hermes://manage?backend=personal&profile=default&token=secret",
            ),
        )
        assertNull(parseHermesDestination("android.intent.action.VIEW", null))
    }

    @Test
    fun `pending destination queue never exceeds its delivery bound`() {
        val first = HermesDestinationRoute.Chats("personal", "default")
        val second = HermesDestinationRoute.Artifacts("personal", "default", artifactId = "artifact-1")
        val third = HermesDestinationRoute.Automations(
            "personal",
            "default",
            AutomationDestination.CRON,
            "cron-1",
        )
        val fourth = HermesDestinationRoute.Manage("personal", "default", ManageSection.CAPABILITIES)

        val pending = listOf<HermesDestinationRoute>(first, second, third)
            .fold(emptyList<HermesDestinationRoute>()) { current, route ->
            appendPendingHermesDestination(current, route)
        }

        assertEquals(listOf(first, second, third), pending)
        assertEquals(pending, appendPendingHermesDestination(pending, fourth))
    }
}

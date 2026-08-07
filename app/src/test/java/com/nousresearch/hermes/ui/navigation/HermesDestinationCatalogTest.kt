package com.nousresearch.hermes.ui.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesDestinationCatalogTest {
    @Test
    fun `every durable destination has exactly one product home and manage subsection`() {
        val catalog = HermesDestinationCatalog.durableDestinations

        assertEquals(ManagementDestination.entries.toSet(), catalog.keys)
        assertEquals(ManagementDestination.entries.size, catalog.values.map { it.legacyDestination }.distinct().size)
        assertEquals(ProductHome.AUTOMATIONS, catalog.getValue(ManagementDestination.CRON).productHome)
        assertEquals(ProductHome.AUTOMATIONS, catalog.getValue(ManagementDestination.AGENTS).productHome)
        assertEquals(ProductHome.AUTOMATIONS, catalog.getValue(ManagementDestination.WEBHOOKS).productHome)
        assertEquals(ProductHome.AUTOMATIONS, catalog.getValue(ManagementDestination.COMMAND_CENTER).productHome)
        assertEquals(ManageSection.CAPABILITIES, catalog.getValue(ManagementDestination.SKILLS).manageSection)
        assertEquals(ManageSection.PROFILES_AND_MODELS, catalog.getValue(ManagementDestination.PROFILES).manageSection)
        assertEquals(ManageSection.CONNECTIONS_AND_DELIVERY, catalog.getValue(ManagementDestination.MESSAGING).manageSection)
        assertEquals(ManageSection.SERVER_AND_ACCOUNT, catalog.getValue(ManagementDestination.BILLING).manageSection)
        assertEquals(ManageSection.MEMORY_AND_LEARNING, catalog.getValue(ManagementDestination.STARMAP).manageSection)
        assertEquals(ManageSection.CAPABILITIES, catalog.getValue(ManagementDestination.HOST_CAPABILITIES).manageSection)
        assertEquals(
            setOf(
                ManageSection.CAPABILITIES,
                ManageSection.PROFILES_AND_MODELS,
                ManageSection.CONNECTIONS_AND_DELIVERY,
                ManageSection.MEMORY_AND_LEARNING,
                ManageSection.SERVER_AND_ACCOUNT,
            ),
            ManageSection.entries.toSet(),
        )
    }

    @Test
    fun `legacy durable routes map to their native product homes`() {
        assertEquals(ProductHome.CHATS, HermesDestinationCatalog.productHome(HermesRoute.SessionAtlas("b", "p")))
        assertEquals(ProductHome.CHATS, HermesDestinationCatalog.productHome(HermesRoute.Conversation("b", "p", "s")))
        assertEquals(ProductHome.ARTIFACTS, HermesDestinationCatalog.productHome(HermesRoute.Files("b", "p")))
        assertEquals(
            ProductHome.AUTOMATIONS,
            HermesDestinationCatalog.productHome(HermesRoute.Management("b", "p", ManagementDestination.CRON)),
        )
        assertEquals(
            ProductHome.MANAGE,
            HermesDestinationCatalog.productHome(HermesRoute.Management("b", "p", ManagementDestination.SKILLS)),
        )
        assertNull(HermesDestinationCatalog.productHome(HermesRoute.Onboarding))
        assertNull(HermesDestinationCatalog.productHome(HermesRoute.BackendPicker()))
    }

    @Test
    fun `new remote routes retain only stable scope and detail identity`() {
        val routes = listOf<HermesDestinationRoute>(
            HermesDestinationRoute.Chats("backend", "profile", "session"),
            HermesDestinationRoute.Artifacts("backend", "profile", artifactId = "artifact"),
            HermesDestinationRoute.Automations(
                "backend",
                "profile",
                AutomationDestination.CRON,
                "cron-id",
            ),
            HermesDestinationRoute.Manage(
                "backend",
                "profile",
                ManageSection.CAPABILITIES,
                ManageDestination.SKILLS,
                "skill-id",
            ),
        )

        routes.forEach { route ->
            val encoded = Json.encodeToString(route)
            assertTrue(encoded.contains("backend"))
            assertTrue(encoded.contains("profile"))
            listOf("credential", "token", "password", "runtime", "websocket", "transcript", "attachment")
                .forEach { forbidden -> assertFalse(encoded.lowercase().contains(forbidden)) }
        }
    }

    @Test
    fun `app settings are intentionally device local`() {
        val encoded = Json.encodeToString<HermesDestinationRoute>(
            HermesDestinationRoute.AppSettings(AppSettingsSection.PRIVACY_AND_SECURITY),
        )

        assertFalse(encoded.contains("backend", ignoreCase = true))
        assertFalse(encoded.contains("profile", ignoreCase = true))
    }

    @Test
    fun `deep link codec round trips every product home including explicit artifact path`() {
        val routes = listOf<HermesDestinationRoute>(
            HermesDestinationRoute.Chats("backend one", "research", "session/1"),
            HermesDestinationRoute.Artifacts("backend one", "research", filePath = "/workspace/report one.md"),
            HermesDestinationRoute.Automations(
                "backend one",
                "research",
                AutomationDestination.WEBHOOKS,
                "webhook:daily",
            ),
            HermesDestinationRoute.Manage(
                "backend one",
                "research",
                ManageSection.MEMORY_AND_LEARNING,
                ManageDestination.STARMAP,
                "learning-node",
            ),
            HermesDestinationRoute.AppSettings(AppSettingsSection.ACCESSIBILITY),
        )

        routes.forEach { route ->
            assertEquals(route, HermesDestinationUri.parse(HermesDestinationUri.encode(route)))
        }
    }

    @Test
    fun `legacy inventory resolves to canonical product destination identities`() {
        assertEquals(
            HermesDestinationRoute.Automations("b", "p", AutomationDestination.AGENTS, "run-1"),
            HermesDestinationCatalog.destination(ManagementDestination.AGENTS, "b", "p", "run-1"),
        )
        assertEquals(
            HermesDestinationRoute.Automations("b", "p", AutomationDestination.WEBHOOKS, "hook-1"),
            HermesDestinationCatalog.destination(ManagementDestination.WEBHOOKS, "b", "p", "hook-1"),
        )
        assertEquals(
            HermesDestinationRoute.Manage(
                "b",
                "p",
                ManageSection.MEMORY_AND_LEARNING,
                ManageDestination.STARMAP,
                "node-1",
            ),
            HermesDestinationCatalog.destination(ManagementDestination.STARMAP, "b", "p", "node-1"),
        )
        assertEquals(
            HermesDestinationRoute.Manage(
                "b",
                "p",
                ManageSection.CAPABILITIES,
                ManageDestination.HOST_CAPABILITIES,
            ),
            HermesDestinationCatalog.destination(ManagementDestination.HOST_CAPABILITIES, "b", "p"),
        )
    }

    @Test
    fun `deep link parser rejects secret fields duplicates and paths outside artifacts`() {
        assertNull(HermesDestinationUri.parse("hermes://chats?backend=b&profile=p&token=secret"))
        assertNull(HermesDestinationUri.parse("hermes://chats?backend=b&backend=other&profile=p"))
        assertNull(HermesDestinationUri.parse("hermes://chats?backend=b&profile=p&path=%2Fserver"))
        assertNull(HermesDestinationUri.parse("hermes://chats/unexpected?backend=b&profile=p"))
        assertNull(HermesDestinationUri.parse("https://chats?backend=b&profile=p"))
        assertNull(HermesDestinationUri.parse("x".repeat(8_193)))
    }

    @Test
    fun `host local desktop capabilities are never represented as Android local actions`() {
        val dispositions = HermesDestinationCatalog.hostCapabilities

        assertEquals(HostCapability.entries.toSet(), dispositions.keys)
        assertEquals(HostCapabilityDisposition.REMOTE_STATUS, dispositions.getValue(HostCapability.TERMINAL_BACKEND))
        assertEquals(
            HostCapabilityDisposition.INTENTIONALLY_UNAVAILABLE,
            dispositions.getValue(HostCapability.LOCAL_FILESYSTEM_REVEAL),
        )
    }
}

package com.nousresearch.hermes.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDestinationCatalogTest {
    @Test
    fun `manage catalog contains every required product section`() {
        assertEquals(
            NativeManageSection.entries.toSet(),
            defaultNativeManageSections().map { it.section }.toSet(),
        )
    }

    @Test
    fun `category catalog keeps required destinations explicit`() {
        val artifacts = defaultNativeArtifactsEntries().map { it.id }.toSet()
        val automations = defaultNativeAutomationsEntries().map { it.id }.toSet()
        val manage = defaultNativeManageSections().flatMap { it.entries }.map { it.id }.toSet()

        assertTrue("remote files", "remote-files" in artifacts)
        assertTrue("artifact index", "artifact-index" in artifacts)
        assertTrue("cron", "cron" in automations)
        assertTrue("command center", "command-center" in automations)
        assertTrue("agents", "agents" in automations)
        assertTrue("webhooks", "webhooks" in automations)
        assertTrue("host capabilities", "host-capabilities" in manage)
        assertTrue("starmap / memory graph", "starmap-memory-graph" in manage)
    }

    @Test
    fun `unsupported and status entries cannot be treated as available actions`() {
        val allEntries = defaultNativeArtifactsEntries() +
            defaultNativeAutomationsEntries() +
            defaultNativeManageSections().flatMap { it.entries }

        assertEquals(
            NativeEntryAvailability.UNAVAILABLE,
            allEntries.first { it.id == "artifact-index" }.availability,
        )
        assertEquals(
            NativeEntryAvailability.REMOTE_STATUS,
            allEntries.first { it.id == "webhooks" }.availability,
        )
        assertEquals(
            NativeEntryAvailability.REMOTE_STATUS,
            allEntries.first { it.id == "host-capabilities" }.availability,
        )
        assertFalse(allEntries.first { it.id == "starmap-memory-graph" }.availability == NativeEntryAvailability.AVAILABLE)
    }
}

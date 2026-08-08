package com.nousresearch.hermes.security

import com.nousresearch.hermes.data.CapabilityDiagnostics
import com.nousresearch.hermes.domain.CapabilityRegistry
import com.nousresearch.hermes.protocol.StatusResponse
import com.nousresearch.hermes.protocol.capabilityContract
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityDiagnosticsTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `diagnostics expose bounded profile scoped state and safe fallback`() {
        val status = json.decodeFromString<StatusResponse>(
            checkNotNull(javaClass.getResource("/fixtures/capabilities/capabilities-absent-0c1a9b7e.json")).readText(),
        )
        val diagnostics = CapabilityDiagnostics.from(
            CapabilityRegistry.resolve(status.capabilityContract(), profile = "work"),
        )

        assertEquals(10, diagnostics.entries.size)
        assertTrue(diagnostics.entries.all { it.profile == "work" })
        assertTrue(diagnostics.entries.all { it.reason.isNotBlank() && it.fallback.isNotBlank() })
        assertTrue(diagnostics.render().length <= CapabilityDiagnostics.MAX_RENDERED_CHARACTERS)
    }
}

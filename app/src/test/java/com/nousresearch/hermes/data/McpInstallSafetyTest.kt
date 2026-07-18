package com.nousresearch.hermes.data

import com.nousresearch.hermes.protocol.McpCatalogEntry
import com.nousresearch.hermes.protocol.McpCatalogEnvRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class McpInstallSafetyTest {
    @Test
    fun `accepts only advertised values and requires mandatory credentials`() {
        val entry = catalogEntry(
            requirements = listOf(
                McpCatalogEnvRequirement("TOKEN", "Token", true),
                McpCatalogEnvRequirement("TEAM", "Team", false),
            ),
        )

        assertEquals(mapOf("TOKEN" to "secret"), validateMcpInstall(entry, mapOf("TOKEN" to " secret ")))
        assertThrows(IllegalArgumentException::class.java) { validateMcpInstall(entry, emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) {
            validateMcpInstall(entry, mapOf("TOKEN" to "secret", "UNADVERTISED" to "value"))
        }
    }

    @Test
    fun `remote server oauth catalog entries remain blocked`() {
        val entry = catalogEntry(authType = "oauth")

        assertThrows(IllegalArgumentException::class.java) { validateMcpInstall(entry, emptyMap()) }
    }

    private fun catalogEntry(
        authType: String = "none",
        requirements: List<McpCatalogEnvRequirement> = emptyList(),
    ) = McpCatalogEntry(
        name = "reviewed",
        source = "optional-mcps/reviewed.yaml",
        transport = "http",
        authType = authType,
        requiredEnv = requirements,
        installed = false,
        enabled = false,
    )
}

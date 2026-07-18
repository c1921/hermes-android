package com.nousresearch.hermes.protocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpProtocolModelsTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `reload response tolerates compute host fields from the audited contract`() {
        val response = json.decodeFromString(
            McpReloadResponse.serializer(),
            """{"status":"reloaded","turn_isolation":true,"host_ack":{"ok":true},"future_field":"ignored"}""",
        )

        assertEquals("reloaded", response.status)
        assertTrue(response.turnIsolation)
    }

    @Test
    fun `configured server decoder does not require secret-bearing config fields`() {
        val response = json.decodeFromString(
            McpServersResponse.serializer(),
            """{"servers":[{"name":"remote","transport":"http","url":"https://example.invalid/mcp","command":null,"args":[],"env":{"TOKEN":"***1234"},"headers":{"Authorization":"***"},"auth":"header","enabled":false,"tools":["search"]}]}""",
        )

        val server = response.servers.single()
        assertEquals("header", server.auth)
        assertFalse(server.enabled)
        assertEquals(listOf("search"), server.tools)
    }

    @Test
    fun `catalog install response preserves background action identity`() {
        val response = json.decodeFromString(
            McpCatalogInstallResponse.serializer(),
            """{"ok":true,"name":"github","background":true,"action":"mcp-install-github-0123abcd"}""",
        )

        assertTrue(response.ok)
        assertTrue(response.background)
        assertEquals("mcp-install-github-0123abcd", response.action)
    }
}

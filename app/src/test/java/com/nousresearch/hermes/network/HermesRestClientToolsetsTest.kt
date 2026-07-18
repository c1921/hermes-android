package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesRestClientToolsetsTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `toolset catalogue preserves server capabilities and profile scope`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """[{"name":"web","label":"Web Search & Scraping","description":"Search and extract","platform":"cli","platform_label":"CLI","enabled":true,"available":true,"configured":false,"tools":["web_extract","web_search"],"future_field":"ignored"}]""",
                ),
            )

            val result = client().toolsets(config(server), COOKIE, "work profile")

            val toolset = result.single()
            assertEquals("web", toolset.name)
            assertEquals("Web Search & Scraping", toolset.label)
            assertEquals("cli", toolset.platform)
            assertEquals("CLI", toolset.platformLabel)
            assertTrue(toolset.enabled)
            assertTrue(toolset.available)
            assertFalse(toolset.configured)
            assertEquals(listOf("web_extract", "web_search"), toolset.tools)
            val request = server.takeRequest()
            assertEquals("/api/tools/toolsets?profile=work%20profile", request.path)
            assertEquals(COOKIE, request.getHeader("Cookie"))
        }
    }

    @Test
    fun `toolset toggle sends only audited boolean and checks platform response`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """{"ok":true,"name":"discord_admin","platform":"discord","enabled":false}""",
                ),
            )

            val result = client().setToolsetEnabled(
                config(server),
                COOKIE,
                "mobile profile",
                "discord admin",
                false,
            )

            assertTrue(result.ok)
            assertEquals("discord_admin", result.name)
            assertEquals("discord", result.platform)
            assertFalse(result.enabled)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/tools/toolsets/discord%20admin?profile=mobile%20profile", request.path)
            assertEquals(COOKIE, request.getHeader("Cookie"))
            val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals(setOf("enabled"), body.keys)
            assertFalse(body.getValue("enabled").jsonPrimitive.boolean)
        }
    }

    private fun client() = HermesRestClient(OkHttpClient(), json)

    private fun config(server: MockWebServer) = BackendConfig(
        id = "fake",
        label = "Fake Hermes",
        baseUrl = server.url("/").toString().trimEnd('/'),
        authMode = AuthMode.DASHBOARD_SESSION,
        allowInsecurePrivateNetwork = true,
    )

    private companion object {
        const val COOKIE = "hermes_session_at=abc"
    }
}

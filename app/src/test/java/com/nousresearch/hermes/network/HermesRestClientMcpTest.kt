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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesRestClientMcpTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `configured servers use the dashboard cookie and profile scope`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """{"servers":[{"name":"filesystem","transport":"stdio","command":"npx","args":["-y","@modelcontextprotocol/server-filesystem"],"url":null,"auth":null,"enabled":true,"tools":null,"env":{"API_KEY":"***7890"},"future_field":"ignored"}]}""",
                ),
            )

            val result = client().mcpServers(config(server), COOKIE, "work profile")

            val configured = result.servers.single()
            assertEquals("filesystem", configured.name)
            assertEquals("stdio", configured.transport)
            assertEquals("npx", configured.command)
            assertNull(configured.url)
            assertTrue(configured.enabled)
            val request = server.takeRequest()
            assertEquals("/api/mcp/servers?profile=work%20profile", request.path)
            assertEquals(COOKIE, request.getHeader("Cookie"))
        }
    }

    @Test
    fun `catalog preserves review details and diagnostics`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """{"entries":[{"name":"linear","description":"Linear MCP","source":"optional-mcps/linear.yaml","transport":"http","auth_type":"oauth","required_env":[{"name":"LINEAR_TEAM","prompt":"Team","required":false}],"command":null,"args":[],"url":"https://mcp.linear.app/mcp","install_url":null,"install_ref":null,"bootstrap":[],"default_enabled":["list_issues"],"post_install":"Authenticate before use.","needs_install":false,"installed":true,"enabled":false}],"diagnostics":[{"name":"broken","kind":"invalid_manifest","message":"Missing transport"}]}""",
                ),
            )

            val result = client().mcpCatalog(config(server), COOKIE, "default")

            val entry = result.entries.single()
            assertEquals("oauth", entry.authType)
            assertEquals("https://mcp.linear.app/mcp", entry.url)
            assertEquals("list_issues", entry.defaultEnabled?.single())
            assertFalse(entry.enabled)
            assertEquals("invalid_manifest", result.diagnostics.single().kind)
            assertEquals("/api/mcp/catalog?profile=default", server.takeRequest().path)
        }
    }

    @Test
    fun `server probe uses the canonical endpoint and capability counts`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """{"ok":true,"tools":[{"name":"read_file","description":"Read a file"}],"prompts":2,"resources":3}""",
                ),
            )

            val result = client().testMcpServer(config(server), COOKIE, "default", "file server")

            assertTrue(result.ok)
            assertEquals(1, result.tools.size)
            assertEquals(2, result.prompts)
            assertEquals(3, result.resources)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/mcp/servers/file%20server/test?profile=default", request.path)
            assertEquals(COOKIE, request.getHeader("Cookie"))
        }
    }

    @Test
    fun `server probe returns audited failure payload without inventing status`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """{"ok":false,"error":"OAuth authentication required — no token found.","tools":[]}""",
                ),
            )

            val result = client().testMcpServer(config(server), COOKIE, "default", "linear")

            assertFalse(result.ok)
            assertEquals("OAuth authentication required — no token found.", result.error)
            assertEquals(0, result.prompts)
            assertEquals(0, result.resources)
        }
    }

    @Test
    fun `enable toggle is profile scoped and sends only the audited boolean`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"ok":true,"name":"linear","enabled":false}"""))

            val result = client().setMcpServerEnabled(config(server), COOKIE, "mobile profile", "linear", false)

            assertTrue(result.ok)
            assertEquals("linear", result.name)
            assertFalse(result.enabled)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/mcp/servers/linear/enabled?profile=mobile%20profile", request.path)
            assertEquals(COOKIE, request.getHeader("Cookie"))
            val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertFalse(body.getValue("enabled").jsonPrimitive.boolean)
            assertEquals(setOf("enabled"), body.keys)
        }
    }

    @Test
    fun `catalog install is profile scoped and sends only reviewed env values`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """{"ok":true,"name":"github","background":true,"action":"mcp-install-github-0123abcd"}""",
                ),
            )

            val result = client().installMcpCatalogEntry(
                config(server),
                COOKIE,
                "work profile",
                "github",
                mapOf("GITHUB_TOKEN" to "secret-value"),
            )

            assertTrue(result.ok)
            assertTrue(result.background)
            assertEquals("mcp-install-github-0123abcd", result.action)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/mcp/catalog/install?profile=work%20profile", request.path)
            assertEquals(COOKIE, request.getHeader("Cookie"))
            val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals(setOf("name", "env", "enable"), body.keys)
            assertEquals("github", body.getValue("name").jsonPrimitive.content)
            assertEquals("secret-value", body.getValue("env").jsonObject.getValue("GITHUB_TOKEN").jsonPrimitive.content)
            assertTrue(body.getValue("enable").jsonPrimitive.boolean)
        }
    }

    @Test
    fun `server removal uses encoded advertised identity and profile`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))

            val result = client().removeMcpServer(config(server), COOKIE, "work profile", "file server")

            assertTrue(result.ok)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/api/mcp/servers/file%20server?profile=work%20profile", request.path)
            assertEquals(COOKIE, request.getHeader("Cookie"))
            assertTrue(request.body.readUtf8().isBlank())
        }
    }

    @Test
    fun `catalog background action accepts only canonical install action names`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """{"name":"mcp-install-github-0123abcd","pid":42,"running":false,"exit_code":0,"lines":[]}""",
                ),
            )

            val result = client().actionStatus(
                config(server),
                COOKIE,
                "mcp-install-github-0123abcd",
                profile = "work profile",
            )

            assertFalse(result.running)
            assertEquals(0, result.exitCode)
            assertEquals(
                "/api/actions/mcp-install-github-0123abcd/status?lines=400&profile=work%20profile",
                server.takeRequest().path,
            )
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

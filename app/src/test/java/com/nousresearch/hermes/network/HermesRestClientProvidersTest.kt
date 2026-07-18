package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesRestClientProvidersTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `provider catalogue validation and persistence use profile scoped audited routes`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val config = BackendConfig(
                id = "fake",
                label = "Fake Hermes",
                baseUrl = server.url("/").toString().trimEnd('/'),
                authMode = AuthMode.TOKEN,
                allowInsecurePrivateNetwork = true,
            )
            val client = HermesRestClient(OkHttpClient(), json)
            server.enqueue(MockResponse().setBody("""{"providers":[{"slug":"openrouter","name":"OpenRouter","authenticated":false,"auth_type":"api_key","key_env":"OPENROUTER_API_KEY","models":["model-a"]}]}"""))
            server.enqueue(MockResponse().setBody("""{"OPENROUTER_API_KEY":{"advanced":false,"category":"provider","description":"OpenRouter key","is_password":true,"is_set":false,"provider":"openrouter","provider_label":"OpenRouter","redacted_value":null,"tools":[]}}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"reachable":true,"message":""}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"key":"OPENROUTER_API_KEY"}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))

            val options = client.globalModelOptions(config, "session-token", "research lab", refresh = true)
            val env = client.envVars(config, "session-token", "research lab")
            val validation = client.validateProviderCredential(config, "session-token", "OPENROUTER_API_KEY", "provider-secret")
            client.setEnvVar(config, "session-token", "research lab", "OPENROUTER_API_KEY", "provider-secret")
            client.deleteEnvVar(config, "session-token", "research lab", "OPENROUTER_API_KEY")

            assertFalse(options.providers.single().authenticated)
            assertEquals("OPENROUTER_API_KEY", options.providers.single().keyEnvironment)
            assertTrue(env.getValue("OPENROUTER_API_KEY").isPassword)
            assertTrue(validation.ok)

            val requests = List(5) { server.takeRequest() }
            assertEquals("/api/model/options?explicit_only=1&include_unconfigured=1&refresh=1&profile=research%20lab", requests[0].path)
            assertEquals("/api/env?profile=research%20lab", requests[1].path)
            assertEquals("/api/providers/validate", requests[2].path)
            assertTrue(requests[2].body.readUtf8().contains("provider-secret"))
            assertEquals("PUT", requests[3].method)
            assertTrue(requests[3].body.readUtf8().contains("research lab"))
            assertEquals("DELETE", requests[4].method)
            assertTrue(requests.all { it.getHeader("Authorization") == "Bearer session-token" })
        }
    }

    @Test
    fun `OAuth catalogue and device login use profile scoped Dashboard routes`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val config = BackendConfig(
                id = "fake",
                label = "Fake Hermes",
                baseUrl = server.url("/").toString().trimEnd('/'),
                authMode = AuthMode.DASHBOARD_SESSION,
                allowInsecurePrivateNetwork = true,
            )
            val client = HermesRestClient(OkHttpClient(), json)
            server.enqueue(
                MockResponse().setBody(
                    fixture("provider-oauth-catalog-5988fe6.json"),
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"session_id":"oauth-1","flow":"device_code","user_code":"ABCD-EFGH","verification_url":"https://portal.nousresearch.com/device","expires_in":600,"poll_interval":5}""",
                ),
            )

            val providers = client.oauthProviders(config, "hermes_session_at=cookie", "research lab")
            val login = client.startProviderOAuth(config, "hermes_session_at=cookie", "research lab", "nous")

            assertEquals("nous", providers.single().id)
            assertFalse(providers.single().status.loggedIn)
            assertEquals("device_code", login.flow)
            assertEquals("ABCD-EFGH", login.userCode)
            assertEquals("https://portal.nousresearch.com/device", login.verificationUrl)

            val catalogueRequest = server.takeRequest()
            val startRequest = server.takeRequest()
            assertEquals("/api/providers/oauth?profile=research%20lab", catalogueRequest.path)
            assertEquals("GET", catalogueRequest.method)
            assertEquals("/api/providers/oauth/nous/start?profile=research%20lab", startRequest.path)
            assertEquals("POST", startRequest.method)
            assertEquals("{}", startRequest.body.readUtf8())
            assertTrue(listOf(catalogueRequest, startRequest).all { it.getHeader("Cookie") == "hermes_session_at=cookie" })
        }
    }

    @Test
    fun `device login polling and cancellation preserve provider and session identity`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val config = BackendConfig(
                id = "fake",
                label = "Fake Hermes",
                baseUrl = server.url("/").toString().trimEnd('/'),
                authMode = AuthMode.DASHBOARD_SESSION,
                allowInsecurePrivateNetwork = true,
            )
            val client = HermesRestClient(OkHttpClient(), json)
            server.enqueue(MockResponse().setBody("""{"session_id":"oauth/1","status":"pending","expires_at":12345}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"session_id":"oauth/1"}"""))
            server.enqueue(MockResponse().setBody("""{"ok":false,"message":"session not found"}"""))

            val status = client.pollProviderOAuth(
                config,
                "hermes_session_at=cookie",
                "research lab",
                "openai-codex",
                "oauth/1",
            )
            val cancelled = client.cancelProviderOAuth(config, "hermes_session_at=cookie", "research lab", "oauth/1")
            val missingSessionCancelled = client.cancelProviderOAuth(config, "hermes_session_at=cookie", "research lab", "missing")

            assertEquals("pending", status.status)
            assertEquals(12345L, status.expiresAt)
            assertTrue(cancelled)
            assertFalse(missingSessionCancelled)

            val pollRequest = server.takeRequest()
            val cancelRequest = server.takeRequest()
            val missingCancelRequest = server.takeRequest()
            assertEquals(
                "/api/providers/oauth/openai-codex/poll/oauth%2F1?profile=research%20lab",
                pollRequest.path,
            )
            assertEquals("GET", pollRequest.method)
            assertEquals("/api/providers/oauth/sessions/oauth%2F1?profile=research%20lab", cancelRequest.path)
            assertEquals("DELETE", cancelRequest.method)
            assertEquals("/api/providers/oauth/sessions/missing?profile=research%20lab", missingCancelRequest.path)
            assertEquals("DELETE", missingCancelRequest.method)
            assertTrue(listOf(pollRequest, cancelRequest, missingCancelRequest).all {
                it.getHeader("Cookie") == "hermes_session_at=cookie"
            })
        }
    }

    @Test
    fun `PKCE submission and disconnect use authenticated advertised provider routes`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val config = BackendConfig(
                id = "fake",
                label = "Fake Hermes",
                baseUrl = server.url("/").toString().trimEnd('/'),
                authMode = AuthMode.DASHBOARD_SESSION,
                allowInsecurePrivateNetwork = true,
            )
            val client = HermesRestClient(OkHttpClient(), json)
            server.enqueue(
                MockResponse().setBody(
                    """{"session_id":"oauth-2","flow":"pkce","auth_url":"https://claude.ai/oauth/authorize","expires_in":900}""",
                ),
            )
            server.enqueue(MockResponse().setBody("""{"ok":true,"status":"approved","message":"Connected"}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"provider":"anthropic"}"""))

            val login = client.startProviderOAuth(config, "hermes_session_at=cookie", "research lab", "anthropic")
            val submitted = client.submitProviderOAuth(
                config,
                "hermes_session_at=cookie",
                "research lab",
                "anthropic",
                "oauth-2",
                "callback#code",
            )
            val disconnected = client.disconnectProviderOAuth(
                config,
                "hermes_session_at=cookie",
                "research lab",
                "anthropic",
            )

            assertEquals("pkce", login.flow)
            assertEquals("https://claude.ai/oauth/authorize", login.authUrl)
            assertTrue(submitted.ok)
            assertEquals("approved", submitted.status)
            assertTrue(disconnected)

            val startRequest = server.takeRequest()
            val submitRequest = server.takeRequest()
            val disconnectRequest = server.takeRequest()
            assertEquals("/api/providers/oauth/anthropic/start?profile=research%20lab", startRequest.path)
            assertEquals("POST", startRequest.method)
            assertEquals("/api/providers/oauth/anthropic/submit?profile=research%20lab", submitRequest.path)
            assertEquals("POST", submitRequest.method)
            assertEquals("""{"session_id":"oauth-2","code":"callback#code"}""", submitRequest.body.readUtf8())
            assertEquals("/api/providers/oauth/anthropic?profile=research%20lab", disconnectRequest.path)
            assertEquals("DELETE", disconnectRequest.method)
            assertTrue(listOf(startRequest, submitRequest, disconnectRequest).all { it.getHeader("Cookie") == "hermes_session_at=cookie" })
        }
    }

    private fun fixture(name: String): String = checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")) {
        "Missing pinned fixture: $name"
    }.readText()
}

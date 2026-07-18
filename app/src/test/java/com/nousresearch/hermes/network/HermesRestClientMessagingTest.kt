package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesRestClientMessagingTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `messaging catalogue preserves server status and redaction`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """{"platforms":[{"id":"telegram","name":"Telegram","enabled":true,"configured":true,"gateway_running":true,"state":"connected","env_vars":[{"key":"TELEGRAM_BOT_TOKEN","required":true,"is_set":true,"redacted_value":"...1234","is_password":true}]}]}""",
                ),
            )

            val result = client().messagingPlatforms(config(server), "hermes_session_at=abc", "work profile")

            val platform = result.platforms.single()
            assertTrue(platform.enabled)
            assertEquals("connected", platform.state)
            assertEquals("...1234", platform.envVars.single().redactedValue)
            val request = server.takeRequest()
            assertEquals("/api/messaging/platforms?profile=work%20profile", request.path)
            assertEquals("hermes_session_at=abc", request.getHeader("Cookie"))
        }
    }

    @Test
    fun `messaging updates are profile scoped and include only submitted fields`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"ok":true,"platform":"discord"}"""))

            val result = client().updateMessagingPlatform(
                config(server),
                "hermes_session_at=abc",
                "default",
                "discord",
                env = mapOf("DISCORD_BOT_TOKEN" to "submitted-once"),
                clearEnv = listOf("DISCORD_HOME_CHANNEL"),
            )

            assertTrue(result.ok)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/messaging/platforms/discord?profile=default", request.path)
            val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("submitted-once", body.getValue("env").jsonObject.getValue("DISCORD_BOT_TOKEN").jsonPrimitive.content)
            assertEquals("DISCORD_HOME_CHANNEL", body.getValue("clear_env").jsonArray.single().jsonPrimitive.content)
            assertFalse("enabled" in body)
        }
    }

    @Test
    fun `messaging connection test uses the canonical endpoint`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"ok":false,"state":"gateway_stopped","message":"Restart the gateway."}"""))

            val result = client().testMessagingPlatform(config(server), "hermes_session_at=abc", "default", "matrix")

            assertFalse(result.ok)
            assertEquals("gateway_stopped", result.state)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/messaging/platforms/matrix/test?profile=default", request.path)
        }
    }

    @Test
    fun `gateway restart and action status stay profile scoped`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"ok":true,"name":"gateway-restart","pid":91}"""))
            server.enqueue(MockResponse().setBody("""{"name":"gateway-restart","running":false,"exit_code":0,"pid":91,"lines":[]}"""))
            val rest = client()

            val started = rest.restartGateway(config(server), "hermes_session_at=abc", "mobile profile")
            val status = rest.actionStatus(
                config(server),
                "hermes_session_at=abc",
                started.name,
                lines = 100,
                profile = "mobile profile",
            )

            assertTrue(started.ok)
            assertEquals(0, status.exitCode)
            assertEquals("/api/gateway/restart?profile=mobile%20profile", server.takeRequest().path)
            assertEquals(
                "/api/actions/gateway-restart/status?lines=100&profile=mobile%20profile",
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
}

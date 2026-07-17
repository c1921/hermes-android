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

class DashboardAuthClientTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `password login posts expected body and returns access session cookie`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Set-Cookie", "__Host-hermes_session_at=session-value; Path=/; Secure; HttpOnly; SameSite=Lax")
                    .setBody("""{"ok":true,"next":"/"}"""),
            )
            server.start()
            val client = DashboardAuthClient(OkHttpClient(), json)

            val cookie = client.login(config(server), "dashboard-user", "password-value")

            assertEquals("__Host-hermes_session_at=session-value", cookie.headerValue)
            val request = server.takeRequest()
            assertEquals("/auth/password-login", request.path)
            assertEquals("POST", request.method)
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"provider\":\"basic\""))
            assertTrue(body.contains("\"username\":\"dashboard-user\""))
            assertTrue(body.contains("\"password\":\"password-value\""))
            assertFalse(cookie.headerValue.contains("password-value"))
            assertFalse(cookie.toString().contains("session-value"))
        }
    }

    @Test
    fun `missing or malformed access cookie rejects login`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
            server.enqueue(MockResponse().setResponseCode(200).addHeader("Set-Cookie", "hermes_session_at=; Path=/; HttpOnly").setBody("""{"ok":true}"""))
            server.start()
            val client = DashboardAuthClient(OkHttpClient(), json)

            val missing = runCatching { client.login(config(server), "user", "password") }.exceptionOrNull()
            val malformed = runCatching { client.login(config(server), "user", "password") }.exceptionOrNull()

            assertTrue(missing is DashboardAuthenticationException)
            assertTrue(malformed is DashboardAuthenticationException)
        }
    }

    @Test
    fun `rejected credentials return a generic authentication failure`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Invalid credentials"}"""))
            server.start()
            val client = DashboardAuthClient(OkHttpClient(), json)

            val failure = runCatching { client.login(config(server), "alice@example.test", "wrong") }.exceptionOrNull()

            assertTrue(failure is DashboardAuthenticationException)
            assertFalse(failure?.message.orEmpty().contains("alice@example.test"))
        }
    }

    private fun config(server: MockWebServer) = BackendConfig(
        id = "fake",
        label = "Fake Dashboard",
        baseUrl = server.url("/").toString().trimEnd('/'),
        authMode = AuthMode.DASHBOARD_SESSION,
        allowInsecurePrivateNetwork = true,
    )
}

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
    fun `password login posts expected body and returns complete session cookie bundle`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(passwordProviderResponse("company-password"))
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Set-Cookie", "__Host-hermes_session_at=session-value; Path=/; Secure; HttpOnly; SameSite=Lax")
                    .addHeader("Set-Cookie", "__Host-hermes_session_rt=refresh-value; Path=/; Secure; HttpOnly; SameSite=Lax")
                    .addHeader("Set-Cookie", "__Host-hermes_session_provider=password-provider; Path=/; Secure; HttpOnly; SameSite=Lax")
                    .setBody("""{"ok":true,"next":"/"}"""),
            )
            server.start()
            val client = DashboardAuthClient(OkHttpClient(), json)

            val cookie = client.login(config(server), "dashboard-user", "password-value")

            assertEquals(
                "__Host-hermes_session_at=session-value; __Host-hermes_session_rt=refresh-value; " +
                    "__Host-hermes_session_provider=password-provider",
                cookie.headerValue,
            )
            assertEquals("/api/auth/providers", server.takeRequest().path)
            val request = server.takeRequest()
            assertEquals("/auth/password-login", request.path)
            assertEquals("POST", request.method)
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"provider\":\"company-password\""))
            assertTrue(body.contains("\"username\":\"dashboard-user\""))
            assertTrue(body.contains("\"password\":\"password-value\""))
            assertFalse(cookie.headerValue.contains("password-value"))
            assertFalse(cookie.toString().contains("session-value"))
            assertFalse(cookie.toString().contains("refresh-value"))
        }
    }

    @Test
    fun `session cookie bundle merges rotated cookies and ignores unrelated cookies`() {
        val cookie = checkNotNull(
            DashboardSessionCredential.fromSetCookieHeaders(
                listOf(
                    "hermes_session_at=access-1; Path=/; HttpOnly",
                    "hermes_session_rt=refresh-1; Path=/; HttpOnly",
                    "hermes_session_provider=testpw; Path=/; HttpOnly",
                    "analytics=not-a-session; Path=/",
                ),
            ),
        )

        assertTrue(
            cookie.mergeSetCookieHeaders(
                listOf(
                    "hermes_session_at=access-2; Path=/; HttpOnly",
                    "hermes_session_rt=refresh-2; Path=/; HttpOnly",
                    "analytics=still-not-a-session; Path=/",
                ),
            ),
        )

        assertEquals(
            "hermes_session_at=access-2; hermes_session_rt=refresh-2; hermes_session_provider=testpw",
            cookie.headerValue,
        )
    }

    @Test
    fun `missing or malformed access cookie rejects login`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(passwordProviderResponse())
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
            server.enqueue(passwordProviderResponse())
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
            server.enqueue(passwordProviderResponse())
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Invalid credentials"}"""))
            server.start()
            val client = DashboardAuthClient(OkHttpClient(), json)

            val failure = runCatching { client.login(config(server), "alice@example.test", "wrong") }.exceptionOrNull()

            assertTrue(failure is DashboardAuthenticationException)
            assertFalse(failure?.message.orEmpty().contains("alice@example.test"))
        }
    }

    @Test
    fun `login rejects ambiguous or absent password providers before submitting credentials`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"providers":[{"name":"oauth","display_name":"OAuth","supports_password":false}]}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"providers":[{"name":"one","display_name":"One","supports_password":true},{"name":"two","display_name":"Two","supports_password":true}]}""",
                ),
            )
            server.start()
            val client = DashboardAuthClient(OkHttpClient(), json)

            val absent = runCatching { client.login(config(server), "user", "password") }.exceptionOrNull()
            val ambiguous = runCatching { client.login(config(server), "user", "password") }.exceptionOrNull()

            assertTrue(absent is DashboardAuthenticationException)
            assertTrue(ambiguous is DashboardAuthenticationException)
            assertEquals(2, server.requestCount)
        }
    }

    private fun passwordProviderResponse(name: String? = null) = MockResponse().setBody(
        name?.let { """{"providers":[{"name":"$it","display_name":"Password","supports_password":true}]}""" }
            ?: checkNotNull(javaClass.getResource("/fixtures/dashboard-auth-providers-f15a38ee.json")).readText(),
    )

    private fun config(server: MockWebServer) = BackendConfig(
        id = "fake",
        label = "Fake Dashboard",
        baseUrl = server.url("/").toString().trimEnd('/'),
        authMode = AuthMode.DASHBOARD_SESSION,
        allowInsecurePrivateNetwork = true,
    )
}

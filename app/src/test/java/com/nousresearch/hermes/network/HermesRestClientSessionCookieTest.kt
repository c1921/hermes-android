package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.data.SessionCredentialStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HermesRestClientSessionCookieTest {
    @Test
    fun `status reuses dashboard session cookie without bearer authorization`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"status":"ok","hermes_version":"0.18.2"}"""))
            server.start()
            val client = HermesRestClient(OkHttpClient(), Json { ignoreUnknownKeys = true })

            client.status(config(server), DashboardSessionCookie("hermes_session_at", "session-value"))

            val request = server.takeRequest()
            assertEquals("hermes_session_at=session-value", request.getHeader("Cookie"))
            assertNull(request.getHeader("Authorization"))
        }
    }

    @Test
    fun `authenticated response persists rotated session cookies`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .addHeader("Set-Cookie", "hermes_session_at=access-2; Path=/; HttpOnly")
                    .addHeader("Set-Cookie", "hermes_session_rt=refresh-2; Path=/; HttpOnly")
                    .setBody("""{"sessions":[]}"""),
            )
            server.start()
            val original = checkNotNull(
                DashboardSessionCookie.fromSetCookieHeaders(
                    listOf(
                        "hermes_session_at=access-1",
                        "hermes_session_rt=refresh-1",
                        "hermes_session_provider=testpw",
                    ),
                ),
            )
            val credentials = RecordingCredentialStore(original)
            val client = HermesRestClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, credentials)

            client.sessions(config(server), original.headerValue)

            assertEquals(
                "hermes_session_at=access-2; hermes_session_rt=refresh-2; hermes_session_provider=testpw",
                credentials.saved?.headerValue,
            )
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

private class RecordingCredentialStore(initial: DashboardSessionCookie) : SessionCredentialStore {
    var saved: DashboardSessionCookie? = initial
    override fun put(backendId: String, cookie: DashboardSessionCookie) { saved = cookie }
    override fun get(backendId: String): DashboardSessionCookie? = saved
    override fun remove(backendId: String) { saved = null }
}

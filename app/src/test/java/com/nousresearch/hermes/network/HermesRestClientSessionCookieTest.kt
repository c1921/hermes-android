package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
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

    private fun config(server: MockWebServer) = BackendConfig(
        id = "fake",
        label = "Fake Dashboard",
        baseUrl = server.url("/").toString().trimEnd('/'),
        authMode = AuthMode.DASHBOARD_SESSION,
        allowInsecurePrivateNetwork = true,
    )
}

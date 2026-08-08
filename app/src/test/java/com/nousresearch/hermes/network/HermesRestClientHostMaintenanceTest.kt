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

class HermesRestClientHostMaintenanceTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `host logs and update check use bounded authenticated host routes`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val client = HermesRestClient(OkHttpClient(), json)
            val config = BackendConfig(
                id = "fake",
                label = "Fake Hermes",
                baseUrl = server.url("/").toString().trimEnd('/'),
                authMode = AuthMode.TOKEN,
                allowInsecurePrivateNetwork = true,
            )
            server.enqueue(MockResponse().setBody("""{"file":"agent","lines":["INFO ready"]}"""))
            server.enqueue(MockResponse().setBody("""{"install_method":"docker","current_version":"0.20.0","behind":null,"update_available":false,"can_apply":false,"update_command":"docker pull","message":"Managed outside Hermes"}"""))

            val logs = client.hostLogs(config, "secret")
            val update = client.hermesUpdateCheck(config, "secret", force = true)

            assertEquals(listOf("INFO ready"), logs.lines)
            assertFalse(update.canApply)
            assertEquals("Managed outside Hermes", update.message)
            val logRequest = server.takeRequest()
            assertEquals("/api/logs?file=agent&lines=200", logRequest.path)
            assertEquals("Bearer secret", logRequest.getHeader("Authorization"))
            assertEquals("/api/hermes/update/check?force=true", server.takeRequest().path)
        }
    }

    @Test
    fun `host log response is bounded before decoding`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val client = HermesRestClient(OkHttpClient(), json)
            val config = BackendConfig("fake", "Fake", server.url("/").toString().trimEnd('/'), AuthMode.TOKEN, true)
            server.enqueue(MockResponse().setBody("x".repeat(524_289)))

            assertTrue(runCatching { client.hostLogs(config, "secret") }.isFailure)
        }
    }
}

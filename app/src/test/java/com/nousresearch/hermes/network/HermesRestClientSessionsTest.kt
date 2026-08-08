package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class HermesRestClientSessionsTest {
    @Test
    fun `sessions request selected profile before applying its limit`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"sessions":[],"total":0}"""))

            HermesRestClient(OkHttpClient(), Json { ignoreUnknownKeys = true }).sessions(
                config(server),
                "secret",
                limit = 30,
                offset = 0,
                profile = "research profile",
            )

            assertEquals(
                "/api/profiles/sessions?limit=30&offset=0&order=recent&profile=research%20profile&exclude_sources=cron",
                server.takeRequest().path,
            )
        }
    }

    @Test
    fun `sessions default request keeps all-profile behavior`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"sessions":[],"total":0}"""))

            HermesRestClient(OkHttpClient(), Json { ignoreUnknownKeys = true }).sessions(
                config(server),
                "secret",
            )

            assertEquals(
                "/api/profiles/sessions?limit=50&offset=0&order=recent&profile=all&exclude_sources=cron",
                server.takeRequest().path,
            )
        }
    }

    private fun config(server: MockWebServer) = BackendConfig(
        id = "fake",
        label = "Fake Hermes",
        baseUrl = server.url("/").toString().trimEnd('/'),
        authMode = AuthMode.TOKEN,
        allowInsecurePrivateNetwork = true,
    )
}

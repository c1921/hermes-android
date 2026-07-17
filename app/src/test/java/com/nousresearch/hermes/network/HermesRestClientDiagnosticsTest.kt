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

class HermesRestClientDiagnosticsTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `diagnostic actions use audited routes and typed status`() = runTest {
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
            server.enqueue(MockResponse().setBody("""{"ok":true,"pid":72,"name":"doctor"}"""))
            server.enqueue(MockResponse().setBody("""{"name":"doctor","running":false,"exit_code":0,"pid":72,"lines":["healthy"]}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"pid":91,"name":"security-audit"}"""))

            val started = client.runDoctor(config, "secret")
            val status = client.actionStatus(config, "secret", "doctor", lines = 9_000)
            val audit = client.runSecurityAudit(config, "secret")

            assertTrue(started.ok)
            assertEquals(72L, started.pid)
            assertFalse(status.running)
            assertEquals(0, status.exitCode)
            assertEquals(listOf("healthy"), status.lines)
            assertEquals("security-audit", audit.name)

            val doctorRequest = server.takeRequest()
            val statusRequest = server.takeRequest()
            val auditRequest = server.takeRequest()
            assertEquals("POST", doctorRequest.method)
            assertEquals("/api/ops/doctor", doctorRequest.path)
            assertEquals("/api/actions/doctor/status?lines=2000", statusRequest.path)
            assertEquals("POST", auditRequest.method)
            assertEquals("/api/ops/security-audit", auditRequest.path)
            assertTrue(listOf(doctorRequest, statusRequest, auditRequest).all { it.getHeader("Authorization") == "Bearer secret" })
        }
    }

    @Test
    fun `diagnostic status rejects arbitrary action names before network access`() = runTest {
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

            val failure = runCatching {
                client.actionStatus(config, "secret", "../sessions")
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals(0, server.requestCount)
        }
    }
}

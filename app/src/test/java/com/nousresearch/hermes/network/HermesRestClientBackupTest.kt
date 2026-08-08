package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesRestClientBackupTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `backup start status and download retain exact authenticated receipt`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val client = HermesRestClient(OkHttpClient(), json)
            val config = BackendConfig("fake", "Fake", server.url("/").toString().trimEnd('/'), AuthMode.TOKEN, true)
            val archive = "/srv/hermes/backups/hermes-backup-2026.zip"
            server.enqueue(MockResponse().setBody("""{"ok":true,"pid":91,"name":"backup","archive":"$archive"}"""))
            server.enqueue(MockResponse().setBody("""{"name":"backup","running":false,"exit_code":0,"pid":91,"lines":["done"]}"""))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/zip").setBody("PK backup"))

            val started = client.startBackup(config, "secret")
            val status = client.actionStatus(config, "secret", "backup")
            val output = ByteArrayOutputStream()
            client.downloadBackup(config, "secret", started.archive, output) { _, _ -> }

            assertEquals(91, started.pid)
            assertEquals(0, status.exitCode)
            assertArrayEquals("PK backup".encodeToByteArray(), output.toByteArray())
            val requests = List(3) { server.takeRequest() }
            assertEquals("POST", requests[0].method)
            assertEquals("/api/ops/backup", requests[0].path)
            assertEquals("/api/actions/backup/status?lines=400", requests[1].path)
            assertEquals("/api/ops/backup/download?archive=%2Fsrv%2Fhermes%2Fbackups%2Fhermes-backup-2026.zip", requests[2].path)
            assertTrue(requests.all { it.getHeader("Authorization") == "Bearer secret" })
        }
    }

    @Test
    fun `backup rejects invalid archive before network access`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val client = HermesRestClient(OkHttpClient(), json)
            val config = BackendConfig("fake", "Fake", server.url("/").toString().trimEnd('/'), AuthMode.TOKEN, true)

            assertTrue(runCatching {
                client.downloadBackup(config, "secret", "", ByteArrayOutputStream()) { _, _ -> }
            }.isFailure)
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `backup rejects declared oversized zip before writing`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val client = HermesRestClient(OkHttpClient(), json)
            val config = BackendConfig("fake", "Fake", server.url("/").toString().trimEnd('/'), AuthMode.TOKEN, true)
            server.enqueue(
                MockResponse()
                    .setBody("PK")
                    .setHeader("Content-Type", "application/zip")
                    .setHeader("Content-Length", 1_073_741_825L),
            )
            val output = ByteArrayOutputStream()

            assertTrue(runCatching {
                client.downloadBackup(config, "secret", "/srv/backups/archive.zip", output) { _, _ -> }
            }.isFailure)
            assertEquals(0, output.size())
        }
    }
}

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

class HermesRestClientFilesTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `managed files use hardened routes and encode server paths`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val client = HermesRestClient(OkHttpClient(), json)
            val config = config(server)
            server.enqueue(
                MockResponse().setBody(
                    """{"path":"/tmp/test space","can_change_path":true,"entries":[{"name":"notes.md","path":"/tmp/test space/notes.md","is_directory":false,"size":5,"mime_type":"text/markdown"}]}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"name":"notes.md","path":"/tmp/test space/notes.md","size":5,"mime_type":"text/markdown","data_url":"data:text/markdown;base64,aGVsbG8="}""",
                ),
            )

            val listing = client.managedFiles(config, "secret", "/tmp/test space")
            val preview = client.readManagedFile(config, "secret", listing.entries.single().path)

            assertTrue(listing.canChangePath)
            assertEquals("notes.md", preview.name)
            val listRequest = server.takeRequest()
            val readRequest = server.takeRequest()
            assertEquals("/api/files?path=%2Ftmp%2Ftest%20space", listRequest.path)
            assertEquals("/api/files/read?path=%2Ftmp%2Ftest%20space%2Fnotes.md", readRequest.path)
            assertEquals("Bearer secret", listRequest.getHeader("Authorization"))
            assertEquals("Bearer secret", readRequest.getHeader("Authorization"))
        }
    }

    @Test
    fun `managed file download streams bytes and reports progress`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val payload = ByteArray(96_000) { (it % 251).toByte() }
            server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))
            val output = ByteArrayOutputStream()
            val progress = mutableListOf<Pair<Long, Long?>>()

            HermesRestClient(OkHttpClient(), json).downloadManagedFile(
                config(server),
                "session=abc",
                "/tmp/result.bin",
                output,
            ) { copied, total -> progress += copied to total }

            assertArrayEquals(payload, output.toByteArray())
            assertEquals(96_000L, progress.last().first)
            assertEquals(96_000L, progress.last().second)
            val request = server.takeRequest()
            assertEquals("/api/files/download?path=%2Ftmp%2Fresult.bin", request.path)
            assertEquals("Bearer session=abc", request.getHeader("Authorization"))
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

package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import java.io.ByteArrayOutputStream
import java.io.IOException
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

    @Test
    fun `filesystem previews use exact authenticated paths and shipped response shapes`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """{"dataUrl":"data:image/png;base64,aGVsbG8="}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"path":"/Users/me/project/read me.md","text":"hello","mimeType":"text/markdown","language":"markdown","byteSize":5,"binary":false,"truncated":false}""",
                ),
            )
            val client = HermesRestClient(OkHttpClient(), json)
            val config = config(server)

            val data = client.readFsDataUrl(config, "secret", "/Users/me/project/image.png")
            val text = client.readFsText(config, "secret", "/Users/me/project/read me.md")

            assertEquals("data:image/png;base64,aGVsbG8=", data.dataUrl)
            assertEquals("hello", text.text)
            assertEquals(5L, text.byteSize)
            val dataRequest = server.takeRequest()
            val textRequest = server.takeRequest()
            assertEquals(
                "/api/fs/read-data-url?path=%2FUsers%2Fme%2Fproject%2Fimage.png",
                dataRequest.path,
            )
            assertEquals(
                "/api/fs/read-text?path=%2FUsers%2Fme%2Fproject%2Fread%20me.md",
                textRequest.path,
            )
            assertEquals("Bearer secret", dataRequest.getHeader("Authorization"))
            assertEquals("Bearer secret", textRequest.getHeader("Authorization"))
        }
    }

    @Test
    fun `filesystem preview uses dashboard cookie without query credentials`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"dataUrl":"data:image/png;base64,"}"""))
            val config = config(server).copy(authMode = AuthMode.DASHBOARD_SESSION)

            HermesRestClient(OkHttpClient(), json).readFsDataUrl(config, "session=abc", "/tmp/a.png")

            val request = server.takeRequest()
            assertEquals("session=abc", request.getHeader("Cookie"))
            assertEquals(null, request.getHeader("Authorization"))
            assertTrue(request.path.orEmpty().endsWith("path=%2Ftmp%2Fa.png"))
            assertTrue("session=abc" !in request.path.orEmpty())
        }
    }

    @Test
    fun `filesystem preview rejects redirects`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", server.url("/stolen")),
            )

            val failure = runCatching {
                HermesRestClient(OkHttpClient(), json)
                    .readFsDataUrl(config(server), "secret", "/tmp/a.png")
            }.exceptionOrNull()
            assertTrue(failure is HermesHttpException)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `filesystem text preview rejects oversized json before parsing`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("x".repeat(1_048_577)))

            val failure = runCatching {
                HermesRestClient(OkHttpClient(), json)
                    .readFsText(config(server), "secret", "/tmp/a.txt")
            }.exceptionOrNull()
            assertTrue(failure is IOException)
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

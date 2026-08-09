package com.nousresearch.hermes.data

import com.nousresearch.hermes.audio.PcmAudioSink
import com.nousresearch.hermes.network.DashboardAuthClient
import com.nousresearch.hermes.network.DashboardSessionCredential
import com.nousresearch.hermes.network.HermesRestClient
import com.nousresearch.hermes.network.HermesVoiceStreamClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceRepositoryStreamTest {
    @Test
    fun `pre-audio stream connection failure falls back to one-shot speech`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val result = repository(server).streamSpeech(config(server), "default", "Read this") {
                error("A failed stream must not open a PCM sink")
            }

            assertEquals(StreamedSpeechResult.FALLBACK, result)
        }
    }

    @Test
    fun `streamed speech writes aligned pcm and completes its sink`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    webSocket.send("{\"type\":\"start\",\"sample_rate\":24000,\"channels\":1}")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("\"done\":true")) {
                        webSocket.send(ByteString.of(1, 2, 3))
                        webSocket.send(ByteString.of(4))
                        webSocket.send("{\"type\":\"end\"}")
                        webSocket.close(1000, "done")
                    }
                }
            }))
            server.start()
            val sink = RecordingPcmSink()
            val repository = repository(server)

            val result = repository.streamSpeech(config(server), "work/ops", "Read this") { format ->
                assertEquals(24_000, format.sampleRate)
                sink
            }

            assertEquals(StreamedSpeechResult.COMPLETED, result)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), sink.bytes.toByteArray())
            assertEquals(true, sink.ended)
            assertEquals("work/ops", server.takeRequest(2, TimeUnit.SECONDS)?.requestUrl?.queryParameter("profile"))
        }
    }

    private fun repository(server: MockWebServer): VoiceRepository {
        val client = OkHttpClient()
        val json = Json { ignoreUnknownKeys = true }
        val credentials = TestCredentials(DashboardSessionCredential("hermes_session_at", "token-value"))
        return VoiceRepository(
            rest = HermesRestClient(client, json, credentials),
            stream = HermesVoiceStreamClient(client, json, DashboardAuthClient(client, json, credentials)),
            credentials = credentials,
        )
    }

    private fun config(server: MockWebServer) = BackendConfig(
        id = "fake",
        label = "Fake Hermes",
        baseUrl = server.url("/").toString().replace("localhost", "127.0.0.1").trimEnd('/'),
        authMode = AuthMode.TOKEN,
        allowInsecurePrivateNetwork = true,
    )
}

private class RecordingPcmSink : PcmAudioSink {
    val bytes = mutableListOf<Byte>()
    var ended = false

    override fun write(pcm: ByteArray) {
        bytes += pcm.toList()
    }

    override fun end() {
        ended = true
    }
}

private class TestCredentials(private val credential: DashboardSessionCredential) : SessionCredentialStore {
    override fun put(backendId: String, cookie: DashboardSessionCredential) = Unit
    override fun get(backendId: String): DashboardSessionCredential = credential
    override fun remove(backendId: String) = Unit
}

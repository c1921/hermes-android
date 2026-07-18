package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesRestClientVoiceTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `voice transcription uses dashboard auth and canonical audio payload`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"ok":true,"transcript":"hello Hermes","provider":"local"}"""))
            val client = HermesRestClient(OkHttpClient(), json)

            val result = client.transcribeAudio(
                config(server),
                "hermes_session_at=abc",
                "data:audio/mp4;base64,dGVzdA==",
                "audio/mp4",
            )

            assertEquals("hello Hermes", result.transcript)
            assertEquals("local", result.provider)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/audio/transcribe", request.path)
            assertEquals("hermes_session_at=abc", request.getHeader("Cookie"))
            val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("audio/mp4", body.getValue("mime_type").jsonPrimitive.content)
            assertTrue(body.getValue("data_url").jsonPrimitive.content.startsWith("data:audio/mp4;base64,"))
        }
    }

    @Test
    fun `voice speech response preserves backend mime and data url`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """{"ok":true,"data_url":"data:audio/mpeg;base64,dGVzdA==","mime_type":"audio/mpeg","provider":"edge"}""",
                ),
            )

            val result = HermesRestClient(OkHttpClient(), json).speakText(
                config(server),
                "hermes_session_at=abc",
                "Read this response",
            )

            assertEquals("audio/mpeg", result.mimeType)
            assertEquals("edge", result.provider)
            val request = server.takeRequest()
            assertEquals("/api/audio/speak", request.path)
            assertEquals("Read this response", json.parseToJsonElement(request.body.readUtf8()).jsonObject.getValue("text").jsonPrimitive.content)
        }
    }

    private fun config(server: MockWebServer) = BackendConfig(
        id = "fake",
        label = "Fake Hermes",
        baseUrl = server.url("/").toString().trimEnd('/'),
        authMode = AuthMode.DASHBOARD_SESSION,
        allowInsecurePrivateNetwork = true,
    )
}

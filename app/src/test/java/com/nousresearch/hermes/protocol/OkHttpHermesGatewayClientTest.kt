package com.nousresearch.hermes.protocol

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpHermesGatewayClientTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `performs real websocket handshake and json rpc round trip`() = runBlocking {
        FakeHermesBackend(json).use { backend ->
            backend.start()
            val client = OkHttpHermesGatewayClient(OkHttpClient(), json)
            val config = BackendConfig(
                id = "fake",
                label = "Fake Hermes",
                baseUrl = backend.baseUrl,
                authMode = AuthMode.TOKEN,
                allowInsecurePrivateNetwork = true,
            )

            client.connect(config, "test-token")
            val result = client.request("session.list", buildJsonObject { })

            assertTrue(client.connectionState.value is GatewayConnectionState.Open)
            assertTrue(result.toString().contains("sessions"))
            assertEquals("session.list", backend.requests.single().getValue("method").toString().trim('"'))
            client.disconnect()
        }
    }

    @Test
    fun `replacing a socket reaches open without publishing an intentional close`() = runBlocking {
        FakeHermesBackend(json).use { backend ->
            backend.start(connectionCount = 2)
            val client = OkHttpHermesGatewayClient(OkHttpClient(), json)
            val config = BackendConfig(
                id = "fake",
                label = "Fake Hermes",
                baseUrl = backend.baseUrl,
                authMode = AuthMode.TOKEN,
                allowInsecurePrivateNetwork = true,
            )

            client.connect(config, "test-token")
            client.connect(config, "test-token")

            assertTrue(client.connectionState.value is GatewayConnectionState.Open)
            client.disconnect()
        }
    }

    @Test
    fun `steering round trip preserves the redirected text`() = runBlocking {
        FakeHermesBackend(json).use { backend ->
            backend.start()
            val client = OkHttpHermesGatewayClient(OkHttpClient(), json)
            val config = BackendConfig(
                id = "fake",
                label = "Fake Hermes",
                baseUrl = backend.baseUrl,
                authMode = AuthMode.TOKEN,
                allowInsecurePrivateNetwork = true,
            )

            client.connect(config, "test-token")
            val response = client.request(
                "session.steer",
                buildJsonObject {
                    put("session_id", "live-1")
                    put("text", "Check the failing test first")
                },
            )
            val result = json.decodeFromJsonElement(SessionSteerResult.serializer(), response)

            assertEquals("queued", result.status)
            assertEquals("Check the failing test first", result.text)
            client.disconnect()
        }
    }
}

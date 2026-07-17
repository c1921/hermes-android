package com.nousresearch.hermes.protocol

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpHermesGatewayClientTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `performs real websocket handshake and json rpc round trip`() = runTest {
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
}

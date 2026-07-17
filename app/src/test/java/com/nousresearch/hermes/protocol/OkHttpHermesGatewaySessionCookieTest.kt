package com.nousresearch.hermes.protocol

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.network.DashboardSessionCookie
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OkHttpHermesGatewaySessionCookieTest {
    @Test
    fun `websocket handshake reuses cookie and has no legacy token query`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) = Unit

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    },
                ),
            )
            server.start()
            val gateway = OkHttpHermesGatewayClient(OkHttpClient(), Json { ignoreUnknownKeys = true })
            val config = BackendConfig(
                id = "fake",
                label = "Fake Dashboard",
                baseUrl = server.url("/").toString().replace("localhost", "127.0.0.1").trimEnd('/'),
                authMode = AuthMode.DASHBOARD_SESSION,
                allowInsecurePrivateNetwork = true,
            )

            gateway.connect(config, DashboardSessionCookie("__Secure-hermes_session_at", "ws-session"))

            val request = server.takeRequest()
            assertEquals("__Secure-hermes_session_at=ws-session", request.getHeader("Cookie"))
            assertFalse(request.path.orEmpty().contains("token="))
            gateway.disconnect()
        }
    }
}

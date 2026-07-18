package com.nousresearch.hermes.protocol

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.network.DashboardAuthClient
import com.nousresearch.hermes.network.DashboardSessionCookie
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OkHttpHermesGatewaySessionCookieTest {
    @Test
    fun `dashboard cookie mints a single use ticket for websocket handshake`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
                    "/api/auth/ws-ticket" -> MockResponse().setBody("""{"ticket":"single-use-ticket","ttl_seconds":30}""")
                    "/api/ws" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) = Unit

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    })
                    else -> MockResponse().setResponseCode(404)
                }
            }
            server.start()
            val json = Json { ignoreUnknownKeys = true }
            val http = OkHttpClient()
            val gateway = OkHttpHermesGatewayClient(http, json, DashboardAuthClient(http, json))
            val config = BackendConfig(
                id = "fake",
                label = "Fake Dashboard",
                baseUrl = server.url("/").toString().replace("localhost", "127.0.0.1").trimEnd('/'),
                authMode = AuthMode.DASHBOARD_SESSION,
                allowInsecurePrivateNetwork = true,
            )

            gateway.connect(config, DashboardSessionCookie("__Secure-hermes_session_at", "ws-session"))

            val ticketRequest = server.takeRequest()
            val webSocketRequest = server.takeRequest()
            assertEquals("__Secure-hermes_session_at=ws-session", ticketRequest.getHeader("Cookie"))
            assertEquals("single-use-ticket", webSocketRequest.requestUrl?.queryParameter("ticket"))
            assertNull(webSocketRequest.getHeader("Cookie"))
            assertFalse(webSocketRequest.path.orEmpty().contains("token="))
            gateway.disconnect()
        }
    }
}

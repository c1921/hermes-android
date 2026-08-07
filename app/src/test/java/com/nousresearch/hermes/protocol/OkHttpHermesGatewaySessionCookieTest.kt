package com.nousresearch.hermes.protocol

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.data.SessionCredentialStore
import com.nousresearch.hermes.network.DashboardAuthClient
import com.nousresearch.hermes.network.DashboardSessionCredential
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
                    "/api/auth/ws-ticket" -> MockResponse()
                        .addHeader("Set-Cookie", "__Secure-hermes_session_at=ws-session-2; Path=/; Secure; HttpOnly")
                        .addHeader("Set-Cookie", "__Secure-hermes_session_rt=refresh-2; Path=/; Secure; HttpOnly")
                        .setBody("""{"ticket":"single-use-ticket","ttl_seconds":30}""")
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
            val config = BackendConfig(
                id = "fake",
                label = "Fake Dashboard",
                baseUrl = server.url("/").toString().replace("localhost", "127.0.0.1").trimEnd('/'),
                authMode = AuthMode.DASHBOARD_SESSION,
                allowInsecurePrivateNetwork = true,
            )
            val credential = checkNotNull(
                DashboardSessionCredential.fromSetCookieHeaders(
                    listOf(
                        "__Secure-hermes_session_at=ws-session",
                        "__Secure-hermes_session_rt=refresh-1",
                        "__Secure-hermes_session_provider=basic",
                    ),
                ),
            )
            val credentials = GatewayCredentialStore(credential)
            val gateway = OkHttpHermesGatewayClient(http, json, DashboardAuthClient(http, json, credentials))

            gateway.connect(config, credential)

            val ticketRequest = server.takeRequest()
            val webSocketRequest = server.takeRequest()
            assertEquals(
                "__Secure-hermes_session_at=ws-session; __Secure-hermes_session_rt=refresh-1; " +
                    "__Secure-hermes_session_provider=basic",
                ticketRequest.getHeader("Cookie"),
            )
            assertEquals(
                "__Secure-hermes_session_at=ws-session-2; __Secure-hermes_session_rt=refresh-2; " +
                    "__Secure-hermes_session_provider=basic",
                credentials.saved?.headerValue,
            )
            assertEquals("single-use-ticket", webSocketRequest.requestUrl?.queryParameter("ticket"))
            assertNull(webSocketRequest.getHeader("Cookie"))
            assertFalse(webSocketRequest.path.orEmpty().contains("token="))
            gateway.disconnect()
        }
    }
}

private class GatewayCredentialStore(initial: DashboardSessionCredential) : SessionCredentialStore {
    var saved: DashboardSessionCredential? = initial
    override fun put(backendId: String, cookie: DashboardSessionCredential) { saved = cookie }
    override fun get(backendId: String): DashboardSessionCredential? = saved
    override fun remove(backendId: String) { saved = null }
}

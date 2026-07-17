package com.nousresearch.hermes.data

import com.nousresearch.hermes.network.DashboardAuthClient
import com.nousresearch.hermes.network.DashboardSessionCookie
import com.nousresearch.hermes.network.HermesRestClient
import com.nousresearch.hermes.protocol.OkHttpHermesGatewayClient
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
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardBackendConnectorTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `successful login REST and websocket validation save only session cookie`() = runBlocking {
        FakeDashboard().use { dashboard ->
            dashboard.start()
            val credentials = RecordingSessionCredentialStore()
            val backends = RecordingBackendSaver()
            val connector = connector(dashboard, credentials, backends)

            connector.loginValidateAndSave(config(dashboard), "admin", "do-not-persist")

            assertEquals("hermes_session_at=session-value", credentials.saved?.headerValue)
            assertEquals(AuthMode.DASHBOARD_SESSION, backends.saved?.authMode)
            assertFalse(credentials.toString().contains("do-not-persist"))
            assertFalse(backends.toString().contains("do-not-persist"))
            assertEquals("hermes_session_at=session-value", dashboard.statusCookie)
            assertEquals("hermes_session_at=session-value", dashboard.webSocketCookie)
        }
    }

    @Test
    fun `REST validation failure does not save backend or cookie`() = runBlocking {
        FakeDashboard(statusCode = 500).use { dashboard ->
            dashboard.start()
            val credentials = RecordingSessionCredentialStore()
            val backends = RecordingBackendSaver()

            val failure = runCatching {
                connector(dashboard, credentials, backends).loginValidateAndSave(config(dashboard), "admin", "password")
            }.exceptionOrNull()

            assertTrue(failure != null)
            assertNull(credentials.saved)
            assertNull(backends.saved)
        }
    }

    @Test
    fun `websocket validation failure does not save backend or cookie`() = runBlocking {
        FakeDashboard(webSocketAccepted = false).use { dashboard ->
            dashboard.start()
            val credentials = RecordingSessionCredentialStore()
            val backends = RecordingBackendSaver()

            val failure = runCatching {
                connector(dashboard, credentials, backends).loginValidateAndSave(config(dashboard), "admin", "password")
            }.exceptionOrNull()

            assertTrue(failure != null)
            assertNull(credentials.saved)
            assertNull(backends.saved)
        }
    }

    @Test
    fun `expired saved cookie becomes reconnect required`() = runBlocking {
        FakeDashboard(statusCode = 401).use { dashboard ->
            dashboard.start()
            val credentials = RecordingSessionCredentialStore()
            val backends = RecordingBackendSaver()
            val connector = connector(dashboard, credentials, backends)

            val failure = runCatching {
                connector.validateSaved(config(dashboard), DashboardSessionCookie("hermes_session_at", "expired"))
            }.exceptionOrNull()

            assertTrue(failure is ReconnectRequiredException)
            assertTrue(failure?.message.orEmpty().contains("reconnect", ignoreCase = true))
        }
    }

    @Test
    fun `legacy token backend requires reconnect and is never reinterpreted`() = runBlocking {
        FakeDashboard().use { dashboard ->
            dashboard.start()
            val connector = connector(dashboard, RecordingSessionCredentialStore(), RecordingBackendSaver())
            val legacy = config(dashboard).copy(authMode = AuthMode.TOKEN)

            val failure = runCatching {
                connector.validateSaved(legacy, DashboardSessionCookie("hermes_session_at", "legacy-token"))
            }.exceptionOrNull()

            assertTrue(failure is ReconnectRequiredException)
            assertTrue(failure?.message.orEmpty().contains("legacy", ignoreCase = true))
            assertNull(dashboard.statusCookie)
        }
    }

    private fun connector(
        dashboard: FakeDashboard,
        credentials: RecordingSessionCredentialStore,
        backends: RecordingBackendSaver,
    ) = DashboardBackendConnector(
        DashboardAuthClient(OkHttpClient(), json),
        HermesRestClient(OkHttpClient(), json),
        OkHttpHermesGatewayClient(OkHttpClient(), json),
        credentials,
        backends,
    )

    private fun config(dashboard: FakeDashboard) = BackendConfig(
        id = "dashboard",
        label = "Dashboard",
        baseUrl = dashboard.baseUrl,
        authMode = AuthMode.DASHBOARD_SESSION,
        allowInsecurePrivateNetwork = true,
    )
}

private class RecordingSessionCredentialStore : SessionCredentialStore {
    var saved: DashboardSessionCookie? = null
    override fun put(backendId: String, cookie: DashboardSessionCookie) { saved = cookie }
    override fun get(backendId: String): DashboardSessionCookie? = saved
    override fun remove(backendId: String) { saved = null }
    override fun toString(): String = "RecordingSessionCredentialStore(saved=${saved?.headerValue})"
}

private class RecordingBackendSaver : BackendSaver {
    var saved: BackendConfig? = null
    override suspend fun save(config: BackendConfig) { saved = config }
    override fun toString(): String = "RecordingBackendSaver(saved=$saved)"
}

private class FakeDashboard(
    private val statusCode: Int = 200,
    private val webSocketAccepted: Boolean = true,
) : AutoCloseable {
    private val server = MockWebServer()
    var statusCookie: String? = null
    var webSocketCookie: String? = null
    val baseUrl: String get() = server.url("/").toString().replace("localhost", "127.0.0.1").trimEnd('/')

    fun start() {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
                "/auth/password-login" -> MockResponse()
                    .setResponseCode(200)
                    .addHeader("Set-Cookie", "hermes_session_at=session-value; Path=/; HttpOnly")
                    .setBody("""{"ok":true}""")
                "/api/status" -> {
                    statusCookie = request.getHeader("Cookie")
                    MockResponse().setResponseCode(statusCode).setBody(
                        if (statusCode == 200) """{"status":"ok","hermes_version":"0.18.2"}""" else """{"detail":"Session expired"}""",
                    )
                }
                "/api/ws" -> {
                    webSocketCookie = request.getHeader("Cookie")
                    if (!webSocketAccepted) MockResponse().setResponseCode(401)
                    else MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) = Unit

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(code, reason)
                        }
                    })
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    override fun close() = server.shutdown()
}

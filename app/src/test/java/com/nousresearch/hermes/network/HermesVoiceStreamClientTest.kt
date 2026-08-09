package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.data.SessionCredentialStore
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesVoiceStreamClientTest {
    @Test
    fun `dashboard sessions use a ticket and encoded profile without cookie on the websocket`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = streamDispatcher()
            server.start()
            val cookie = checkNotNull(
                DashboardSessionCredential.fromSetCookieHeaders(
                    listOf("hermes_session_at=session-value", "hermes_session_provider=basic"),
                ),
            )
            val client = HermesVoiceStreamClient(
                OkHttpClient(),
                Json { ignoreUnknownKeys = true },
                DashboardAuthClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, RecordingCredentials(cookie)),
            )
            val events = CopyOnWriteArrayList<VoiceStreamEvent>()

            val session = client.open(config(server, AuthMode.DASHBOARD_SESSION), "work/ops", sessionCookie = cookie) {
                events += it
            }
            session.stop()

            val ticketRequest = server.takeRequest(2, TimeUnit.SECONDS)
            val socketRequest = server.takeRequest(2, TimeUnit.SECONDS)
            assertEquals("hermes_session_at=session-value; hermes_session_provider=basic", ticketRequest?.getHeader("Cookie"))
            assertEquals("single-use-ticket", socketRequest?.requestUrl?.queryParameter("ticket"))
            assertEquals("work/ops", socketRequest?.requestUrl?.queryParameter("profile"))
            assertFalse(socketRequest?.headers?.names()?.contains("Cookie") == true)
            assertTrue(events.any { it is VoiceStreamEvent.Stopped })
        }
    }

    @Test
    fun `token auth sends token and streams aligned pcm through end`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = streamDispatcher(sendAudioOnDone = true)
            server.start()
            val client = HermesVoiceStreamClient(OkHttpClient(), Json, unusedAuthClient())
            val events = CopyOnWriteArrayList<VoiceStreamEvent>()

            val session = client.open(
                config(server, AuthMode.TOKEN),
                "default",
                token = "token-value",
            ) { events += it }
            assertTrue(session.append("Hello Hermes."))
            assertTrue(session.finish())
            awaitEvent(events) { it is VoiceStreamEvent.Ended }

            val request = server.takeRequest(2, TimeUnit.SECONDS)
            assertEquals("token-value", request?.requestUrl?.queryParameter("token"))
            assertEquals("default", request?.requestUrl?.queryParameter("profile"))
            assertArrayEquals(
                byteArrayOf(1, 2),
                (events.first { it is VoiceStreamEvent.Audio } as VoiceStreamEvent.Audio).pcm,
            )
            assertArrayEquals(
                byteArrayOf(3, 4, 5, 6),
                (events.filterIsInstance<VoiceStreamEvent.Audio>()[1]).pcm,
            )
        }
    }

    @Test
    fun `fallback is terminal and does not expose provider payload`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = streamDispatcher(sendFallback = true)
            server.start()
            val client = HermesVoiceStreamClient(OkHttpClient(), Json, unusedAuthClient())
            val events = CopyOnWriteArrayList<VoiceStreamEvent>()

            val session = client.open(config(server, AuthMode.OAUTH), "default", token = "oauth-token") {
                events += it
            }
            awaitEvent(events) { it is VoiceStreamEvent.Fallback }

            assertFalse(session.append("must not send"))
            assertEquals(1, events.count { it is VoiceStreamEvent.Fallback })
        }
    }

    @Test
    fun `invalid start metadata fails before audio delivery`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = streamDispatcher(sendInvalidStart = true)
            server.start()
            val client = HermesVoiceStreamClient(OkHttpClient(), Json, unusedAuthClient())
            val events = CopyOnWriteArrayList<VoiceStreamEvent>()

            client.open(config(server, AuthMode.TOKEN), "default", token = "token") { events += it }
            awaitEvent(events) { it is VoiceStreamEvent.Failed }

            assertEquals(VoiceStreamFailure.INVALID_START, (events.last() as VoiceStreamEvent.Failed).reason)
            assertFalse(events.any { it is VoiceStreamEvent.Audio })
        }
    }

    private fun streamDispatcher(
        sendAudioOnDone: Boolean = false,
        sendFallback: Boolean = false,
        sendInvalidStart: Boolean = false,
    ) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
            "/api/auth/ws-ticket" -> MockResponse().setBody("{\"ticket\":\"single-use-ticket\"}")
            "/api/audio/speak-stream" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    when {
                        sendFallback -> {
                            webSocket.send("{\"type\":\"fallback\"}")
                            webSocket.close(1000, "fallback")
                        }
                        sendInvalidStart -> {
                            webSocket.send("{\"type\":\"start\",\"sample_rate\":0,\"channels\":1}")
                            webSocket.close(1000, "invalid")
                        }
                        else -> webSocket.send("{\"type\":\"start\",\"sample_rate\":24000,\"channels\":1}")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (sendAudioOnDone && text.contains("\"done\":true")) {
                        webSocket.send(ByteString.of(1, 2, 3))
                        webSocket.send(ByteString.of(4, 5, 6))
                        webSocket.send("{\"type\":\"end\"}")
                        webSocket.close(1000, "done")
                    } else if (text.contains("\"stop\":true")) {
                        webSocket.close(1000, "stopped")
                    }
                }
            })
            else -> MockResponse().setResponseCode(404)
        }
    }

    private fun config(server: MockWebServer, authMode: AuthMode) = BackendConfig(
        id = "fake",
        label = "Fake Hermes",
        baseUrl = server.url("/").toString().replace("localhost", "127.0.0.1").trimEnd('/'),
        authMode = authMode,
        allowInsecurePrivateNetwork = true,
    )

    private fun unusedAuthClient() = DashboardAuthClient(
        OkHttpClient(),
        Json,
        RecordingCredentials(DashboardSessionCredential("hermes_session_at", "unused")),
    )

    private fun awaitEvent(events: List<VoiceStreamEvent>, predicate: (VoiceStreamEvent) -> Boolean) {
        repeat(100) {
            if (events.any(predicate)) return
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting for stream event; events=$events")
    }
}

private class RecordingCredentials(initial: DashboardSessionCredential) : SessionCredentialStore {
    var saved = initial
    override fun put(backendId: String, cookie: DashboardSessionCredential) { saved = cookie }
    override fun get(backendId: String): DashboardSessionCredential = saved
    override fun remove(backendId: String) { }
}

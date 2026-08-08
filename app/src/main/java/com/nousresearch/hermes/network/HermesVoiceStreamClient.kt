package com.nousresearch.hermes.network

import com.nousresearch.hermes.audio.PcmFrameAssembler
import com.nousresearch.hermes.audio.VoicePcmFormat
import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

sealed interface VoiceStreamEvent {
    data class Started(val format: VoicePcmFormat) : VoiceStreamEvent
    data class Audio(val pcm: ByteArray) : VoiceStreamEvent
    data object Fallback : VoiceStreamEvent
    data object Ended : VoiceStreamEvent
    data object Stopped : VoiceStreamEvent
    data class Disconnected(val code: Int) : VoiceStreamEvent
    data class Failed(val reason: VoiceStreamFailure) : VoiceStreamEvent
}

enum class VoiceStreamFailure {
    CONNECTION,
    INVALID_START,
    INVALID_AUDIO,
    UNEXPECTED_SERVER_FRAME,
}

class VoiceStreamException(val reason: VoiceStreamFailure) : IOException(
    "Hermes voice stream failed: ${reason.name.lowercase()}",
)

interface VoiceStreamSession {
    fun append(text: String): Boolean
    fun finish(): Boolean
    fun stop(): Boolean
}

fun interface VoiceStreamListener {
    fun onEvent(event: VoiceStreamEvent)
}

@Singleton
class HermesVoiceStreamClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val authClient: DashboardAuthClient,
) {
    suspend fun open(
        config: BackendConfig,
        profile: String,
        token: String? = null,
        sessionCookie: DashboardSessionCredential? = null,
        listener: VoiceStreamListener,
    ): VoiceStreamSession = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(streamUrl(config, profile, token, sessionCookie))
            .header("User-Agent", "Hermes-Android/0.1")
            .build()
        val opened = CompletableDeferred<Unit>()
        val session = Session(opened, listener)
        val socket = client.newWebSocket(request, session)
        session.attach(socket)
        try {
            withTimeout(CONNECT_TIMEOUT_MILLIS) { opened.await() }
            session
        } catch (error: Throwable) {
            session.abort()
            if (error is CancellationException) throw error
            if (error is VoiceStreamException) throw error
            throw VoiceStreamException(VoiceStreamFailure.CONNECTION)
        }
    }

    private suspend fun streamUrl(
        config: BackendConfig,
        profile: String,
        token: String?,
        sessionCookie: DashboardSessionCredential?,
    ): HttpUrl {
        val cleanProfile = profile.trim()
        require(cleanProfile.isNotBlank()) { "A Hermes profile is required for voice streaming" }
        val base = TransportPolicy.validate(config).getOrThrow().toString().trimEnd('/').toHttpUrl()
        val builder = base.newBuilder()
            .addPathSegments("api/audio/speak-stream")
            .addQueryParameter("profile", cleanProfile)
        when (config.authMode) {
            AuthMode.DASHBOARD_SESSION -> builder.addQueryParameter(
                "ticket",
                authClient.mintWebSocketTicket(config, requireNotNull(sessionCookie) {
                    "Dashboard voice streaming requires a session cookie"
                }),
            )
            AuthMode.TOKEN,
            AuthMode.OAUTH,
            -> builder.addQueryParameter(
                "token",
                requireNotNull(token) { "Token voice streaming requires a bearer token" }
                    .also { require(it.isNotBlank()) { "Token voice streaming requires a bearer token" } },
            )
        }
        return builder.build()
    }

    private inner class Session(
        private val opened: CompletableDeferred<Unit>,
        private val listener: VoiceStreamListener,
    ) : WebSocketListener(), VoiceStreamSession {
        private val terminal = AtomicBoolean(false)
        private val finished = AtomicBoolean(false)
        private var socket: WebSocket? = null
        private var assembler: PcmFrameAssembler? = null

        fun attach(socket: WebSocket) {
            this.socket = socket
        }

        fun abort() {
            terminal.set(true)
            socket?.cancel()
        }

        override fun append(text: String): Boolean {
            if (text.isBlank() || terminal.get() || finished.get()) return false
            return send(buildJsonObject { put("text", text) })
        }

        override fun finish(): Boolean {
            if (terminal.get() || !finished.compareAndSet(false, true)) return false
            return send(buildJsonObject { put("done", true) })
        }

        override fun stop(): Boolean {
            if (!terminal.compareAndSet(false, true)) return false
            socket?.send(encode(buildJsonObject { put("stop", true) }))
            socket?.close(1000, "voice stopped")
            listener.onEvent(VoiceStreamEvent.Stopped)
            return true
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (terminal.get()) return
            val frame = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: return fail(VoiceStreamFailure.UNEXPECTED_SERVER_FRAME)
            when (runCatching { frame["type"]?.jsonPrimitive?.content }.getOrNull()) {
                "start" -> start(frame)
                "fallback" -> terminalEvent(VoiceStreamEvent.Fallback)
                "end" -> end()
                else -> fail(VoiceStreamFailure.UNEXPECTED_SERVER_FRAME)
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (terminal.get()) return
            val output = try {
                val active = assembler ?: throw IllegalArgumentException("PCM arrived before start")
                active.append(bytes.toByteArray())
            } catch (_: IllegalArgumentException) {
                fail(VoiceStreamFailure.INVALID_AUDIO)
                return
            }
            if (!terminal.get() && output.isNotEmpty()) listener.onEvent(VoiceStreamEvent.Audio(output))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val failure = VoiceStreamException(VoiceStreamFailure.CONNECTION)
            if (!opened.isCompleted) opened.completeExceptionally(failure)
            fail(VoiceStreamFailure.CONNECTION)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!opened.isCompleted) opened.completeExceptionally(VoiceStreamException(VoiceStreamFailure.CONNECTION))
            if (terminal.compareAndSet(false, true)) listener.onEvent(VoiceStreamEvent.Disconnected(code))
        }

        private fun start(frame: JsonElement) {
            if (assembler != null || terminal.get()) return fail(VoiceStreamFailure.INVALID_START)
            val format = runCatching {
                VoicePcmFormat(
                    sampleRate = frame.jsonObject["sample_rate"]?.jsonPrimitive?.intOrNull
                        ?: error("missing sample rate"),
                    channels = frame.jsonObject["channels"]?.jsonPrimitive?.intOrNull
                        ?: error("missing channels"),
                )
            }.getOrNull() ?: return fail(VoiceStreamFailure.INVALID_START)
            assembler = PcmFrameAssembler(format)
            listener.onEvent(VoiceStreamEvent.Started(format))
        }

        private fun end() {
            val active = assembler ?: return fail(VoiceStreamFailure.INVALID_START)
            try {
                active.finish()
            } catch (_: IllegalArgumentException) {
                fail(VoiceStreamFailure.INVALID_AUDIO)
                return
            }
            terminalEvent(VoiceStreamEvent.Ended)
        }

        private fun send(frame: JsonElement): Boolean {
            val socket = socket ?: return false
            val accepted = socket.send(encode(frame))
            if (!accepted) fail(VoiceStreamFailure.CONNECTION)
            return accepted
        }

        private fun encode(frame: JsonElement): String =
            json.encodeToString(JsonElement.serializer(), frame)

        private fun fail(reason: VoiceStreamFailure) {
            terminalEvent(VoiceStreamEvent.Failed(reason))
        }

        private fun terminalEvent(event: VoiceStreamEvent) {
            if (!terminal.compareAndSet(false, true)) return
            listener.onEvent(event)
            socket?.close(1000, "voice stream complete")
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000L
    }
}

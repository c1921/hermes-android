package com.nousresearch.hermes.protocol

import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.network.TransportPolicy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@Singleton
class OkHttpHermesGatewayClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) : HermesGatewayClient {
    private val requestIds = AtomicLong(0)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()
    private val mutableConnectionState = MutableStateFlow<GatewayConnectionState>(GatewayConnectionState.Idle)
    private val mutableEvents = MutableSharedFlow<GatewayEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var socket: WebSocket? = null

    override val connectionState = mutableConnectionState.asStateFlow()
    override val events = mutableEvents.asSharedFlow()

    override suspend fun connect(config: BackendConfig, token: String) {
        disconnect()
        mutableConnectionState.value = GatewayConnectionState.Connecting(attempt = 1)
        val opened = CompletableDeferred<Unit>()
        val request = Request.Builder()
            .url(gatewayUrl(config, token))
            .header("User-Agent", "Hermes-Android/0.1")
            .build()
        val nextSocket = client.newWebSocket(request, listener(opened))
        socket = nextSocket
        try {
            opened.await()
        } catch (error: Throwable) {
            if (socket === nextSocket) socket = null
            nextSocket.cancel()
            throw error
        }
    }

    override suspend fun disconnect() {
        val previous = socket
        socket = null
        previous?.close(1000, "client disconnect")
        failPending(HermesRpcException("Hermes gateway disconnected"))
        mutableConnectionState.value = GatewayConnectionState.Closed("client disconnect")
    }

    override suspend fun request(method: String, params: JsonElement): JsonElement {
        val activeSocket = socket ?: throw HermesRpcException("Hermes gateway is not connected")
        val id = requestIds.incrementAndGet()
        val deferred = CompletableDeferred<JsonElement>()
        pending[id] = deferred
        val frame = JsonRpcRequest(id = id, method = method, params = params)
        val accepted = activeSocket.send(json.encodeToString(JsonRpcRequest.serializer(), frame))
        if (!accepted) {
            pending.remove(id)
            throw HermesRpcException("Hermes gateway rejected the request")
        }
        return try {
            deferred.await()
        } finally {
            pending.remove(id)
        }
    }

    private fun listener(opened: CompletableDeferred<Unit>) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (socket !== webSocket) return
            mutableConnectionState.value = GatewayConnectionState.Open
            opened.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (socket !== webSocket) return
            val frame = runCatching { json.decodeFromString(JsonRpcFrame.serializer(), text) }
                .getOrElse { return }
            frame.params?.takeIf { frame.method == "event" }?.let {
                mutableEvents.tryEmit(it)
                return
            }
            val id = frame.id ?: return
            val call = pending.remove(id) ?: return
            frame.error?.let {
                call.completeExceptionally(HermesRpcException(it.message, it.code))
            } ?: call.complete(frame.result ?: kotlinx.serialization.json.JsonNull)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (socket !== webSocket) return
            socket = null
            failPending(HermesRpcException("Hermes gateway closed: $reason"))
            mutableConnectionState.value = GatewayConnectionState.Closed(reason)
            if (!opened.isCompleted) opened.completeExceptionally(HermesRpcException(reason))
        }

        override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
            if (socket !== webSocket) return
            socket = null
            failPending(throwable)
            mutableConnectionState.value = GatewayConnectionState.Failed(
                throwable.message ?: "Hermes gateway connection failed",
            )
            if (!opened.isCompleted) opened.completeExceptionally(throwable)
        }
    }

    private fun gatewayUrl(config: BackendConfig, token: String): HttpUrl {
        val uri = TransportPolicy.validate(config).getOrThrow()
        val base = uri.toString().trimEnd('/').toHttpUrl()
        return base.newBuilder()
            .scheme(if (base.isHttps) "wss" else "ws")
            .addPathSegments("api/ws")
            .addQueryParameter("token", token)
            .build()
    }

    private fun failPending(error: Throwable) {
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
    }
}

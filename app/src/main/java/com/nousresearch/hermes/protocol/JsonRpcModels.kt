package com.nousresearch.hermes.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JsonRpcRequest(
    val id: Long,
    val method: String,
    val params: JsonElement,
    val jsonrpc: String = "2.0",
)

@Serializable
data class JsonRpcError(
    val code: Int? = null,
    val message: String = "Hermes request failed",
    val data: JsonElement? = null,
)

@Serializable
data class JsonRpcFrame(
    val id: Long? = null,
    val method: String? = null,
    val params: GatewayEvent? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
    val jsonrpc: String? = null,
)

@Serializable
data class GatewayEvent(
    val type: String,
    @SerialName("session_id") val sessionId: String? = null,
    val payload: JsonElement? = null,
)

class HermesRpcException(
    message: String,
    val rpcCode: Int? = null,
) : Exception(message)

sealed interface GatewayConnectionState {
    data object Idle : GatewayConnectionState
    data class Connecting(val attempt: Int) : GatewayConnectionState
    data object Open : GatewayConnectionState
    data class Reconnecting(val attempt: Int, val retryInMillis: Long) : GatewayConnectionState
    data class Closed(val reason: String?) : GatewayConnectionState
    data class Failed(val message: String) : GatewayConnectionState
}


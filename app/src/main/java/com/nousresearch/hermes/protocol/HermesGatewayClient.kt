package com.nousresearch.hermes.protocol

import com.nousresearch.hermes.data.BackendConfig
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonElement

interface HermesGatewayClient {
    val connectionState: StateFlow<GatewayConnectionState>
    val events: SharedFlow<GatewayEvent>

    suspend fun connect(config: BackendConfig, token: String)
    suspend fun disconnect()
    suspend fun request(method: String, params: JsonElement): JsonElement
}

suspend inline fun <reified P, reified R> HermesGatewayClient.request(
    method: String,
    params: P,
    serializer: SerializationStrategy<P>,
    json: kotlinx.serialization.json.Json,
): R {
    val element = json.encodeToJsonElement(serializer, params)
    return json.decodeFromJsonElement(request(method, element))
}


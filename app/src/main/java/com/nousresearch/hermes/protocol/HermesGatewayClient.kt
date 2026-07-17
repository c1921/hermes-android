package com.nousresearch.hermes.protocol

import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.network.DashboardSessionCookie
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

interface HermesGatewayClient {
    val connectionState: StateFlow<GatewayConnectionState>
    val events: SharedFlow<GatewayEvent>

    suspend fun connect(config: BackendConfig, token: String)
    suspend fun connect(config: BackendConfig, cookie: DashboardSessionCookie) = connect(config, cookie.headerValue)
    suspend fun disconnect()
    suspend fun request(method: String, params: JsonElement): JsonElement
}

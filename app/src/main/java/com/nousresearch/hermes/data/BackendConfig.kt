package com.nousresearch.hermes.data

import kotlinx.serialization.Serializable

@Serializable
data class BackendConfig(
    val id: String,
    val label: String,
    val baseUrl: String,
    val authMode: AuthMode,
    val allowInsecurePrivateNetwork: Boolean = false,
    val lastHermesVersion: String? = null,
)

@Serializable
enum class AuthMode {
    TOKEN,
    OAUTH,
    DASHBOARD_SESSION,
}

interface BackendSaver {
    suspend fun save(config: BackendConfig)
}

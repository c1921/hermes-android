package com.nousresearch.hermes.data

import com.nousresearch.hermes.network.DashboardAuthClient
import com.nousresearch.hermes.network.DashboardSessionCredential
import com.nousresearch.hermes.network.HermesHttpException
import com.nousresearch.hermes.network.HermesRestClient
import com.nousresearch.hermes.protocol.HermesGatewayClient
import com.nousresearch.hermes.protocol.StatusResponse
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

interface SessionCredentialStore {
    fun put(backendId: String, cookie: DashboardSessionCredential)
    fun get(backendId: String): DashboardSessionCredential?
    fun remove(backendId: String)
}

class DashboardBackendConnector @Inject constructor(
    private val authClient: DashboardAuthClient,
    private val restClient: HermesRestClient,
    private val gateway: HermesGatewayClient,
    private val credentials: SessionCredentialStore,
    private val backends: BackendSaver,
) {
    suspend fun loginValidateAndSave(
        config: BackendConfig,
        username: String,
        password: String,
    ): StatusResponse {
        require(config.authMode == AuthMode.DASHBOARD_SESSION) { "Dashboard session authentication is required" }
        val cookie = authClient.login(config, username.trim(), password)
        val status = validate(config, cookie)
        gateway.disconnect()
        credentials.put(config.id, cookie)
        try {
            backends.save(config.copy(lastHermesVersion = status.hermesVersion ?: status.version))
        } catch (error: Throwable) {
            credentials.remove(config.id)
            throw error
        }
        return status
    }

    suspend fun validateSaved(config: BackendConfig, cookie: DashboardSessionCredential): StatusResponse {
        if (config.authMode != AuthMode.DASHBOARD_SESSION) {
            throw ReconnectRequiredException("Legacy token-only backend records must reconnect with dashboard credentials.")
        }
        return try {
            validate(config, cookie).also { credentials.put(config.id, cookie) }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (error is HermesHttpException && error.statusCode != 401 && error.statusCode != 403) throw error
            throw ReconnectRequiredException("Dashboard session expired or was rejected; reconnect is required.", error)
        }
    }

    private suspend fun validate(config: BackendConfig, cookie: DashboardSessionCredential): StatusResponse {
        val status = restClient.status(config, cookie)
        require(status.status == "ok" || status.status == "ready" || status.hermesVersion != null || status.version != null) {
            "The dashboard answered but did not identify a ready Hermes backend"
        }
        gateway.connect(config, cookie)
        return status
    }
}

class ReconnectRequiredException(message: String, cause: Throwable? = null) : IOException(message, cause)

package com.nousresearch.hermes.data

import com.nousresearch.hermes.domain.TimelineReducer
import com.nousresearch.hermes.domain.TimelineState
import com.nousresearch.hermes.network.HermesRestClient
import com.nousresearch.hermes.protocol.GatewayConnectionState
import com.nousresearch.hermes.protocol.HermesGatewayClient
import com.nousresearch.hermes.protocol.SessionCreateResult
import com.nousresearch.hermes.protocol.SessionResumeResult
import com.nousresearch.hermes.protocol.StatusResponse
import com.nousresearch.hermes.protocol.StoredSession
import com.nousresearch.hermes.security.SecureTokenStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class HermesState(
    val backend: BackendConfig? = null,
    val status: StatusResponse? = null,
    val sessions: List<StoredSession> = emptyList(),
    val activeStoredSession: StoredSession? = null,
    val runtimeSessionId: String? = null,
    val timeline: TimelineState = TimelineState(),
    val loading: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
)

@Singleton
class HermesRepository @Inject constructor(
    private val backendRegistry: BackendRegistry,
    private val tokenStore: SecureTokenStore,
    private val restClient: HermesRestClient,
    private val gateway: HermesGatewayClient,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(HermesState())
    val state = mutableState.asStateFlow()
    val connectionState = gateway.connectionState

    init {
        scope.launch {
            combine(backendRegistry.backends, backendRegistry.activeBackendId) { backends, activeId ->
                backends.firstOrNull { it.id == activeId }
            }.collectLatest { backend ->
                if (backend == null) {
                    gateway.disconnect()
                    mutableState.value = HermesState()
                } else {
                    connect(backend)
                }
            }
        }
        scope.launch {
            gateway.events.collect { event ->
                val current = mutableState.value
                val runtimeId = current.runtimeSessionId
                if (event.sessionId == null || runtimeId == null || event.sessionId == runtimeId) {
                    mutableState.value = current.copy(
                        timeline = TimelineReducer.reduce(current.timeline, event),
                    )
                }
            }
        }
    }

    suspend fun testAndSave(config: BackendConfig, token: String): StatusResponse {
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        return try {
            val status = restClient.status(config, token)
            require(status.status == "ok" || status.status == "ready" || status.hermesVersion != null || status.version != null) {
                "The server answered, but did not identify itself as a ready Hermes backend"
            }
            gateway.connect(config, token)
            gateway.disconnect()
            tokenStore.put(config.id, token)
            backendRegistry.save(config.copy(lastHermesVersion = status.hermesVersion ?: status.version))
            status
        } catch (error: Throwable) {
            fail(error)
            throw error
        }
    }

    suspend fun refreshSessions() {
        val (backend, token) = activeCredentials()
        setLoading(true)
        runCatching { restClient.sessions(backend, token).sessions }
            .onSuccess { sessions -> mutableState.value = mutableState.value.copy(sessions = sessions, loading = false, error = null) }
            .onFailure { fail(it) }
    }

    suspend fun openSession(session: StoredSession) {
        val (_, _) = activeCredentials()
        setLoading(true)
        val current = mutableState.value
        mutableState.value = current.copy(activeStoredSession = session, timeline = TimelineState(), error = null)
        runCatching {
            val prefetch = restClient.sessionMessages(
                requireNotNull(mutableState.value.backend),
                requireNotNull(tokenStore.get(requireNotNull(mutableState.value.backend).id)),
                session.durableId,
                session.profile,
            )
            val resumed = gateway.request(
                "session.resume",
                buildJsonObject {
                    put("session_id", session.durableId)
                    put("cols", 96)
                    put("source", "android")
                    session.profile?.let { put("profile", it) }
                },
            )
            val decoded = json.decodeFromJsonElement(SessionResumeResult.serializer(), resumed)
            prefetch to decoded
        }.onSuccess { (prefetch, resumed) ->
            mutableState.value = mutableState.value.copy(
                runtimeSessionId = resumed.runtimeSessionId,
                timeline = TimelineReducer.hydrate(prefetch.messages.ifEmpty { resumed.messages }),
                loading = false,
                error = null,
            )
        }.onFailure(::fail)
    }

    suspend fun newSession(profile: String? = null) {
        activeCredentials()
        setLoading(true)
        runCatching {
            val result = gateway.request(
                "session.create",
                buildJsonObject {
                    put("cols", 96)
                    put("source", "android")
                    profile?.let { put("profile", it) }
                },
            )
            json.decodeFromJsonElement(SessionCreateResult.serializer(), result)
        }.onSuccess { created ->
            mutableState.value = mutableState.value.copy(
                activeStoredSession = null,
                runtimeSessionId = created.runtimeSessionId,
                timeline = TimelineReducer.hydrate(created.messages),
                loading = false,
                error = null,
            )
        }.onFailure(::fail)
    }

    suspend fun send(text: String) {
        val cleaned = text.trim()
        require(cleaned.isNotEmpty())
        val sessionId = mutableState.value.runtimeSessionId ?: run {
            newSession()
            requireNotNull(mutableState.value.runtimeSessionId)
        }
        val optimisticId = "local:${UUID.randomUUID()}"
        mutableState.value = mutableState.value.copy(
            timeline = TimelineReducer.appendUserMessage(mutableState.value.timeline, optimisticId, cleaned),
            sending = true,
            error = null,
        )
        runCatching {
            gateway.request(
                "prompt.submit",
                buildJsonObject {
                    put("session_id", sessionId)
                    put("text", cleaned)
                },
            )
        }.onSuccess {
            mutableState.value = mutableState.value.copy(sending = false)
        }.onFailure(::fail)
    }

    suspend fun interrupt() {
        val sessionId = mutableState.value.runtimeSessionId ?: return
        gateway.request("session.interrupt", buildJsonObject { put("session_id", sessionId) })
    }

    suspend fun respondToApproval(choice: String) {
        val request = mutableState.value.timeline.approval ?: return
        gateway.request(
            "approval.respond",
            buildJsonObject {
                put("session_id", request.sessionId)
                put("choice", choice)
            },
        )
        mutableState.value = mutableState.value.copy(
            timeline = TimelineReducer.clearApproval(mutableState.value.timeline),
        )
    }

    suspend fun respondToClarification(answer: String) {
        val request = mutableState.value.timeline.clarification ?: return
        gateway.request(
            "clarify.respond",
            buildJsonObject {
                put("request_id", request.requestId)
                put("answer", answer)
            },
        )
        mutableState.value = mutableState.value.copy(
            timeline = TimelineReducer.clearClarification(mutableState.value.timeline),
        )
    }

    suspend fun archiveActive() {
        val session = mutableState.value.activeStoredSession ?: return
        val (backend, token) = activeCredentials()
        restClient.archiveSession(backend, token, session.durableId, true, session.profile)
        mutableState.value = mutableState.value.copy(activeStoredSession = null, runtimeSessionId = null, timeline = TimelineState())
        refreshSessions()
    }

    suspend fun disconnectAndForget() {
        val backend = mutableState.value.backend ?: return
        gateway.disconnect()
        tokenStore.remove(backend.id)
        backendRegistry.remove(backend.id)
    }

    private suspend fun connect(backend: BackendConfig) {
        val token = tokenStore.get(backend.id)
        if (token.isNullOrBlank()) {
            mutableState.value = HermesState(backend = backend, error = "Saved credentials are unavailable. Reconnect this backend.")
            return
        }
        mutableState.value = HermesState(backend = backend, loading = true)
        runCatching {
            val status = restClient.status(backend, token)
            gateway.connect(backend, token)
            val sessions = restClient.sessions(backend, token).sessions
            status to sessions
        }.onSuccess { (status, sessions) ->
            mutableState.value = mutableState.value.copy(status = status, sessions = sessions, loading = false, error = null)
        }.onFailure(::fail)
    }

    private suspend fun activeCredentials(): Pair<BackendConfig, String> {
        val backend = mutableState.value.backend ?: error("No Hermes backend is selected")
        val token = tokenStore.get(backend.id) ?: error("Hermes credentials are unavailable")
        if (gateway.connectionState.value !is GatewayConnectionState.Open) gateway.connect(backend, token)
        return backend to token
    }

    private fun setLoading(value: Boolean) {
        mutableState.value = mutableState.value.copy(loading = value)
    }

    private fun fail(error: Throwable) {
        mutableState.value = mutableState.value.copy(
            loading = false,
            sending = false,
            error = error.message ?: error::class.simpleName ?: "Hermes operation failed",
        )
    }
}

package com.nousresearch.hermes.data

import android.net.Uri
import com.nousresearch.hermes.domain.TimelineReducer
import com.nousresearch.hermes.domain.TimelineState
import com.nousresearch.hermes.network.HermesRestClient
import com.nousresearch.hermes.protocol.ConfigSetResult
import com.nousresearch.hermes.protocol.CronJob
import com.nousresearch.hermes.protocol.CronJobCreatePayload
import com.nousresearch.hermes.protocol.CronJobUpdates
import com.nousresearch.hermes.protocol.FileAttachResult
import com.nousresearch.hermes.protocol.EnvVarInfo
import com.nousresearch.hermes.protocol.GatewayConnectionState
import com.nousresearch.hermes.protocol.HermesGatewayClient
import com.nousresearch.hermes.protocol.ImageAttachResult
import com.nousresearch.hermes.protocol.ModelOptionsResult
import com.nousresearch.hermes.protocol.PdfAttachResult
import com.nousresearch.hermes.protocol.ProfileCreatePayload
import com.nousresearch.hermes.protocol.ProfileInfo
import com.nousresearch.hermes.protocol.SessionCreateResult
import com.nousresearch.hermes.protocol.SessionBranchResult
import com.nousresearch.hermes.protocol.SessionCompressResult
import com.nousresearch.hermes.protocol.SessionHistoryResult
import com.nousresearch.hermes.protocol.SessionResumeResult
import com.nousresearch.hermes.protocol.SessionRuntimeInfo
import com.nousresearch.hermes.protocol.SessionSteerResult
import com.nousresearch.hermes.protocol.SessionTitleResult
import com.nousresearch.hermes.protocol.SessionUndoResult
import com.nousresearch.hermes.protocol.SkillInfo
import com.nousresearch.hermes.protocol.SkillHubPreview
import com.nousresearch.hermes.protocol.SkillHubResult
import com.nousresearch.hermes.protocol.SkillHubScanResult
import com.nousresearch.hermes.protocol.StatusResponse
import com.nousresearch.hermes.protocol.StoredSession
import com.nousresearch.hermes.security.DiagnosticRedactor
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class HermesState(
    val backend: BackendConfig? = null,
    val savedBackends: List<BackendConfig> = emptyList(),
    val status: StatusResponse? = null,
    val sessions: List<StoredSession> = emptyList(),
    val activeStoredSession: StoredSession? = null,
    val runtimeSessionId: String? = null,
    val timeline: TimelineState = TimelineState(),
    val loading: Boolean = false,
    val sending: Boolean = false,
    val attaching: Boolean = false,
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val runtimeInfo: SessionRuntimeInfo = SessionRuntimeInfo(),
    val modelOptions: ModelOptionsResult? = null,
    val modelsLoading: Boolean = false,
    val pendingModelConfirmation: PendingModelConfirmation? = null,
    val skills: List<SkillInfo> = emptyList(),
    val skillHubResults: List<SkillHubResult> = emptyList(),
    val skillHubReview: SkillHubReview? = null,
    val skillHubLoading: Boolean = false,
    val skillAction: DiagnosticRunState? = null,
    val cronJobs: List<CronJob> = emptyList(),
    val cronRuns: Map<String, List<StoredSession>> = emptyMap(),
    val profiles: List<ProfileInfo> = emptyList(),
    val activeProfile: String = "default",
    val currentProfile: String = "default",
    val managementLoading: Boolean = false,
    val diagnostics: Map<DiagnosticAction, DiagnosticRunState> = emptyMap(),
    val providerOptions: ModelOptionsResult? = null,
    val providerEnv: Map<String, EnvVarInfo> = emptyMap(),
    val providersLoading: Boolean = false,
    val providerNotice: String? = null,
    val reconnectRequiredBackendId: String? = null,
    val error: String? = null,
) {
    val compatibilityWarning: String?
        get() = when {
            runtimeSessionId == null -> null
            runtimeInfo.desktopContract == null ->
                "This Hermes server does not report a desktop contract version. Version-gated controls are hidden."
            runtimeInfo.desktopContract < MINIMUM_DESKTOP_CONTRACT ->
                "This session reports desktop contract v${runtimeInfo.desktopContract}; Android expects v$MINIMUM_DESKTOP_CONTRACT. Update Hermes for full controls."
            else -> null
        }

    val supportsRemoteAttachments: Boolean
        get() = runtimeSessionId != null && (runtimeInfo.desktopContract ?: 0) >= ATTACHMENT_DESKTOP_CONTRACT

    val supportsSessionYolo: Boolean
        get() = runtimeSessionId != null && (runtimeInfo.desktopContract ?: 0) >= MINIMUM_DESKTOP_CONTRACT

    private companion object {
        const val ATTACHMENT_DESKTOP_CONTRACT = 2
        const val MINIMUM_DESKTOP_CONTRACT = 3
    }
}

enum class DiagnosticAction(val wireName: String) {
    DOCTOR("doctor"),
    SECURITY_AUDIT("security-audit"),
}

data class DiagnosticRunState(
    val running: Boolean = false,
    val pid: Long? = null,
    val exitCode: Int? = null,
    val lines: List<String> = emptyList(),
    val error: String? = null,
    val timedOut: Boolean = false,
)

data class SkillHubReview(
    val preview: SkillHubPreview,
    val scan: SkillHubScanResult,
)

data class ModelSelection(val provider: String, val model: String) {
    fun rpcValue(): String {
        require(provider.isSafeModelToken() && model.isSafeModelToken()) {
            "Hermes returned a model identifier that cannot be switched safely"
        }
        return "$model --provider $provider --session"
    }
}

data class PendingModelConfirmation(
    val selection: ModelSelection,
    val message: String,
)

data class PendingAttachment(
    val id: String,
    val label: String,
    val mimeType: String,
    val byteCount: Int,
    val refText: String? = null,
    val queuedImagePaths: List<String> = emptyList(),
)

@Singleton
class HermesRepository @Inject constructor(
    private val backendRegistry: BackendRegistry,
    private val tokenStore: SessionCredentialStore,
    private val restClient: HermesRestClient,
    private val gateway: HermesGatewayClient,
    private val dashboardConnector: DashboardBackendConnector,
    private val json: Json,
    private val attachmentReader: AttachmentReader,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(HermesState())
    private var reconnectJob: Job? = null
    private var intentionalDisconnect = false
    val state = mutableState.asStateFlow()
    val connectionState = gateway.connectionState

    init {
        scope.launch {
            combine(backendRegistry.backends, backendRegistry.activeBackendId) { backends, activeId ->
                backends to backends.firstOrNull { it.id == activeId }
            }.collectLatest { (backends, backend) ->
                if (backend == null) {
                    intentionalDisconnect = true
                    reconnectJob?.cancel()
                    gateway.disconnect()
                    mutableState.value = HermesState(savedBackends = backends)
                } else {
                    mutableState.value = mutableState.value.copy(savedBackends = backends)
                    connect(backend)
                }
            }
        }
        scope.launch {
            gateway.events.collect { event ->
                val current = mutableState.value
                val runtimeId = current.runtimeSessionId
                if (event.sessionId == null || runtimeId == null || event.sessionId == runtimeId) {
                    val runtimeInfo = if (event.type == "session.info" && event.payload != null) {
                        runCatching {
                            json.decodeFromJsonElement(SessionRuntimeInfo.serializer(), event.payload)
                        }.getOrDefault(current.runtimeInfo)
                    } else {
                        current.runtimeInfo
                    }
                    val activeStoredSession = if (
                        runtimeInfo.storedSessionId.isNotBlank() &&
                        current.activeStoredSession?.durableId.isNullOrBlank()
                    ) {
                        (current.activeStoredSession ?: StoredSession()).copy(
                            sessionId = runtimeInfo.storedSessionId,
                            title = runtimeInfo.title.ifBlank { current.activeStoredSession?.title.orEmpty() },
                        )
                    } else {
                        current.activeStoredSession
                    }
                    mutableState.value = current.copy(
                        runtimeInfo = runtimeInfo,
                        activeStoredSession = activeStoredSession,
                        timeline = TimelineReducer.reduce(current.timeline, event),
                    )
                }
            }
        }
        scope.launch {
            gateway.connectionState.collect { connection ->
                if (
                    !intentionalDisconnect &&
                    mutableState.value.backend != null &&
                    (connection is GatewayConnectionState.Closed || connection is GatewayConnectionState.Failed)
                ) {
                    scheduleReconnect()
                }
            }
        }
    }

    suspend fun testAndSave(config: BackendConfig, username: String, password: String): StatusResponse {
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        return try {
            dashboardConnector.loginValidateAndSave(config, username, password)
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
        var opened = false
        runCatching {
            val prefetch = restClient.sessionMessages(
                requireNotNull(mutableState.value.backend),
                requireNotNull(tokenStore.get(requireNotNull(mutableState.value.backend).id)).headerValue,
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
                runtimeInfo = resumed.info,
                loading = false,
                error = null,
            )
            opened = true
        }.onFailure(::fail)
        if (opened) refreshModelOptions()
    }

    suspend fun newSession(profile: String? = null) {
        activeCredentials()
        setLoading(true)
        var opened = false
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
                runtimeInfo = created.info,
                loading = false,
                error = null,
            )
            opened = true
        }.onFailure(::fail)
        if (opened) refreshModelOptions()
    }

    suspend fun refreshModelOptions(refresh: Boolean = false) {
        val sessionId = mutableState.value.runtimeSessionId ?: return
        mutableState.value = mutableState.value.copy(modelsLoading = true, error = null)
        runCatching {
            gateway.request(
                "model.options",
                buildJsonObject {
                    put("session_id", sessionId)
                    put("explicit_only", true)
                    if (refresh) put("refresh", true)
                },
            )
        }.mapCatching { json.decodeFromJsonElement(ModelOptionsResult.serializer(), it) }
            .onSuccess { options ->
                mutableState.value = mutableState.value.copy(
                    modelOptions = options,
                    modelsLoading = false,
                    runtimeInfo = mutableState.value.runtimeInfo.copy(
                        model = options.model ?: mutableState.value.runtimeInfo.model,
                        provider = options.provider ?: mutableState.value.runtimeInfo.provider,
                    ),
                )
            }
            .onFailure { error ->
                mutableState.value = mutableState.value.copy(modelsLoading = false)
                fail(error)
            }
    }

    suspend fun selectModel(provider: String, model: String, confirmExpensive: Boolean = false) {
        val sessionId = mutableState.value.runtimeSessionId ?: return
        val selection = ModelSelection(provider, model)
        mutableState.value = mutableState.value.copy(modelsLoading = true, error = null)
        runCatching {
            val response = gateway.request(
                "config.set",
                buildJsonObject {
                    put("session_id", sessionId)
                    put("key", "model")
                    put("value", selection.rpcValue())
                    if (confirmExpensive) put("confirm_expensive_model", true)
                },
            )
            json.decodeFromJsonElement(ConfigSetResult.serializer(), response)
        }.onSuccess { result ->
            if (result.confirmRequired) {
                mutableState.value = mutableState.value.copy(
                    modelsLoading = false,
                    pendingModelConfirmation = PendingModelConfirmation(
                        selection,
                        result.confirmMessage.ifBlank { "Hermes requires confirmation before using this model." },
                    ),
                )
            } else {
                mutableState.value = mutableState.value.copy(
                    modelsLoading = false,
                    pendingModelConfirmation = null,
                    runtimeInfo = mutableState.value.runtimeInfo.copy(model = model, provider = provider),
                )
            }
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(modelsLoading = false)
            fail(error)
        }
    }

    suspend fun confirmModelSelection() {
        val pending = mutableState.value.pendingModelConfirmation ?: return
        selectModel(pending.selection.provider, pending.selection.model, confirmExpensive = true)
    }

    fun cancelModelSelection() {
        mutableState.value = mutableState.value.copy(pendingModelConfirmation = null)
    }

    suspend fun setReasoningEffort(effort: String) = setSessionConfig("reasoning", effort)

    suspend fun setFastMode(enabled: Boolean) = setSessionConfig("fast", if (enabled) "fast" else "normal")

    suspend fun setYolo(enabled: Boolean) = setSessionConfig("yolo", if (enabled) "on" else "off", "session")

    private suspend fun setSessionConfig(key: String, value: String, scope: String? = null) {
        val sessionId = mutableState.value.runtimeSessionId ?: return
        runCatching {
            val response = gateway.request(
                "config.set",
                buildJsonObject {
                    put("session_id", sessionId)
                    put("key", key)
                    put("value", value)
                    scope?.let { put("scope", it) }
                },
            )
            json.decodeFromJsonElement(ConfigSetResult.serializer(), response)
        }.onSuccess { result ->
            val current = mutableState.value.runtimeInfo
            val next = when (key) {
                "reasoning" -> current.copy(reasoningEffort = result.value)
                "fast" -> current.copy(fast = result.value == "fast", serviceTier = if (result.value == "fast") "priority" else "")
                "yolo" -> current.copy(yolo = result.value == "1")
                else -> current
            }
            mutableState.value = mutableState.value.copy(runtimeInfo = next, error = null)
        }.onFailure(::fail)
    }

    suspend fun send(text: String) {
        val cleaned = text.trim()
        require(cleaned.isNotEmpty())
        val sessionId = mutableState.value.runtimeSessionId ?: run {
            newSession()
            requireNotNull(mutableState.value.runtimeSessionId)
        }
        val attachmentRefs = mutableState.value.pendingAttachments.mapNotNull { it.refText }
        val submittedText = buildString {
            append(cleaned)
            if (attachmentRefs.isNotEmpty()) append("\n\n").append(attachmentRefs.joinToString("\n"))
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
                    put("text", submittedText)
                },
            )
        }.onSuccess {
            mutableState.value = mutableState.value.copy(sending = false, pendingAttachments = emptyList())
        }.onFailure(::fail)
    }

    suspend fun steer(text: String) {
        val cleaned = text.trim()
        require(cleaned.isNotEmpty())
        val sessionId = mutableState.value.runtimeSessionId ?: return
        runCatching {
            val response = gateway.request(
                "session.steer",
                buildJsonObject {
                    put("session_id", sessionId)
                    put("text", cleaned)
                },
            )
            json.decodeFromJsonElement(SessionSteerResult.serializer(), response).also {
                require(it.status == "queued") { "Hermes rejected the steering message" }
            }
        }.onSuccess {
            mutableState.value = mutableState.value.copy(error = null)
        }.onFailure(::fail)
    }

    suspend fun renameActive(title: String) {
        val cleaned = title.trim()
        require(cleaned.isNotEmpty() && cleaned.length <= 200) { "Session titles must be 1–200 characters" }
        val sessionId = mutableState.value.runtimeSessionId ?: return
        runCatching {
            val response = gateway.request(
                "session.title",
                buildJsonObject {
                    put("session_id", sessionId)
                    put("title", cleaned)
                },
            )
            json.decodeFromJsonElement(SessionTitleResult.serializer(), response)
        }.onSuccess { result ->
            val active = mutableState.value.activeStoredSession
            val durableId = result.sessionKey ?: active?.durableId
            mutableState.value = mutableState.value.copy(
                activeStoredSession = (active ?: StoredSession()).copy(title = result.title),
                runtimeInfo = mutableState.value.runtimeInfo.copy(title = result.title),
                sessions = mutableState.value.sessions.map {
                    if (it.durableId == durableId) it.copy(title = result.title) else it
                },
                error = null,
            )
        }.onFailure(::fail)
    }

    suspend fun branchActive(name: String = "") {
        val sessionId = mutableState.value.runtimeSessionId ?: return
        val profile = mutableState.value.activeStoredSession?.profile
        runCatching {
            val response = gateway.request(
                "session.branch",
                buildJsonObject {
                    put("session_id", sessionId)
                    name.trim().takeIf(String::isNotBlank)?.let { put("name", it.take(200)) }
                },
            )
            json.decodeFromJsonElement(SessionBranchResult.serializer(), response)
        }.onSuccess { branch ->
            mutableState.value = mutableState.value.copy(
                runtimeSessionId = branch.runtimeSessionId,
                activeStoredSession = StoredSession(title = branch.title, profile = profile, source = "android"),
                runtimeInfo = mutableState.value.runtimeInfo.copy(title = branch.title, storedSessionId = "", running = false),
                error = null,
            )
            refreshModelOptions()
        }.onFailure(::fail)
    }

    suspend fun undoLastTurn() {
        val sessionId = mutableState.value.runtimeSessionId ?: return
        runCatching {
            val undo = gateway.request("session.undo", buildJsonObject { put("session_id", sessionId) })
            val removed = json.decodeFromJsonElement(SessionUndoResult.serializer(), undo)
            val history = gateway.request("session.history", buildJsonObject { put("session_id", sessionId) })
            removed to json.decodeFromJsonElement(SessionHistoryResult.serializer(), history)
        }.onSuccess { (undo, history) ->
            mutableState.value = mutableState.value.copy(
                timeline = TimelineReducer.hydrate(history.messages),
                error = if (undo.removed == 0) "Hermes had no completed turn to undo." else null,
            )
        }.onFailure(::fail)
    }

    suspend fun compressActive(focusTopic: String = "") {
        val sessionId = mutableState.value.runtimeSessionId ?: return
        setLoading(true)
        runCatching {
            val response = gateway.request(
                "session.compress",
                buildJsonObject {
                    put("session_id", sessionId)
                    focusTopic.trim().takeIf(String::isNotBlank)?.let { put("focus_topic", it.take(500)) }
                },
            )
            json.decodeFromJsonElement(SessionCompressResult.serializer(), response)
        }.onSuccess { compressed ->
            mutableState.value = mutableState.value.copy(
                timeline = if (compressed.messages.isEmpty()) {
                    mutableState.value.timeline
                } else {
                    TimelineReducer.hydrate(compressed.messages)
                },
                runtimeInfo = compressed.info ?: mutableState.value.runtimeInfo,
                loading = false,
                error = if (compressed.status == "aborted") "Hermes left the context unchanged because compression was not useful." else null,
            )
        }.onFailure(::fail)
    }

    suspend fun refreshSkills() {
        val (backend, token) = activeCredentials()
        mutableState.value = mutableState.value.copy(managementLoading = true, error = null)
        runCatching { restClient.skills(backend, token) }
            .onSuccess { skills ->
                mutableState.value = mutableState.value.copy(
                    skills = skills.sortedWith(compareByDescending<SkillInfo> { it.usage ?: 0 }.thenBy { it.name }),
                    managementLoading = false,
                )
            }
            .onFailure(::fail)
    }

    suspend fun toggleSkill(name: String, enabled: Boolean) {
        val (backend, token) = activeCredentials()
        runCatching { restClient.toggleSkill(backend, token, name, enabled) }
            .onSuccess { changed ->
                mutableState.value = mutableState.value.copy(
                    skills = mutableState.value.skills.map {
                        if (it.name == changed.name) it.copy(enabled = changed.enabled) else it
                    },
                    error = null,
                )
            }
            .onFailure(::fail)
    }

    suspend fun loadSkillHub(query: String = "") {
        val (backend, token) = activeCredentials()
        val profile = mutableState.value.activeProfile
        mutableState.value = mutableState.value.copy(skillHubLoading = true, skillHubReview = null, error = null)
        runCatching {
            if (query.isBlank()) restClient.skillHubSources(backend, token, profile).featured
            else restClient.searchSkillHub(backend, token, profile, query.trim()).results
        }.onSuccess { results ->
            mutableState.value = mutableState.value.copy(skillHubResults = results, skillHubLoading = false)
        }.onFailure(::fail)
    }

    suspend fun reviewSkill(identifier: String) {
        val (backend, token) = activeCredentials()
        val profile = mutableState.value.activeProfile
        mutableState.value = mutableState.value.copy(skillHubLoading = true, skillHubReview = null, error = null)
        runCatching {
            SkillHubReview(
                preview = restClient.previewSkillHub(backend, token, profile, identifier),
                scan = restClient.scanSkillHub(backend, token, profile, identifier),
            )
        }.onSuccess { review ->
            require(review.preview.identifier == review.scan.identifier) { "Hermes returned mismatched skill review data" }
            mutableState.value = mutableState.value.copy(skillHubReview = review, skillHubLoading = false)
        }.onFailure(::fail)
    }

    fun closeSkillReview() {
        mutableState.value = mutableState.value.copy(skillHubReview = null)
    }

    suspend fun installReviewedSkill() {
        try {
            val review = mutableState.value.skillHubReview ?: error("Review and scan a skill before installing it")
            require(review.scan.policy != "block") { review.scan.policyReason ?: "Hermes blocked this skill" }
            val (backend, token) = activeCredentials()
            val started = restClient.installSkillHub(backend, token, mutableState.value.activeProfile, review.preview.identifier)
            mutableState.value = mutableState.value.copy(skillHubReview = null)
            pollSkillAction(started.name, started.pid)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            fail(error)
        }
    }

    suspend fun uninstallSkill(name: String) {
        runCatching {
            val (backend, token) = activeCredentials()
            restClient.uninstallSkillHub(backend, token, mutableState.value.activeProfile, name)
        }.onSuccess { pollSkillAction(it.name, it.pid) }.onFailure(::fail)
    }

    suspend fun updateSkills() {
        runCatching {
            val (backend, token) = activeCredentials()
            restClient.updateSkillsHub(backend, token, mutableState.value.activeProfile)
        }.onSuccess { pollSkillAction(it.name, it.pid) }.onFailure(::fail)
    }

    private suspend fun pollSkillAction(name: String, pid: Long) {
        val (backend, token) = activeCredentials()
        mutableState.value = mutableState.value.copy(skillAction = DiagnosticRunState(running = true, pid = pid), error = null)
        try {
            repeat(DIAGNOSTIC_POLL_LIMIT) {
                val status = restClient.actionStatus(backend, token, name)
                mutableState.value = mutableState.value.copy(
                    skillAction = DiagnosticRunState(
                        running = status.running,
                        pid = status.pid ?: pid,
                        exitCode = status.exitCode,
                        lines = DiagnosticRedactor.redactLines(status.lines),
                    ),
                )
                if (!status.running) {
                    refreshSkills()
                    return
                }
                delay(DIAGNOSTIC_POLL_INTERVAL_MILLIS)
            }
            mutableState.value = mutableState.value.copy(
                skillAction = mutableState.value.skillAction?.copy(
                    running = false,
                    timedOut = true,
                    error = "Status polling stopped after two minutes. The server action may still be running.",
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            mutableState.value = mutableState.value.copy(
                skillAction = mutableState.value.skillAction?.copy(
                    running = false,
                    error = DiagnosticRedactor.redact(error.message.orEmpty()).ifBlank { "Skill action failed" },
                ),
            )
        }
    }

    suspend fun refreshCronJobs() {
        val (backend, token) = activeCredentials()
        mutableState.value = mutableState.value.copy(managementLoading = true, error = null)
        runCatching { restClient.cronJobs(backend, token) }
            .onSuccess { jobs ->
                mutableState.value = mutableState.value.copy(cronJobs = jobs, managementLoading = false)
            }
            .onFailure(::fail)
    }

    suspend fun refreshCronRuns(jobId: String) {
        val (backend, token) = activeCredentials()
        mutableState.value = mutableState.value.copy(managementLoading = true, error = null)
        runCatching { restClient.cronRuns(backend, token, jobId).runs }
            .onSuccess { runs ->
                mutableState.value = mutableState.value.copy(
                    cronRuns = mutableState.value.cronRuns + (jobId to runs),
                    managementLoading = false,
                    error = null,
                )
            }
            .onFailure(::fail)
    }

    suspend fun setCronEnabled(jobId: String, enabled: Boolean) {
        val (backend, token) = activeCredentials()
        runCatching { restClient.setCronEnabled(backend, token, jobId, enabled) }
            .onSuccess(::replaceCronJob)
            .onFailure(::fail)
    }

    suspend fun triggerCron(jobId: String) {
        val (backend, token) = activeCredentials()
        runCatching { restClient.triggerCron(backend, token, jobId) }
            .onSuccess(::replaceCronJob)
            .onFailure(::fail)
    }

    suspend fun createCron(name: String, prompt: String, schedule: String, deliver: String) {
        val cleanPrompt = prompt.trim()
        val cleanSchedule = schedule.trim()
        require(cleanPrompt.isNotEmpty() && cleanSchedule.isNotEmpty()) { "Cron prompt and schedule are required" }
        val (backend, token) = activeCredentials()
        runCatching {
            restClient.createCron(
                backend,
                token,
                CronJobCreatePayload(
                    name = name.trim().takeIf(String::isNotEmpty),
                    prompt = cleanPrompt,
                    schedule = cleanSchedule,
                    deliver = deliver.trim().takeIf(String::isNotEmpty),
                ),
            )
        }.onSuccess(::replaceCronJob).onFailure(::fail)
    }

    suspend fun updateCron(jobId: String, name: String, prompt: String, schedule: String, deliver: String) {
        val cleanPrompt = prompt.trim()
        val cleanSchedule = schedule.trim()
        require(cleanPrompt.isNotEmpty() && cleanSchedule.isNotEmpty()) { "Cron prompt and schedule are required" }
        val (backend, token) = activeCredentials()
        runCatching {
            restClient.updateCron(
                backend,
                token,
                jobId,
                CronJobUpdates(
                    name = name.trim(),
                    prompt = cleanPrompt,
                    schedule = cleanSchedule,
                    deliver = deliver.trim(),
                ),
            )
        }.onSuccess(::replaceCronJob).onFailure(::fail)
    }

    suspend fun deleteCron(jobId: String) {
        val (backend, token) = activeCredentials()
        runCatching { restClient.deleteCron(backend, token, jobId) }
            .onSuccess {
                mutableState.value = mutableState.value.copy(
                    cronJobs = mutableState.value.cronJobs.filterNot { it.id == jobId },
                    cronRuns = mutableState.value.cronRuns - jobId,
                    error = null,
                )
            }
            .onFailure(::fail)
    }

    suspend fun refreshProfiles() {
        val (backend, token) = activeCredentials()
        mutableState.value = mutableState.value.copy(managementLoading = true, error = null)
        val result = runCatching {
            restClient.profiles(backend, token) to restClient.activeProfile(backend, token)
        }
        result.onSuccess { (profiles, active) ->
            mutableState.value = mutableState.value.copy(
                profiles = profiles.profiles.sortedWith(compareByDescending<ProfileInfo> { it.isDefault }.thenBy { it.name }),
                activeProfile = active.active,
                currentProfile = active.current,
                managementLoading = false,
                error = null,
            )
        }.onFailure(::fail)
    }

    suspend fun createProfile(name: String, cloneFrom: String, cloneAll: Boolean, noSkills: Boolean) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Profile name is required" }
        val (backend, token) = activeCredentials()
        val result = runCatching {
            restClient.createProfile(
                backend,
                token,
                ProfileCreatePayload(
                    name = cleanName,
                    cloneFrom = cloneFrom.trim().takeIf(String::isNotEmpty),
                    cloneAll = cloneAll,
                    noSkills = noSkills,
                ),
            )
        }
        if (result.isSuccess) {
            refreshProfiles()
            refreshSessions()
        } else {
            fail(requireNotNull(result.exceptionOrNull()))
        }
    }

    suspend fun renameProfile(name: String, newName: String) {
        val cleanName = newName.trim()
        require(cleanName.isNotEmpty()) { "New profile name is required" }
        val (backend, token) = activeCredentials()
        val result = runCatching { restClient.renameProfile(backend, token, name, cleanName) }
        if (result.isSuccess) {
            refreshProfiles()
            refreshSessions()
        } else {
            fail(requireNotNull(result.exceptionOrNull()))
        }
    }

    suspend fun setActiveProfile(name: String) {
        val (backend, token) = activeCredentials()
        val result = runCatching { restClient.setActiveProfile(backend, token, name) }
        if (result.isSuccess) {
            mutableState.value = mutableState.value.copy(
                providerOptions = null,
                providerEnv = emptyMap(),
                providerNotice = null,
            )
            refreshProfiles()
        } else {
            fail(requireNotNull(result.exceptionOrNull()))
        }
    }

    suspend fun deleteProfile(name: String) {
        val profile = mutableState.value.profiles.firstOrNull { it.name == name } ?: return
        require(!profile.isDefault && name != mutableState.value.currentProfile) {
            "The default or currently running Hermes profile cannot be deleted"
        }
        val (backend, token) = activeCredentials()
        val result = runCatching { restClient.deleteProfile(backend, token, name) }
        if (result.isSuccess) {
            refreshProfiles()
            refreshSessions()
        } else {
            fail(requireNotNull(result.exceptionOrNull()))
        }
    }

    suspend fun runDiagnostic(action: DiagnosticAction) {
        val (backend, token) = activeCredentials()
        updateDiagnostic(action, DiagnosticRunState(running = true))
        try {
            val started = when (action) {
                DiagnosticAction.DOCTOR -> restClient.runDoctor(backend, token)
                DiagnosticAction.SECURITY_AUDIT -> restClient.runSecurityAudit(backend, token)
            }
            require(started.ok && started.name == action.wireName) {
                "Hermes did not start the requested diagnostic action"
            }
            updateDiagnostic(action, DiagnosticRunState(running = true, pid = started.pid))
            repeat(DIAGNOSTIC_POLL_LIMIT) {
                val status = restClient.actionStatus(backend, token, action.wireName)
                updateDiagnostic(
                    action,
                    DiagnosticRunState(
                        running = status.running,
                        pid = status.pid ?: started.pid,
                        exitCode = status.exitCode,
                        lines = DiagnosticRedactor.redactLines(status.lines),
                    ),
                )
                if (!status.running) return
                delay(DIAGNOSTIC_POLL_INTERVAL_MILLIS)
            }
            val current = mutableState.value.diagnostics[action] ?: DiagnosticRunState()
            updateDiagnostic(
                action,
                current.copy(
                    running = false,
                    timedOut = true,
                    error = "Status polling stopped after two minutes. The server action may still be running.",
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val current = mutableState.value.diagnostics[action] ?: DiagnosticRunState()
            updateDiagnostic(
                action,
                current.copy(
                    running = false,
                    error = DiagnosticRedactor.redact(error.message.orEmpty()).ifBlank { "Diagnostic action failed" },
                ),
            )
        }
    }

    suspend fun refreshProviders(refresh: Boolean = false) {
        val (backend, token) = activeCredentials()
        mutableState.value = mutableState.value.copy(providersLoading = true, error = null)
        runCatching {
            val active = restClient.activeProfile(backend, token)
            val options = restClient.globalModelOptions(backend, token, active.active, refresh)
            Triple(active, options, restClient.envVars(backend, token, active.active))
        }.onSuccess { (active, options, env) ->
            mutableState.value = mutableState.value.copy(
                activeProfile = active.active,
                currentProfile = active.current,
                providerOptions = options,
                providerEnv = env.filterValues { !it.channelManaged && (it.category == "provider" || it.provider.isNotBlank()) },
                providersLoading = false,
                error = null,
            )
        }.onFailure(::fail)
    }

    suspend fun saveProviderSetting(key: String, value: String, apiKey: String = "") {
        val info = mutableState.value.providerEnv[key] ?: error("Hermes did not advertise this provider setting")
        require(!info.channelManaged && (info.category == "provider" || info.provider.isNotBlank())) {
            "This setting is not managed by the provider surface"
        }
        val clean = value.trim()
        require(clean.isNotEmpty() && clean.length <= 32_768) { "Provider value must be between 1 and 32,768 characters" }
        mutableState.value = mutableState.value.copy(providersLoading = true, providerNotice = null, error = null)
        val (backend, token) = activeCredentials()
        val validation = runCatching { restClient.validateProviderCredential(backend, token, key, clean, apiKey) }
            .getOrElse { error -> fail(error); return }
        if (!validation.ok) {
            mutableState.value = mutableState.value.copy(
                providersLoading = false,
                error = validation.message.ifBlank {
                    if (validation.reachable) "Hermes rejected this provider value." else "Hermes could not validate this provider value."
                },
            )
            return
        }
        runCatching { restClient.setEnvVar(backend, token, mutableState.value.activeProfile, key, clean) }
            .onSuccess {
                mutableState.value = mutableState.value.copy(
                    providerNotice = validation.message.ifBlank {
                        if (validation.reachable) "Provider credential validated and saved on Hermes." else "Provider setting saved; this provider has no live validation probe."
                    },
                )
                refreshProviders(refresh = true)
            }
            .onFailure(::fail)
    }

    suspend fun deleteProviderSetting(key: String) {
        val info = mutableState.value.providerEnv[key] ?: return
        require(!info.channelManaged && (info.category == "provider" || info.provider.isNotBlank())) {
            "This setting is not managed by the provider surface"
        }
        val (backend, token) = activeCredentials()
        mutableState.value = mutableState.value.copy(providersLoading = true, providerNotice = null, error = null)
        runCatching { restClient.deleteEnvVar(backend, token, mutableState.value.activeProfile, key) }
            .onSuccess {
                mutableState.value = mutableState.value.copy(providerNotice = "Provider setting removed from Hermes.")
                refreshProviders(refresh = true)
            }
            .onFailure(::fail)
    }

    private fun updateDiagnostic(action: DiagnosticAction, run: DiagnosticRunState) {
        mutableState.value = mutableState.value.copy(
            diagnostics = mutableState.value.diagnostics + (action to run),
        )
    }

    private fun replaceCronJob(job: CronJob) {
        val existing = mutableState.value.cronJobs
        mutableState.value = mutableState.value.copy(
            cronJobs = if (existing.any { it.id == job.id }) {
                existing.map { if (it.id == job.id) job else it }
            } else {
                existing + job
            },
            managementLoading = false,
            error = null,
        )
    }

    suspend fun attach(uri: Uri) {
        val sessionId = mutableState.value.runtimeSessionId ?: run {
            newSession()
            requireNotNull(mutableState.value.runtimeSessionId)
        }
        mutableState.value = mutableState.value.copy(attaching = true, error = null)
        runCatching {
            val payload = attachmentReader.read(uri)
            when {
                payload.mimeType.startsWith("image/") -> {
                    val result = gateway.request(
                        "image.attach_bytes",
                        buildJsonObject {
                            put("session_id", sessionId)
                            put("content_base64", payload.base64)
                            put("filename", payload.displayName)
                        },
                    )
                    val attached = json.decodeFromJsonElement(ImageAttachResult.serializer(), result)
                    PendingAttachment(
                        id = UUID.randomUUID().toString(),
                        label = payload.displayName,
                        mimeType = payload.mimeType,
                        byteCount = payload.byteCount,
                        queuedImagePaths = listOf(attached.path),
                    )
                }
                payload.mimeType == "application/pdf" -> {
                    val result = gateway.request(
                        "pdf.attach",
                        buildJsonObject {
                            put("session_id", sessionId)
                            put("content_base64", payload.base64)
                            put("filename", payload.displayName)
                        },
                    )
                    val attached = json.decodeFromJsonElement(PdfAttachResult.serializer(), result)
                    PendingAttachment(
                        id = UUID.randomUUID().toString(),
                        label = payload.displayName,
                        mimeType = payload.mimeType,
                        byteCount = payload.byteCount,
                        queuedImagePaths = attached.pages.map { it.path },
                    )
                }
                else -> {
                    val result = gateway.request(
                        "file.attach",
                        buildJsonObject {
                            put("session_id", sessionId)
                            put("name", payload.displayName)
                            put("path", payload.displayName)
                            put("data_url", "data:${payload.mimeType};base64,${payload.base64}")
                        },
                    )
                    val attached = json.decodeFromJsonElement(FileAttachResult.serializer(), result)
                    PendingAttachment(
                        id = UUID.randomUUID().toString(),
                        label = payload.displayName,
                        mimeType = payload.mimeType,
                        byteCount = payload.byteCount,
                        refText = attached.refText,
                    )
                }
            }
        }.onSuccess { attachment ->
            mutableState.value = mutableState.value.copy(
                attaching = false,
                pendingAttachments = mutableState.value.pendingAttachments + attachment,
            )
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(attaching = false)
            fail(error)
        }
    }

    suspend fun removePendingAttachment(id: String) {
        val attachment = mutableState.value.pendingAttachments.firstOrNull { it.id == id } ?: return
        val sessionId = mutableState.value.runtimeSessionId
        if (sessionId != null) {
            attachment.queuedImagePaths.forEach { path ->
                runCatching {
                    gateway.request(
                        "image.detach",
                        buildJsonObject {
                            put("session_id", sessionId)
                            put("path", path)
                        },
                    )
                }
            }
        }
        mutableState.value = mutableState.value.copy(
            pendingAttachments = mutableState.value.pendingAttachments.filterNot { it.id == id },
        )
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
        intentionalDisconnect = true
        reconnectJob?.cancel()
        gateway.disconnect()
        tokenStore.remove(backend.id)
        backendRegistry.remove(backend.id)
    }

    suspend fun selectBackend(id: String) {
        require(mutableState.value.savedBackends.any { it.id == id }) { "Saved Hermes backend was not found" }
        backendRegistry.select(id)
    }

    suspend fun forgetBackend(id: String) {
        tokenStore.remove(id)
        backendRegistry.remove(id)
    }

    private suspend fun connect(backend: BackendConfig) {
        intentionalDisconnect = false
        if (backend.authMode != AuthMode.DASHBOARD_SESSION) {
            mutableState.value = HermesState(
                savedBackends = mutableState.value.savedBackends,
                reconnectRequiredBackendId = backend.id,
                error = "This legacy token-only backend must reconnect with its dashboard username and password.",
            )
            return
        }
        val cookie = tokenStore.get(backend.id)
        if (cookie == null) {
            mutableState.value = HermesState(
                savedBackends = mutableState.value.savedBackends,
                reconnectRequiredBackendId = backend.id,
                error = "Saved dashboard session is unavailable. Reconnect this backend.",
            )
            return
        }
        mutableState.value = HermesState(
            backend = backend,
            savedBackends = mutableState.value.savedBackends,
            loading = true,
            reconnectRequiredBackendId = null,
        )
        runCatching {
            val status = dashboardConnector.validateSaved(backend, cookie)
            val sessions = restClient.sessions(backend, cookie.headerValue).sessions
            status to sessions
        }.onSuccess { (status, sessions) ->
            mutableState.value = mutableState.value.copy(status = status, sessions = sessions, loading = false, error = null)
        }.onFailure(::fail)
    }

    private suspend fun activeCredentials(): Pair<BackendConfig, String> {
        val backend = mutableState.value.backend ?: error("No Hermes backend is selected")
        if (backend.authMode != AuthMode.DASHBOARD_SESSION) throw ReconnectRequiredException(
            "Legacy backend credentials cannot be used; reconnect is required.",
        )
        val cookie = tokenStore.get(backend.id) ?: throw ReconnectRequiredException("Dashboard session is unavailable; reconnect is required.")
        if (gateway.connectionState.value !is GatewayConnectionState.Open) gateway.connect(backend, cookie)
        return backend to cookie.headerValue
    }

    private fun setLoading(value: Boolean) {
        mutableState.value = mutableState.value.copy(loading = value)
    }

    private fun fail(error: Throwable) {
        val reconnect = error is ReconnectRequiredException || (error is com.nousresearch.hermes.network.HermesHttpException && error.statusCode in setOf(401, 403))
        val reconnectBackendId = mutableState.value.backend?.id
        if (reconnect) {
            intentionalDisconnect = true
            reconnectJob?.cancel()
            reconnectBackendId?.let(tokenStore::remove)
            scope.launch { gateway.disconnect() }
        }
        mutableState.value = mutableState.value.copy(
            backend = if (reconnect) null else mutableState.value.backend,
            loading = false,
            sending = false,
            attaching = false,
            modelsLoading = false,
            managementLoading = false,
            providersLoading = false,
            skillHubLoading = false,
            reconnectRequiredBackendId = if (reconnect) reconnectBackendId else mutableState.value.reconnectRequiredBackendId,
            error = if (reconnect) "Dashboard session expired or was rejected. Reconnect with your username and password." else
                error.message ?: error::class.simpleName ?: "Hermes operation failed",
        )
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            val backend = mutableState.value.backend ?: return@launch
            val cookie = tokenStore.get(backend.id) ?: return@launch
            val delays = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L)
            for ((index, retryDelay) in delays.withIndex()) {
                if (intentionalDisconnect || mutableState.value.backend?.id != backend.id) return@launch
                delay(retryDelay)
                val connected = runCatching { gateway.connect(backend, cookie) }.isSuccess
                if (connected) {
                    val active = mutableState.value.activeStoredSession
                    if (active != null) runCatching { openSession(active) }
                    return@launch
                }
                mutableState.value = mutableState.value.copy(
                    error = "Hermes reconnect attempt ${index + 1} failed; retrying in ${delays.getOrElse(index + 1) { retryDelay } / 1_000}s.",
                )
            }
            mutableState.value = mutableState.value.copy(
                error = "Hermes remains unreachable after bounded retries. Check the backend, TLS and network, then retry.",
            )
        }
    }
}

private const val DIAGNOSTIC_POLL_INTERVAL_MILLIS = 1_000L
private const val DIAGNOSTIC_POLL_LIMIT = 120

private fun String.isSafeModelToken(): Boolean =
    isNotBlank() && length <= 512 && !startsWith('-') && none { it.isWhitespace() || it.isISOControl() }

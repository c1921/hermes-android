package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.protocol.ActionResponse
import com.nousresearch.hermes.protocol.ActionStatusResponse
import com.nousresearch.hermes.protocol.AnalyticsResponse
import com.nousresearch.hermes.protocol.AudioSpeakResponse
import com.nousresearch.hermes.protocol.AudioTranscriptionResponse
import com.nousresearch.hermes.protocol.EnvVarInfo
import com.nousresearch.hermes.protocol.CronJob
import com.nousresearch.hermes.protocol.CronJobCreatePayload
import com.nousresearch.hermes.protocol.CronJobUpdates
import com.nousresearch.hermes.protocol.CronRunPage
import com.nousresearch.hermes.protocol.ActiveProfileResponse
import com.nousresearch.hermes.protocol.ProfileCreatePayload
import com.nousresearch.hermes.protocol.ProfilesResponse
import com.nousresearch.hermes.protocol.ProviderValidationResult
import com.nousresearch.hermes.protocol.ModelOptionsResult
import com.nousresearch.hermes.protocol.ManagedFileReadResponse
import com.nousresearch.hermes.protocol.ManagedFilesResponse
import com.nousresearch.hermes.protocol.McpCatalogResponse
import com.nousresearch.hermes.protocol.McpServerTestResponse
import com.nousresearch.hermes.protocol.McpServerToggleResponse
import com.nousresearch.hermes.protocol.McpServersResponse
import com.nousresearch.hermes.protocol.MessagingPlatformTestResponse
import com.nousresearch.hermes.protocol.MessagingPlatformUpdateResponse
import com.nousresearch.hermes.protocol.MessagingPlatformsResponse
import com.nousresearch.hermes.protocol.SessionMessagePage
import com.nousresearch.hermes.protocol.SessionPage
import com.nousresearch.hermes.protocol.SessionSearchPage
import com.nousresearch.hermes.protocol.SkillInfo
import com.nousresearch.hermes.protocol.SkillHubPreview
import com.nousresearch.hermes.protocol.SkillHubScanResult
import com.nousresearch.hermes.protocol.SkillHubSearchResponse
import com.nousresearch.hermes.protocol.SkillHubSourcesResponse
import com.nousresearch.hermes.protocol.SkillToggleResult
import com.nousresearch.hermes.protocol.StatusResponse
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HermesRestClient(
    private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun status(config: BackendConfig, token: String?): StatusResponse =
        get(config, token, "/api/status", StatusResponse.serializer())

    suspend fun status(config: BackendConfig, cookie: DashboardSessionCookie): StatusResponse =
        get(config, cookie.headerValue, "/api/status", StatusResponse.serializer())

    suspend fun sessions(
        config: BackendConfig,
        token: String,
        limit: Int = 50,
        offset: Int = 0,
    ): SessionPage = get(
        config,
        token,
        "/api/profiles/sessions?limit=$limit&offset=$offset&order=recent&profile=all&exclude_sources=cron",
        SessionPage.serializer(),
    )

    suspend fun sessionMessages(
        config: BackendConfig,
        token: String,
        sessionId: String,
        profile: String?,
    ): SessionMessagePage {
        val profileQuery = profile?.let { "?profile=${encodePathSegment(it)}" }.orEmpty()
        return get(
            config,
            token,
            "/api/sessions/${encodePathSegment(sessionId)}/messages$profileQuery",
            SessionMessagePage.serializer(),
        )
    }

    suspend fun searchSessions(
        config: BackendConfig,
        token: String,
        query: String,
        profile: String,
        limit: Int = 30,
    ): SessionSearchPage {
        require(query.isNotBlank()) { "Session search query is required" }
        return get(
            config,
            token,
            "/api/sessions/search?q=${encodePathSegment(query.take(200))}&limit=${limit.coerceIn(1, 100)}&profile=${encodePathSegment(profile)}",
            SessionSearchPage.serializer(),
        )
    }

    suspend fun managedFiles(
        config: BackendConfig,
        token: String,
        path: String? = null,
    ): ManagedFilesResponse {
        val query = path?.takeIf(String::isNotBlank)?.let { "?path=${encodePathSegment(it)}" }.orEmpty()
        return get(config, token, "/api/files$query", ManagedFilesResponse.serializer())
    }

    suspend fun readManagedFile(
        config: BackendConfig,
        token: String,
        path: String,
    ): ManagedFileReadResponse = get(
        config,
        token,
        "/api/files/read?path=${encodePathSegment(path)}",
        ManagedFileReadResponse.serializer(),
    )

    suspend fun transcribeAudio(
        config: BackendConfig,
        token: String,
        dataUrl: String,
        mimeType: String,
    ): AudioTranscriptionResponse = json.decodeFromJsonElement(
        AudioTranscriptionResponse.serializer(),
        request(
            config,
            token,
            "/api/audio/transcribe",
            method = "POST",
            body = buildJsonObject {
                put("data_url", dataUrl)
                put("mime_type", mimeType)
            },
        ),
    )

    suspend fun speakText(
        config: BackendConfig,
        token: String,
        text: String,
    ): AudioSpeakResponse = json.decodeFromJsonElement(
        AudioSpeakResponse.serializer(),
        request(
            config,
            token,
            "/api/audio/speak",
            method = "POST",
            body = buildJsonObject { put("text", text) },
        ),
    )

    suspend fun messagingPlatforms(
        config: BackendConfig,
        token: String,
        profile: String,
    ): MessagingPlatformsResponse = get(
        config,
        token,
        "/api/messaging/platforms?profile=${encodePathSegment(profile)}",
        MessagingPlatformsResponse.serializer(),
    )

    suspend fun updateMessagingPlatform(
        config: BackendConfig,
        token: String,
        profile: String,
        platformId: String,
        enabled: Boolean? = null,
        env: Map<String, String> = emptyMap(),
        clearEnv: List<String> = emptyList(),
    ): MessagingPlatformUpdateResponse = json.decodeFromJsonElement(
        MessagingPlatformUpdateResponse.serializer(),
        request(
            config,
            token,
            "/api/messaging/platforms/${encodePathSegment(platformId)}?profile=${encodePathSegment(profile)}",
            method = "PUT",
            body = buildJsonObject {
                enabled?.let { put("enabled", it) }
                put("env", buildJsonObject { env.forEach { (key, value) -> put(key, value) } })
                put("clear_env", buildJsonArray { clearEnv.forEach(::add) })
            },
        ),
    )

    suspend fun testMessagingPlatform(
        config: BackendConfig,
        token: String,
        profile: String,
        platformId: String,
    ): MessagingPlatformTestResponse = json.decodeFromJsonElement(
        MessagingPlatformTestResponse.serializer(),
        request(
            config,
            token,
            "/api/messaging/platforms/${encodePathSegment(platformId)}/test?profile=${encodePathSegment(profile)}",
            method = "POST",
            body = buildJsonObject { },
        ),
    )

    suspend fun restartGateway(
        config: BackendConfig,
        token: String,
        profile: String,
    ): ActionResponse = startAction(
        config,
        token,
        "/api/gateway/restart?profile=${encodePathSegment(profile)}",
    )

    suspend fun mcpServers(
        config: BackendConfig,
        token: String,
        profile: String,
    ): McpServersResponse = get(
        config,
        token,
        "/api/mcp/servers?profile=${encodePathSegment(profile)}",
        McpServersResponse.serializer(),
    )

    suspend fun mcpCatalog(
        config: BackendConfig,
        token: String,
        profile: String,
    ): McpCatalogResponse = get(
        config,
        token,
        "/api/mcp/catalog?profile=${encodePathSegment(profile)}",
        McpCatalogResponse.serializer(),
    )

    suspend fun testMcpServer(
        config: BackendConfig,
        token: String,
        profile: String,
        name: String,
    ): McpServerTestResponse = json.decodeFromJsonElement(
        McpServerTestResponse.serializer(),
        request(
            config,
            token,
            "/api/mcp/servers/${encodePathSegment(name)}/test?profile=${encodePathSegment(profile)}",
            method = "POST",
            body = buildJsonObject { },
        ),
    )

    suspend fun setMcpServerEnabled(
        config: BackendConfig,
        token: String,
        profile: String,
        name: String,
        enabled: Boolean,
    ): McpServerToggleResponse = json.decodeFromJsonElement(
        McpServerToggleResponse.serializer(),
        request(
            config,
            token,
            "/api/mcp/servers/${encodePathSegment(name)}/enabled?profile=${encodePathSegment(profile)}",
            method = "PUT",
            body = buildJsonObject { put("enabled", enabled) },
        ),
    )

    suspend fun usageAnalytics(
        config: BackendConfig,
        token: String,
        profile: String,
        days: Int,
    ): AnalyticsResponse {
        require(days in 1..3650) { "Usage period must be between 1 and 3,650 days" }
        return get(
            config,
            token,
            "/api/analytics/usage?days=$days&profile=${encodePathSegment(profile)}",
            AnalyticsResponse.serializer(),
        )
    }

    suspend fun downloadManagedFile(
        config: BackendConfig,
        token: String,
        path: String,
        output: OutputStream,
        onProgress: (bytesCopied: Long, totalBytes: Long?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val base = TransportPolicy.validate(config).getOrThrow().toString().trimEnd('/')
        val request = Request.Builder()
            .url("$base/api/files/download?path=${encodePathSegment(path)}")
            .get()
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "Hermes-Android/0.1")
            .apply {
                if (config.authMode == com.nousresearch.hermes.data.AuthMode.DASHBOARD_SESSION) {
                    header("Cookie", token)
                } else {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()
        val call = client.newCall(request)
        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = response.body?.string().orEmpty().take(500)
                    throw HermesHttpException(response.code, detail.ifBlank { response.message })
                }
                val body = response.body ?: throw IOException("Hermes returned an empty file response")
                val total = body.contentLength().takeIf { it >= 0 }
                body.byteStream().use { input ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    var copied = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(copied, total)
                    }
                    output.flush()
                }
            }
        } finally {
            cancellation?.dispose()
        }
    }

    suspend fun renameSession(
        config: BackendConfig,
        token: String,
        sessionId: String,
        title: String,
        profile: String?,
    ) {
        val body = buildJsonObject {
            put("title", title)
            profile?.let { put("profile", it) }
        }
        request(
            config,
            token,
            "/api/sessions/${encodePathSegment(sessionId)}",
            method = "PATCH",
            body = body,
        )
    }

    suspend fun archiveSession(
        config: BackendConfig,
        token: String,
        sessionId: String,
        archived: Boolean,
        profile: String?,
    ) {
        val body = buildJsonObject {
            put("archived", archived)
            profile?.let { put("profile", it) }
        }
        request(
            config,
            token,
            "/api/sessions/${encodePathSegment(sessionId)}",
            method = "PATCH",
            body = body,
        )
    }

    suspend fun getJson(config: BackendConfig, token: String, path: String): JsonElement =
        request(config, token, path)

    suspend fun skills(config: BackendConfig, token: String): List<SkillInfo> =
        get(config, token, "/api/skills", ListSerializer(SkillInfo.serializer()))

    suspend fun toggleSkill(
        config: BackendConfig,
        token: String,
        name: String,
        enabled: Boolean,
    ): SkillToggleResult = json.decodeFromJsonElement(
        SkillToggleResult.serializer(),
        request(
            config,
            token,
            "/api/skills/toggle",
            method = "PUT",
            body = buildJsonObject {
                put("name", name)
                put("enabled", enabled)
            },
        ),
    )

    suspend fun cronJobs(config: BackendConfig, token: String): List<CronJob> =
        get(config, token, "/api/cron/jobs", ListSerializer(CronJob.serializer()))

    suspend fun cronRuns(
        config: BackendConfig,
        token: String,
        jobId: String,
        limit: Int = 20,
    ): CronRunPage = get(
        config,
        token,
        "/api/cron/jobs/${encodePathSegment(jobId)}/runs?limit=${limit.coerceIn(1, 100)}",
        CronRunPage.serializer(),
    )

    suspend fun setCronEnabled(
        config: BackendConfig,
        token: String,
        jobId: String,
        enabled: Boolean,
    ): CronJob = json.decodeFromJsonElement(
        CronJob.serializer(),
        request(
            config,
            token,
            "/api/cron/jobs/${encodePathSegment(jobId)}/${if (enabled) "resume" else "pause"}",
            method = "POST",
            body = buildJsonObject { },
        ),
    )

    suspend fun triggerCron(config: BackendConfig, token: String, jobId: String): CronJob =
        json.decodeFromJsonElement(
            CronJob.serializer(),
            request(
                config,
                token,
                "/api/cron/jobs/${encodePathSegment(jobId)}/trigger",
                method = "POST",
                body = buildJsonObject { },
            ),
        )

    suspend fun createCron(
        config: BackendConfig,
        token: String,
        payload: CronJobCreatePayload,
    ): CronJob = json.decodeFromJsonElement(
        CronJob.serializer(),
        request(
            config,
            token,
            "/api/cron/jobs",
            method = "POST",
            body = json.encodeToJsonElement(CronJobCreatePayload.serializer(), payload),
        ),
    )

    suspend fun updateCron(
        config: BackendConfig,
        token: String,
        jobId: String,
        updates: CronJobUpdates,
    ): CronJob = json.decodeFromJsonElement(
        CronJob.serializer(),
        request(
            config,
            token,
            "/api/cron/jobs/${encodePathSegment(jobId)}",
            method = "PUT",
            body = buildJsonObject {
                put("updates", json.encodeToJsonElement(CronJobUpdates.serializer(), updates))
            },
        ),
    )

    suspend fun deleteCron(config: BackendConfig, token: String, jobId: String) {
        request(
            config,
            token,
            "/api/cron/jobs/${encodePathSegment(jobId)}",
            method = "DELETE",
        )
    }

    suspend fun profiles(config: BackendConfig, token: String): ProfilesResponse =
        get(config, token, "/api/profiles", ProfilesResponse.serializer())

    suspend fun activeProfile(config: BackendConfig, token: String): ActiveProfileResponse =
        get(config, token, "/api/profiles/active", ActiveProfileResponse.serializer())

    suspend fun createProfile(config: BackendConfig, token: String, payload: ProfileCreatePayload) {
        request(
            config,
            token,
            "/api/profiles",
            method = "POST",
            body = json.encodeToJsonElement(ProfileCreatePayload.serializer(), payload),
        )
    }

    suspend fun renameProfile(config: BackendConfig, token: String, name: String, newName: String) {
        request(
            config,
            token,
            "/api/profiles/${encodePathSegment(name)}",
            method = "PATCH",
            body = buildJsonObject { put("new_name", newName) },
        )
    }

    suspend fun setActiveProfile(config: BackendConfig, token: String, name: String) {
        request(
            config,
            token,
            "/api/profiles/active",
            method = "POST",
            body = buildJsonObject { put("name", name) },
        )
    }

    suspend fun deleteProfile(config: BackendConfig, token: String, name: String) {
        request(
            config,
            token,
            "/api/profiles/${encodePathSegment(name)}",
            method = "DELETE",
        )
    }

    suspend fun runDoctor(config: BackendConfig, token: String): ActionResponse =
        startAction(config, token, "/api/ops/doctor")

    suspend fun runSecurityAudit(config: BackendConfig, token: String): ActionResponse =
        startAction(config, token, "/api/ops/security-audit")

    suspend fun actionStatus(
        config: BackendConfig,
        token: String,
        name: String,
        lines: Int = 400,
        profile: String? = null,
    ): ActionStatusResponse {
        require(name in ALLOWED_ACTIONS || SKILL_ACTION.matches(name)) { "Unsupported Hermes background action" }
        val query = buildList {
            add("lines=${lines.coerceIn(1, 2_000)}")
            profile?.let { add("profile=${encodePathSegment(it)}") }
        }.joinToString("&")
        return get(
            config,
            token,
            "/api/actions/$name/status?$query",
            ActionStatusResponse.serializer(),
        )
    }

    suspend fun globalModelOptions(
        config: BackendConfig,
        token: String,
        profile: String,
        refresh: Boolean = false,
    ): ModelOptionsResult = get(
        config,
        token,
        "/api/model/options?explicit_only=1&include_unconfigured=1&refresh=${if (refresh) 1 else 0}&profile=${encodePathSegment(profile)}",
        ModelOptionsResult.serializer(),
    )

    suspend fun envVars(config: BackendConfig, token: String, profile: String): Map<String, EnvVarInfo> = get(
        config,
        token,
        "/api/env?profile=${encodePathSegment(profile)}",
        MapSerializer(String.serializer(), EnvVarInfo.serializer()),
    )

    suspend fun validateProviderCredential(
        config: BackendConfig,
        token: String,
        key: String,
        value: String,
        apiKey: String = "",
    ): ProviderValidationResult = json.decodeFromJsonElement(
        ProviderValidationResult.serializer(),
        request(
            config,
            token,
            "/api/providers/validate",
            method = "POST",
            body = buildJsonObject {
                put("key", key)
                put("value", value)
                put("api_key", apiKey)
            },
        ),
    )

    suspend fun setEnvVar(config: BackendConfig, token: String, profile: String, key: String, value: String) {
        request(
            config,
            token,
            "/api/env",
            method = "PUT",
            body = buildJsonObject {
                put("key", key)
                put("value", value)
                put("profile", profile)
            },
        )
    }

    suspend fun deleteEnvVar(config: BackendConfig, token: String, profile: String, key: String) {
        request(
            config,
            token,
            "/api/env",
            method = "DELETE",
            body = buildJsonObject {
                put("key", key)
                put("profile", profile)
            },
        )
    }

    suspend fun skillHubSources(config: BackendConfig, token: String, profile: String): SkillHubSourcesResponse = get(
        config,
        token,
        "/api/skills/hub/sources?profile=${encodePathSegment(profile)}",
        SkillHubSourcesResponse.serializer(),
    )

    suspend fun searchSkillHub(
        config: BackendConfig,
        token: String,
        profile: String,
        query: String,
    ): SkillHubSearchResponse = get(
        config,
        token,
        "/api/skills/hub/search?q=${encodePathSegment(query)}&source=all&limit=30&profile=${encodePathSegment(profile)}",
        SkillHubSearchResponse.serializer(),
    )

    suspend fun previewSkillHub(config: BackendConfig, token: String, profile: String, identifier: String): SkillHubPreview = get(
        config,
        token,
        "/api/skills/hub/preview?identifier=${encodePathSegment(identifier)}&profile=${encodePathSegment(profile)}",
        SkillHubPreview.serializer(),
    )

    suspend fun scanSkillHub(config: BackendConfig, token: String, profile: String, identifier: String): SkillHubScanResult = get(
        config,
        token,
        "/api/skills/hub/scan?identifier=${encodePathSegment(identifier)}&profile=${encodePathSegment(profile)}",
        SkillHubScanResult.serializer(),
    )

    suspend fun installSkillHub(config: BackendConfig, token: String, profile: String, identifier: String): ActionResponse =
        startAction(
            config,
            token,
            "/api/skills/hub/install",
            buildJsonObject { put("identifier", identifier); put("profile", profile) },
        )

    suspend fun uninstallSkillHub(config: BackendConfig, token: String, profile: String, name: String): ActionResponse =
        startAction(
            config,
            token,
            "/api/skills/hub/uninstall",
            buildJsonObject { put("name", name); put("profile", profile) },
        )

    suspend fun updateSkillsHub(config: BackendConfig, token: String, profile: String): ActionResponse =
        startAction(config, token, "/api/skills/hub/update", buildJsonObject { put("profile", profile) })

    private suspend fun startAction(
        config: BackendConfig,
        token: String,
        path: String,
        body: JsonElement = buildJsonObject { },
    ): ActionResponse =
        json.decodeFromJsonElement(
            ActionResponse.serializer(),
            request(config, token, path, method = "POST", body = body),
        )

    private suspend fun <T> get(
        config: BackendConfig,
        token: String?,
        path: String,
        serializer: DeserializationStrategy<T>,
    ): T = json.decodeFromJsonElement(serializer, request(config, token, path))

    private suspend fun request(
        config: BackendConfig,
        token: String?,
        path: String,
        method: String = "GET",
        body: JsonElement? = null,
    ): JsonElement = withContext(Dispatchers.IO) {
        val base = TransportPolicy.validate(config).getOrThrow().toString().trimEnd('/')
        require(path.startsWith('/')) { "Hermes API paths must be absolute" }
        val requestBody = body?.let {
            json.encodeToString(JsonElement.serializer(), it).toRequestBody(JSON_MEDIA_TYPE)
        }
        val request = Request.Builder()
            .url(base + path)
            .method(method, requestBody)
            .header("Accept", "application/json")
            .header("User-Agent", "Hermes-Android/0.1")
            .apply {
                if (!token.isNullOrBlank()) {
                    if (config.authMode == com.nousresearch.hermes.data.AuthMode.DASHBOARD_SESSION) {
                        header("Cookie", token)
                    } else {
                        header("Authorization", "Bearer $token")
                    }
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    json.parseToJsonElement(raw).toString().take(500)
                }.getOrDefault(raw.take(500))
                throw HermesHttpException(response.code, detail.ifBlank { response.message })
            }
            if (raw.isBlank()) buildJsonObject { put("ok", true) } else json.parseToJsonElement(raw)
        }
    }

    private fun encodePathSegment(value: String): String =
        okhttp3.HttpUrl.Builder().scheme("https").host("placeholder.invalid").addPathSegment(value)
            .build().encodedPath.removePrefix("/")

    private companion object {
        val ALLOWED_ACTIONS = setOf("doctor", "security-audit", "gateway-restart")
        val SKILL_ACTION = Regex("skills-(?:install|uninstall|update)(?:-[a-z0-9-]{1,80})?")
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
    }
}

class HermesHttpException(
    val statusCode: Int,
    detail: String,
) : IOException("Hermes returned HTTP $statusCode: $detail")

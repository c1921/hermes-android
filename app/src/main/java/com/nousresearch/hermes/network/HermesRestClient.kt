package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.protocol.CronJob
import com.nousresearch.hermes.protocol.CronJobCreatePayload
import com.nousresearch.hermes.protocol.CronJobUpdates
import com.nousresearch.hermes.protocol.SessionMessagePage
import com.nousresearch.hermes.protocol.SessionPage
import com.nousresearch.hermes.protocol.SkillInfo
import com.nousresearch.hermes.protocol.SkillToggleResult
import com.nousresearch.hermes.protocol.StatusResponse
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
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
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
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
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class HermesHttpException(
    val statusCode: Int,
    detail: String,
) : IOException("Hermes returned HTTP $statusCode: $detail")

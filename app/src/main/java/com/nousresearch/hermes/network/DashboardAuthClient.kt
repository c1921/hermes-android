package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.BackendConfig
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class DashboardSessionCookie(val name: String, val value: String) {
    init {
        require(name in ACCEPTED_NAMES && value.isNotBlank() && value.length <= 8_192 && value.none { it.isISOControl() || it == ';' }) {
            "Malformed Hermes Dashboard session cookie"
        }
    }

    val headerValue: String get() = "$name=$value"

    override fun toString(): String = "DashboardSessionCookie(name=$name, value=<redacted>)"

    companion object {
        private val ACCEPTED_NAMES = setOf(
            "hermes_session_at",
            "__Host-hermes_session_at",
            "__Secure-hermes_session_at",
        )

        fun fromSetCookieHeaders(headers: List<String>): DashboardSessionCookie? = headers.firstNotNullOfOrNull { header ->
            val pair = header.substringBefore(';').trim()
            val separator = pair.indexOf('=')
            if (separator <= 0) return@firstNotNullOfOrNull null
            val name = pair.substring(0, separator).trim()
            val value = pair.substring(separator + 1).trim()
            runCatching { DashboardSessionCookie(name, value) }.getOrNull()
        }
    }
}

class DashboardAuthClient(
    private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun login(config: BackendConfig, username: String, password: String): DashboardSessionCookie =
        withContext(Dispatchers.IO) {
            val base = TransportPolicy.validate(config).getOrThrow().toString().trimEnd('/')
            require(username.isNotBlank() && password.isNotEmpty()) { "Dashboard username and password are required" }
            val body = json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                buildJsonObject {
                    put("provider", "basic")
                    put("username", username)
                    put("password", password)
                    put("next", "")
                },
            ).toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$base/auth/password-login")
                .post(body)
                .header("Accept", "application/json")
                .header("User-Agent", "Hermes-Android/0.1")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DashboardAuthenticationException(
                        when (response.code) {
                            401 -> "Invalid dashboard username or password."
                            429 -> "Too many dashboard login attempts. Wait before reconnecting."
                            else -> "Hermes Dashboard sign-in failed with HTTP ${response.code}."
                        },
                    )
                }
                DashboardSessionCookie.fromSetCookieHeaders(response.headers.values("Set-Cookie"))
                    ?: throw DashboardAuthenticationException("Hermes Dashboard did not return a valid access session cookie.")
            }
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class DashboardAuthenticationException(message: String) : IOException(message)

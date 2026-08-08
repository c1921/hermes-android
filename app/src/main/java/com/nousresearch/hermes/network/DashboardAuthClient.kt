package com.nousresearch.hermes.network

import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.data.SessionCredentialStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class DashboardAuthProvider(
    val name: String,
    val displayName: String,
    val supportsPassword: Boolean,
)

class DashboardSessionCredential private constructor(cookies: Map<String, String>) {
    private val values = LinkedHashMap(cookies)

    constructor(name: String, value: String) : this(mapOf(name to value))

    init {
        require(isValid(values)) { "Malformed Hermes Dashboard session cookie" }
    }

    val headerValue: String
        @Synchronized get() = ordered(values).joinToString("; ") { (name, value) -> "$name=$value" }

    @Synchronized
    fun mergeSetCookieHeaders(headers: List<String>): Boolean {
        val updates = parseSetCookieHeaders(headers) ?: return false
        if (updates.isEmpty()) return false
        val merged = LinkedHashMap(values)
        updates.forEach { update ->
            merged.keys.filter { baseName(it) == update.baseName }.forEach { merged.remove(it) }
            if (!update.deleted) merged[update.name] = update.value
        }
        if (!isValid(merged)) return false
        if (merged == values) return false
        values.clear()
        values.putAll(merged)
        return true
    }

    override fun toString(): String = "DashboardSessionCredential(<redacted>)"

    override fun equals(other: Any?): Boolean = other is DashboardSessionCredential && headerValue == other.headerValue

    override fun hashCode(): Int = headerValue.hashCode()

    companion object {
        private val BASE_NAMES = listOf(
            "hermes_session_at",
            "hermes_session_rt",
            "hermes_session_provider",
        )
        private val PREFIXES = listOf("__Host-", "__Secure-", "")
        private const val MAX_COOKIE_VALUE_LENGTH = 8_192
        private const val MAX_HEADER_LENGTH = 24_576

        fun fromSetCookieHeaders(headers: List<String>): DashboardSessionCredential? {
            val updates = parseSetCookieHeaders(headers) ?: return null
            val cookies = updates.filterNot(CookieUpdate::deleted).associate { it.name to it.value }
            return runCatching { DashboardSessionCredential(cookies) }.getOrNull()
        }

        fun fromCookieHeader(header: String): DashboardSessionCredential? {
            if (header.isBlank() || header.length > MAX_HEADER_LENGTH) return null
            val cookies = LinkedHashMap<String, String>()
            for (part in header.split(';')) {
                val pair = parsePair(part) ?: return null
                if (baseName(pair.first) == null || cookies.put(pair.first, pair.second) != null) return null
            }
            return runCatching { DashboardSessionCredential(cookies) }.getOrNull()
        }

        private fun parseSetCookieHeaders(headers: List<String>): List<CookieUpdate>? {
            val updates = mutableListOf<CookieUpdate>()
            val seen = mutableSetOf<String>()
            for (header in headers) {
                val pair = parsePair(header.substringBefore(';')) ?: continue
                val base = baseName(pair.first) ?: continue
                if (!seen.add(base)) return null
                val deleted = pair.second.isEmpty() || header.split(';').drop(1).any {
                    it.trim().equals("Max-Age=0", ignoreCase = true)
                }
                updates += CookieUpdate(pair.first, base, pair.second, deleted)
            }
            return updates
        }

        private fun parsePair(raw: String): Pair<String, String>? {
            val pair = raw.trim()
            val separator = pair.indexOf('=')
            if (separator <= 0) return null
            val name = pair.substring(0, separator).trim()
            val value = pair.substring(separator + 1).trim()
            if (name.isBlank() || value.length > MAX_COOKIE_VALUE_LENGTH) return null
            if (name.any { it.isISOControl() || it == ';' } || value.any { it.isISOControl() || it == ';' }) return null
            return name to value
        }

        private fun baseName(name: String): String? = BASE_NAMES.firstOrNull { base ->
            PREFIXES.any { prefix -> name == prefix + base }
        }

        private fun isValid(cookies: Map<String, String>): Boolean {
            if (cookies.isEmpty()) return false
            val bases = cookies.keys.map { baseName(it) ?: return false }
            if (bases.toSet().size != bases.size || "hermes_session_at" !in bases) return false
            if (cookies.values.any { it.isBlank() || it.length > MAX_COOKIE_VALUE_LENGTH || it.any(Char::isISOControl) || ';' in it }) {
                return false
            }
            return ordered(cookies).sumOf { (name, value) -> name.length + value.length + 2 } <= MAX_HEADER_LENGTH
        }

        private fun ordered(cookies: Map<String, String>): List<Map.Entry<String, String>> =
            cookies.entries.sortedBy { BASE_NAMES.indexOf(baseName(it.key)) }
    }
}

private data class CookieUpdate(
    val name: String,
    val baseName: String,
    val value: String,
    val deleted: Boolean,
)

class DashboardAuthClient(
    client: OkHttpClient,
    private val json: Json,
    private val credentials: SessionCredentialStore? = null,
) {
    private val transport = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun login(
        config: BackendConfig,
        username: String,
        password: String,
        passwordProvider: String? = null,
    ): DashboardSessionCredential =
        withContext(Dispatchers.IO) {
            val base = TransportPolicy.validate(config).getOrThrow().toString().trimEnd('/')
            require(username.isNotBlank() && password.isNotEmpty()) { "Dashboard username and password are required" }
            val provider = selectPasswordProvider(discoverPasswordProvidersBlocking(base), passwordProvider)
            val body = json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                buildJsonObject {
                    put("provider", provider)
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
            transport.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DashboardAuthenticationException(
                        when (response.code) {
                            401 -> "Invalid dashboard username or password."
                            429 -> "Too many dashboard login attempts. Wait before reconnecting."
                            else -> "Hermes Dashboard sign-in failed with HTTP ${response.code}."
                        },
                    )
                }
                DashboardSessionCredential.fromSetCookieHeaders(response.headers.values("Set-Cookie"))
                    ?: throw DashboardAuthenticationException("Hermes Dashboard did not return a valid access session cookie.")
            }
        }

    suspend fun discoverPasswordProviders(config: BackendConfig): List<DashboardAuthProvider> =
        withContext(Dispatchers.IO) {
            val base = TransportPolicy.validate(config).getOrThrow().toString().trimEnd('/')
            val providers = discoverPasswordProvidersBlocking(base).filter(DashboardAuthProvider::supportsPassword)
            if (providers.isEmpty()) {
                throw DashboardAuthenticationException("Hermes Dashboard does not advertise password sign-in.")
            }
            providers
        }

    private fun discoverPasswordProvidersBlocking(base: String): List<DashboardAuthProvider> {
        val request = Request.Builder()
            .url("$base/api/auth/providers")
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", "Hermes-Android/0.1")
            .build()
        return transport.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DashboardAuthenticationException(
                    "Hermes Dashboard password-provider discovery failed with HTTP ${response.code}.",
                )
            }
            val raw = readBoundedBody(response)
            val advertised = try {
                json.decodeFromString<DashboardAuthProvidersResponse>(raw)
            } catch (error: Throwable) {
                throw DashboardAuthenticationException("Hermes Dashboard returned malformed password-provider discovery data.")
            }
            sanitizeProviders(advertised.providers)
        }
    }

    private fun selectPasswordProvider(
        advertised: List<DashboardAuthProvider>,
        selectedName: String?,
    ): String {
        val passwordProviders = advertised.filter(DashboardAuthProvider::supportsPassword)
        if (selectedName != null) {
            requireSafeProviderName(selectedName)
            val selected = advertised.singleOrNull { it.name == selectedName }
                ?: throw DashboardAuthenticationException("The selected dashboard password provider is no longer advertised.")
            if (!selected.supportsPassword) {
                throw DashboardAuthenticationException("The selected dashboard provider does not support password sign-in.")
            }
            return selected.name
        }
        return when (passwordProviders.size) {
            1 -> passwordProviders.single().name
            0 -> throw DashboardAuthenticationException("Hermes Dashboard does not advertise password sign-in.")
            else -> throw DashboardAuthenticationException(
                "Hermes Dashboard advertises multiple password providers; choose one before connecting Android.",
            )
        }
    }

    private fun sanitizeProviders(rawProviders: List<DashboardAuthProviderPayload>): List<DashboardAuthProvider> {
        if (rawProviders.isEmpty()) return emptyList()
        if (rawProviders.size > MAX_ADVERTISED_PROVIDERS) {
            throw DashboardAuthenticationException("Hermes Dashboard advertised too many password providers.")
        }
        val names = mutableSetOf<String>()
        val foldedNames = mutableSetOf<String>()
        return rawProviders.map { raw ->
            val name = sanitizeProviderField(raw.name, "name")
            if (!names.add(name) || !foldedNames.add(name.lowercase(Locale.ROOT))) {
                throw DashboardAuthenticationException("Hermes Dashboard advertised duplicate password providers.")
            }
            val displayName = raw.displayName?.let { sanitizeProviderField(it, "display name") } ?: name
            DashboardAuthProvider(name, displayName, raw.supportsPassword)
        }
    }

    private fun sanitizeProviderField(value: String, field: String): String {
        val trimmed = value.trim()
        if (
            trimmed.isBlank() ||
            trimmed.length > MAX_PROVIDER_FIELD_LENGTH ||
            trimmed.any(Char::isISOControl)
        ) {
            throw DashboardAuthenticationException("Hermes Dashboard advertised an invalid password-provider $field.")
        }
        if (value != trimmed) {
            throw DashboardAuthenticationException("Hermes Dashboard advertised an invalid password-provider $field.")
        }
        return trimmed
    }

    private fun requireSafeProviderName(value: String) {
        if (
            value.isBlank() ||
            value.length > MAX_PROVIDER_FIELD_LENGTH ||
            value != value.trim() ||
            value.any(Char::isISOControl)
        ) {
            throw DashboardAuthenticationException("The selected dashboard password provider is invalid.")
        }
    }

    private fun readBoundedBody(response: okhttp3.Response): String {
        val body = response.body ?: throw DashboardAuthenticationException(
            "Hermes Dashboard returned an empty password-provider discovery response.",
        )
        if (body.contentLength() > MAX_PROVIDER_RESPONSE_BYTES) {
            throw DashboardAuthenticationException("Hermes Dashboard returned an oversized password-provider discovery response.")
        }
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_PROVIDER_RESPONSE_BYTES) {
                    throw DashboardAuthenticationException("Hermes Dashboard returned an oversized password-provider discovery response.")
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    suspend fun mintWebSocketTicket(config: BackendConfig, cookie: DashboardSessionCredential): String =
        withContext(Dispatchers.IO) {
            val base = TransportPolicy.validate(config).getOrThrow().toString().trimEnd('/')
            val sentHeader = cookie.headerValue
            val request = Request.Builder()
                .url("$base/api/auth/ws-ticket")
                .post(ByteArray(0).toRequestBody())
                .header("Accept", "application/json")
                .header("Cookie", cookie.headerValue)
                .header("User-Agent", "Hermes-Android/0.1")
                .build()
            transport.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DashboardAuthenticationException(
                        when (response.code) {
                            401, 403 -> "Dashboard session expired or was rejected."
                            else -> "Hermes Dashboard WebSocket ticket request failed with HTTP ${response.code}."
                        },
                    )
                }
                if (cookie.mergeSetCookieHeaders(response.headers.values("Set-Cookie"))) {
                    val stored = credentials?.get(config.id)
                    if (stored?.headerValue == sentHeader) credentials.put(config.id, cookie)
                }
                val ticket = response.body?.string()?.let {
                    runCatching { json.decodeFromString<WebSocketTicketResponse>(it) }.getOrNull()
                }?.ticket.orEmpty()
                if (ticket.isBlank() || ticket.length > 8_192 || ticket.any(Char::isISOControl)) {
                    throw DashboardAuthenticationException("Hermes Dashboard returned a malformed WebSocket ticket.")
                }
                ticket
            }
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class WebSocketTicketResponse(val ticket: String)

@Serializable
private data class DashboardAuthProvidersResponse(val providers: List<DashboardAuthProviderPayload> = emptyList())

@Serializable
private data class DashboardAuthProviderPayload(
    val name: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("supports_password") val supportsPassword: Boolean = false,
)

class DashboardAuthenticationException(message: String) : IOException(message)

private const val MAX_ADVERTISED_PROVIDERS = 32
private const val MAX_PROVIDER_FIELD_LENGTH = 128
private const val MAX_PROVIDER_RESPONSE_BYTES = 256 * 1024

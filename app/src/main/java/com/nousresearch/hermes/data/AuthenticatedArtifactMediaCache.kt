package com.nousresearch.hermes.data

import android.content.Context
import com.nousresearch.hermes.network.HermesRestClient
import com.nousresearch.hermes.protocol.FsTextPreview
import com.nousresearch.hermes.security.SecureTokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ArtifactMediaRequest(
    val profileId: String,
    val sessionId: String,
    val contentIdentity: String,
    val path: String,
    val expectedMimeType: String? = null,
    val expectedSize: Long? = null,
    val expectedSha256: String? = null,
)

data class CachedArtifactMedia(
    val file: File,
    val mimeType: String,
    val byteSize: Long,
    val sha256: String,
)

@Singleton
class AuthenticatedArtifactMediaCache @Inject constructor(
    private val rest: HermesRestClient,
    private val credentials: SecureTokenStore,
    @ApplicationContext context: Context,
) {
    private val root = File(context.noBackupFilesDir, CACHE_DIRECTORY)
    private val cache = ArtifactMediaDiskCache(root)

    suspend fun load(config: BackendConfig, request: ArtifactMediaRequest): CachedArtifactMedia {
        val credential = credential(config)
        val partitionIdentity = sha256(
            "${config.id}\u0000$credential".toByteArray(StandardCharsets.UTF_8),
        )
        return cache.load(request, partitionIdentity) { path ->
            rest.readFsDataUrl(config, credential, path).dataUrl
        }
    }

    suspend fun readText(config: BackendConfig, path: String): FsTextPreview {
        validateServerPath(path)
        val response = rest.readFsText(config, credential(config), path)
        return validateFsTextPreview(path, response)
    }

    private fun credential(config: BackendConfig): String = credentials.get(config.id)?.headerValue
        ?: throw IOException("Reconnect ${config.label} before opening this artifact")

    private companion object {
        const val CACHE_DIRECTORY = "hermes-artifact-media-v1"
    }
}

internal fun interface ArtifactMediaLoader {
    suspend fun load(path: String): String
}

internal class ArtifactMediaDiskCache(
    private val root: File,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val maximumBytes: Long = DEFAULT_MAXIMUM_CACHE_BYTES,
    private val maximumEntries: Int = DEFAULT_MAXIMUM_CACHE_ENTRIES,
    private val ttlMillis: Long = DEFAULT_CACHE_TTL_MILLIS,
    private val json: Json = Json { ignoreUnknownKeys = false; explicitNulls = false },
) {
    private val mutex = Mutex()

    suspend fun load(
        request: ArtifactMediaRequest,
        cachePartitionIdentity: String = TEST_CACHE_PARTITION,
        loader: ArtifactMediaLoader,
    ): CachedArtifactMedia {
        validateRequest(request)
        require(SAFE_KEY.matches(cachePartitionIdentity)) { "Artifact cache partition is invalid" }
        val key = request.cacheKey(cachePartitionIdentity)
        val dataFile = File(root, "$key.bin")
        val metadataFile = File(root, "$key.json")
        mutex.withLock {
            root.mkdirs()
            if (!root.isDirectory) throw IOException("Android could not create the artifact cache")
            cleanupUnlocked()
            if (request.expectedSha256 != null) {
                readValid(request, key, dataFile, metadataFile)?.let { cached ->
                    val accessedAt = nowMillis()
                    writeMetadata(metadataFile, cached.toMetadata(key, accessedAt))
                    dataFile.setLastModified(accessedAt)
                    return cached
                }
            }
            deletePair(dataFile, metadataFile)
        }

        val parsed = parseDataUrl(loader.load(request.path))
        validatePayload(request, parsed)
        val digest = sha256(parsed.bytes)
        request.expectedSha256?.let { expected ->
            if (!digest.equals(expected, ignoreCase = true)) throw IOException("Hermes returned artifact data with an unexpected digest")
        }

        return mutex.withLock {
            val temporaryData = File(root, "$key.${UUID.randomUUID()}.tmp")
            val temporaryMetadata = File(root, "$key.${UUID.randomUUID()}.tmp")
            try {
                temporaryData.outputStream().buffered().use { it.write(parsed.bytes) }
                val storedAt = nowMillis()
                val metadata = ArtifactCacheMetadata(
                    key = key,
                    mimeType = parsed.mimeType,
                    byteSize = parsed.bytes.size.toLong(),
                    sha256 = digest,
                    storedAtMillis = storedAt,
                    accessedAtMillis = storedAt,
                )
                temporaryMetadata.writeText(json.encodeToString(ArtifactCacheMetadata.serializer(), metadata))
                moveAtomically(temporaryData, dataFile)
                moveAtomically(temporaryMetadata, metadataFile)
                cleanupUnlocked()
                readValid(request, key, dataFile, metadataFile)
                    ?: throw IOException("Android could not validate the cached artifact")
            } finally {
                temporaryData.delete()
                temporaryMetadata.delete()
            }
        }
    }

    internal suspend fun cleanup() = mutex.withLock { cleanupUnlocked() }

    private fun cleanupUnlocked() {
        if (!root.isDirectory) return
        val now = nowMillis()
        root.listFiles().orEmpty()
            .filter { it.name.endsWith(".tmp") }
            .forEach(File::delete)

        val entries = root.listFiles().orEmpty()
            .filter { it.name.endsWith(".json") }
            .mapNotNull { metadataFile ->
                val metadata = runCatching {
                    json.decodeFromString(ArtifactCacheMetadata.serializer(), metadataFile.readText())
                }.getOrNull()
                val dataFile = metadata?.let { File(root, "${it.key}.bin") }
                if (
                    metadata == null ||
                    !SAFE_KEY.matches(metadata.key) ||
                    dataFile == null ||
                    !dataFile.isFile ||
                    now - metadata.storedAtMillis > ttlMillis
                ) {
                    metadataFile.delete()
                    dataFile?.delete()
                    null
                } else {
                    CacheEntry(metadataFile, dataFile, metadata)
                }
            }
            .sortedWith(compareBy<CacheEntry> { it.metadata.accessedAtMillis }.thenBy { it.metadata.key })
            .toMutableList()

        val knownNames = entries.flatMap { listOf(it.metadataFile.name, it.dataFile.name) }.toSet()
        root.listFiles().orEmpty()
            .filter { it.name !in knownNames }
            .forEach(File::delete)

        var totalBytes = entries.sumOf { it.dataFile.length() }
        while (entries.size > maximumEntries || totalBytes > maximumBytes) {
            val removed = entries.removeAt(0)
            totalBytes -= removed.dataFile.length()
            deletePair(removed.dataFile, removed.metadataFile)
        }
    }

    private fun readValid(
        request: ArtifactMediaRequest,
        key: String,
        dataFile: File,
        metadataFile: File,
    ): CachedArtifactMedia? {
        if (!dataFile.isFile || !metadataFile.isFile) return null
        val metadata = runCatching {
            json.decodeFromString(ArtifactCacheMetadata.serializer(), metadataFile.readText())
        }.getOrNull() ?: return null
        if (metadata.key != key || metadata.byteSize != dataFile.length()) return null
        if (metadata.byteSize !in 0..MAXIMUM_ARTIFACT_BYTES) return null
        if (!isAllowedMimeType(metadata.mimeType) || !SAFE_KEY.matches(metadata.sha256)) return null
        if (nowMillis() - metadata.storedAtMillis > ttlMillis) return null
        if (request.expectedSize != null && request.expectedSize != metadata.byteSize) return null
        val expectedMime = request.expectedMimeType?.normalizedMime()
        if (expectedMime != null && expectedMime != metadata.mimeType) return null
        val digest = sha256(dataFile.readBytes())
        if (digest != metadata.sha256) return null
        if (request.expectedSha256 != null && !digest.equals(request.expectedSha256, ignoreCase = true)) return null
        return CachedArtifactMedia(dataFile, metadata.mimeType, metadata.byteSize, digest)
    }

    private fun validatePayload(request: ArtifactMediaRequest, parsed: ParsedDataUrl) {
        request.expectedSize?.let { expected ->
            if (expected != parsed.bytes.size.toLong()) throw IOException("Hermes returned artifact data with an unexpected size")
        }
        request.expectedMimeType?.normalizedMime()?.let { expected ->
            if (expected != parsed.mimeType) throw IOException("Hermes returned artifact data with an unexpected type")
        }
    }

    private fun parseDataUrl(value: String): ParsedDataUrl {
        val comma = value.indexOf(',')
        if (!value.startsWith("data:") || comma <= 5) throw IOException("Hermes returned invalid artifact data")
        val headerParts = value.substring(5, comma).split(';').map(String::trim)
        if (headerParts.size < 2 || !headerParts.last().equals("base64", ignoreCase = true)) {
            throw IOException("Hermes returned invalid artifact data")
        }
        if (headerParts.drop(1).dropLast(1).any { parameter ->
                parameter.length !in 1..128 || parameter.none { it == '=' } || parameter.any(Char::isISOControl)
            }
        ) {
            throw IOException("Hermes returned invalid artifact data")
        }
        val mimeType = headerParts.first().normalizedMime()
        if (!isAllowedMimeType(mimeType)) throw IOException("Hermes returned an unsupported artifact type")
        val payload = value.substring(comma + 1)
        val estimatedBytes = (payload.length.toLong() * 3L) / 4L
        if (estimatedBytes > MAXIMUM_ARTIFACT_BYTES + 2L) throw IOException("Artifact exceeds the Android safety limit")
        val bytes = try {
            Base64.getDecoder().decode(payload)
        } catch (_: IllegalArgumentException) {
            throw IOException("Hermes returned malformed artifact data")
        }
        if (bytes.size.toLong() > MAXIMUM_ARTIFACT_BYTES) throw IOException("Artifact exceeds the Android safety limit")
        return ParsedDataUrl(mimeType, bytes)
    }

    private fun writeMetadata(file: File, metadata: ArtifactCacheMetadata) {
        val temporary = File(root, "${metadata.key}.${UUID.randomUUID()}.tmp")
        try {
            temporary.writeText(json.encodeToString(ArtifactCacheMetadata.serializer(), metadata))
            moveAtomically(temporary, file)
        } finally {
            temporary.delete()
        }
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun validateRequest(request: ArtifactMediaRequest) {
        require(request.profileId.isNotBlank()) { "Artifact profile scope is required" }
        require(request.sessionId.isNotBlank()) { "Artifact session scope is required" }
        require(request.contentIdentity.isNotBlank()) { "Artifact content identity is required" }
        require(request.path.isNotBlank()) { "Artifact path is required" }
        validateServerPath(request.path)
        require(request.profileId.length <= MAXIMUM_SCOPE_CHARACTERS) { "Artifact profile scope is too long" }
        require(request.sessionId.length <= MAXIMUM_SCOPE_CHARACTERS) { "Artifact session scope is too long" }
        require(request.contentIdentity.length <= MAXIMUM_SCOPE_CHARACTERS) { "Artifact content identity is too long" }
        request.expectedSize?.let { require(it in 0..MAXIMUM_ARTIFACT_BYTES) { "Artifact size is invalid" } }
        request.expectedSha256?.let { require(SAFE_KEY.matches(it)) { "Artifact digest is invalid" } }
        request.expectedMimeType?.normalizedMime()?.let {
            require(isAllowedMimeType(it)) { "Artifact type is unsupported" }
        }
    }

    private fun ArtifactMediaRequest.cacheKey(cachePartitionIdentity: String): String = sha256(
        listOf(
            CACHE_FORMAT_VERSION,
            cachePartitionIdentity,
            profileId,
            sessionId,
            contentIdentity,
            path,
            expectedMimeType?.normalizedMime().orEmpty(),
            expectedSize?.toString().orEmpty(),
            expectedSha256?.lowercase().orEmpty(),
        ).joinToString("\u0000").toByteArray(StandardCharsets.UTF_8),
    )

    private fun CachedArtifactMedia.toMetadata(key: String, accessedAt: Long): ArtifactCacheMetadata =
        ArtifactCacheMetadata(
            key = key,
            mimeType = mimeType,
            byteSize = byteSize,
            sha256 = sha256,
            storedAtMillis = runCatching {
                json.decodeFromString(
                    ArtifactCacheMetadata.serializer(),
                    File(root, "$key.json").readText(),
                ).storedAtMillis
            }.getOrDefault(accessedAt),
            accessedAtMillis = accessedAt,
        )

    private fun deletePair(dataFile: File, metadataFile: File) {
        dataFile.delete()
        metadataFile.delete()
    }

    private data class ParsedDataUrl(val mimeType: String, val bytes: ByteArray)
    private data class CacheEntry(
        val metadataFile: File,
        val dataFile: File,
        val metadata: ArtifactCacheMetadata,
    )
}

@Serializable
private data class ArtifactCacheMetadata(
    val key: String,
    val mimeType: String,
    val byteSize: Long,
    val sha256: String,
    val storedAtMillis: Long,
    val accessedAtMillis: Long,
)

private fun String.normalizedMime(): String = lowercase().substringBefore(';').trim()

private fun isAllowedMimeType(mimeType: String): Boolean =
    mimeType.startsWith("text/") || mimeType in SAFE_MEDIA_MIME_TYPES || mimeType in SAFE_DOCUMENT_MIME_TYPES

private val SAFE_MEDIA_MIME_TYPES = setOf(
    "audio/aac",
    "audio/flac",
    "audio/mp4",
    "audio/mpeg",
    "audio/ogg",
    "audio/wav",
    "audio/webm",
    "image/bmp",
    "image/gif",
    "image/jpeg",
    "image/png",
    "image/webp",
    "video/mp4",
    "video/mpeg",
    "video/ogg",
    "video/quicktime",
    "video/webm",
)

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private val SAFE_DOCUMENT_MIME_TYPES = setOf(
    "application/json",
    "application/octet-stream",
    "application/pdf",
    "application/xml",
    "application/zip",
)
private val SAFE_TEXT_MIME_TYPES = setOf(
    "application/javascript",
    "application/json",
    "application/toml",
    "application/x-httpd-php",
    "application/x-sh",
    "application/x-yaml",
    "application/xml",
    "application/yaml",
)
private val SAFE_KEY = Regex("[a-fA-F0-9]{64}")
private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:[\\\\/].+")
private const val TEST_CACHE_PARTITION = "0000000000000000000000000000000000000000000000000000000000000000"
private const val CACHE_FORMAT_VERSION = "artifact-media-v1"
private const val MAXIMUM_ARTIFACT_BYTES = 16L * 1024L * 1024L
private const val MAXIMUM_TEXT_PREVIEW_BYTES = 512 * 1024
private const val MAXIMUM_TEXT_SOURCE_BYTES = 64L * 1024L * 1024L
private const val MAXIMUM_SCOPE_CHARACTERS = 1_024
private const val MAXIMUM_PATH_CHARACTERS = 4_096
private const val DEFAULT_MAXIMUM_CACHE_BYTES = 64L * 1024L * 1024L
private const val DEFAULT_MAXIMUM_CACHE_ENTRIES = 64
private const val DEFAULT_CACHE_TTL_MILLIS = 7L * 24L * 60L * 60L * 1_000L

private fun validateServerPath(path: String) {
    require(path == path.trim() && path.isNotEmpty()) { "Artifact path is invalid" }
    require(path.length <= MAXIMUM_PATH_CHARACTERS) { "Artifact path is too long" }
    require('\u0000' !in path) { "Artifact path is invalid" }
    require(path.startsWith('/') || WINDOWS_ABSOLUTE_PATH.matches(path)) { "Artifact path must be absolute" }
}

internal fun validateFsTextPreview(requestedPath: String, response: FsTextPreview): FsTextPreview {
    if (response.path != requestedPath) throw IOException("Hermes returned a different artifact than requested")
    if (response.binary) throw IOException("Hermes returned binary data for a text artifact")
    if (response.byteSize !in 0..MAXIMUM_TEXT_SOURCE_BYTES) throw IOException("Artifact text exceeds the Android safety limit")
    if (response.text.toByteArray(StandardCharsets.UTF_8).size > MAXIMUM_TEXT_PREVIEW_BYTES) {
        throw IOException("Artifact text preview exceeds the Android safety limit")
    }
    val mimeType = response.mimeType.normalizedMime()
    if (mimeType != "image/svg+xml" && !mimeType.startsWith("text/") && mimeType !in SAFE_TEXT_MIME_TYPES) {
        throw IOException("Hermes returned an unsupported text artifact type")
    }
    return response
}

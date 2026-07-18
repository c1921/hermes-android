package com.nousresearch.hermes.data

import android.content.Context
import android.net.Uri
import com.nousresearch.hermes.network.HermesRestClient
import com.nousresearch.hermes.protocol.ManagedFileEntry
import com.nousresearch.hermes.protocol.ManagedFilesResponse
import com.nousresearch.hermes.security.SecureTokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

enum class WorkspacePreviewKind { TEXT, HTML, IMAGE, PDF }

data class WorkspaceFilePreview(
    val entry: ManagedFileEntry,
    val kind: WorkspacePreviewKind,
    val mimeType: String,
    val text: String = "",
    val bytes: ByteArray = byteArrayOf(),
)

@Singleton
class WorkspaceFilesRepository @Inject constructor(
    private val rest: HermesRestClient,
    private val credentials: SecureTokenStore,
    @ApplicationContext private val context: Context,
) {
    suspend fun list(config: BackendConfig, path: String?): ManagedFilesResponse =
        rest.managedFiles(config, credential(config), path?.validatedPath())

    suspend fun preview(config: BackendConfig, entry: ManagedFileEntry): WorkspaceFilePreview {
        require(!entry.isDirectory) { "Directories cannot be previewed" }
        val kind = previewKind(entry)
            ?: throw IOException("Preview is not available for ${entry.mimeType ?: "this file type"}. Save it to your device to open it safely.")
        val maximum = maximumPreviewBytes(kind)
        entry.size?.let { size ->
            if (size > maximum) throw IOException("${entry.name} is too large for an in-app preview")
        }
        val response = rest.readManagedFile(config, credential(config), entry.path.validatedPath())
        if (response.path != entry.path) throw IOException("Hermes returned a different file than requested")
        val bytes = decodeDataUrl(response.dataUrl, maximum)
        if (response.size != bytes.size.toLong()) throw IOException("Hermes returned an incomplete file preview")
        val mimeType = response.mimeType.lowercase().substringBefore(';').trim()
        val responseKind = previewKind(entry.copy(mimeType = mimeType))
        if (responseKind != kind) throw IOException("Hermes returned an unexpected file type for ${entry.name}")
        return when (kind) {
            WorkspacePreviewKind.TEXT -> {
                if (looksBinary(bytes)) throw IOException("${entry.name} contains binary data and cannot be shown as text")
                WorkspaceFilePreview(entry, kind, mimeType, text = String(bytes, StandardCharsets.UTF_8))
            }
            WorkspacePreviewKind.HTML -> WorkspaceFilePreview(
                entry,
                kind,
                mimeType,
                text = String(bytes, StandardCharsets.UTF_8),
            )
            WorkspacePreviewKind.IMAGE,
            WorkspacePreviewKind.PDF,
            -> WorkspaceFilePreview(entry, kind, mimeType, bytes = bytes)
        }
    }

    suspend fun download(
        config: BackendConfig,
        entry: ManagedFileEntry,
        destination: Uri,
        onProgress: (bytesCopied: Long, totalBytes: Long?) -> Unit,
    ) {
        require(!entry.isDirectory) { "Directories cannot be downloaded" }
        val resolver = context.contentResolver
        var complete = false
        try {
            val output = resolver.openOutputStream(destination, "w")
                ?: throw IOException("Android could not open the selected destination")
            output.use {
                rest.downloadManagedFile(
                    config,
                    credential(config),
                    entry.path.validatedPath(),
                    it,
                    onProgress,
                )
            }
            complete = true
        } finally {
            if (!complete) runCatching { resolver.delete(destination, null, null) }
        }
    }

    private fun credential(config: BackendConfig): String = credentials.get(config.id)?.headerValue
        ?: throw IOException("Reconnect ${config.label} before browsing its files")
}

internal fun previewKind(entry: ManagedFileEntry): WorkspacePreviewKind? {
    val mime = entry.mimeType.orEmpty().lowercase().substringBefore(';').trim()
    val extension = entry.name.substringAfterLast('.', "").lowercase()
    return when {
        mime == "text/html" || extension in setOf("html", "htm") -> WorkspacePreviewKind.HTML
        mime == "application/pdf" || extension == "pdf" -> WorkspacePreviewKind.PDF
        mime in SAFE_RASTER_IMAGE_MIME_TYPES -> WorkspacePreviewKind.IMAGE
        mime.startsWith("text/") || mime in SAFE_TEXT_MIME_TYPES || extension in SAFE_TEXT_EXTENSIONS -> WorkspacePreviewKind.TEXT
        else -> null
    }
}

internal fun decodeDataUrl(dataUrl: String, maximumBytes: Long): ByteArray {
    val comma = dataUrl.indexOf(',')
    if (!dataUrl.startsWith("data:") || comma <= 5 || ";base64" !in dataUrl.substring(0, comma)) {
        throw IOException("Hermes returned an invalid file preview")
    }
    val payload = dataUrl.substring(comma + 1)
    val estimatedSize = (payload.length.toLong() * 3L) / 4L
    if (estimatedSize > maximumBytes + 2L) throw IOException("File preview exceeds the Android safety limit")
    val decoded = try {
        Base64.getDecoder().decode(payload)
    } catch (_: IllegalArgumentException) {
        throw IOException("Hermes returned malformed file data")
    }
    if (decoded.size.toLong() > maximumBytes) throw IOException("File preview exceeds the Android safety limit")
    return decoded
}

private fun String.validatedPath(): String {
    val normalized = trim()
    require(normalized.isNotEmpty()) { "A file path is required" }
    require(normalized.length <= MAX_MANAGED_PATH_CHARACTERS) { "File path is too long" }
    require('\u0000' !in normalized) { "File path is invalid" }
    return normalized
}

private fun maximumPreviewBytes(kind: WorkspacePreviewKind): Long = when (kind) {
    WorkspacePreviewKind.TEXT -> MAX_TEXT_PREVIEW_BYTES
    WorkspacePreviewKind.HTML -> MAX_HTML_PREVIEW_BYTES
    WorkspacePreviewKind.IMAGE -> MAX_IMAGE_PREVIEW_BYTES
    WorkspacePreviewKind.PDF -> MAX_PDF_PREVIEW_BYTES
}

private fun looksBinary(bytes: ByteArray): Boolean {
    val sample = bytes.take(4_096)
    if (sample.any { it == 0.toByte() }) return true
    if (sample.isEmpty()) return false
    val suspicious = sample.count { byte ->
        val value = byte.toInt() and 0xff
        value < 32 && value !in setOf(9, 10, 13)
    }
    return suspicious.toDouble() / sample.size > 0.12
}

private val SAFE_RASTER_IMAGE_MIME_TYPES = setOf(
    "image/png",
    "image/jpeg",
    "image/gif",
    "image/webp",
    "image/bmp",
)

private val SAFE_TEXT_MIME_TYPES = setOf(
    "application/json",
    "application/xml",
    "application/javascript",
    "application/x-httpd-php",
    "application/x-sh",
    "image/svg+xml",
)

private val SAFE_TEXT_EXTENSIONS = setOf(
    "c", "conf", "cpp", "css", "csv", "go", "graphql", "h", "hpp", "java", "js", "json", "jsx",
    "kt", "kts", "lua", "md", "mjs", "py", "rb", "rs", "sh", "sql", "svg", "toml", "ts", "tsx",
    "txt", "xml", "yaml", "yml", "zsh",
)

private const val MAX_MANAGED_PATH_CHARACTERS = 4_096
private const val MAX_TEXT_PREVIEW_BYTES = 1L * 1024L * 1024L
private const val MAX_HTML_PREVIEW_BYTES = 2L * 1024L * 1024L
private const val MAX_IMAGE_PREVIEW_BYTES = 16L * 1024L * 1024L
private const val MAX_PDF_PREVIEW_BYTES = 24L * 1024L * 1024L

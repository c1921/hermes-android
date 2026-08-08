package com.nousresearch.hermes.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import com.nousresearch.hermes.platform.safeContentName
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AttachmentPayload(
    val displayName: String,
    val mimeType: String,
    val base64: String,
    val byteCount: Int,
)

data class AttachmentMetadata(
    val displayName: String,
    val mimeType: String,
    val declaredSize: Long?,
)

@Singleton
class AttachmentReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun inspect(uri: Uri): AttachmentMetadata = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.let { safeContentName(it, "attachment") }
        val declaredSize = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.takeIf { it >= 0 }
        require(declaredSize == null || declaredSize <= MAX_BYTES.toLong()) {
            "Attachment is too large. Android uploads are currently capped at ${MAX_BYTES / 1_048_576} MiB."
        }
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val reportedMimeType = resolver.getType(uri)?.lowercase()?.substringBefore(';')?.trim()
            ?.takeIf(String::isNotBlank)
        val inferredMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.lowercase()
        val mimeType = inferredMimeType.takeIf { reportedMimeType == null || reportedMimeType == "application/octet-stream" }
            ?: reportedMimeType
            ?: "application/octet-stream"
        require(isSupportedAttachmentMime(mimeType)) {
            "This attachment type is not supported by Hermes Android ($mimeType)."
        }
        AttachmentMetadata(displayName, mimeType, declaredSize)
    }

    suspend fun read(
        uri: Uri,
        metadata: AttachmentMetadata? = null,
        releaseAfterRead: Boolean = true,
        onProgress: (bytesRead: Int, totalBytes: Long?) -> Unit = { _, _ -> },
    ): AttachmentPayload {
        val resolvedMetadata = metadata ?: inspect(uri)
        return withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            try {
                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    val output = java.io.ByteArrayOutputStream()
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= MAX_BYTES) {
                            "Attachment is too large. Android uploads are currently capped at " +
                                "${MAX_BYTES / 1_048_576} MiB."
                        }
                        output.write(buffer, 0, count)
                        onProgress(output.size(), resolvedMetadata.declaredSize)
                    }
                    output.toByteArray()
                } ?: error("Android could not open this document")
                require(bytes.isNotEmpty()) { "The selected document is empty" }
                AttachmentPayload(
                    displayName = resolvedMetadata.displayName,
                    mimeType = resolvedMetadata.mimeType,
                    base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                    byteCount = bytes.size,
                )
            } finally {
                if (releaseAfterRead) release(uri)
            }
        }
    }

    fun release(uri: Uri) {
        if (uri.authority == "${context.packageName}.fileprovider") {
            context.contentResolver.delete(uri, null, null)
        }
    }

    private companion object {
        const val MAX_BYTES = 10 * 1_048_576
    }
}

internal fun isSupportedAttachmentMime(mimeType: String): Boolean {
    val normalized = mimeType.lowercase().substringBefore(';').trim()
    if (normalized in BLOCKED_ATTACHMENT_MIME_TYPES) return false
    return normalized.startsWith("text/") || normalized.startsWith("image/") ||
        normalized.startsWith("audio/") || normalized.startsWith("video/") || normalized.startsWith("application/")
}

private val BLOCKED_ATTACHMENT_MIME_TYPES = setOf(
    "application/vnd.android.package-archive",
    "application/x-executable",
    "application/x-msdownload",
    "application/x-sharedlib",
)

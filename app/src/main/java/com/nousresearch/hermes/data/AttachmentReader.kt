package com.nousresearch.hermes.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
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

@Singleton
class AttachmentReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun read(uri: Uri): AttachmentPayload = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }.let { safeContentName(it, "attachment") }
            val declaredSize = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            require(declaredSize == null || declaredSize < 0 || declaredSize <= MAX_BYTES.toLong()) {
                "Attachment is too large. Android uploads are currently capped at ${MAX_BYTES / 1_048_576} MiB."
            }
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val output = java.io.ByteArrayOutputStream()
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_BYTES) {
                        "Attachment is too large. Android uploads are currently capped at ${MAX_BYTES / 1_048_576} MiB."
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("Android could not open this document")
            require(bytes.isNotEmpty()) { "The selected document is empty" }
            AttachmentPayload(
                displayName = displayName,
                mimeType = resolver.getType(uri)?.lowercase() ?: "application/octet-stream",
                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                byteCount = bytes.size,
            )
        } finally {
            if (uri.authority == "${context.packageName}.fileprovider") resolver.delete(uri, null, null)
        }
    }

    private companion object {
        const val MAX_BYTES = 10 * 1_048_576
    }
}

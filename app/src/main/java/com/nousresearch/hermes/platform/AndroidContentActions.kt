package com.nousresearch.hermes.platform

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

fun textShareIntent(text: String): Intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, text)
}

fun sharedFileUri(context: Context, name: String, bytes: ByteArray): Uri {
    require(bytes.isNotEmpty()) { "The file is empty" }
    val root = File(context.cacheDir, "shared").apply { check(mkdirs() || isDirectory) }
    root.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > SHARED_FILE_MAX_AGE_MS }
        ?.forEach(File::deleteRecursively)
    val directory = File(root, UUID.randomUUID().toString()).apply { check(mkdir()) }
    val file = File(directory, safeContentName(name, "hermes-file")).apply { writeBytes(bytes) }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

fun fileShareIntent(uri: Uri, mimeType: String, name: String): Intent = Intent(Intent.ACTION_SEND).apply {
    type = mimeType
    putExtra(Intent.EXTRA_STREAM, uri)
    putExtra(Intent.EXTRA_TITLE, name)
    clipData = ClipData.newRawUri(name, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

fun fileOpenIntent(uri: Uri, mimeType: String, name: String): Intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, mimeType)
    clipData = ClipData.newRawUri(name, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

internal fun safeContentName(raw: String?, fallback: String): String =
    raw?.take(200)?.replace(UNSAFE_NAME_CHARS, "_")?.takeUnless { it.isBlank() || it == "." || it == ".." } ?: fallback

private val UNSAFE_NAME_CHARS = Regex("[^A-Za-z0-9._() -]")
private const val SHARED_FILE_MAX_AGE_MS = 60L * 60L * 1_000L

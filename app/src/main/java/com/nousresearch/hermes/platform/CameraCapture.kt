package com.nousresearch.hermes.platform

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun newCameraCaptureUri(context: Context): Uri {
    val directory = File(context.cacheDir, "camera").apply { check(mkdirs() || isDirectory) }
    directory.listFiles()
        ?.filter { it.name.startsWith("hermes-camera-") && it.lastModified() < System.currentTimeMillis() - MAX_CAPTURE_AGE_MILLIS }
        ?.forEach(File::delete)
    val file = File.createTempFile("hermes-camera-", ".jpg", directory)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private const val MAX_CAPTURE_AGE_MILLIS = 24 * 60 * 60 * 1_000L

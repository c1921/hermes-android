package com.nousresearch.hermes.platform

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun newCameraCaptureUri(context: Context): Uri {
    val directory = File(context.cacheDir, "camera").apply { check(mkdirs() || isDirectory) }
    val file = File.createTempFile("hermes-camera-", ".jpg", directory)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

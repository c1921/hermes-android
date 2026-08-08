package com.nousresearch.hermes.platform

import androidx.test.platform.app.InstrumentationRegistry
import com.nousresearch.hermes.data.AttachmentReader
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCaptureTest {
    @Test
    fun capturedPhotoUsesPrivateContentUriAndIsDeletedAfterReading() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = newCameraCaptureUri(context)
        val bytes = byteArrayOf(1, 2, 3, 4)
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }

        val payload = AttachmentReader(context).read(uri)

        assertEquals("image/jpeg", payload.mimeType)
        assertEquals(bytes.toList(), Base64.getDecoder().decode(payload.base64).toList())
        assertTrue(runCatching { context.contentResolver.openInputStream(uri)!!.close() }.isFailure)
    }

    @Test
    fun abandonedCameraFilesArePrunedOnTheNextCapture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val old = File(context.cacheDir, "camera/hermes-camera-abandoned.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
            setLastModified(System.currentTimeMillis() - 25 * 60 * 60 * 1_000L)
        }

        val fresh = newCameraCaptureUri(context)

        assertTrue(!old.exists())
        context.contentResolver.delete(fresh, null, null)
    }
}

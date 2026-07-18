package com.nousresearch.hermes.platform

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidContentActionsTest {
    @Test
    fun sharedFileUsesPrivateProviderAndReadOnlyIntents() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = "Hermes artifact".toByteArray()
        val uri = sharedFileUri(context, "report/<final>.txt", bytes)

        assertEquals(bytes.toList(), context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toList() })
        assertEquals("report__final_.txt", uri.lastPathSegment)
        assertEquals("hermes-file", safeContentName("..", "hermes-file"))
        listOf(
            fileShareIntent(uri, "text/plain", "report.txt") to Intent.ACTION_SEND,
            fileOpenIntent(uri, "text/plain", "report.txt") to Intent.ACTION_VIEW,
        ).forEach { (intent, action) ->
            assertEquals(action, intent.action)
            assertEquals("text/plain", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        }
        context.contentResolver.delete(uri, null, null)
    }

    @Test
    fun textShareUsesPlainTextWithoutGrantFlags() {
        val intent = textShareIntent("Hello from Hermes")

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals("Hello from Hermes", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertTrue(!intent.hasExtra(Intent.EXTRA_STREAM))
    }
}

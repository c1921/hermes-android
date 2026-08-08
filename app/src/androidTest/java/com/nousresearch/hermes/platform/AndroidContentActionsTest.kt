package com.nousresearch.hermes.platform

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        }
        context.contentResolver.delete(uri, null, null)
    }

    @Test
    fun sharedFilesUseSeparateDirectoriesAndKeepEarlierExport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = sharedFileUri(context, "first.txt", "first".toByteArray())
        val second = sharedFileUri(context, "second.txt", "second".toByteArray())

        assertNotEquals(
            first.path!!.substringBeforeLast('/'),
            second.path!!.substringBeforeLast('/'),
        )
        assertEquals("first", context.contentResolver.openInputStream(first)!!.use { it.readBytes().decodeToString() })
        assertEquals("second", context.contentResolver.openInputStream(second)!!.use { it.readBytes().decodeToString() })
        context.contentResolver.delete(first, null, null)
        context.contentResolver.delete(second, null, null)
    }

    @Test
    fun sharedFilePrunesOnlyEntriesOlderThanTheConfiguredAge() {
        val root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "shared-test-${System.nanoTime()}",
        ).apply { check(mkdirs()) }
        try {
            val stale = File(root, "stale").apply { check(mkdirs()) }
            File(stale, "payload.txt").writeText("stale")
            val fresh = File(root, "fresh").apply { check(mkdirs()) }
            File(fresh, "payload.txt").writeText("fresh")
            check(stale.setLastModified(0L))
            check(fresh.setLastModified(2_000_001L))

            pruneStaleSharedFiles(root, nowMillis = 3_600_001L, maxAgeMillis = 3_600_000L)

            assertTrue(!stale.exists())
            assertTrue(fresh.exists())
            assertEquals("fresh", File(fresh, "payload.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptySharedFileIsRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        sharedFileUri(context, "empty.txt", ByteArray(0))
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

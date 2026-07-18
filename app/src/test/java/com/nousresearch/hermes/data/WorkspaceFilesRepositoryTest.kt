package com.nousresearch.hermes.data

import com.nousresearch.hermes.protocol.ManagedFileEntry
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFilesRepositoryTest {
    @Test
    fun `preview kind allows safe formats and treats svg as text`() {
        assertEquals(WorkspacePreviewKind.TEXT, previewKind(entry("notes.md", "text/markdown")))
        assertEquals(WorkspacePreviewKind.HTML, previewKind(entry("report.html", "text/html")))
        assertEquals(WorkspacePreviewKind.IMAGE, previewKind(entry("plot.webp", "image/webp")))
        assertEquals(WorkspacePreviewKind.PDF, previewKind(entry("brief.pdf", "application/pdf")))
        assertEquals(WorkspacePreviewKind.TEXT, previewKind(entry("diagram.svg", "image/svg+xml")))
        assertNull(previewKind(entry("archive.zip", "application/zip")))
    }

    @Test
    fun `data url decoding enforces format and byte limit`() {
        assertArrayEquals("hello".toByteArray(), decodeDataUrl("data:text/plain;base64,aGVsbG8=", 5))

        val oversized = runCatching {
            decodeDataUrl("data:text/plain;base64,aGVsbG8=", 4)
        }.exceptionOrNull()
        assertTrue(oversized is IOException)

        val malformed = runCatching {
            decodeDataUrl("data:text/plain,hello", 100)
        }.exceptionOrNull()
        assertTrue(malformed is IOException)
    }

    private fun entry(name: String, mimeType: String) = ManagedFileEntry(
        name = name,
        path = "/workspace/$name",
        isDirectory = false,
        mimeType = mimeType,
    )
}

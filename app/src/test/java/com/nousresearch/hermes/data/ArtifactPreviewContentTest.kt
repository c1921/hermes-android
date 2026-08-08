package com.nousresearch.hermes.data

import com.nousresearch.hermes.domain.DetectedArtifact
import com.nousresearch.hermes.domain.DetectedArtifactKind
import com.nousresearch.hermes.domain.DetectedArtifactOrigin
import com.nousresearch.hermes.domain.DetectedArtifactSource
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactPreviewContentTest {
    private val origin = DetectedArtifactOrigin("backend", "default", "session", messageId = "message")

    @Test
    fun `fenced html svg and code stay local with explicit render modes`() {
        val html = inlineArtifactPreview(artifact(DetectedArtifactKind.HTML, "<h1>Report</h1>", "text/html"))
        val svg = inlineArtifactPreview(artifact(DetectedArtifactKind.SVG, "<svg><circle r=\"4\"/></svg>", "image/svg+xml"))
        val code = inlineArtifactPreview(artifact(DetectedArtifactKind.CODE, "fun main() = Unit", "text/x-kotlin"))

        assertTrue(html is ArtifactPreviewContent.Text)
        assertTrue(svg is ArtifactPreviewContent.Text)
        assertTrue(code is ArtifactPreviewContent.Text)
        assertEquals(ArtifactTextRenderMode.HTML, (html as ArtifactPreviewContent.Text).renderMode)
        assertEquals(ArtifactTextRenderMode.SVG, (svg as ArtifactPreviewContent.Text).renderMode)
        assertEquals(ArtifactTextRenderMode.SOURCE, (code as ArtifactPreviewContent.Text).renderMode)
    }

    @Test
    fun `bounded inline image decodes without a network request`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val dataUrl = "data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}"

        val preview = inlineArtifactPreview(
            artifact(DetectedArtifactKind.IMAGE, dataUrl, "image/png", DetectedArtifactSource.INLINE_IMAGE),
        )

        assertTrue(preview is ArtifactPreviewContent.Binary)
        preview as ArtifactPreviewContent.Binary
        assertEquals("image/png", preview.mimeType)
        assertArrayEquals(bytes, preview.bytes)
    }

    @Test
    fun `credential free web links remain explicit external content`() {
        val preview = inlineArtifactPreview(artifact(DetectedArtifactKind.LINK, "https://example.com/report"))

        assertTrue(preview is ArtifactPreviewContent.External)
        assertEquals("https://example.com/report", (preview as ArtifactPreviewContent.External).url)
    }

    @Test
    fun `server paths require authenticated loading`() {
        assertNull(inlineArtifactPreview(artifact(DetectedArtifactKind.FILE, "/Users/luinbytes/report.md", "text/markdown")))
    }

    private fun artifact(
        kind: DetectedArtifactKind,
        value: String,
        mimeType: String? = null,
        source: DetectedArtifactSource = DetectedArtifactSource.FENCED_CODE,
    ) = DetectedArtifact(
        id = "artifact-${kind.name.lowercase()}",
        kind = kind,
        value = value,
        label = "report",
        mimeType = mimeType,
        source = source,
        origin = origin,
    )
}

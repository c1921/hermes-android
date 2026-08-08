package com.nousresearch.hermes.domain

import com.nousresearch.hermes.protocol.ManagedFileEntry
import com.nousresearch.hermes.protocol.ProtocolMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectedArtifactsTest {
    private val scope = DetectedArtifactScope("backend-a", "profile-a", "session-a")

    @Test
    fun `identity is deterministic scoped and does not contain raw content`() {
        val message = ProtocolMessage("message-1", "assistant", JsonPrimitive("See https://example.com/report.pdf"))
        val first = DetectedArtifactRepository.detect(scope, listOf(message)).single()
        val second = DetectedArtifactRepository.detect(scope, listOf(message)).single()
        val otherSession = DetectedArtifactRepository.detect(scope.copy(sessionId = "session-b"), listOf(message)).single()

        assertEquals(first.id, second.id)
        assertNotEquals(first.id, otherSession.id)
        assertFalse(first.id.contains("example.com"))
        assertEquals(DetectedArtifactProvenance.DETECTED, first.provenance)
        assertEquals("session-a", first.origin.sessionId)
        assertEquals("message-1", first.origin.messageId)
    }

    @Test
    fun `safe links and image markdown are detected while credentials and local urls are rejected`() {
        val message = ProtocolMessage(
            "message-2",
            "assistant",
            JsonPrimitive(
                """
                [docs](https://example.com/docs?q=1#top)
                ![cat](https://example.com/cat.png)
                https://user:secret@example.com/private
                file:///Users/me/private.txt
                javascript:alert(1)
                """.trimIndent(),
            ),
        )
        val artifacts = DetectedArtifactRepository.detect(scope, listOf(message))

        assertEquals(listOf(DetectedArtifactKind.LINK, DetectedArtifactKind.IMAGE), artifacts.map { it.kind })
        assertEquals("https://example.com/docs?q=1#top", artifacts[0].href)
        assertEquals("https://example.com/cat.png", artifacts[1].href)
        assertTrue(artifacts.none { it.value.startsWith("file:", ignoreCase = true) })
    }

    @Test
    fun `media markers and encoded media hrefs retain a session origin without file urls`() {
        val message = ProtocolMessage(
            "message-3",
            "tool",
            JsonPrimitive("audio MEDIA:\"/tmp/song.mp3\" and [Image](#media:%2Ftmp%2Fcat.png)"),
        )
        val artifacts = DetectedArtifactRepository.detect(scope, listOf(message))

        assertEquals(listOf(DetectedArtifactKind.FILE, DetectedArtifactKind.IMAGE), artifacts.map { it.kind })
        assertEquals("/tmp/song.mp3", artifacts[0].value)
        assertEquals("/tmp/cat.png", artifacts[1].value)
        assertTrue(artifacts.all { it.href == null || !it.href.startsWith("file:") })
        assertTrue(artifacts.all { it.origin.messageId == "message-3" && it.origin.partId != null })
    }

    @Test
    fun `media markers preserve safe remote and inline audio as media`() {
        val inlineAudio = "data:audio/ogg;base64,${"A".repeat(64)}"
        val message = ProtocolMessage(
            "message-media",
            "tool",
            JsonPrimitive("MEDIA:https://example.com/voice.opus MEDIA:$inlineAudio"),
        )

        val artifacts = DetectedArtifactRepository.detect(scope, listOf(message))

        assertEquals(listOf(DetectedArtifactKind.FILE, DetectedArtifactKind.FILE), artifacts.map { it.kind })
        assertEquals("https://example.com/voice.opus", artifacts[0].href)
        assertEquals("audio/ogg; codecs=opus", artifacts[0].mimeType)
        assertNull(artifacts[1].href)
        assertEquals("audio/ogg", artifacts[1].mimeType)
    }

    @Test
    fun `fenced html svg and generic code match desktop promotion thresholds`() {
        val html = "<html><body>${"x".repeat(150)}</body></html>"
        val svg = "<svg>${"x".repeat(2_000)}</svg>"
        val code = List(48) { "val line$it = {$it};" }.joinToString("\n")
        val message = ProtocolMessage(
            "message-4",
            "assistant",
            JsonPrimitive(
                """
                ```html
                $html
                ```
                ```svg
                $svg
                ```
                ```kotlin
                $code
                ```
                ```html
                <html><body>too small</body></html>
                ```
                ```text
                ${List(60) { "prose line $it" }.joinToString("\n")}
                ```
                ```html
                ${"x".repeat(DetectedArtifactLimits.MAX_CODE_CHARACTERS + 1)}
                ```
                """.trimIndent(),
            ),
        )
        val artifacts = DetectedArtifactRepository.detect(scope, listOf(message))

        assertEquals(
            listOf(DetectedArtifactKind.HTML, DetectedArtifactKind.SVG, DetectedArtifactKind.CODE),
            artifacts.map { it.kind },
        )
        assertEquals("text/html", artifacts[0].mimeType)
        assertEquals("image/svg+xml", artifacts[1].mimeType)
    }

    @Test
    fun `inline image and image directive are bounded and direct file urls are ignored`() {
        val good = "data:image/png;base64,${"A".repeat(64)}"
        val huge = "data:image/png;base64,${"A".repeat(DetectedArtifactLimits.MAX_INLINE_IMAGE_CHARACTERS + 1)}"
        val message = ProtocolMessage(
            "message-5",
            "user",
            JsonPrimitive("$good @image:`/tmp/shot.png` $huge @image:file:///private/key.png"),
        )
        val artifacts = DetectedArtifactRepository.detect(scope, listOf(message))

        assertEquals(listOf(DetectedArtifactKind.IMAGE, DetectedArtifactKind.IMAGE), artifacts.map { it.kind })
        assertEquals(good, artifacts[0].value)
        assertEquals("/tmp/shot.png", artifacts[1].value)
        assertNull(artifacts.firstOrNull { it.value.startsWith("file:") })
    }

    @Test
    fun `svg sources remain text isolated instead of generic images`() {
        val inline = "data:image/svg+xml;base64,${"A".repeat(64)}"
        val message = ProtocolMessage(
            "message-svg",
            "user",
            JsonPrimitive("$inline @image:`/tmp/diagram.svg`"),
        )
        val managed = ManagedFileEntry("page.html", "/tmp/page.html", false, mimeType = "text/html")

        val artifacts = DetectedArtifactRepository.detect(scope, listOf(message), listOf(managed))

        assertEquals(
            listOf(DetectedArtifactKind.SVG, DetectedArtifactKind.SVG, DetectedArtifactKind.HTML),
            artifacts.map { it.kind },
        )
        assertTrue(artifacts.filter { it.kind == DetectedArtifactKind.SVG }.all { it.href == null })
    }

    @Test
    fun `successful split image generate rows are paired while pending and failed rows are omitted`() {
        val assistant = ProtocolMessage(
            "assistant-1",
            "assistant",
            JsonPrimitive("Generated"),
            toolCalls = Json.parseToJsonElement(
                """[
                    {"id":"success-call","function":{"name":"image_generate","arguments":"{}"}},
                    {"id":"failed-call","function":{"name":"image_generate","arguments":"{}"}},
                    {"id":"pending-call","function":{"name":"image_generate","arguments":"{}"}}
                ]""",
            ),
        )
        val rows = listOf(
            ProtocolMessage(
                "tool-success",
                "tool",
                JsonPrimitive("""{"success":true,"host_image":"/cache/cat.png"}"""),
                toolCallId = "success-call",
                toolName = "image_generate",
            ),
            ProtocolMessage(
                "tool-failed",
                "tool",
                JsonPrimitive("""{"success":false,"image":"/cache/no.png"}"""),
                toolCallId = "failed-call",
                toolName = "image_generate",
            ),
        )
        val artifacts = DetectedArtifactRepository.detect(scope, listOf(assistant) + rows)

        assertEquals(1, artifacts.count { it.source == DetectedArtifactSource.GENERATED_IMAGE })
        assertEquals("/cache/cat.png", artifacts.single { it.source == DetectedArtifactSource.GENERATED_IMAGE }.value)
        assertEquals("success-call", artifacts.single { it.source == DetectedArtifactSource.GENERATED_IMAGE }.origin.partId?.substringAfterLast(':'))
    }

    @Test
    fun `agent visible image alone and assistant image directives are not display artifacts`() {
        val assistant = ProtocolMessage(
            "assistant-directive",
            "assistant",
            JsonPrimitive("@image:/private/assistant.png"),
            toolCalls = Json.parseToJsonElement(
                """[{"id":"echo-only","function":{"name":"image_generate","arguments":"{}"}}]""",
            ),
        )
        val result = ProtocolMessage(
            "tool-echo",
            "tool",
            JsonPrimitive("""{"success":true,"agent_visible_image":"/cache/echo.png"}"""),
            toolCallId = "echo-only",
            toolName = "image_generate",
        )
        val echoed = ProtocolMessage(
            "assistant-echo",
            "assistant",
            JsonPrimitive("![generated](/cache/echo.png)"),
        )

        assertTrue(DetectedArtifactRepository.detect(scope, listOf(assistant, result, echoed)).isEmpty())
    }

    @Test
    fun `generated image display source suppresses later exact assistant echo`() {
        val assistant = ProtocolMessage(
            "assistant-generate",
            "assistant",
            JsonPrimitive("Generating"),
            toolCalls = Json.parseToJsonElement(
                """[{"id":"image-call","function":{"name":"image_generate","arguments":"{}"}}]""",
            ),
        )
        val result = ProtocolMessage(
            "tool-image",
            "tool",
            JsonPrimitive("""{"success":true,"host_image":"/cache/cat.png"}"""),
            toolCallId = "image-call",
            toolName = "image_generate",
        )
        val echo = ProtocolMessage("assistant-echo", "assistant", JsonPrimitive("![cat](/cache/cat.png)"))

        val artifacts = DetectedArtifactRepository.detect(scope, listOf(assistant, result, echo))

        assertEquals(1, artifacts.size)
        assertEquals(DetectedArtifactSource.GENERATED_IMAGE, artifacts.single().source)
    }

    @Test
    fun `detector rejects incomplete scope`() {
        assertTrue(
            runCatching {
                DetectedArtifactRepository.detect(scope.copy(sessionId = ""), emptyList())
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `managed files are a bounded detected fallback and arbitrary artifact json is ignored`() {
        val entries = (0..DetectedArtifactLimits.MAX_MANAGED_FILES).map { index ->
            ManagedFileEntry("file-$index.txt", "/workspace/file-$index.txt", false, mimeType = "text/plain")
        }
        val message = ProtocolMessage(
            "message-6",
            "tool",
            Json.parseToJsonElement(
                """{"artifact":"/secret/not-an-artifact.png","file":"/secret/not-a-file.txt","image_url":"https://evil.example/x.png"}""",
            ),
        )
        val artifacts = DetectedArtifactRepository.detect(scope, listOf(message), entries)

        assertEquals(DetectedArtifactLimits.MAX_MANAGED_FILES, artifacts.size)
        assertTrue(artifacts.all { it.source == DetectedArtifactSource.MANAGED_FILE_FALLBACK })
        assertTrue(artifacts.none { it.value.contains("not-an-artifact") || it.value.contains("evil.example") })
    }

    @Test
    fun `known tool output media is detected but duplicate content is stable and ordered`() {
        val output = ProtocolMessage(
            "tool-1",
            "tool",
            JsonPrimitive("""{"output":"MEDIA:/tmp/voice.mp3 https://example.com/readme.md"}"""),
        )
        val duplicate = ProtocolMessage(
            "tool-1",
            "tool",
            JsonPrimitive("MEDIA:/tmp/voice.mp3 https://example.com/readme.md"),
        )
        val artifacts = DetectedArtifactRepository.detect(scope, listOf(output, duplicate))

        assertEquals(listOf("/tmp/voice.mp3", "https://example.com/readme.md"), artifacts.map { it.value })
        assertEquals(2, artifacts.map { it.id }.distinct().size)
    }
}

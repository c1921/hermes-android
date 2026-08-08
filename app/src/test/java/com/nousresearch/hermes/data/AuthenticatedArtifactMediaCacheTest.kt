package com.nousresearch.hermes.data

import com.nousresearch.hermes.protocol.FsTextPreview
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AuthenticatedArtifactMediaCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `cache stores hashed identity and reuses a validated response`() = runTest {
        val root = temporaryFolder.newFolder("cache")
        val cache = ArtifactMediaDiskCache(root)
        var loads = 0
        val request = request(
            path = "/Users/private/project/cat.png",
            contentIdentity = "message/content",
            expectedMimeType = "image/png",
            expectedSize = 5,
            expectedSha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
        )

        val first = cache.load(request) {
            loads += 1
            "data:image/png;base64,aGVsbG8="
        }
        val second = cache.load(request) {
            loads += 1
            error("validated cache entries must not be fetched again")
        }

        assertEquals(1, loads)
        assertEquals(first.file, second.file)
        assertEquals("hello", second.file.readText())
        assertEquals("image/png", second.mimeType)
        assertTrue(root.listFiles().orEmpty().all { it.name.matches(Regex("[a-f0-9]{64}\\.(?:bin|json)")) })
        val storedText = root.listFiles().orEmpty().joinToString("\n") { it.readText() }
        assertFalse(storedText.contains("/Users/private"))
    }

    @Test
    fun `cache rejects mismatched response identity`() = runTest {
        val dataUrl = "data:image/png;base64,aGVsbG8="
        val mismatchRequests = listOf(
            request("/tmp/a", expectedMimeType = "image/jpeg"),
            request("/tmp/a", expectedSize = 4),
            request("/tmp/a", expectedSha256 = "0".repeat(64)),
        )

        mismatchRequests.forEachIndexed { index, request ->
            val cache = ArtifactMediaDiskCache(temporaryFolder.newFolder("mismatch-$index"))
            expectIOException { cache.load(request) { dataUrl } }
        }
    }

    @Test
    fun `cache partitions credentials and refetches entries without a trusted digest`() = runTest {
        val cache = ArtifactMediaDiskCache(temporaryFolder.newFolder("partitions"))
        var loads = 0
        val pathOnly = request("/tmp/changing.png", expectedMimeType = "image/png", expectedSize = 5)
        val first = cache.load(pathOnly, "1".repeat(64)) {
            loads += 1
            "data:image/png;base64,aGVsbG8="
        }
        val second = cache.load(pathOnly, "1".repeat(64)) {
            loads += 1
            "data:image/png;base64,aGVsbG8="
        }
        val rotated = cache.load(pathOnly, "2".repeat(64)) {
            loads += 1
            "data:image/png;base64,aGVsbG8="
        }

        assertEquals(3, loads)
        assertEquals(first.file, second.file)
        assertNotEquals(first.file, rotated.file)
    }

    @Test
    fun `cache accepts shipped mime parameters and normalizes the type`() = runTest {
        val cache = ArtifactMediaDiskCache(temporaryFolder.newFolder("mime-parameters"))

        val media = cache.load(request("/tmp/voice.opus", expectedMimeType = "audio/ogg")) {
            "data:audio/ogg; codecs=opus;base64,aGVsbG8="
        }

        assertEquals("audio/ogg", media.mimeType)
        assertEquals("hello", media.file.readText())
    }

    @Test
    fun `cache rejects malformed unsupported and oversized data urls`() = runTest {
        val hostileValues = listOf(
            "data:image/png,not-base64",
            "data:image/png;base64,%%%%",
            "data:image/svg+xml;base64,PHN2Zy8+",
            "data:text/html;base64;name=x,PGgxPk5vPC9oMT4=",
        )
        hostileValues.forEachIndexed { index, value ->
            val cache = ArtifactMediaDiskCache(temporaryFolder.newFolder("hostile-$index"))
            expectIOException { cache.load(request("/tmp/a")) { value } }
        }

        val oversizedPayload = "A".repeat(22_369_624)
        val cache = ArtifactMediaDiskCache(temporaryFolder.newFolder("oversized"))
        expectIOException {
            cache.load(request("/tmp/large")) {
                "data:application/octet-stream;base64,$oversizedPayload"
            }
        }
    }

    @Test
    fun `cleanup deterministically enforces age entry and byte bounds`() = runTest {
        var now = 1_000L
        val root = temporaryFolder.newFolder("cleanup")
        val cache = ArtifactMediaDiskCache(
            root = root,
            nowMillis = { now },
            maximumBytes = 6,
            maximumEntries = 2,
            ttlMillis = 100,
        )

        val first = cache.load(request("/tmp/first")) {
            "data:text/plain;base64,MTEx"
        }
        now += 1
        val second = cache.load(request("/tmp/second")) {
            "data:text/plain;base64,MjIy"
        }
        now += 1
        cache.load(request("/tmp/third")) {
            "data:text/plain;base64,MzMz"
        }

        assertFalse(first.file.exists())
        assertTrue(second.file.exists())
        assertEquals(2, root.listFiles().orEmpty().count { it.extension == "bin" })

        now += 101
        cache.cleanup()
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `external urls and invalid request metadata never reach the loader`() = runTest {
        val cache = ArtifactMediaDiskCache(temporaryFolder.newFolder("requests"))
        var loaded = false
        val failures = listOf(
            request("/tmp/a", profileId = ""),
            request("/tmp/a", sessionId = ""),
            request("/tmp/a", contentIdentity = ""),
            request(""),
            request("relative/file.png"),
            request("https://example.com/a.png"),
            request("file:///tmp/a.png"),
            request("data:image/png;base64,AAAA"),
            request("javascript:alert(1)"),
            request("/tmp/a", expectedSize = 16L * 1024L * 1024L + 1L),
            request("/tmp/a", expectedSha256 = "bad"),
        )
        failures.forEach { request ->
            val failure = runCatching {
                cache.load(request) {
                    loaded = true
                    "data:text/plain;base64,"
                }
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
        }
        assertFalse(loaded)
    }

    @Test
    fun `text preview validates response identity type and bounds`() {
        val valid = FsTextPreview(
            path = "/tmp/report.svg",
            text = "<svg></svg>",
            mimeType = "image/svg+xml",
            byteSize = 11,
        )
        assertEquals(valid, validateFsTextPreview("/tmp/report.svg", valid))

        val hostile = listOf(
            valid.copy(path = "/tmp/other.svg"),
            valid.copy(binary = true),
            valid.copy(byteSize = 64L * 1024L * 1024L + 1L),
            valid.copy(mimeType = "application/x-executable"),
            valid.copy(text = "x".repeat(512 * 1024 + 1)),
        )
        hostile.forEach { response ->
            assertTrue(
                runCatching { validateFsTextPreview("/tmp/report.svg", response) }.exceptionOrNull() is IOException,
            )
        }
    }

    private suspend fun expectIOException(block: suspend () -> Unit) {
        assertTrue(runCatching { block() }.exceptionOrNull() is IOException)
    }

    private fun request(
        path: String,
        profileId: String = "profile",
        sessionId: String = "session",
        contentIdentity: String = "content",
        expectedMimeType: String? = null,
        expectedSize: Long? = null,
        expectedSha256: String? = null,
    ) = ArtifactMediaRequest(
        profileId = profileId,
        sessionId = sessionId,
        contentIdentity = contentIdentity,
        path = path,
        expectedMimeType = expectedMimeType,
        expectedSize = expectedSize,
        expectedSha256 = expectedSha256,
    )
}

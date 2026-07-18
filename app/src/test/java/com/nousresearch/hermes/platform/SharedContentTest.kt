package com.nousresearch.hermes.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedContentTest {
    @Test
    fun `sanitizer keeps bounded text and distinct content uris only`() {
        val result = sanitizeSharedContent(
            id = "share-1",
            text = "hello\u0000" + "x".repeat(20_000),
            uriStrings = listOf(
                "content://documents/one",
                "content://documents/one",
                "file:///sdcard/secret.txt",
                "https://example.com/file.txt",
                "content://documents/two",
                "content://documents/three",
                "content://documents/four",
                "content://documents/five",
                "content://documents/six",
            ),
        )

        requireNotNull(result)
        assertEquals("share-1", result.id)
        assertEquals(10_000, result.text.length)
        assertTrue('\u0000' !in result.text)
        assertEquals(
            listOf(
                "content://documents/one",
                "content://documents/two",
                "content://documents/three",
                "content://documents/four",
                "content://documents/five",
            ),
            result.uriStrings,
        )
    }

    @Test
    fun `sanitizer rejects empty and malformed shares`() {
        assertNull(sanitizeSharedContent("empty", "  ", emptyList()))
        assertNull(
            sanitizeSharedContent(
                id = "unsafe",
                text = null,
                uriStrings = listOf("content:///missing-authority", "not a uri", "file:///tmp/a"),
            ),
        )
    }

    @Test
    fun `shared text appends to an existing draft without exceeding draft capacity`() {
        assertEquals("shared", mergeSharedText("", "shared", maxCharacters = 20))
        assertEquals("draft\n\nshared", mergeSharedText("draft", "shared", maxCharacters = 20))
        assertEquals("1234567890\n\nabcdefg", mergeSharedText("1234567890", "abcdefghij", maxCharacters = 19))
    }
}

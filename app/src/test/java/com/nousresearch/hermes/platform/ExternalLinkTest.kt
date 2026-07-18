package com.nousresearch.hermes.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalLinkTest {
    @Test
    fun `only credential-free web links can leave the app`() {
        assertEquals("https://nousresearch.com/docs?q=1#top", safeExternalUrl(" https://nousresearch.com/docs?q=1#top "))
        assertNull(safeExternalUrl("javascript:alert(1)"))
        assertNull(safeExternalUrl("file:///data/user/0/secrets"))
        assertNull(safeExternalUrl("https://user:secret@example.com"))
        assertNull(safeExternalUrl("https://example.com\nintent://escape"))
    }
}

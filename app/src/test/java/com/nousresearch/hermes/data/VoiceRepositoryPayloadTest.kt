package com.nousresearch.hermes.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VoiceRepositoryPayloadTest {
    @Test
    fun `spoken audio uses the mime type embedded by Hermes`() {
        val audio = decodeSpokenAudio("data:audio/mpeg;base64,dGVzdA==", "audio/wav")

        assertEquals("audio/mpeg", audio.mimeType)
        assertEquals(".mp3", audio.extension)
        assertArrayEquals("test".encodeToByteArray(), audio.bytes)
    }

    @Test
    fun `spoken audio rejects active or malformed payloads`() {
        assertThrows(IllegalArgumentException::class.java) {
            decodeSpokenAudio("data:text/html;base64,PGgxPm5vPC9oMT4=", "text/html")
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeSpokenAudio("data:audio/mpeg,not-base64", "audio/mpeg")
        }
    }
}

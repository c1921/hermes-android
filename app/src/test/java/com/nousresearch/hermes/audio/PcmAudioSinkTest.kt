package com.nousresearch.hermes.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PcmAudioSinkTest {
    @Test
    fun `pcm format is mono 16 bit with a bounded sample rate`() {
        val format = VoicePcmFormat(sampleRate = 24_000, channels = 1)

        assertEquals(1, format.channels)
        assertEquals(2, format.sampleWidthBytes)
        assertThrows(IllegalArgumentException::class.java) {
            VoicePcmFormat(sampleRate = 7_999, channels = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VoicePcmFormat(sampleRate = 96_001, channels = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VoicePcmFormat(sampleRate = 24_000, channels = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VoicePcmFormat(sampleRate = 24_000, channels = 1, sampleWidthBytes = 4)
        }
    }

    @Test
    fun `pcm writes are nonempty aligned and bounded`() {
        validatePcmChunk(byteArrayOf(1, 2))
        assertThrows(IllegalArgumentException::class.java) { validatePcmChunk(byteArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { validatePcmChunk(byteArrayOf(1)) }
        assertThrows(IllegalArgumentException::class.java) {
            validatePcmChunk(ByteArray(MAX_PCM_CHUNK_BYTES + 2))
        }
    }

    @Test
    fun `stream lifecycle rejects writes after end or stop`() {
        val ended = PcmStreamLifecycle()
        ended.requireWritable()
        ended.end()
        assertThrows(IllegalStateException::class.java) { ended.requireWritable() }

        val stopped = PcmStreamLifecycle()
        stopped.stop()
        assertThrows(IllegalStateException::class.java) { stopped.requireWritable() }
    }
}

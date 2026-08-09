package com.nousresearch.hermes.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PcmFrameAssemblerTest {
    @Test
    fun `odd byte boundaries are carried into the next frame`() {
        val assembler = PcmFrameAssembler(VoicePcmFormat(sampleRate = 24_000, channels = 1))

        assertArrayEquals(byteArrayOf(1, 2), assembler.append(byteArrayOf(1, 2, 3)))
        assertArrayEquals(byteArrayOf(3, 4, 5, 6), assembler.append(byteArrayOf(4, 5, 6)))
        assembler.finish()
    }

    @Test
    fun `an incomplete final sample is rejected`() {
        val assembler = PcmFrameAssembler(VoicePcmFormat(sampleRate = 24_000, channels = 1))

        assembler.append(byteArrayOf(1))

        assertThrows(IllegalArgumentException::class.java) { assembler.finish() }
    }

    @Test
    fun `format and frame limits are enforced`() {
        assertThrows(IllegalArgumentException::class.java) {
            VoicePcmFormat(sampleRate = 7_999, channels = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VoicePcmFormat(sampleRate = 24_000, channels = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VoicePcmFormat(sampleRate = 24_000, channels = 1, sampleWidthBytes = 4)
        }

        val assembler = PcmFrameAssembler(
            format = VoicePcmFormat(sampleRate = 24_000, channels = 1),
            maxFrameBytes = 4,
            maxStreamBytes = 6,
        )
        assertThrows(IllegalArgumentException::class.java) {
            assembler.append(ByteArray(5))
        }
        assembler.append(ByteArray(4))
        assertThrows(IllegalArgumentException::class.java) {
            assembler.append(ByteArray(4))
        }
    }
}

package com.nousresearch.hermes.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class VoiceHapticTest {
    @Test
    fun `voice transitions have distinct bounded haptics`() {
        val signatures = VoiceHaptic.entries.map { it.timings.contentToString() to it.amplitudes.contentToString() }

        assertEquals(VoiceHaptic.entries.size, signatures.distinct().size)
        assertTrue(VoiceHaptic.entries.all { haptic ->
            haptic.timings.size == haptic.amplitudes.size &&
                haptic.timings.sum() <= 120L &&
                haptic.amplitudes.all { it in 0..255 }
        })
    }

    @Test
    fun `reduced feedback modes suppress voice haptics`() {
        assertTrue(shouldPerformVoiceHaptic(hasVibrator = true, powerSave = false, hapticEnabled = true))
        assertFalse(shouldPerformVoiceHaptic(hasVibrator = true, powerSave = true, hapticEnabled = true))
        assertFalse(shouldPerformVoiceHaptic(hasVibrator = true, powerSave = false, hapticEnabled = false))
        assertFalse(shouldPerformVoiceHaptic(hasVibrator = false, powerSave = false, hapticEnabled = true))
    }
}

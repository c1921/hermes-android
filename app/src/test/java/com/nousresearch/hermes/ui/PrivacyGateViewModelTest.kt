package com.nousresearch.hermes.ui

import com.nousresearch.hermes.security.PrivacyLockPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyGateViewModelTest {
    @Test
    fun `gate locks on process start and long background but not rotation`() {
        val gate = PrivacyGateViewModel()
        assertTrue(gate.isLocked(enabled = true))
        assertFalse(gate.isLocked(enabled = false))

        gate.unlock()
        gate.onBackground(elapsedMillis = 1_000L, changingConfigurations = true)
        gate.onForeground(elapsedMillis = Long.MAX_VALUE)
        assertFalse(gate.isLocked(enabled = true))

        gate.onBackground(elapsedMillis = 2_000L, changingConfigurations = false)
        gate.onForeground(elapsedMillis = 2_000L + PrivacyLockPolicy.BACKGROUND_TIMEOUT_MILLIS)
        assertTrue(gate.isLocked(enabled = true))

        gate.authenticationError("Cancelled")
        gate.unlock()
        assertNull(gate.error)
        assertFalse(gate.isLocked(enabled = true))
        gate.lock()
        assertTrue(gate.isLocked(enabled = true))
    }
}

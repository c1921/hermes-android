package com.nousresearch.hermes.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyLockPolicyTest {
    @Test
    fun `fresh process requires unlock`() {
        assertTrue(PrivacyLockPolicy.shouldLockOnForeground(PrivacyLockState(), nowElapsedMillis = 1L))
    }

    @Test
    fun `short app background does not require unlock`() {
        val unlocked = PrivacyLockPolicy.unlock(PrivacyLockState())
        val backgrounded = PrivacyLockPolicy.onBackground(
            unlocked,
            elapsedMillis = 1_000L,
            reason = PrivacyBackgroundReason.APP_BACKGROUND,
        )

        assertFalse(PrivacyLockPolicy.shouldLockOnForeground(backgrounded, nowElapsedMillis = 300_999L))
    }

    @Test
    fun `five minute app background requires unlock`() {
        val unlocked = PrivacyLockPolicy.unlock(PrivacyLockState())
        val backgrounded = PrivacyLockPolicy.onBackground(
            unlocked,
            elapsedMillis = 1_000L,
            reason = PrivacyBackgroundReason.APP_BACKGROUND,
        )

        assertTrue(
            PrivacyLockPolicy.shouldLockOnForeground(
                backgrounded,
                nowElapsedMillis = 1_000L + PrivacyLockPolicy.BACKGROUND_TIMEOUT_MILLIS,
            ),
        )
    }

    @Test
    fun `configuration change is not treated as app background`() {
        val unlocked = PrivacyLockPolicy.unlock(PrivacyLockState())
        val rotated = PrivacyLockPolicy.onBackground(
            unlocked,
            elapsedMillis = 1_000L,
            reason = PrivacyBackgroundReason.CONFIGURATION_CHANGE,
        )

        assertFalse(PrivacyLockPolicy.shouldLockOnForeground(rotated, nowElapsedMillis = Long.MAX_VALUE))
    }
}

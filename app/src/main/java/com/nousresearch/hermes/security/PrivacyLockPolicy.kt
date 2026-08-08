package com.nousresearch.hermes.security

data class PrivacyLockState(
    val requiresUnlock: Boolean = true,
    val backgroundedAtElapsedMillis: Long? = null,
)

enum class PrivacyBackgroundReason {
    APP_BACKGROUND,
    CONFIGURATION_CHANGE,
}

object PrivacyLockPolicy {
    const val BACKGROUND_TIMEOUT_MILLIS = 5 * 60 * 1000L

    fun unlock(state: PrivacyLockState): PrivacyLockState =
        state.copy(requiresUnlock = false, backgroundedAtElapsedMillis = null)

    fun onBackground(
        state: PrivacyLockState,
        elapsedMillis: Long,
        reason: PrivacyBackgroundReason,
    ): PrivacyLockState = state.copy(
        backgroundedAtElapsedMillis = elapsedMillis.takeUnless {
            reason == PrivacyBackgroundReason.CONFIGURATION_CHANGE
        },
    )

    fun shouldLockOnForeground(
        state: PrivacyLockState,
        nowElapsedMillis: Long,
    ): Boolean {
        if (state.requiresUnlock) return true
        val backgroundedAt = state.backgroundedAtElapsedMillis ?: return false
        return nowElapsedMillis - backgroundedAt >= BACKGROUND_TIMEOUT_MILLIS
    }
}

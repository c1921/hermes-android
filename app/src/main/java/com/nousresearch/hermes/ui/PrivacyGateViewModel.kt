package com.nousresearch.hermes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.nousresearch.hermes.security.PrivacyBackgroundReason
import com.nousresearch.hermes.security.PrivacyLockPolicy
import com.nousresearch.hermes.security.PrivacyLockState

class PrivacyGateViewModel : ViewModel() {
    private var state by mutableStateOf(PrivacyLockState())

    var error by mutableStateOf<String?>(null)
        private set

    fun isLocked(enabled: Boolean): Boolean = enabled && state.requiresUnlock

    fun onBackground(elapsedMillis: Long, changingConfigurations: Boolean) {
        state = PrivacyLockPolicy.onBackground(
            state = state,
            elapsedMillis = elapsedMillis,
            reason = if (changingConfigurations) {
                PrivacyBackgroundReason.CONFIGURATION_CHANGE
            } else {
                PrivacyBackgroundReason.APP_BACKGROUND
            },
        )
    }

    fun onForeground(elapsedMillis: Long) {
        state = state.copy(
            requiresUnlock = PrivacyLockPolicy.shouldLockOnForeground(state, elapsedMillis),
            backgroundedAtElapsedMillis = null,
        )
    }

    fun unlock() {
        state = PrivacyLockPolicy.unlock(state)
        error = null
    }

    fun lock() {
        state = state.copy(requiresUnlock = true, backgroundedAtElapsedMillis = null)
        error = null
    }

    fun authenticationError(message: String?) {
        error = message
    }
}

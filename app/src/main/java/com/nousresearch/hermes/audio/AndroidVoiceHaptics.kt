package com.nousresearch.hermes.audio

import android.content.Context
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceHaptic(val timings: LongArray, val amplitudes: IntArray) {
    RECORD_START(longArrayOf(0, 28), intArrayOf(0, 120)),
    RECORD_LOCK(longArrayOf(0, 18, 42, 28), intArrayOf(0, 90, 0, 150)),
    RECORD_CANCEL(longArrayOf(0, 45, 30, 45), intArrayOf(0, 180, 0, 180)),
    RECORD_STOP(longArrayOf(0, 35), intArrayOf(0, 80)),
}

internal fun shouldPerformVoiceHaptic(hasVibrator: Boolean, powerSave: Boolean, hapticEnabled: Boolean): Boolean =
    hasVibrator && !powerSave && hapticEnabled

@Singleton
class AndroidVoiceHaptics @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    @Suppress("DEPRECATION") // The legacy setting is the API 28-compatible system haptic-feedback opt-out.
    fun perform(haptic: VoiceHaptic) {
        runCatching {
            val enabled = Settings.System.getInt(
                context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1,
            ) != 0
            if (shouldPerformVoiceHaptic(vibrator.hasVibrator(), powerManager.isPowerSaveMode, enabled)) {
                vibrator.vibrate(VibrationEffect.createWaveform(haptic.timings, haptic.amplitudes, -1))
            }
        }
    }
}

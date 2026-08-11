package com.nousresearch.hermes

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nousresearch.hermes.data.PrivacyPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MainActivityPrivacyTest {
    @Test
    fun secureScreenFlagDoesNotFollowBiometricReentry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PrivacyPreferences(context)
        runBlocking {
            preferences.setSecureScreen(false)
            preferences.setBiometricReentry(false)
        }

        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            runBlocking {
                preferences.setSecureScreen(true)
                preferences.setBiometricReentry(false)
            }
            awaitWindowFlag(scenario, expected = true)

            runBlocking {
                preferences.setBiometricReentry(true)
                preferences.setSecureScreen(false)
            }
            awaitWindowFlag(scenario, expected = false)
            scenario.onActivity { activity ->
                assertFalse(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
            runBlocking { preferences.setBiometricReentry(false) }
        }
    }

    private fun awaitWindowFlag(scenario: ActivityScenario<MainActivity>, expected: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            var secure = false
            scenario.onActivity { activity ->
                secure = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
            }
            if (secure == expected) return
            instrumentation.waitForIdleSync()
            SystemClock.sleep(50L)
        }
        scenario.onActivity { activity ->
            assertEquals(expected, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }
}

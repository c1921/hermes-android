package com.nousresearch.hermes.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE_NAME = "com.nousresearch.hermes"
private const val FIXTURE_ACTIVITY_NAME = "com.nousresearch.hermes.benchmark.BenchmarkFixtureActivity"
private const val FIXTURE_RESET_EXTRA = "hermes.benchmark.fixture_reset"

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun realMainActivityStartup() {
        baselineProfileRule.collect(packageName = TARGET_PACKAGE_NAME) {
            pressHome()
            startActivityAndWait()
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
        }
    }

    @Test
    fun fixturePrimarySurfaces() {
        baselineProfileRule.collect(packageName = TARGET_PACKAGE_NAME) {
            val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            listOf("Atlas", "Chats", "Files", "Artifacts", "Manage").forEach { surface ->
                pressHome()
                startActivityAndWait(
                    Intent().setComponent(ComponentName(TARGET_PACKAGE_NAME, FIXTURE_ACTIVITY_NAME))
                        .putExtra(FIXTURE_RESET_EXTRA, System.nanoTime()),
                )
                uiDevice.waitForIdle()
                if (surface != "Chats") uiDevice.clickText(surface)
            }
        }
    }
}

private fun UiDevice.clickText(text: String) {
    requireNotNull(
        wait(Until.findObject(By.desc(text)), 2_000L)
            ?: wait(Until.findObject(By.textContains(text)), 13_000L),
    ) { "Baseline Profile fixture surface not found: $text" }
        .click()
    waitForIdle()
}

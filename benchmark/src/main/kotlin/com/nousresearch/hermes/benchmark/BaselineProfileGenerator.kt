package com.nousresearch.hermes.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE_NAME = "com.nousresearch.hermes"
private const val FIXTURE_ACTIVITY_NAME = "com.nousresearch.hermes.benchmark.BenchmarkFixtureActivity"

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
            pressHome()
            startActivityAndWait(Intent().setComponent(ComponentName(TARGET_PACKAGE_NAME, FIXTURE_ACTIVITY_NAME)))
            val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            uiDevice.waitForIdle()
            uiDevice.clickTextIfVisible("Atlas")
            uiDevice.clickTextIfVisible("Chats")
            uiDevice.clickTextIfVisible("Files")
            uiDevice.clickTextIfVisible("Artifacts")
            uiDevice.clickTextIfVisible("Manage")
        }
    }
}

private fun UiDevice.clickTextIfVisible(text: String): UiObject2? =
    findObject(By.textContains(text))?.also {
        it.click()
        waitForIdle()
    }

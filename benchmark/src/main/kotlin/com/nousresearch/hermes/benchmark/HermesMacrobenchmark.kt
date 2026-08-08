package com.nousresearch.hermes.benchmark

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE_NAME = "com.nousresearch.hermes"
private const val FIXTURE_ACTIVITY_NAME = "com.nousresearch.hermes.benchmark.BenchmarkFixtureActivity"

@RunWith(AndroidJUnit4::class)
class HermesStartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupReportsTtidTtfdAndFrames() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            device.waitForIdle()
        }
    }

    @Test
    fun warmStartupReportsFrames() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.WARM,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait(Intent().setComponent(ComponentName(TARGET_PACKAGE_NAME, FIXTURE_ACTIVITY_NAME)))
            device.waitForIdle()
        }
    }
}

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class HermesSurfaceJourneyBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun atlasJourney() = measureSurface("Atlas") {}

    @Test
    fun chatContinuousStreamJourney() = measureSurface("Chats", waitForIdleBeforeJourney = false) {
        SystemClock.sleep(3_200)
    }

    @Test
    fun transcriptScrollDuringContinuousStreamJourney() = measureSurface("Chats", waitForIdleBeforeJourney = false) {
        repeat(8) {
            swipe(displayWidth / 2, displayHeight * 3 / 4, displayWidth / 2, displayHeight / 4, 24)
            SystemClock.sleep(250)
        }
    }

    @Test
    fun composerJourney() = measureSurface("Chats") {
        findObject(By.textContains("Message Hermes"))?.apply {
            click()
            text = "Deterministic benchmark composer input"
        }
        waitForIdle()
    }

    @Test
    fun filesJourney() = measureSurface("Files") {}

    @Test
    fun artifactsJourney() = measureSurface("Artifacts") {}

    @Test
    fun manageJourney() = measureSurface("Manage") {}

    private fun measureSurface(
        label: String,
        waitForIdleBeforeJourney: Boolean = true,
        journey: UiDevice.() -> Unit,
    ) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
            iterations = 3,
            startupMode = StartupMode.WARM,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait(Intent().setComponent(ComponentName(TARGET_PACKAGE_NAME, FIXTURE_ACTIVITY_NAME)))
            if (label != "Chats") device.clickTextIfVisible(label)
            if (waitForIdleBeforeJourney) device.waitForIdle() else SystemClock.sleep(100)
            device.journey()
            device.waitForIdle()
        }
    }
}

private fun UiDevice.clickTextIfVisible(text: String) {
    findObject(By.textContains(text))?.click()
    waitForIdle()
}

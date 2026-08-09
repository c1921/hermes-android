package com.nousresearch.hermes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nousresearch.hermes.platform.HermesEntryRequestStoreEntryPoint
import dagger.hilt.android.EntryPointAccessors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesEntryRequestLifecycleTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = EntryPointAccessors.fromApplication(
        context,
        HermesEntryRequestStoreEntryPoint::class.java,
    ).entryRequestStore()

    @Test
    fun shareRequestSurvivesRecreationOnceWithoutRetainingActivityPayload() {
        clearStore()
        val intent = Intent(Intent.ACTION_SEND, null, context, MainActivity::class.java)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "private draft")

        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity -> activity.startActivity(intent) }
            awaitDeliveryCount(1)
            scenario.onActivity(::assertPayloadCleared)

            scenario.recreate()

            awaitDeliveryCount(1)
            scenario.onActivity(::assertPayloadCleared)
        }
        clearStore()
    }

    @Test
    fun rejectedIntentSecretsAreClearedBeforeRecreation() {
        clearStore()
        val malicious = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("hermes://chats?backend=personal&profile=default"),
            context,
            MainActivity::class.java,
        ).putExtra("approval_token", "must-not-survive")

        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity -> activity.startActivity(malicious) }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue(store.deliveries.value.isEmpty())
            scenario.onActivity(::assertPayloadCleared)
            scenario.recreate()
            assertTrue(store.deliveries.value.isEmpty())
            scenario.onActivity(::assertPayloadCleared)
        }
    }

    private fun assertPayloadCleared(activity: MainActivity) {
        assertNull(activity.intent.action)
        assertNull(activity.intent.data)
        assertTrue(activity.intent.extras?.keySet().orEmpty().isEmpty())
    }

    private fun clearStore() {
        store.deliveries.value.forEach { store.discard(it.request.id) }
    }

    private fun awaitDeliveryCount(expected: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (store.deliveries.value.size == expected) return
            SystemClock.sleep(50L)
        }
        assertEquals(expected, store.deliveries.value.size)
    }
}

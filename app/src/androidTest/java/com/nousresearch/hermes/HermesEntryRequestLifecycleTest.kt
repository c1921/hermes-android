package com.nousresearch.hermes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.nousresearch.hermes.platform.HermesEntryRequestStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesEntryRequestLifecycleTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = EntryPointAccessors.fromApplication(
        context,
        EntryRequestStoreEntryPoint::class.java,
    ).entryRequestStore()

    @Test
    fun shareRequestSurvivesRecreationOnceWithoutRetainingActivityPayload() {
        clearStore()
        val intent = Intent(Intent.ACTION_SEND, null, context, MainActivity::class.java)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "private draft")

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            assertEquals(1, store.deliveries.value.size)
            scenario.onActivity(::assertPayloadCleared)

            scenario.recreate()

            assertEquals(1, store.deliveries.value.size)
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

        ActivityScenario.launch<MainActivity>(malicious).use { scenario ->
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
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface EntryRequestStoreEntryPoint {
    fun entryRequestStore(): HermesEntryRequestStore
}

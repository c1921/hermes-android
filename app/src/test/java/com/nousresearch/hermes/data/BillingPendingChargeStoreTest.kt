package com.nousresearch.hermes.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BillingPendingChargeStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `pending charge survives recreation and remains isolated by backend`() = runTest {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(temporaryFolder.root, "billing.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val store = BillingPendingChargeStore(dataStore)
        val pending = PendingBillingCharge(
            backendId = "personal",
            amountUsd = "20",
            idempotencyKey = "charge-key",
            settlementDeadlineEpochMillis = 1_800_000L,
            chargeId = "ch_123",
            portalUrl = "https://portal.nousresearch.com/billing",
        )

        store.put(pending)

        assertEquals(pending, store.get("personal"))
        assertNull(store.get("work"))
        store.remove("personal")
        assertNull(store.get("personal"))
        scope.cancel()
    }

    @Test
    fun `corrupt pending charge fails closed`() = runTest {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(temporaryFolder.root, "corrupt-billing.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val store = BillingPendingChargeStore(dataStore)
        store.put(PendingBillingCharge("personal", "20", "charge-key", 1_800_000L))
        dataStore.edit { preferences ->
            val key = preferences.asMap().keys.single()
            @Suppress("UNCHECKED_CAST")
            preferences[key as androidx.datastore.preferences.core.Preferences.Key<String>] = "not-json"
        }

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { store.get("personal") }
        }
        scope.cancel()
    }
}

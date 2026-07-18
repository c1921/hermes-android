package com.nousresearch.hermes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.billingPendingChargeDataStore by preferencesDataStore("hermes_billing_pending_charges")

@Serializable
data class PendingBillingCharge(
    val backendId: String,
    val amountUsd: String,
    val idempotencyKey: String,
    val settlementDeadlineEpochMillis: Long,
    val chargeId: String? = null,
    val portalUrl: String? = null,
)

@Singleton
class BillingPendingChargeStore internal constructor(
    private val store: DataStore<Preferences>,
    private val json: Json = Json,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        json: Json,
    ) : this(context.billingPendingChargeDataStore, json)

    suspend fun get(backendId: String): PendingBillingCharge? {
        val raw = store.data.first()[key(backendId)] ?: return null
        val pending = runCatching { json.decodeFromString<PendingBillingCharge>(raw) }
            .getOrElse { throw IllegalStateException("Saved pending charge could not be read", it) }
        check(pending.backendId == backendId) { "Saved pending charge belongs to another backend" }
        return pending
    }

    suspend fun put(pending: PendingBillingCharge) {
        require(pending.backendId.isNotBlank()) { "Pending charge backend is required" }
        require(pending.amountUsd.isNotBlank()) { "Pending charge amount is required" }
        require(pending.idempotencyKey.isNotBlank()) { "Pending charge idempotency key is required" }
        require(pending.settlementDeadlineEpochMillis > 0L) { "Pending charge deadline is required" }
        store.edit { it[key(pending.backendId)] = json.encodeToString(pending) }
    }

    suspend fun remove(backendId: String) {
        store.edit { it.remove(key(backendId)) }
    }

    private fun key(backendId: String) = stringPreferencesKey(
        "pending_charge.v1.${backendId.sha256()}",
    )
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray())
    .joinToString("") { "%02x".format(it) }

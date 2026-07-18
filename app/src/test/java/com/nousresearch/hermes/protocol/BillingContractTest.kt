package com.nousresearch.hermes.protocol

import com.nousresearch.hermes.ui.manageSubscriptionUrl
import com.nousresearch.hermes.data.billingAutoReloadParams
import com.nousresearch.hermes.data.billingChargeParams
import com.nousresearch.hermes.data.billingChargeStatusParams
import com.nousresearch.hermes.data.billingStepUpParams
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesPinnedDesktopBillingState() {
        val fixture = checkNotNull(javaClass.getResource("/fixtures/billing-state-5988fe6.json")).readText()
        val state = json.decodeFromString(BillingStateResponse.serializer(), fixture)

        assertTrue(state.loggedIn)
        assertEquals("$24.50", state.balanceDisplay)
        assertEquals("4242", state.card?.last4)
        assertEquals("25", state.autoReload?.reloadToUsd)
        assertEquals("$40.00", state.usage?.planBar?.remainingDisplay)
    }

    @Test
    fun subscriptionManagementUsesOnlyTheAdvertisedPortalOrigin() {
        assertEquals(
            "https://portal.nousresearch.com/manage-subscription?org_id=org+42",
            manageSubscriptionUrl("https://portal.nousresearch.com/billing?return=evil", "org 42"),
        )
    }

    @Test
    fun malformedPortalFallsBackToTheOfficialNousOrigin() {
        assertEquals(
            "https://portal.nousresearch.com/manage-subscription",
            manageSubscriptionUrl("javascript:alert(1)", null),
        )
    }

    @Test
    fun refusalEnvelopeDoesNotDecodeAsSuccessfulLoggedOutState() {
        val state = json.decodeFromString(
            BillingStateResponse.serializer(),
            """{"ok":false,"error":"endpoint_unavailable","message":"Billing is offline"}""",
        )

        assertEquals(false, state.ok)
        assertEquals("endpoint_unavailable", state.error)
        assertEquals("Billing is offline", state.message)
    }

    @Test
    fun subscriptionCatalogKeepsTheCurrentAdvertisedPrice() {
        val state = json.decodeFromString(
            SubscriptionStateResponse.serializer(),
            """{"ok":true,"logged_in":true,"current":{"tier_id":"ultra","tier_name":"Ultra"},"tiers":[{"tier_id":"ultra","name":"Ultra","tier_order":4,"dollars_per_month_display":"$200","monthly_credits":"200","is_current":true,"is_enabled":true}]}""",
        )

        assertEquals("$200", state.tiers.single().dollarsPerMonthDisplay)
        assertTrue(state.tiers.single().isCurrent)
    }

    @Test
    fun billingMutationsUseThePinnedDesktopParameterNames() {
        assertEquals(
            """{"amount_usd":"20","idempotency_key":"same-key"}""",
            billingChargeParams("20", "same-key").toString(),
        )
        assertEquals("""{"charge_id":"ch_123"}""", billingChargeStatusParams("ch_123").toString())
        assertEquals(
            """{"enabled":true,"threshold":"5","top_up_amount":"25"}""",
            billingAutoReloadParams(true, "5", "25").toString(),
        )
        assertEquals("""{"session_id":"runtime-1"}""", billingStepUpParams("runtime-1").toString())
    }
}

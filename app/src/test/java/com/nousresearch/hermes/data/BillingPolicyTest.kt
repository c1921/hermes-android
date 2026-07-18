package com.nousresearch.hermes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class BillingPolicyTest {
    @Test
    fun transientSendFailuresReuseTheIdempotencyKey() {
        listOf("endpoint_unavailable", "rate_limited", "temporarily_unavailable", "network_error").forEach {
            assertTrue(BillingPolicy.forCode(it).reuseIdempotencyKey)
        }
    }

    @Test
    fun unknownFailuresHaveNoInventedRecovery() {
        val policy = BillingPolicy.forCode("future_server_refusal")

        assertEquals(BillingRecovery.NONE, policy.recovery)
        assertFalse(policy.reuseIdempotencyKey)
        assertFalse(policy.ambiguousMidPoll)
    }

    @Test
    fun scopeLossDuringPollingIsAmbiguous() {
        val policy = BillingPolicy.forCode("insufficient_scope")

        assertEquals(BillingRecovery.STEP_UP, policy.recovery)
        assertTrue(policy.ambiguousMidPoll)
    }

    @Test
    fun completeDesktopPolicyKeepsKnownRecoveries() {
        val expected = mapOf(
            "auto_top_up_disabled_failures" to BillingRecovery.PORTAL,
            "cli_billing_disabled" to BillingRecovery.PORTAL,
            "consent_required" to BillingRecovery.PORTAL,
            "endpoint_unavailable" to BillingRecovery.RETRY,
            "idempotency_conflict" to BillingRecovery.NONE,
            "idempotency_key_required" to BillingRecovery.NONE,
            "insufficient_scope" to BillingRecovery.STEP_UP,
            "internal_error" to BillingRecovery.RETRY,
            "invalid_charge_id" to BillingRecovery.NONE,
            "invalid_request" to BillingRecovery.NONE,
            "monthly_cap_exceeded" to BillingRecovery.PORTAL,
            "network_error" to BillingRecovery.RETRY,
            "no_payment_method" to BillingRecovery.PORTAL,
            "org_access_denied" to BillingRecovery.PORTAL,
            "preview_rejected" to BillingRecovery.NONE,
            "rate_limited" to BillingRecovery.RETRY,
            "remote_spending_disabled" to BillingRecovery.PORTAL,
            "remote_spending_revoked" to BillingRecovery.RECONNECT,
            "role_required" to BillingRecovery.PORTAL,
            "session_revoked" to BillingRecovery.LOGIN,
            "stripe_unavailable" to BillingRecovery.RETRY,
            "temporarily_unavailable" to BillingRecovery.RETRY,
            "upgrade_cap_exceeded" to BillingRecovery.NONE,
            "validation_failed" to BillingRecovery.NONE,
        )

        expected.forEach { (code, recovery) -> assertEquals(code, recovery, BillingPolicy.forCode(code).recovery) }
    }

    @Test
    fun moneyValidationNormalizesWithinServerBounds() {
        assertEquals("20", validateBillingAmount(" 20.00 ", "5", "100"))
        assertEquals("5.25", validateBillingAmount("5.25", "5", "100"))
    }

    @Test
    fun moneyValidationRejectsPrecisionAndBoundsErrors() {
        assertThrows(IllegalArgumentException::class.java) { validateBillingAmount("5.001", "5", "100") }
        assertThrows(IllegalArgumentException::class.java) { validateBillingAmount("4.99", "5", "100") }
        assertThrows(IllegalArgumentException::class.java) { validateBillingAmount("100.01", "5", "100") }
    }

    @Test
    fun refusalCopyPreservesDesktopActorAndCapDetails() {
        assertEquals(
            "An admin turned off terminal billing for this device. Reconnect it from the gateway settings.",
            billingRefusalMessage("remote_spending_revoked", null, actor = "admin"),
        )
        assertEquals(
            "The monthly spend cap has been reached with $4.50 headroom left.",
            billingRefusalMessage(
                "monthly_cap_exceeded",
                null,
                payload = com.nousresearch.hermes.protocol.BillingErrorPayload(remainingUsd = "4.50"),
            ),
        )
    }
}

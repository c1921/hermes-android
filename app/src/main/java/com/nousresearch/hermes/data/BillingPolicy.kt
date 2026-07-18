package com.nousresearch.hermes.data

enum class BillingRecovery { LOGIN, NONE, PORTAL, RECONNECT, RETRY, STEP_UP }

data class BillingRefusalPolicy(
    val recovery: BillingRecovery = BillingRecovery.NONE,
    val reuseIdempotencyKey: Boolean = false,
    val ambiguousMidPoll: Boolean = false,
)

object BillingPolicy {
    private val policies = mapOf(
        "auto_top_up_disabled_failures" to BillingRefusalPolicy(BillingRecovery.PORTAL),
        "cli_billing_disabled" to BillingRefusalPolicy(BillingRecovery.PORTAL),
        "consent_required" to BillingRefusalPolicy(BillingRecovery.PORTAL),
        "endpoint_unavailable" to BillingRefusalPolicy(BillingRecovery.RETRY, reuseIdempotencyKey = true),
        "idempotency_conflict" to BillingRefusalPolicy(),
        "idempotency_key_required" to BillingRefusalPolicy(),
        "insufficient_scope" to BillingRefusalPolicy(BillingRecovery.STEP_UP, ambiguousMidPoll = true),
        "internal_error" to BillingRefusalPolicy(BillingRecovery.RETRY),
        "invalid_charge_id" to BillingRefusalPolicy(),
        "invalid_request" to BillingRefusalPolicy(),
        "monthly_cap_exceeded" to BillingRefusalPolicy(BillingRecovery.PORTAL),
        "network_error" to BillingRefusalPolicy(BillingRecovery.RETRY, reuseIdempotencyKey = true),
        "no_payment_method" to BillingRefusalPolicy(BillingRecovery.PORTAL),
        "org_access_denied" to BillingRefusalPolicy(BillingRecovery.PORTAL),
        "preview_rejected" to BillingRefusalPolicy(),
        "rate_limited" to BillingRefusalPolicy(BillingRecovery.RETRY, reuseIdempotencyKey = true),
        "remote_spending_disabled" to BillingRefusalPolicy(BillingRecovery.PORTAL),
        "remote_spending_revoked" to BillingRefusalPolicy(BillingRecovery.RECONNECT, ambiguousMidPoll = true),
        "role_required" to BillingRefusalPolicy(BillingRecovery.PORTAL),
        "session_revoked" to BillingRefusalPolicy(BillingRecovery.LOGIN, ambiguousMidPoll = true),
        "stripe_unavailable" to BillingRefusalPolicy(BillingRecovery.RETRY, reuseIdempotencyKey = true),
        "temporarily_unavailable" to BillingRefusalPolicy(BillingRecovery.RETRY, reuseIdempotencyKey = true),
        "upgrade_cap_exceeded" to BillingRefusalPolicy(),
        "validation_failed" to BillingRefusalPolicy(),
    )

    fun forCode(code: String?): BillingRefusalPolicy = policies[code] ?: BillingRefusalPolicy()
}

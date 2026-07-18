package com.nousresearch.hermes.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UsageBarData(
    val kind: String = "plan",
    @SerialName("remaining_display") val remainingDisplay: String = "",
    @SerialName("total_display") val totalDisplay: String = "",
    @SerialName("spent_display") val spentDisplay: String = "",
    @SerialName("pct_used") val percentUsed: Double? = null,
    @SerialName("fill_fraction") val fillFraction: Float = 0f,
)

@Serializable
data class UsageModelData(
    val available: Boolean = false,
    val status: String? = null,
    @SerialName("plan_name") val planName: String? = null,
    @SerialName("renews_at") val renewsAt: String? = null,
    @SerialName("renews_display") val renewsDisplay: String? = null,
    @SerialName("subscription_remaining_display") val subscriptionRemainingDisplay: String? = null,
    @SerialName("topup_remaining_display") val topupRemainingDisplay: String? = null,
    @SerialName("total_spendable_display") val totalSpendableDisplay: String? = null,
    @SerialName("has_topup") val hasTopup: Boolean = false,
    @SerialName("plan_bar") val planBar: UsageBarData? = null,
    @SerialName("topup_bar") val topupBar: UsageBarData? = null,
)

@Serializable
data class BillingCardInfo(
    val brand: String = "",
    val last4: String = "",
    val masked: String = "",
    val display: String? = null,
    @SerialName("resolved_via") val resolvedVia: String? = null,
)

@Serializable
data class BillingMonthlyCap(
    @SerialName("is_default_ceiling") val isDefaultCeiling: Boolean = false,
    @SerialName("limit_display") val limitDisplay: String = "",
    @SerialName("limit_usd") val limitUsd: String? = null,
    @SerialName("spent_display") val spentDisplay: String = "",
    @SerialName("spent_this_month_usd") val spentThisMonthUsd: String? = null,
)

@Serializable
data class BillingAutoReloadCard(
    val kind: String = "none",
    @SerialName("payment_method_id") val paymentMethodId: String? = null,
    val brand: String? = null,
    val last4: String? = null,
)

@Serializable
data class BillingAutoReload(
    val card: BillingAutoReloadCard = BillingAutoReloadCard(),
    val enabled: Boolean = false,
    @SerialName("reload_to_display") val reloadToDisplay: String = "",
    @SerialName("reload_to_usd") val reloadToUsd: String? = null,
    @SerialName("threshold_display") val thresholdDisplay: String = "",
    @SerialName("threshold_usd") val thresholdUsd: String? = null,
)

@Serializable
data class BillingStateResponse(
    val ok: Boolean = true,
    @SerialName("logged_in") val loggedIn: Boolean = false,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("can_change_plan") val canChangePlan: Boolean = false,
    @SerialName("can_charge") val canCharge: Boolean = false,
    @SerialName("balance_usd") val balanceUsd: String? = null,
    @SerialName("balance_display") val balanceDisplay: String = "",
    @SerialName("cli_billing_enabled") val cliBillingEnabled: Boolean = false,
    @SerialName("charge_presets") val chargePresets: List<String> = emptyList(),
    @SerialName("charge_presets_display") val chargePresetsDisplay: List<String> = emptyList(),
    @SerialName("min_usd") val minUsd: String? = null,
    @SerialName("max_usd") val maxUsd: String? = null,
    val card: BillingCardInfo? = null,
    @SerialName("monthly_cap") val monthlyCap: BillingMonthlyCap? = null,
    @SerialName("auto_reload") val autoReload: BillingAutoReload? = null,
    @SerialName("portal_url") val portalUrl: String? = null,
    @SerialName("org_name") val orgName: String? = null,
    val role: String? = null,
    val error: String? = null,
    val message: String? = null,
    val usage: UsageModelData? = null,
)

@Serializable
data class BillingErrorPayload(
    val isDefaultCeiling: Boolean? = null,
    val remainingUsd: String? = null,
)

@Serializable
data class SubscriptionCurrent(
    @SerialName("tier_id") val tierId: String? = null,
    @SerialName("tier_name") val tierName: String? = null,
    @SerialName("monthly_credits") val monthlyCredits: String? = null,
    @SerialName("credits_remaining") val creditsRemaining: String? = null,
    @SerialName("cycle_ends_at") val cycleEndsAt: String? = null,
    @SerialName("pending_downgrade_tier_name") val pendingDowngradeTierName: String? = null,
    @SerialName("pending_downgrade_at") val pendingDowngradeAt: String? = null,
    @SerialName("pending_downgrade_display") val pendingDowngradeDisplay: String? = null,
    @SerialName("cancel_at_period_end") val cancelAtPeriodEnd: Boolean = false,
    @SerialName("cancellation_effective_at") val cancellationEffectiveAt: String? = null,
    @SerialName("cancellation_effective_display") val cancellationEffectiveDisplay: String? = null,
)

@Serializable
data class SubscriptionTierOption(
    @SerialName("tier_id") val tierId: String,
    val name: String,
    @SerialName("tier_order") val tierOrder: Int = 0,
    @SerialName("dollars_per_month_display") val dollarsPerMonthDisplay: String = "",
    @SerialName("monthly_credits") val monthlyCredits: String? = null,
    @SerialName("is_current") val isCurrent: Boolean = false,
    @SerialName("is_enabled") val isEnabled: Boolean = true,
)

@Serializable
data class SubscriptionStateResponse(
    val ok: Boolean = true,
    @SerialName("logged_in") val loggedIn: Boolean = false,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("can_change_plan") val canChangePlan: Boolean = false,
    @SerialName("org_name") val orgName: String? = null,
    @SerialName("org_id") val orgId: String? = null,
    val role: String? = null,
    val context: String = "personal",
    val current: SubscriptionCurrent? = null,
    val tiers: List<SubscriptionTierOption> = emptyList(),
    @SerialName("portal_url") val portalUrl: String? = null,
    val error: String? = null,
    val usage: UsageModelData? = null,
)

@Serializable
data class BillingMutationResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val actor: String? = null,
    val code: String? = null,
    val message: String? = null,
    @SerialName("portal_url") val portalUrl: String? = null,
    @SerialName("retry_after") val retryAfter: Long? = null,
    val payload: JsonObject? = null,
    val recovery: String? = null,
    val granted: Boolean? = null,
)

@Serializable
data class BillingChargeResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val actor: String? = null,
    val code: String? = null,
    val message: String? = null,
    @SerialName("charge_id") val chargeId: String? = null,
    @SerialName("idempotency_key") val idempotencyKey: String? = null,
    @SerialName("portal_url") val portalUrl: String? = null,
    @SerialName("retry_after") val retryAfter: Long? = null,
    val payload: BillingErrorPayload? = null,
    val recovery: String? = null,
)

@Serializable
data class BillingChargeStatusResponse(
    val ok: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val message: String? = null,
    val reason: String? = null,
    val payload: BillingErrorPayload? = null,
    @SerialName("amount_usd") val amountUsd: String? = null,
    @SerialName("portal_url") val portalUrl: String? = null,
    @SerialName("retry_after") val retryAfter: Long? = null,
    @SerialName("settled_at") val settledAt: String? = null,
)

@Serializable
data class BillingStepUpVerification(
    @SerialName("verification_url") val verificationUrl: String,
    @SerialName("user_code") val userCode: String? = null,
)

package com.nousresearch.hermes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.BillingRecovery
import com.nousresearch.hermes.data.BillingRetryIntent
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.data.validateBillingAmount
import com.nousresearch.hermes.protocol.BillingStateResponse
import com.nousresearch.hermes.protocol.SubscriptionStateResponse
import com.nousresearch.hermes.protocol.UsageBarData

@Composable
internal fun BillingScreen(
    state: HermesState,
    onRefresh: () -> Unit,
    onCharge: (String) -> Unit,
    onUpdateAutoReload: (Boolean, String, String) -> Unit,
    onStepUp: () -> Unit,
    onAcknowledgeUnconfirmedCharge: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onRefresh() }
    val billing = state.billingState
    val subscription = state.subscriptionState
    val accountLoggedIn = billing?.loggedIn == true && subscription?.loggedIn != false
    val summaryBilling = billing?.takeIf { it.ok && accountLoggedIn }
    val portalUrl = state.billingPortalUrl ?: billing?.portalUrl ?: subscription?.portalUrl
    var pendingCharge by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }
            Column(Modifier.weight(1f)) {
                Text("BILLING", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text("Nous Portal account", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRefresh, enabled = !state.billingLoading && !state.billingBusy) {
                if (state.billingLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Refresh, "Refresh billing")
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                BillingSummary(
                    balance = summaryBilling?.balanceDisplay.orUnavailable(),
                    plan = billingPlan(subscription, summaryBilling),
                    autoReload = summaryBilling?.autoReload?.let { if (it.enabled) "Enabled" else "Off" }.orUnavailable(),
                )
            }
            if (!state.billingSupported) {
                item { BillingNotice("Billing unavailable", state.billingError ?: "Update Hermes to use billing on Android.") }
            } else if (billing == null && state.billingLoading) {
                item { BillingLoading() }
            } else if (billing != null && !billing.ok) {
                Unit
            } else if (billing != null && !accountLoggedIn) {
                item {
                    BillingNotice(
                        title = "Connect your Nous account",
                        message = "Open the Nous portal to connect billing and credits to Hermes.",
                        action = portalUrl?.let { { PortalButton(it, onOpenUrl) } },
                    )
                }
            } else if (billing != null) {
                item { BillingSectionTitle("ACCOUNT") }
                item { PaymentMethodCard(billing, portalUrl, onOpenUrl) }
                item { SubscriptionCard(state, portalUrl, onOpenUrl) }
                item {
                    BuyCreditsCard(
                        billing = billing,
                        busy = state.billingBusy || state.billingLoading || state.billingChargeUnconfirmed,
                        onSelectAmount = { pendingCharge = it },
                    )
                }
                item {
                    AutoReloadCard(
                        billing = billing,
                        busy = state.billingBusy || state.billingLoading,
                        onSave = onUpdateAutoReload,
                        onOpenUrl = onOpenUrl,
                    )
                }
                val usage = subscription?.usage ?: billing.usage
                val subscriptionProgress = creditProgress(
                    subscription?.current?.creditsRemaining,
                    subscription?.current?.monthlyCredits,
                )
                item { BillingSectionTitle("USAGE") }
                if (subscriptionProgress != null) {
                    item { BillingMetricCard("Subscription credits", subscriptionProgress) }
                } else {
                    usage?.planBar?.let { item { BillingUsageCard("Subscription credits", it) } }
                        ?: item {
                        BillingMetricCard(
                            "Subscription credits",
                            usage?.subscriptionRemainingDisplay.orUnavailable(),
                        )
                    }
                }
                usage?.topupBar?.let { item { BillingUsageCard("Top-up credits", it) } }
                    ?: item {
                        BillingMetricCard(
                            "Top-up credits",
                            usage?.topupRemainingDisplay ?: billing.balanceDisplay.orUnavailable(),
                        )
                    }
                billing.monthlyCap?.let { cap ->
                    item {
                        BillingMetricCard(
                            "Monthly cap",
                            "${cap.spentDisplay.orUnavailable()} spent of ${cap.limitDisplay.orUnavailable()}",
                        )
                    }
                }
            }
            state.billingNotice?.let { item { BillingNotice("Billing", it) } }
            state.billingError?.takeIf { state.billingSupported }?.let { error ->
                item {
                    BillingNotice(
                        title = "Billing needs attention",
                        message = error,
                        action = {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                when (state.billingRecovery) {
                                    BillingRecovery.STEP_UP -> OutlinedButton(onClick = onStepUp, enabled = !state.billingBusy) {
                                        Text("Verify to continue")
                                    }
                                    BillingRecovery.RETRY -> OutlinedButton(
                                        onClick = {
                                            when (val retry = state.billingRetryIntent) {
                                                is BillingRetryIntent.Charge -> onCharge(retry.amountUsd)
                                                is BillingRetryIntent.AutoReload -> onUpdateAutoReload(
                                                    retry.enabled,
                                                    retry.thresholdUsd,
                                                    retry.reloadToUsd,
                                                )
                                                BillingRetryIntent.StepUp -> onStepUp()
                                                BillingRetryIntent.Refresh, null -> onRefresh()
                                            }
                                        },
                                        enabled = !state.billingBusy,
                                    ) {
                                        Text("Retry")
                                    }
                                    else -> Unit
                                }
                                portalUrl?.let { PortalButton(it, onOpenUrl) }
                            }
                        },
                    )
                }
            }
            if (state.billingChargeUnconfirmed) {
                item {
                    BillingNotice(
                        title = "Confirm your balance",
                        message = "A previous purchase may still settle. Check your Nous balance or portal before buying again.",
                        action = {
                            OutlinedButton(onClick = onAcknowledgeUnconfirmedCharge, enabled = !state.billingBusy) {
                                Text("I checked my balance")
                            }
                        },
                    )
                }
            }
            state.billingStepUpVerification?.let { verification ->
                item {
                    BillingNotice(
                        title = "Verify this device",
                        message = verification.userCode?.let { "Enter code $it on the verification page." }
                            ?: "Finish verification in the browser.",
                        action = { PortalButton(verification.verificationUrl, onOpenUrl, "Open verification") },
                    )
                }
            }
        }
    }

    pendingCharge?.let { amount ->
        AlertDialog(
            onDismissRequest = { pendingCharge = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Buy ${amount.money()} credits?") },
            text = { Text("Hermes will charge the saved payment method for this Nous account.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingCharge = null
                        onCharge(amount)
                    },
                    modifier = Modifier.semantics { contentDescription = "Confirm credit purchase" },
                ) { Text("Buy credits") }
            },
            dismissButton = { TextButton(onClick = { pendingCharge = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BillingSummary(balance: String, plan: String, autoReload: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BillingSummaryItem("BALANCE", balance, Modifier.weight(1f))
        BillingSummaryItem("PLAN", plan, Modifier.weight(1f))
        BillingSummaryItem("AUTO-REFILL", autoReload, Modifier.weight(1f))
    }
}

@Composable
private fun BillingSummaryItem(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PaymentMethodCard(billing: BillingStateResponse, portalUrl: String?, onOpenUrl: (String) -> Unit) {
    BillingRowCard(
        title = "Payment method",
        value = billing.card?.let { card ->
            card.display ?: card.masked.takeIf(String::isNotBlank) ?: "${card.brand} •••• ${card.last4}"
        } ?: "No card on file",
        description = "Card used for top-ups and subscription renewals.",
        action = portalUrl?.let { { PortalButton(it, onOpenUrl, "Update") } },
    )
}

@Composable
private fun SubscriptionCard(state: HermesState, portalUrl: String?, onOpenUrl: (String) -> Unit) {
    val current = state.subscriptionState?.current
    BillingRowCard(
        title = "Subscription",
        value = current?.tierName ?: state.billingState?.usage?.planName.orUnavailable(),
        description = current?.cycleEndsAt?.let { "Renews ${it.substringBefore('T')}" } ?: "Review or change your plan in the portal.",
        action = portalUrl?.let { { PortalButton(manageSubscriptionUrl(it, state.subscriptionState?.orgId), onOpenUrl, "Adjust plan") } },
    )
}

@Composable
private fun BuyCreditsCard(billing: BillingStateResponse, busy: Boolean, onSelectAmount: (String) -> Unit) {
    var customAmount by rememberSaveable(billing.minUsd) { mutableStateOf("") }
    val enabled = billing.card != null && billing.isAdmin && billing.canCharge && billing.cliBillingEnabled && !busy
    val validCustomAmount = runCatching {
        validateBillingAmount(customAmount, billing.minUsd, billing.maxUsd)
    }.getOrNull()
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Buy credits", style = MaterialTheme.typography.titleMedium)
            Text(
                if (enabled) "Add top-up credits for agent runs outside your plan."
                else "A saved card and terminal billing permission are required.",
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                billing.chargePresets.forEachIndexed { index, amount ->
                    val validPreset = runCatching { validateBillingAmount(amount, billing.minUsd, billing.maxUsd) }.getOrNull()
                    OutlinedButton(onClick = { validPreset?.let(onSelectAmount) }, enabled = enabled && validPreset != null) {
                        Text(billing.chargePresetsDisplay.getOrNull(index) ?: amount.money())
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customAmount,
                    onValueChange = { customAmount = it.take(12).filter { char -> char.isDigit() || char == '.' } },
                    label = { Text("Custom amount") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                )
                Button(onClick = { validCustomAmount?.let(onSelectAmount) }, enabled = enabled && validCustomAmount != null) {
                    Text("Buy")
                }
            }
            if (customAmount.isNotBlank() && validCustomAmount == null) {
                Text(
                    billingAmountHint(billing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AutoReloadCard(
    billing: BillingStateResponse,
    busy: Boolean,
    onSave: (Boolean, String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val auto = billing.autoReload
    val portalUrl = billing.portalUrl
    if (auto == null) {
        BillingRowCard("Auto-refill", "N/A", "Manage auto-refill in the Nous portal.")
        return
    }
    if (!auto.enabled) {
        BillingRowCard(
            "Auto-refill",
            "Off",
            "Turn on auto-refill from the Nous portal.",
            portalUrl?.let { { PortalButton(it, onOpenUrl, "Open portal") } },
        )
        return
    }
    if (auto.card.kind == "distinct") {
        val card = listOfNotNull(auto.card.brand, auto.card.last4?.let { "••$it" }).joinToString(" ").ifBlank { "a different card" }
        BillingRowCard(
            "Auto-refill",
            "Enabled",
            "Auto-refill charges $card. Reconcile it in the Nous portal.",
            portalUrl?.let { { PortalButton(it, onOpenUrl, "Reconcile") } },
        )
        return
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDisable by rememberSaveable { mutableStateOf(false) }
    var threshold by rememberSaveable(auto.thresholdUsd, auto.thresholdDisplay) {
        mutableStateOf(billingWireAmount(auto.thresholdUsd, auto.thresholdDisplay))
    }
    var reloadTo by rememberSaveable(auto.reloadToUsd, auto.reloadToDisplay) {
        mutableStateOf(billingWireAmount(auto.reloadToUsd, auto.reloadToDisplay))
    }
    val validThreshold = runCatching { validateBillingAmount(threshold, billing.minUsd, billing.maxUsd) }.getOrNull()
    val validReloadTo = runCatching { validateBillingAmount(reloadTo, billing.minUsd, billing.maxUsd) }.getOrNull()
    val validPair = validThreshold != null && validReloadTo != null &&
        validReloadTo.toBigDecimal() > validThreshold.toBigDecimal()
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-refill", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Refill ${auto.reloadToDisplay.orUnavailable()} when balance falls below ${auto.thresholdDisplay.orUnavailable()}.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!editing) OutlinedButton(onClick = { editing = true }, enabled = !busy) { Text("Manage") }
            }
            if (editing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BillingAmountField("Threshold", threshold, { threshold = it }, Modifier.weight(1f), !busy)
                    BillingAmountField("Reload to", reloadTo, { reloadTo = it }, Modifier.weight(1f), !busy)
                }
                if (!validPair) {
                    Text(
                        "Use valid amounts within the account bounds, with reload-to greater than the threshold.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { editing = false }, enabled = !busy) { Text("Cancel") }
                    OutlinedButton(onClick = { confirmDisable = true }, enabled = validPair && !busy) { Text("Turn off") }
                    Button(
                        onClick = { onSave(true, validThreshold.orEmpty(), validReloadTo.orEmpty()) },
                        enabled = validPair && !busy,
                    ) { Text("Save") }
                }
            }
        }
    }
    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Turn off auto-refill?") },
            text = { Text("Hermes will stop automatically adding credits when the balance is low.") },
            confirmButton = {
                Button(onClick = { confirmDisable = false; onSave(false, threshold, reloadTo) }) { Text("Turn off") }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BillingAmountField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(12).filter { char -> char.isDigit() || char == '.' }) },
        label = { Text(label) },
        prefix = { Text("$") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun BillingUsageCard(title: String, bar: UsageBarData) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text("${bar.remainingDisplay.orUnavailable()} remaining", style = MaterialTheme.typography.labelMedium)
            }
            LinearProgressIndicator(progress = { bar.fillFraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text("${bar.spentDisplay.orUnavailable()} spent of ${bar.totalDisplay.orUnavailable()}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BillingMetricCard(title: String, value: String) = BillingRowCard(title, value, "Portal spending guardrail.")

@Composable
private fun BillingRowCard(
    title: String,
    value: String,
    description: String,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            action?.invoke()
        }
    }
}

@Composable
private fun BillingNotice(title: String, message: String, action: (@Composable () -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodySmall)
            action?.invoke()
        }
    }
}

@Composable
private fun BillingLoading() {
    Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun BillingSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun PortalButton(url: String, onOpenUrl: (String) -> Unit, label: String = "Open portal") {
    OutlinedButton(onClick = { onOpenUrl(url) }) {
        Text(label)
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.padding(start = 6.dp).size(16.dp))
    }
}

private fun String?.orUnavailable(): String = this?.takeIf(String::isNotBlank) ?: "N/A"

private fun String.money(): String {
    val amount = toBigDecimalOrNull() ?: return this
    return "$" + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
}

private fun billingAmountHint(billing: BillingStateResponse): String = when {
    billing.minUsd != null && billing.maxUsd != null -> "Enter ${billing.minUsd.money()} to ${billing.maxUsd.money()}, with at most 2 decimals."
    billing.minUsd != null -> "Minimum ${billing.minUsd.money()}, with at most 2 decimals."
    billing.maxUsd != null -> "Maximum ${billing.maxUsd.money()}, with at most 2 decimals."
    else -> "Enter a positive dollar amount with at most 2 decimals."
}

private fun billingWireAmount(raw: String?, display: String): String =
    raw?.takeIf(String::isNotBlank) ?: display.replace(Regex("[^0-9.]"), "")

private fun creditProgress(remaining: String?, monthly: String?): String? {
    val left = remaining?.toBigDecimalOrNull() ?: return null
    val total = monthly?.toBigDecimalOrNull() ?: return null
    return if (left.signum() < 0) {
        "${java.math.BigDecimal.ZERO.toPlainString().money()} of ${total.toPlainString().money()} left / ${left.abs().toPlainString().money()} over"
    } else {
        "${left.toPlainString().money()} of ${total.toPlainString().money()} left"
    }
}

private fun billingPlan(subscription: SubscriptionStateResponse?, billing: BillingStateResponse?): String {
    val current = subscription?.current
    val name = current?.tierName ?: billing?.usage?.planName ?: return "N/A"
    val tier = subscription?.tiers?.firstOrNull { it.isCurrent || it.tierId == current?.tierId }
    val price = tier?.dollarsPerMonthDisplay?.takeIf(String::isNotBlank) ?: return name
    return "$name / $price/mo"
}

internal fun manageSubscriptionUrl(portalUrl: String, orgId: String?): String {
    val uri = runCatching { java.net.URI(portalUrl) }.getOrNull()
    val base = if (uri?.scheme in setOf("http", "https") && !uri?.rawAuthority.isNullOrBlank()) {
        "${uri?.scheme}://${uri?.rawAuthority}"
    } else {
        "https://portal.nousresearch.com"
    }
    return buildString {
        append(base)
        append("/manage-subscription")
        if (!orgId.isNullOrBlank()) append("?org_id=").append(java.net.URLEncoder.encode(orgId, "UTF-8"))
    }
}

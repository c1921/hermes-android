package com.nousresearch.hermes.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.protocol.BillingAutoReload
import com.nousresearch.hermes.protocol.BillingAutoReloadCard
import com.nousresearch.hermes.protocol.BillingCardInfo
import com.nousresearch.hermes.protocol.BillingStateResponse
import com.nousresearch.hermes.protocol.SubscriptionCurrent
import com.nousresearch.hermes.protocol.SubscriptionStateResponse
import com.nousresearch.hermes.protocol.SubscriptionTierOption
import com.nousresearch.hermes.protocol.UsageBarData
import com.nousresearch.hermes.protocol.UsageModelData
import com.nousresearch.hermes.ui.theme.HermesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BillingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun connectedAccountShowsDesktopBillingSummaryAndUsage() {
        render(
            HermesState(
                billingState = billingState(),
                subscriptionState = SubscriptionStateResponse(
                    loggedIn = true,
                    current = SubscriptionCurrent(tierId = "super", tierName = "Super", cycleEndsAt = "2026-08-18T12:00:00Z"),
                    tiers = listOf(
                        SubscriptionTierOption(
                            tierId = "super",
                            name = "Super",
                            dollarsPerMonthDisplay = "$20",
                            isCurrent = true,
                        ),
                    ),
                ),
            ),
        )

        compose.onNodeWithText("BALANCE").assertExists()
        compose.onAllNodesWithText("$24.50", useUnmergedTree = true).fetchSemanticsNodes().let { assertEquals(2, it.size) }
        compose.onNodeWithText("Super / $20/mo").assertExists()
        compose.onNodeWithText("ACCOUNT").assertExists()
        compose.onNodeWithText("Visa •••• 4242").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("USAGE"))
        compose.onNodeWithText("USAGE").assertExists()
        compose.onNodeWithText("$40.00 remaining").performScrollTo().assertExists()
    }

    @Test
    fun presetChargeRequiresConfirmationBeforeCallingHermes() {
        var charged: String? = null
        render(HermesState(billingState = billingState()), onCharge = { charged = it })

        compose.onNodeWithText("$20").performClick()
        compose.onNodeWithText("Buy $20.00 credits?").assertExists()
        compose.onNodeWithContentDescription("Confirm credit purchase").performClick()

        compose.runOnIdle { assertEquals("20", charged) }
    }

    @Test
    fun loggedOutAccountUsesServerPortalUrl() {
        var opened: String? = null
        render(
            HermesState(
                billingState = BillingStateResponse(
                    loggedIn = false,
                    portalUrl = "https://portal.nousresearch.com/billing",
                ),
            ),
            onOpenUrl = { opened = it },
        )

        compose.onNodeWithText("Connect your Nous account").assertExists()
        compose.onNodeWithText("Open portal").performClick()
        compose.runOnIdle { assertEquals("https://portal.nousresearch.com/billing", opened) }
    }

    @Test
    fun invalidCustomAmountCannotReachPurchaseConfirmation() {
        render(HermesState(billingState = billingState()))

        compose.onNodeWithText("Custom amount").performScrollTo().performTextInput(".")
        compose.onNodeWithText("Buy").assertIsNotEnabled()
        compose.onNodeWithText("Enter $5.00 to $100.00, with at most 2 decimals.").assertExists()
    }

    @Test
    fun oldGatewayShowsOneUpgradeNoticeInsteadOfABrokenAccount() {
        render(
            HermesState(
                billingSupported = false,
                billingError = "Billing requires a newer Hermes gateway.",
            ),
        )

        compose.onNodeWithText("Billing unavailable").assertExists()
        compose.onNodeWithText("Billing needs attention").assertDoesNotExist()
        compose.onNodeWithText("Connect your Nous account").assertDoesNotExist()
    }

    @Test
    fun overdrawnSubscriptionStatesTheOverageInText() {
        render(
            HermesState(
                billingState = billingState(),
                subscriptionState = SubscriptionStateResponse(
                    loggedIn = true,
                    current = SubscriptionCurrent(
                        tierName = "Super",
                        creditsRemaining = "-0.791",
                        monthlyCredits = "50",
                    ),
                ),
            ),
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("$0.00 of $50.00 left / $0.79 over"))
        compose.onNodeWithText("$0.00 of $50.00 left / $0.79 over", useUnmergedTree = true).assertExists()
    }

    @Test
    fun billingRefusalDoesNotMasqueradeAsLoggedOut() {
        render(
            HermesState(
                billingState = BillingStateResponse(ok = false, error = "endpoint_unavailable"),
                billingError = "Billing endpoint unavailable.",
                billingRecovery = com.nousresearch.hermes.data.BillingRecovery.RETRY,
            ),
        )

        compose.onNodeWithText("Billing needs attention").assertExists()
        compose.onNodeWithText("Connect your Nous account").assertDoesNotExist()
    }

    @Test
    fun subscriptionUsageTakesPrecedenceOverBillingFallback() {
        render(
            HermesState(
                billingState = billingState(),
                subscriptionState = SubscriptionStateResponse(
                    loggedIn = true,
                    usage = UsageModelData(
                        planBar = UsageBarData(
                            remainingDisplay = "$7.00",
                            totalDisplay = "$20.00",
                            spentDisplay = "$13.00",
                            fillFraction = 0.65f,
                        ),
                    ),
                ),
            ),
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("$7.00 remaining"))
        compose.onNodeWithText("$7.00 remaining", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("$40.00 remaining").assertDoesNotExist()
    }

    @Test
    fun distinctAutoRefillCardRoutesReconciliationToPortal() {
        var opened: String? = null
        val portal = "https://portal.nousresearch.com/billing"
        render(
            HermesState(
                billingState = billingState().copy(
                    portalUrl = portal,
                    autoReload = billingState().autoReload?.copy(
                        card = BillingAutoReloadCard(kind = "distinct", brand = "Visa", last4 = "1111"),
                    ),
                ),
            ),
            onOpenUrl = { opened = it },
        )

        compose.onNodeWithText("Reconcile").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(portal, opened) }
        compose.onNodeWithText("Manage").assertDoesNotExist()
    }

    @Test
    fun unconfirmedChargeStaysBlockedUntilBalanceReview() {
        var acknowledged = false
        render(
            HermesState(
                billingState = billingState(),
                billingChargeUnconfirmed = true,
                billingError = "Charge outcome is unconfirmed.",
            ),
            onAcknowledgeUnconfirmedCharge = { acknowledged = true },
        )

        compose.onNodeWithText("$20").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("I checked my balance"))
        compose.onNodeWithText("I checked my balance").performClick()
        compose.runOnIdle { assertEquals(true, acknowledged) }
    }

    @Test
    fun unconfirmedChargeCanBeAcknowledgedAfterAnErrorClearingRefresh() {
        var acknowledged = false
        render(
            HermesState(
                billingState = billingState(),
                billingChargeUnconfirmed = true,
                billingError = null,
            ),
            onAcknowledgeUnconfirmedCharge = { acknowledged = true },
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("I checked my balance"))
        compose.onNodeWithText("I checked my balance").performClick()
        compose.runOnIdle { assertEquals(true, acknowledged) }
    }

    private fun render(
        state: HermesState,
        onCharge: (String) -> Unit = {},
        onOpenUrl: (String) -> Unit = {},
        onAcknowledgeUnconfirmedCharge: () -> Unit = {},
    ) {
        compose.setContent {
            HermesTheme {
                BillingScreen(
                    state = state,
                    onRefresh = {},
                    onCharge = onCharge,
                    onUpdateAutoReload = { _, _, _ -> },
                    onStepUp = {},
                    onAcknowledgeUnconfirmedCharge = onAcknowledgeUnconfirmedCharge,
                    onOpenUrl = onOpenUrl,
                    onBack = null,
                )
            }
        }
    }

    private fun billingState() = BillingStateResponse(
        loggedIn = true,
        isAdmin = true,
        balanceDisplay = "$24.50",
        canCharge = true,
        cliBillingEnabled = true,
        card = BillingCardInfo(brand = "Visa", last4 = "4242", masked = "Visa •••• 4242"),
        chargePresets = listOf("10", "20", "50"),
        chargePresetsDisplay = listOf("$10", "$20", "$50"),
        minUsd = "5",
        maxUsd = "100",
        autoReload = BillingAutoReload(
            enabled = true,
            thresholdDisplay = "$5.00",
            thresholdUsd = "5",
            reloadToDisplay = "$25.00",
            reloadToUsd = "25",
        ),
        usage = UsageModelData(
            planName = "Super",
            planBar = UsageBarData(
                remainingDisplay = "$40.00",
                totalDisplay = "$50.00",
                spentDisplay = "$10.00",
                fillFraction = 0.2f,
            ),
        ),
    )
}

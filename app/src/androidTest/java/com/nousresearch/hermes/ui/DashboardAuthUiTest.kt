package com.nousresearch.hermes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nousresearch.hermes.network.DashboardAuthProvider
import com.nousresearch.hermes.ui.theme.HermesTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DashboardAuthUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun multiplePasswordProvidersRequireAndRetainNativeSelection() {
        var selected by mutableStateOf<String?>(null)
        compose.setContent {
            HermesTheme {
                DashboardPasswordProviderSelector(
                    providers = listOf(
                        DashboardAuthProvider("staff-password", "Staff", supportsPassword = true),
                        DashboardAuthProvider("customer-password", "Customer", supportsPassword = true),
                    ),
                    selectedProvider = selected,
                    onSelected = { selected = it },
                )
            }
        }

        compose.onNodeWithTag("password-provider-customer-password").performClick()

        compose.onNodeWithTag("password-provider-customer-password").assertIsSelected()
    }

    @Test
    fun oauthFallbackExplainsBrowserCookieBoundary() {
        compose.setContent { HermesTheme { DashboardOAuthAvailabilityNotice() } }

        compose.onNodeWithText("Hermes Android never reads browser or Custom Tab cookies.", substring = true)
            .assertExists()
    }

    @Test
    fun leavingOnboardingInvalidatesPendingDiscoveryAndNeverSubmitsCredentials() {
        val providers = CompletableDeferred<List<DashboardAuthProvider>>()
        val connectionCount = AtomicInteger(0)
        compose.setContent {
            HermesTheme {
                OnboardingScreen(
                    busy = false,
                    error = null,
                    onDiscoverPasswordProviders = { _, _ -> providers.await() },
                    onConnect = { _, _, _, _, _, _ -> connectionCount.incrementAndGet() },
                )
            }
        }
        compose.onNodeWithText("CONNECT TO HERMES").performClick()
        compose.onNodeWithText("Hermes backend URL").performTextInput("https://hermes.example")
        compose.onNodeWithText("Dashboard username").performTextInput("user")
        compose.onNodeWithText("Dashboard password").performTextInput("secret")
        compose.onNodeWithText("Check sign-in options and save").performClick()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle {
            providers.complete(listOf(DashboardAuthProvider("basic", "Password", supportsPassword = true)))
        }
        compose.waitForIdle()

        assertEquals(0, connectionCount.get())
    }
}

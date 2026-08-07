package com.nousresearch.hermes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test

class AppSettingsDeepLinkTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun appSettingsDeepLinkDoesNotRequireAnAuthenticatedBackend() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("hermes://app-settings"), context, MainActivity::class.java)

        ActivityScenario.launch<MainActivity>(intent).use {
            compose.onNodeWithText("APP SETTINGS").assertIsDisplayed()
            compose.onNodeWithText("APPEARANCE").assertIsDisplayed()
        }
    }
}

package com.nousresearch.hermes.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nousresearch.hermes.data.HermesState
import com.nousresearch.hermes.data.ProviderOAuthSession
import com.nousresearch.hermes.protocol.ModelOptionsResult
import com.nousresearch.hermes.protocol.ModelProvider
import com.nousresearch.hermes.protocol.OAuthProvider
import com.nousresearch.hermes.protocol.OAuthProviderStatus
import com.nousresearch.hermes.ui.theme.HermesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProvidersScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun advertisedAccountStartsItsServerOwnedLoginFlow() {
        var startedProvider: String? = null
        val state = HermesState(
            providerOptions = ModelOptionsResult(),
            oauthProviders = listOf(
                OAuthProvider(
                    id = "nous",
                    name = "Nous Portal",
                    flow = "device_code",
                    docsUrl = "https://portal.nousresearch.com",
                    status = OAuthProviderStatus(loggedIn = false),
                ),
            ),
        )

        compose.setContent {
            HermesTheme {
                ProvidersScreen(
                    state = state,
                    onRefresh = {},
                    onSave = { _, _, _ -> },
                    onDelete = {},
                    onStartOAuth = { startedProvider = it },
                    onSubmitOAuth = {},
                    onCancelOAuth = {},
                    onDisconnectOAuth = {},
                    onOpenUrl = {},
                    onCopy = { _, _ -> },
                    onBack = null,
                )
            }
        }

        compose.onNodeWithText("ACCOUNTS").assertExists()
        compose.onNodeWithText("Nous Portal").assertExists()
        compose.onNodeWithText("Connect").performClick()
        compose.runOnIdle { assertEquals("nous", startedProvider) }
    }

    @Test
    fun deviceCodeSessionCanCopyItsCodeOpenItsBrowserAndCancel() {
        var copied: Pair<String, String>? = null
        var openedUrl: String? = null
        var cancelled = false
        val state = HermesState(
            providerOptions = ModelOptionsResult(),
            oauthProviders = listOf(provider(id = "nous", name = "Nous Portal", flow = "device_code")),
            providerOAuthSession = ProviderOAuthSession(
                providerId = "nous",
                providerName = "Nous Portal",
                flow = "device_code",
                sessionId = "session-42",
                browserUrl = "https://portal.nousresearch.com/device",
                userCode = "HERM-4242",
                expiresAtEpochMillis = Long.MAX_VALUE,
                pollIntervalSeconds = 5,
            ),
        )

        render(
            state = state,
            onCancelOAuth = { cancelled = true },
            onOpenUrl = { openedUrl = it },
            onCopy = { label, value -> copied = label to value },
        )

        compose.onNodeWithText("HERM-4242").assertExists()
        compose.onNodeWithText("Copy code").performClick()
        compose.runOnIdle { assertEquals("Authorization code" to "HERM-4242", copied) }
        compose.onNodeWithText("Open browser").performClick()
        compose.runOnIdle { assertEquals("https://portal.nousresearch.com/device", openedUrl) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(true, cancelled) }
    }

    @Test
    fun pkceSessionSubmitsOnlyTheCodeEnteredByTheUser() {
        var submittedCode: String? = null
        val state = HermesState(
            providerOptions = ModelOptionsResult(),
            oauthProviders = listOf(provider(id = "openai", name = "OpenAI Codex", flow = "pkce")),
            providerOAuthSession = ProviderOAuthSession(
                providerId = "openai",
                providerName = "OpenAI Codex",
                flow = "pkce",
                sessionId = "session-99",
                browserUrl = "https://auth.openai.com/authorize",
                expiresAtEpochMillis = Long.MAX_VALUE,
                pollIntervalSeconds = 0,
            ),
        )

        render(state = state, onSubmitOAuth = { submittedCode = it })

        compose.onNodeWithText("Authorization code").performTextInput("  returned-code  ")
        compose.onNodeWithText("Complete sign-in").performClick()
        compose.runOnIdle { assertEquals("returned-code", submittedCode) }
    }

    @Test
    fun externalFlowShowsServerCommandWithoutExecutingIt() {
        var copied: Pair<String, String>? = null
        var refreshes = 0
        val command = "hermes auth login anthropic"
        val state = HermesState(
            providerOptions = ModelOptionsResult(),
            oauthProviders = listOf(
                provider(
                    id = "anthropic",
                    name = "Anthropic",
                    flow = "external",
                    cliCommand = command,
                    docsUrl = "https://docs.anthropic.com/auth",
                ),
            ),
        )

        render(
            state = state,
            onRefresh = { refreshes += 1 },
            onCopy = { label, value -> copied = label to value },
        )

        compose.onNodeWithText("Connect").performClick()
        compose.onNodeWithText(command).assertExists()
        compose.onNodeWithText("Copy command").performClick()
        compose.runOnIdle { assertEquals("Hermes login command" to command, copied) }
        compose.onNodeWithText("Recheck").performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test
    fun disconnectRequiresConfirmationForAnAdvertisedProvider() {
        var disconnectedProvider: String? = null
        val state = HermesState(
            providerOptions = ModelOptionsResult(),
            oauthProviders = listOf(
                provider(
                    id = "nous",
                    name = "Nous Portal",
                    flow = "device_code",
                    loggedIn = true,
                    disconnectable = true,
                ),
            ),
        )

        render(state = state, onDisconnectOAuth = { disconnectedProvider = it })

        compose.onNodeWithText("Disconnect").performClick()
        compose.onNodeWithText("Disconnect Nous Portal?").assertExists()
        compose.onNodeWithContentDescription("Confirm disconnect").performClick()
        compose.runOnIdle { assertEquals("nous", disconnectedProvider) }
    }

    @Test
    fun unknownFutureFlowHasNoDeadConnectAction() {
        val state = HermesState(
            providerOptions = ModelOptionsResult(),
            oauthProviders = listOf(provider(id = "future", name = "Future Provider", flow = "redirect_v2")),
        )

        render(state = state)

        compose.onNodeWithText("Unsupported").assertExists()
        compose.onNodeWithText("Connect").assertDoesNotExist()
    }

    @Test
    fun olderServerWithoutAccountsKeepsApiKeyProvidersVisible() {
        val state = HermesState(
            providerOptions = ModelOptionsResult(
                providers = listOf(ModelProvider(slug = "openrouter", name = "OpenRouter")),
            ),
            providerAccountsSupported = false,
        )

        render(state = state)

        compose.onNodeWithText("ACCOUNTS").assertDoesNotExist()
        compose.onNodeWithText("OpenRouter").assertExists()
    }

    private fun render(
        state: HermesState,
        onRefresh: () -> Unit = {},
        onSubmitOAuth: (String) -> Unit = {},
        onCancelOAuth: () -> Unit = {},
        onDisconnectOAuth: (String) -> Unit = {},
        onOpenUrl: (String) -> Unit = {},
        onCopy: (String, String) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            HermesTheme {
                ProvidersScreen(
                    state = state,
                    onRefresh = onRefresh,
                    onSave = { _, _, _ -> },
                    onDelete = {},
                    onStartOAuth = {},
                    onSubmitOAuth = onSubmitOAuth,
                    onCancelOAuth = onCancelOAuth,
                    onDisconnectOAuth = onDisconnectOAuth,
                    onOpenUrl = onOpenUrl,
                    onCopy = onCopy,
                    onBack = null,
                )
            }
        }
    }

    private fun provider(
        id: String,
        name: String,
        flow: String,
        cliCommand: String = "",
        docsUrl: String = "",
        loggedIn: Boolean = false,
        disconnectable: Boolean = false,
    ) = OAuthProvider(
        id = id,
        name = name,
        flow = flow,
        cliCommand = cliCommand,
        docsUrl = docsUrl,
        disconnectable = disconnectable,
        status = OAuthProviderStatus(loggedIn = loggedIn),
    )
}

package com.nousresearch.hermes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nousresearch.hermes.domain.SensitiveInputKind
import com.nousresearch.hermes.ui.theme.HermesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SensitiveInputDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sudoValueUsesPasswordSemanticsAndSubmitsUnmodified() {
        var submitted: String? = null
        compose.setContent {
            HermesTheme {
                SensitiveInputDialog(
                    requestId = "sudo-1",
                    kind = SensitiveInputKind.SUDO_PASSWORD,
                    prompt = "Hermes needs a sudo password to continue this command.",
                    environmentVariable = null,
                    onSubmit = { submitted = it },
                )
            }
        }

        compose.onNodeWithText("SUDO PASSWORD REQUIRED").assertExists()
        compose.onNodeWithText("Password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
            .performTextInput("test-only-secret")
        compose.onNodeWithText("CONTINUE").performClick()

        compose.runOnIdle { assertEquals("test-only-secret", submitted) }
    }

    @Test
    fun cancellingSecretRequestSubmitsEmptyValue() {
        var submitted: String? = null
        compose.setContent {
            HermesTheme {
                SensitiveInputDialog(
                    requestId = "secret-1",
                    kind = SensitiveInputKind.SECRET,
                    prompt = "Token for the isolated test target",
                    environmentVariable = "DEPLOY_TOKEN",
                    onSubmit = { submitted = it },
                )
            }
        }

        compose.onNodeWithText("DEPLOY_TOKEN", substring = true).assertExists()
        compose.onNodeWithText("CANCEL REQUEST").performClick()

        compose.runOnIdle { assertEquals("", submitted) }
    }

    @Test
    fun aNewRequestCannotReuseThePreviousSecretValue() {
        var requestId by mutableStateOf("secret-1")
        compose.setContent {
            HermesTheme {
                SensitiveInputDialog(
                    requestId = requestId,
                    kind = SensitiveInputKind.SECRET,
                    prompt = "Token for the isolated test target",
                    environmentVariable = "DEPLOY_TOKEN",
                    onSubmit = { },
                )
            }
        }

        compose.onNodeWithText("Secret value").performTextInput("first-request-only")
        compose.runOnIdle { requestId = "secret-2" }

        compose.onNodeWithText("first-request-only").assertDoesNotExist()
    }
}

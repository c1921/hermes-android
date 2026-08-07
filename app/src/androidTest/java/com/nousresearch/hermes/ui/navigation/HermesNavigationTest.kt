package com.nousresearch.hermes.ui.navigation

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.junit.Rule
import org.junit.Test

class HermesNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var dispatcher: OnBackPressedDispatcher

    private fun showNavigation() {
        composeRule.setContent {
            dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            val controller = rememberNavController()
            val navigator = remember(controller) { HermesNavigator(controller) }
            NavHost(controller, startDestination = HermesRoute.SessionAtlas("backend-1", "default")) {
                composable<HermesRoute.SessionAtlas> {
                    Button(onClick = {
                        navigator.openConversation("backend-1", "default", "session-1")
                    }) { Text("Open conversation") }
                }
                composable<HermesRoute.Conversation> {
                    Button(onClick = { navigator.openFiles("backend-1", "default", "/workspace") }) {
                        Text("Open files")
                    }
                }
                composable<HermesRoute.Files> {
                    Button(onClick = { navigator.back("backend-1", "default") }) { Text("Back from files") }
                }
            }
        }
        composeRule.onNodeWithText("Open conversation").performClick()
        composeRule.onNodeWithText("Open files").performClick()
    }

    @Test
    fun atlasConversationFilesBackReturnsToConversationOrigin() {
        showNavigation()
        composeRule.onNodeWithText("Back from files").performClick()

        composeRule.onNodeWithText("Open files").assertIsDisplayed()
    }

    @Test
    fun completedPredictiveBackReturnsToConversationOrigin() {
        showNavigation()

        composeRule.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(180f, 400f, 0.75f, BackEventCompat.EDGE_LEFT))
            dispatcher.onBackPressed()
        }

        composeRule.onNodeWithText("Open files").assertIsDisplayed()
    }

    @Test
    fun cancelledPredictiveBackKeepsFilesOpen() {
        showNavigation()

        composeRule.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(90f, 400f, 0.4f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackCancelled()
        }

        composeRule.onNodeWithText("Back from files").assertIsDisplayed()
    }

    @Test
    fun filesRouteAndOriginSurviveSavedStateRecreation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            val controller = rememberNavController()
            val navigator = remember(controller) { HermesNavigator(controller) }
            NavHost(controller, startDestination = HermesRoute.SessionAtlas("backend-1", "default")) {
                composable<HermesRoute.SessionAtlas> {
                    Button(onClick = {
                        navigator.openConversation("backend-1", "default", "session-1")
                    }) { Text("Open conversation") }
                }
                composable<HermesRoute.Conversation> {
                    Button(onClick = { navigator.openFiles("backend-1", "default", "/workspace") }) {
                        Text("Open files")
                    }
                }
                composable<HermesRoute.Files> {
                    Button(onClick = { navigator.back("backend-1", "default") }) { Text("Back from files") }
                }
            }
        }
        composeRule.onNodeWithText("Open conversation").performClick()
        composeRule.onNodeWithText("Open files").performClick()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Back from files").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Open files").assertIsDisplayed()
    }

    @Test
    fun durableConversationIdentitySurvivesSavedStateRecreation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            val controller = rememberNavController()
            val navigator = remember(controller) { HermesNavigator(controller) }
            NavHost(controller, startDestination = HermesRoute.SessionAtlas("backend-intended", "research")) {
                composable<HermesRoute.SessionAtlas> {
                    Button(onClick = {
                        navigator.openConversation("backend-intended", "research", "stored-session")
                    }) { Text("Restore conversation") }
                }
                composable<HermesRoute.Conversation> { entry ->
                    val route = entry.toRoute<HermesRoute.Conversation>()
                    Text("${route.backendId}/${route.profileId}/${route.sessionId}")
                }
            }
        }
        composeRule.onNodeWithText("Restore conversation").performClick()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("backend-intended/research/stored-session").assertIsDisplayed()
    }
}

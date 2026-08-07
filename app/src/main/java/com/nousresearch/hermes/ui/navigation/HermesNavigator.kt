package com.nousresearch.hermes.ui.navigation

import androidx.navigation.NavHostController

class HermesNavigator(private val controller: NavHostController) {
    fun openOnboarding(clearHistory: Boolean = false) {
        navigate(HermesRoute.Onboarding, clearHistory)
    }

    fun openAtlas(backendId: String, profileId: String, clearHistory: Boolean = false) {
        navigate(HermesRoute.SessionAtlas(backendId, profileId), clearHistory)
    }

    private fun navigate(route: HermesRoute, clearHistory: Boolean = false) {
        when (route) {
            HermesRoute.Onboarding -> controller.navigate(HermesRoute.Onboarding) {
                launchSingleTop = true
                if (clearHistory) popUpTo(controller.graph.id) { inclusive = true }
            }
            is HermesRoute.BackendPicker -> controller.navigate(route) {
                launchSingleTop = true
                if (clearHistory) popUpTo(controller.graph.id) { inclusive = true }
            }
            is HermesRoute.SessionAtlas -> controller.navigate(route) {
                launchSingleTop = true
                if (clearHistory) popUpTo(controller.graph.id) { inclusive = true }
            }
            is HermesRoute.Conversation -> controller.navigate(route) {
                launchSingleTop = true
                if (clearHistory) popUpTo(controller.graph.id) { inclusive = true }
            }
            is HermesRoute.Files -> controller.navigate(route) {
                launchSingleTop = true
                if (clearHistory) popUpTo(controller.graph.id) { inclusive = true }
            }
            is HermesRoute.Management -> controller.navigate(route) {
                launchSingleTop = true
                if (clearHistory) popUpTo(controller.graph.id) { inclusive = true }
            }
        }
    }

    fun openConversation(backendId: String, profileId: String, sessionId: String) {
        controller.navigate(HermesRoute.Conversation(backendId, profileId, sessionId)) {
            launchSingleTop = true
        }
    }

    fun openFiles(backendId: String, profileId: String, path: String?) {
        controller.navigate(HermesRoute.Files(backendId, profileId, path)) { launchSingleTop = true }
    }

    fun openManagement(backendId: String, profileId: String, destination: ManagementDestination) {
        controller.navigate(HermesRoute.Management(backendId, profileId, destination)) { launchSingleTop = true }
    }

    fun openBackendPicker(
        returnBackendId: String? = null,
        profileId: String? = null,
        clearHistory: Boolean = false,
    ) {
        navigate(HermesRoute.BackendPicker(returnBackendId, profileId), clearHistory)
    }

    fun replace(route: HermesRoute) {
        navigate(route, clearHistory = true)
    }

    fun back(fallbackBackendId: String, fallbackProfileId: String) {
        if (!controller.popBackStack()) openAtlas(fallbackBackendId, fallbackProfileId, clearHistory = true)
    }
}

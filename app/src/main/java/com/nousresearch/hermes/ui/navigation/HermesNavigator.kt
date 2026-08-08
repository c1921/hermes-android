package com.nousresearch.hermes.ui.navigation

import androidx.navigation.NavHostController

class HermesNavigator(private val controller: NavHostController) {
    fun openOnboarding(clearHistory: Boolean = false) {
        navigate(HermesRoute.Onboarding, clearHistory)
    }

    fun openAtlas(backendId: String, profileId: String, clearHistory: Boolean = false) {
        navigate(HermesDestinationRoute.Chats(backendId, profileId), clearHistory)
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
            is HermesDestinationRoute.Chats -> controller.navigate(route) {
                launchSingleTop = true
                if (clearHistory) popUpTo(controller.graph.id) { inclusive = true }
            }
            is HermesDestinationRoute.Artifacts -> controller.navigate(route) {
                launchSingleTop = true
                if (clearHistory) popUpTo(controller.graph.id) { inclusive = true }
            }
            is HermesDestinationRoute.Automations -> controller.navigate(route) {
                launchSingleTop = true
                if (clearHistory) popUpTo(controller.graph.id) { inclusive = true }
            }
            is HermesDestinationRoute.Manage -> controller.navigate(route) {
                launchSingleTop = true
                if (clearHistory) popUpTo(controller.graph.id) { inclusive = true }
            }
            is HermesDestinationRoute.AppSettings -> controller.navigate(route) {
                launchSingleTop = true
                if (clearHistory) popUpTo(controller.graph.id) { inclusive = true }
            }
        }
    }

    fun openProductRoute(route: HermesDestinationRoute, clearHistory: Boolean = false) {
        navigate(route, clearHistory)
    }

    fun openChats(
        backendId: String,
        profileId: String,
        sessionId: String? = null,
        messageId: String? = null,
    ) {
        navigate(HermesDestinationRoute.Chats(backendId, profileId, sessionId, messageId))
    }

    fun openArtifacts(
        backendId: String,
        profileId: String,
        artifactId: String? = null,
        filePath: String? = null,
    ) {
        navigate(HermesDestinationRoute.Artifacts(backendId, profileId, artifactId, filePath))
    }

    fun openAutomations(
        backendId: String,
        profileId: String,
        destination: AutomationDestination? = null,
        resourceId: String? = null,
    ) {
        navigate(HermesDestinationRoute.Automations(backendId, profileId, destination, resourceId))
    }

    fun openManage(
        backendId: String,
        profileId: String,
        section: ManageSection? = null,
        destination: ManageDestination? = null,
        resourceId: String? = null,
    ) {
        navigate(HermesDestinationRoute.Manage(backendId, profileId, section, destination, resourceId))
    }

    fun openAppSettings(section: AppSettingsSection? = null) {
        navigate(HermesDestinationRoute.AppSettings(section))
    }

    fun openConversation(
        backendId: String,
        profileId: String,
        sessionId: String,
        messageId: String? = null,
    ) {
        navigate(HermesDestinationRoute.Chats(backendId, profileId, sessionId, messageId))
    }

    fun openFiles(backendId: String, profileId: String, path: String?) {
        navigate(HermesDestinationRoute.Artifacts(backendId, profileId, filePath = path))
    }

    fun openManagement(backendId: String, profileId: String, destination: ManagementDestination) {
        navigate(HermesDestinationCatalog.destination(destination, backendId, profileId))
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

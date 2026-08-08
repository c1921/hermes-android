package com.nousresearch.hermes.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface HermesRoute {
    @Serializable
    data object Onboarding : HermesRoute

    @Serializable
    data class BackendPicker(
        val returnBackendId: String? = null,
        val profileId: String? = null,
    ) : HermesRoute

    @Serializable
    data class SessionAtlas(
        val backendId: String,
        val profileId: String,
    ) : HermesRoute {
        init {
            require(backendId.isNotBlank())
            require(profileId.isNotBlank())
        }
    }

    @Serializable
    data class Conversation(
        val backendId: String,
        val profileId: String,
        val sessionId: String,
    ) : HermesRoute {
        init {
            require(backendId.isNotBlank())
            require(profileId.isNotBlank())
            require(sessionId.isNotBlank())
        }
    }

    @Serializable
    data class Files(
        val backendId: String,
        val profileId: String,
        val path: String? = null,
    ) : HermesRoute {
        init {
            require(backendId.isNotBlank())
            require(profileId.isNotBlank())
        }
    }

    @Serializable
    data class Management(
        val backendId: String,
        val profileId: String,
        val destination: ManagementDestination,
    ) : HermesRoute {
        init {
            require(backendId.isNotBlank())
            require(profileId.isNotBlank())
        }
    }
}

@Serializable
enum class ManagementDestination {
    SKILLS,
    CRON,
    WEBHOOKS,
    PROFILES,
    BACKENDS,
    DIAGNOSTICS,
    PROVIDERS,
    MESSAGING,
    MCP,
    USAGE,
    BILLING,
    AGENTS,
    COMMAND_CENTER,
    STARMAP,
    HOST_CAPABILITIES,
    CONFIG,
}

/** Authoritative native product hierarchy; legacy routes remain while call sites migrate. */
@Serializable
sealed interface HermesDestinationRoute : HermesRoute {
    @Serializable
    data class Chats(
        val backendId: String,
        val profileId: String,
        val sessionId: String? = null,
        val messageId: String? = null,
    ) : HermesDestinationRoute {
        init {
            requireRemoteIdentity(backendId, profileId)
            requireOptionalStableId(sessionId)
            requireOptionalStableId(messageId)
        }
    }

    @Serializable
    data class Artifacts(
        val backendId: String,
        val profileId: String,
        val artifactId: String? = null,
        val filePath: String? = null,
    ) : HermesDestinationRoute {
        init {
            requireRemoteIdentity(backendId, profileId)
            requireOptionalStableId(artifactId)
            require(filePath == null || filePath.isNotBlank())
        }
    }

    @Serializable
    data class Automations(
        val backendId: String,
        val profileId: String,
        val destination: AutomationDestination? = null,
        val resourceId: String? = null,
    ) : HermesDestinationRoute {
        init {
            requireRemoteIdentity(backendId, profileId)
            requireOptionalStableId(resourceId)
        }
    }

    @Serializable
    data class Manage(
        val backendId: String,
        val profileId: String,
        val section: ManageSection? = null,
        val destination: ManageDestination? = null,
        val resourceId: String? = null,
    ) : HermesDestinationRoute {
        init {
            requireRemoteIdentity(backendId, profileId)
            requireOptionalStableId(resourceId)
            require(destination == null || section == null || destination.section == section)
        }
    }

    /** Device-local preferences deliberately carry no Hermes backend scope. */
    @Serializable
    data class AppSettings(
        val section: AppSettingsSection? = null,
    ) : HermesDestinationRoute
}

@Serializable
enum class ManageSection {
    CAPABILITIES,
    PROFILES_AND_MODELS,
    CONNECTIONS_AND_DELIVERY,
    MEMORY_AND_LEARNING,
    SERVER_AND_ACCOUNT,
}

@Serializable
enum class AutomationDestination {
    CRON,
    AGENTS,
    WEBHOOKS,
    COMMAND_CENTER,
}

@Serializable
enum class ManageDestination(val section: ManageSection) {
    SKILLS(ManageSection.CAPABILITIES),
    MCP(ManageSection.CAPABILITIES),
    HOST_CAPABILITIES(ManageSection.CAPABILITIES),
    PROFILES(ManageSection.PROFILES_AND_MODELS),
    BACKENDS(ManageSection.CONNECTIONS_AND_DELIVERY),
    PROVIDERS(ManageSection.CONNECTIONS_AND_DELIVERY),
    MESSAGING(ManageSection.CONNECTIONS_AND_DELIVERY),
    STARMAP(ManageSection.MEMORY_AND_LEARNING),
    DIAGNOSTICS(ManageSection.SERVER_AND_ACCOUNT),
    USAGE(ManageSection.SERVER_AND_ACCOUNT),
    BILLING(ManageSection.SERVER_AND_ACCOUNT),
    CONFIG(ManageSection.SERVER_AND_ACCOUNT),
}

@Serializable
enum class AppSettingsSection {
    APPEARANCE,
    PRIVACY_AND_SECURITY,
    NOTIFICATIONS,
    ACCESSIBILITY,
}

private fun requireRemoteIdentity(backendId: String, profileId: String) {
    require(backendId.isNotBlank())
    require(profileId.isNotBlank())
}

private fun requireOptionalStableId(value: String?) {
    require(value == null || value.isNotBlank())
}

data class SessionIdentity(
    val backendId: String,
    val profileId: String,
    val sessionId: String,
)

data class AutomationResourceIdentity(
    val destination: AutomationDestination,
    val resourceId: String,
)

data class RouteResolution(
    val route: HermesRoute,
    val explanation: String? = null,
    val mutationsEnabled: Boolean = false,
)

fun resolveRestoredRoute(
    route: HermesRoute,
    availableBackendIds: Set<String>,
    authenticatedBackendId: String?,
    authoritativeSessions: Set<SessionIdentity>,
): RouteResolution {
    val backendId = route.backendIdOrNull()
        ?: return RouteResolution(route = route)
    if (backendId !in availableBackendIds) {
        return RouteResolution(
            route = HermesRoute.BackendPicker(),
            explanation = "The backend for this destination is no longer available. Choose a backend to continue.",
        )
    }
    if (authenticatedBackendId != backendId) {
        return RouteResolution(
            route = HermesRoute.BackendPicker(
                returnBackendId = backendId,
                profileId = route.profileIdOrNull(),
            ),
            explanation = "Reconnect to this backend before continuing.",
        )
    }
    if (route is HermesRoute.Conversation) {
        val identity = SessionIdentity(route.backendId, route.profileId, route.sessionId)
        if (identity !in authoritativeSessions) {
            return RouteResolution(
                route = HermesRoute.SessionAtlas(route.backendId, route.profileId),
                explanation = "That Hermes session could not be found. Choose another session.",
            )
        }
    }
    if (route is HermesDestinationRoute.Chats && route.sessionId != null) {
        val identity = SessionIdentity(route.backendId, route.profileId, route.sessionId)
        if (identity !in authoritativeSessions) {
            return RouteResolution(
                route = HermesDestinationRoute.Chats(route.backendId, route.profileId),
                explanation = "That Hermes session could not be found. Choose another session.",
            )
        }
    }
    return RouteResolution(route = route, mutationsEnabled = true)
}

/**
 * Resolve an Android system entry only from authenticated, server-authoritative
 * identity. Stable route fields select a candidate; they never grant access.
 */
fun resolveEntryDestination(
    route: HermesDestinationRoute,
    availableBackendIds: Set<String>,
    authenticatedBackendId: String?,
    authoritativeSessions: Set<SessionIdentity>,
    authoritativeProfileIds: Set<String>,
    fallbackProfileId: String,
    authoritativeAutomationResources: Set<AutomationResourceIdentity>,
): RouteResolution {
    val restored = resolveRestoredRoute(
        route = route,
        availableBackendIds = availableBackendIds,
        authenticatedBackendId = authenticatedBackendId,
        authoritativeSessions = authoritativeSessions,
    )
    if (restored.route != route) return restored
    val profileId = route.profileIdOrNull()
    if (profileId != null && profileId !in authoritativeProfileIds) {
        val safeProfileId = fallbackProfileId.takeIf(authoritativeProfileIds::contains)
            ?: authoritativeProfileIds.firstOrNull()
            ?: fallbackProfileId
        return RouteResolution(
            route = HermesDestinationRoute.Chats(route.backendIdOrNull().orEmpty(), safeProfileId),
            explanation = "That Hermes profile could not be found. Opened Chats for the authenticated profile instead.",
        )
    }
    return when (route) {
        is HermesDestinationRoute.Artifacts -> if (route.artifactId != null || route.filePath != null) {
            RouteResolution(
                route = HermesDestinationRoute.Artifacts(route.backendId, route.profileId),
                explanation = "This external artifact reference could not be verified. Opened Artifacts without the resource instead.",
            )
        } else {
            restored
        }
        is HermesDestinationRoute.Automations -> if (
            route.resourceId != null && (
                route.destination == null || AutomationResourceIdentity(
                    destination = route.destination,
                    resourceId = route.resourceId,
                ) !in authoritativeAutomationResources
            )
        ) {
            RouteResolution(
                route = HermesDestinationRoute.Automations(route.backendId, route.profileId, route.destination),
                explanation = "That automation could not be verified. Opened Automations without the resource instead.",
            )
        } else {
            restored
        }
        is HermesDestinationRoute.Manage -> if (route.resourceId != null) {
            RouteResolution(
                route = HermesDestinationRoute.Manage(route.backendId, route.profileId, route.section, route.destination),
                explanation = "This external management resource could not be verified. Opened its section without the resource instead.",
            )
        } else {
            restored
        }
        is HermesDestinationRoute.Chats -> if (route.messageId != null) {
            RouteResolution(
                route = HermesDestinationRoute.Chats(route.backendId, route.profileId, route.sessionId),
                explanation = "This external transcript message reference could not be verified. Opened Chats without the message instead.",
                mutationsEnabled = restored.mutationsEnabled,
            )
        } else {
            restored
        }
        is HermesDestinationRoute.AppSettings,
        -> restored
    }
}

fun HermesRoute.backendIdOrNull(): String? = when (this) {
    HermesRoute.Onboarding, is HermesRoute.BackendPicker -> null
    is HermesRoute.SessionAtlas -> backendId
    is HermesRoute.Conversation -> backendId
    is HermesRoute.Files -> backendId
    is HermesRoute.Management -> backendId
    is HermesDestinationRoute.Chats -> backendId
    is HermesDestinationRoute.Artifacts -> backendId
    is HermesDestinationRoute.Automations -> backendId
    is HermesDestinationRoute.Manage -> backendId
    is HermesDestinationRoute.AppSettings -> null
}

private fun HermesRoute.profileIdOrNull(): String? = when (this) {
    HermesRoute.Onboarding -> null
    is HermesRoute.BackendPicker -> profileId
    is HermesRoute.SessionAtlas -> profileId
    is HermesRoute.Conversation -> profileId
    is HermesRoute.Files -> profileId
    is HermesRoute.Management -> profileId
    is HermesDestinationRoute.Chats -> profileId
    is HermesDestinationRoute.Artifacts -> profileId
    is HermesDestinationRoute.Automations -> profileId
    is HermesDestinationRoute.Manage -> profileId
    is HermesDestinationRoute.AppSettings -> null
}

fun conversationMutationsEnabled(
    route: HermesRoute.Conversation,
    activeBackendId: String?,
    activeSession: SessionIdentity?,
    runtimeStoredSessionId: String?,
    runtimeSessionId: String?,
): Boolean = activeBackendId == route.backendId &&
    activeSession == SessionIdentity(route.backendId, route.profileId, route.sessionId) &&
    runtimeStoredSessionId == route.sessionId &&
    !runtimeSessionId.isNullOrBlank()

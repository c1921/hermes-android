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
    PROFILES,
    BACKENDS,
    DIAGNOSTICS,
    PROVIDERS,
    MESSAGING,
    MCP,
    USAGE,
    BILLING,
    AGENTS,
    CONFIG,
}

data class SessionIdentity(
    val backendId: String,
    val profileId: String,
    val sessionId: String,
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
            route = HermesRoute.BackendPicker(),
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
    return RouteResolution(route = route, mutationsEnabled = true)
}

fun HermesRoute.backendIdOrNull(): String? = when (this) {
    HermesRoute.Onboarding, is HermesRoute.BackendPicker -> null
    is HermesRoute.SessionAtlas -> backendId
    is HermesRoute.Conversation -> backendId
    is HermesRoute.Files -> backendId
    is HermesRoute.Management -> backendId
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

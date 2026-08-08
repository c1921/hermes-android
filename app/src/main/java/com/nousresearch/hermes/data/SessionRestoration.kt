package com.nousresearch.hermes.data

import com.nousresearch.hermes.protocol.StoredSession
import kotlinx.serialization.Serializable

/**
 * The only session identity Android may persist for restoration. Runtime IDs,
 * transcripts, credentials, attachments, and share payloads deliberately do
 * not belong here.
 */
@Serializable
data class SessionTarget(
    val backendId: String,
    val profile: String,
    val sessionId: String,
) {
    init {
        require(backendId.isNotBlank())
        require(profile.isNotBlank())
        require(sessionId.isNotBlank())
    }
}

enum class SessionRestorationStatus {
    IDLE,
    AUTHENTICATING,
    REHYDRATING,
    READY,
    BACKEND_UNAVAILABLE,
    AUTHENTICATION_REQUIRED,
    RECOVERY_REQUIRED,
    SESSION_UNAVAILABLE,
    PROFILE_MISMATCH,
}

data class SessionRestorationState(
    val status: SessionRestorationStatus = SessionRestorationStatus.IDLE,
    val target: SessionTarget? = null,
    val session: StoredSession? = null,
    val explanation: String? = null,
) {
    val mutationsEnabled: Boolean
        get() = status == SessionRestorationStatus.READY
}

fun resolveSessionTarget(
    target: SessionTarget?,
    availableBackendIds: Set<String>,
    authenticatedBackendId: String?,
    sessions: List<StoredSession>,
): SessionRestorationState {
    if (target == null) return SessionRestorationState(status = SessionRestorationStatus.READY)
    if (target.backendId !in availableBackendIds) {
        return SessionRestorationState(
            status = SessionRestorationStatus.BACKEND_UNAVAILABLE,
            target = target,
            explanation = "The saved Hermes backend is no longer available. Choose a backend to continue.",
        )
    }
    if (authenticatedBackendId == null) {
        return SessionRestorationState(
            status = SessionRestorationStatus.AUTHENTICATION_REQUIRED,
            target = target,
            explanation = "Reconnect to this Hermes backend before continuing.",
        )
    }
    if (authenticatedBackendId != target.backendId) {
        return SessionRestorationState(
            status = SessionRestorationStatus.AUTHENTICATION_REQUIRED,
            target = target,
            explanation = "Reconnect to this Hermes backend before continuing.",
        )
    }

    val sameId = sessions.filter { it.durableId == target.sessionId }
    val session = sameId.firstOrNull { it.profile.normalizedProfile() == target.profile }
    if (session != null) {
        return SessionRestorationState(
            status = SessionRestorationStatus.READY,
            target = target,
            session = session,
        )
    }
    if (sameId.isNotEmpty()) {
        return SessionRestorationState(
            status = SessionRestorationStatus.PROFILE_MISMATCH,
            target = target,
            explanation = "That Hermes session belongs to another profile. Choose the matching profile to continue.",
        )
    }
    return SessionRestorationState(
        status = SessionRestorationStatus.SESSION_UNAVAILABLE,
        target = target,
        explanation = "That Hermes session could not be found. Choose another session to continue.",
    )
}

fun String?.normalizedProfile(): String = this?.takeIf(String::isNotBlank) ?: "default"

fun shouldAcceptRuntimeEvent(
    restorationStatus: SessionRestorationStatus,
    currentRuntimeSessionId: String?,
    eventRuntimeSessionId: String?,
): Boolean = when (restorationStatus) {
    SessionRestorationStatus.IDLE,
    SessionRestorationStatus.AUTHENTICATING,
    SessionRestorationStatus.AUTHENTICATION_REQUIRED,
    SessionRestorationStatus.BACKEND_UNAVAILABLE,
    SessionRestorationStatus.RECOVERY_REQUIRED,
    SessionRestorationStatus.SESSION_UNAVAILABLE,
    SessionRestorationStatus.PROFILE_MISMATCH,
    -> false
    SessionRestorationStatus.REHYDRATING ->
        !currentRuntimeSessionId.isNullOrBlank() && eventRuntimeSessionId == currentRuntimeSessionId
    SessionRestorationStatus.READY ->
        !currentRuntimeSessionId.isNullOrBlank() && eventRuntimeSessionId == currentRuntimeSessionId
}

fun shouldAcceptSubagentEvent(
    restorationStatus: SessionRestorationStatus,
    currentRuntimeSessionId: String?,
    eventRuntimeSessionId: String?,
): Boolean = restorationStatus == SessionRestorationStatus.READY ||
    shouldAcceptRuntimeEvent(restorationStatus, currentRuntimeSessionId, eventRuntimeSessionId)

fun SessionRestorationStatus.allowsRecoveryRequest(): Boolean = this in setOf(
    SessionRestorationStatus.READY,
    SessionRestorationStatus.RECOVERY_REQUIRED,
    SessionRestorationStatus.SESSION_UNAVAILABLE,
    SessionRestorationStatus.PROFILE_MISMATCH,
)

package com.nousresearch.hermes.protocol

/**
 * Server-owned capabilities from the resolved decision map in #6.  These
 * identifiers are contract names, not UI destinations.  A destination must
 * never infer authority from its own visibility.
 */
enum class HermesBackendCapability(
    val wireName: String,
    val fallback: CapabilityFallback,
    val minimumMethods: Map<String, Set<String>> = emptyMap(),
    val minimumEvents: Set<String> = emptySet(),
    val requiresProfileScope: Boolean = false,
) {
    FULL_CLIENT_BOUNDARY_V1(
        wireName = "full_client_boundary_v1",
        fallback = CapabilityFallback.PIN_AUDITED_PROVENANCE_AND_HIDE_UNSUPPORTED_MUTATIONS,
    ),
    NATIVE_PKCE_V1(
        wireName = "native_pkce_v1",
        fallback = CapabilityFallback.RENEWABLE_DASHBOARD_SESSION,
        minimumMethods = mapOf(
            "auth.native.start" to setOf("redirect_uri", "code_challenge", "state"),
            "auth.native.exchange" to setOf("code", "code_verifier", "redirect_uri"),
            "auth.native.refresh" to setOf("refresh_token"),
            "auth.native.revoke" to setOf("refresh_token"),
        ),
    ),
    PROFILE_SCOPING_V1(
        wireName = "profile_scoping_v1",
        fallback = CapabilityFallback.RESTRICT_UNPROVEN_PROFILE_MUTATIONS,
        requiresProfileScope = true,
    ),
    EVENT_REPLAY_V1(
        wireName = "event_replay_v1",
        fallback = CapabilityFallback.REHYDRATE_AUTHORITATIVE_HISTORY,
        minimumMethods = mapOf(
            "session.events.resume" to setOf("session_id", "profile", "cursor"),
        ),
        minimumEvents = setOf("resync.required"),
        requiresProfileScope = true,
    ),
    MULTI_CLIENT_V1(
        wireName = "multi_client_v1",
        fallback = CapabilityFallback.WARN_AND_REHYDRATE_AFTER_TAKEOVER,
        minimumMethods = mapOf(
            "session.control" to setOf("session_id", "profile", "mode"),
        ),
        requiresProfileScope = true,
    ),
    CLIENT_DEVICES_V1(
        wireName = "client_devices_v1",
        fallback = CapabilityFallback.FOREGROUND_PROMPTS_ONLY,
        minimumMethods = mapOf(
            "client.devices.register" to setOf("installation_key", "push_token"),
            "client.devices.revoke" to setOf("device_id"),
            "notifications.ack" to setOf("event_id"),
            "approval.action" to setOf("action_token", "decision"),
        ),
        requiresProfileScope = true,
    ),
    ARTIFACTS_UPLOADS_V1(
        wireName = "artifacts_uploads_v1",
        fallback = CapabilityFallback.BOUNDED_FOREGROUND_ATTACHMENTS,
        minimumMethods = mapOf(
            "artifact.list" to setOf("profile"),
            "artifact.read" to setOf("artifact_id", "profile"),
            "artifact.download" to setOf("artifact_id", "profile"),
            "upload.start" to setOf("profile", "name", "mime", "size", "digest"),
            "upload.complete" to setOf("profile", "upload_id", "digest"),
        ),
        requiresProfileScope = true,
    ),
    MCP_PATCH_OAUTH_V1(
        wireName = "mcp_patch_oauth_v1",
        fallback = CapabilityFallback.MCP_CATALOG_ONLY,
        minimumMethods = mapOf(
            "mcp.server.patch" to setOf("profile", "name", "patch", "revision"),
            "mcp.oauth.start" to setOf("profile", "name"),
            "mcp.oauth.status" to setOf("profile", "name", "transaction_id"),
            "mcp.oauth.cancel" to setOf("profile", "name", "transaction_id"),
        ),
        requiresProfileScope = true,
    ),
    ROLLBACK_PRECONDITION_V1(
        wireName = "rollback_precondition_v1",
        fallback = CapabilityFallback.IMMEDIATE_DIFF_RECHECK_AND_CONFIRMATION,
        minimumMethods = mapOf(
            "rollback.diff" to setOf("session_id", "checkpoint"),
            "rollback.restore" to setOf("session_id", "checkpoint", "expected_preview_id"),
        ),
        requiresProfileScope = true,
    ),
    TOOL_LIFECYCLE_V1(
        wireName = "tool_lifecycle_v1",
        fallback = CapabilityFallback.BOUNDED_GENERIC_TOOL_RENDERER,
        minimumEvents = setOf("tool.start", "tool.update", "tool.complete"),
        requiresProfileScope = true,
    ),
    ;

    companion object {
        /** Naming aliases keep the adapter tolerant of the #6 prose and future server spelling. */
        val PROFILE_ISOLATION_V1: HermesBackendCapability get() = PROFILE_SCOPING_V1
        val ARTIFACTS_V1: HermesBackendCapability get() = ARTIFACTS_UPLOADS_V1
        val MOBILE_PUSH_V1: HermesBackendCapability get() = CLIENT_DEVICES_V1

        fun fromWireName(value: String): HermesBackendCapability? = when (value) {
            "profile_isolation_v1" -> PROFILE_SCOPING_V1
            "artifacts_v1" -> ARTIFACTS_UPLOADS_V1
            "mobile_push_v1" -> CLIENT_DEVICES_V1
            else -> entries.firstOrNull { it.wireName == value }
        }
    }
}

enum class CapabilityFallback(val explanation: String) {
    PIN_AUDITED_PROVENANCE_AND_HIDE_UNSUPPORTED_MUTATIONS(
        "Pin the audited Hermes contract and hide mutations that are not explicitly advertised.",
    ),
    RENEWABLE_DASHBOARD_SESSION(
        "Use the renewable password-backed Dashboard session; never extract browser cookies.",
    ),
    RESTRICT_UNPROVEN_PROFILE_MUTATIONS(
        "Keep profile-scoped mutations disabled until Hermes resolves and authorizes the selected profile.",
    ),
    REHYDRATE_AUTHORITATIVE_HISTORY(
        "Rehydrate authoritative history and live projection; do not claim exact replay.",
    ),
    WARN_AND_REHYDRATE_AFTER_TAKEOVER(
        "Warn about concurrent control and rehydrate after a takeover instead of silently stealing transport.",
    ),
    FOREGROUND_PROMPTS_ONLY(
        "Keep prompts in the foreground; do not simulate background delivery with a permanent socket or polling.",
    ),
    BOUNDED_FOREGROUND_ATTACHMENTS(
        "Keep bounded foreground attachments and managed files; do not persist raw URIs as durable uploads.",
    ),
    MCP_CATALOG_ONLY(
        "Keep reviewed MCP catalog inspection, testing, toggling and removal; do not round-trip hidden secrets.",
    ),
    IMMEDIATE_DIFF_RECHECK_AND_CONFIRMATION(
        "Use the current immediate diff recheck, busy-session rejection and second confirmation.",
    ),
    BOUNDED_GENERIC_TOOL_RENDERER(
        "Render known events safely and use a bounded generic fallback for unknown tool lifecycle data.",
    ),
}

enum class CapabilityStateKind {
    ADVERTISED,
    ABSENT,
    MALFORMED,
    UNAUTHORIZED,
    WRONG_PROFILE,
    OLDER_SERVER,
    NEWER_SERVER,
}

sealed interface CapabilityState {
    val capability: HermesBackendCapability
    val profile: String?
    val reason: String
    val fallback: String
    val kind: CapabilityStateKind
    val isAvailable: Boolean get() = kind == CapabilityStateKind.ADVERTISED

    data class Advertised(
        override val capability: HermesBackendCapability,
        override val profile: String?,
        val declaration: CapabilityDeclaration,
        override val reason: String = "Hermes advertised ${capability.wireName} for this profile.",
    ) : CapabilityState {
        override val fallback: String get() = capability.fallback.explanation
        override val kind: CapabilityStateKind get() = CapabilityStateKind.ADVERTISED
    }

    data class Absent(
        override val capability: HermesBackendCapability,
        override val profile: String?,
        override val reason: String = "Hermes did not advertise ${capability.wireName}.",
    ) : CapabilityState {
        override val fallback: String get() = capability.fallback.explanation
        override val kind: CapabilityStateKind get() = CapabilityStateKind.ABSENT
    }

    data class Malformed(
        override val capability: HermesBackendCapability,
        override val profile: String?,
        override val reason: String,
    ) : CapabilityState {
        override val fallback: String get() = capability.fallback.explanation
        override val kind: CapabilityStateKind get() = CapabilityStateKind.MALFORMED
    }

    data class Unauthorized(
        override val capability: HermesBackendCapability,
        override val profile: String?,
        override val reason: String = "Hermes did not authorize ${capability.wireName} for this client.",
    ) : CapabilityState {
        override val fallback: String get() = capability.fallback.explanation
        override val kind: CapabilityStateKind get() = CapabilityStateKind.UNAUTHORIZED
    }

    data class WrongProfile(
        override val capability: HermesBackendCapability,
        override val profile: String?,
        override val reason: String,
    ) : CapabilityState {
        override val fallback: String get() = capability.fallback.explanation
        override val kind: CapabilityStateKind get() = CapabilityStateKind.WRONG_PROFILE
    }

    data class OlderServer(
        override val capability: HermesBackendCapability,
        override val profile: String?,
        override val reason: String,
    ) : CapabilityState {
        override val fallback: String get() = capability.fallback.explanation
        override val kind: CapabilityStateKind get() = CapabilityStateKind.OLDER_SERVER
    }

    data class NewerServer(
        override val capability: HermesBackendCapability,
        override val profile: String?,
        override val reason: String,
    ) : CapabilityState {
        override val fallback: String get() = capability.fallback.explanation
        override val kind: CapabilityStateKind get() = CapabilityStateKind.NEWER_SERVER
    }
}

enum class CapabilityMutationRejection {
    CAPABILITY_UNAVAILABLE,
    WRONG_PROFILE,
    METHOD_NOT_ADVERTISED,
    MISSING_PARAMETER,
    UNKNOWN_PARAMETER,
    UNAUTHORIZED,
}

sealed interface CapabilityMutationDecision {
    data class Allowed(
        val capability: HermesBackendCapability,
        val method: String,
        val profile: String?,
    ) : CapabilityMutationDecision

    data class Rejected(
        val capability: HermesBackendCapability,
        val reason: CapabilityMutationRejection,
        val explanation: String,
    ) : CapabilityMutationDecision
}

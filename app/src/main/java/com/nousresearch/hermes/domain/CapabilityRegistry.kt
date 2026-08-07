package com.nousresearch.hermes.domain

import com.nousresearch.hermes.protocol.CapabilityContractDocument
import com.nousresearch.hermes.protocol.CapabilityContractParseResult
import com.nousresearch.hermes.protocol.CapabilityMutationDecision
import com.nousresearch.hermes.protocol.CapabilityMutationRejection
import com.nousresearch.hermes.protocol.CapabilityState
import com.nousresearch.hermes.protocol.HermesBackendCapability
import com.nousresearch.hermes.protocol.StatusResponse
import com.nousresearch.hermes.protocol.capabilityContract

/**
 * One fail-closed resolution point for all server-owned parity contracts.
 * Callers receive typed state and must use [authorizeMutation] immediately
 * before any authority-bearing request.
 */
data class CapabilityRegistry(
    val parseResult: CapabilityContractParseResult,
    val resolvedProfile: String?,
    val states: Map<HermesBackendCapability, CapabilityState>,
    val compatibilityAdapters: Map<CurrentHermesFeature, CurrentHermesCompatibilityAdapter>,
) {
    fun state(capability: HermesBackendCapability): CapabilityState = states.getValue(capability)

    fun compatibilityAdapter(feature: CurrentHermesFeature): CurrentHermesCompatibilityAdapter =
        compatibilityAdapters.getValue(feature)

    fun authorizeMutation(
        capability: HermesBackendCapability,
        profile: String?,
        method: String,
        parameters: Set<String>,
        scopes: Set<String>,
    ): CapabilityMutationDecision {
        val state = state(capability)
        if (!state.isAvailable || state !is CapabilityState.Advertised) {
            return CapabilityMutationDecision.Rejected(
                capability = capability,
                reason = CapabilityMutationRejection.CAPABILITY_UNAVAILABLE,
                explanation = "${state.reason} ${state.fallback}",
            )
        }
        val declaration = state.declaration
        if (capability.requiresProfileScope) {
            if (profile.isNullOrBlank() || resolvedProfile != profile) {
                return CapabilityMutationDecision.Rejected(
                    capability,
                    CapabilityMutationRejection.WRONG_PROFILE,
                    "The selected profile is not the profile Hermes authorized for this capability.",
                )
            }
            if (declaration.profileScope != com.nousresearch.hermes.protocol.CapabilityProfileScope.PROFILE) {
                return CapabilityMutationDecision.Rejected(
                    capability,
                    CapabilityMutationRejection.WRONG_PROFILE,
                    "Hermes did not advertise explicit profile scope for this capability.",
                )
            }
            if (declaration.profiles.isNotEmpty() && profile !in declaration.profiles) {
                return CapabilityMutationDecision.Rejected(
                    capability,
                    CapabilityMutationRejection.WRONG_PROFILE,
                    "Hermes did not authorize the selected profile for this capability.",
                )
            }
        }

        val contract = declaration.methods[method]
            ?: return CapabilityMutationDecision.Rejected(
                capability,
                CapabilityMutationRejection.METHOD_NOT_ADVERTISED,
                "Hermes did not advertise method $method for ${capability.wireName}.",
            )
        if (!parameters.containsAll(contract.requiredParameters)) {
            return CapabilityMutationDecision.Rejected(
                capability,
                CapabilityMutationRejection.MISSING_PARAMETER,
                "The request is missing one or more parameters advertised for $method.",
            )
        }
        if (!parameters.all { it in contract.allowedParameters }) {
            return CapabilityMutationDecision.Rejected(
                capability,
                CapabilityMutationRejection.UNKNOWN_PARAMETER,
                "The request contains a parameter that Hermes did not advertise for $method.",
            )
        }
        val requiredScopes = declaration.scopes + contract.scopes
        if (!scopes.containsAll(requiredScopes)) {
            return CapabilityMutationDecision.Rejected(
                capability,
                CapabilityMutationRejection.UNAUTHORIZED,
                "The authenticated client does not hold every scope required for $method.",
            )
        }
        return CapabilityMutationDecision.Allowed(capability, method, profile)
    }

    companion object {
        fun resolve(
            status: StatusResponse,
            profile: String?,
            clientContractVersion: Int = 1,
        ): CapabilityRegistry = resolve(status.capabilityContract(), profile, clientContractVersion)

        fun resolve(
            parseResult: CapabilityContractParseResult,
            profile: String?,
            clientContractVersion: Int = 1,
        ): CapabilityRegistry {
            val document = (parseResult as? CapabilityContractParseResult.Valid)?.document
            val states = HermesBackendCapability.entries.associateWith { capability ->
                resolveState(parseResult, document, capability, profile, clientContractVersion)
            }
            return CapabilityRegistry(
                parseResult = parseResult,
                resolvedProfile = document?.resolvedProfile,
                states = states,
                compatibilityAdapters = CurrentHermesCompatibility.adapters,
            )
        }

        private fun resolveState(
            result: CapabilityContractParseResult,
            document: CapabilityContractDocument?,
            capability: HermesBackendCapability,
            profile: String?,
            clientContractVersion: Int,
        ): CapabilityState = when (result) {
            CapabilityContractParseResult.Missing -> CapabilityState.Absent(capability, profile)
            is CapabilityContractParseResult.Malformed -> CapabilityState.Malformed(capability, profile, result.reason)
            is CapabilityContractParseResult.Unsupported -> when (result.compatibility) {
                com.nousresearch.hermes.protocol.CapabilityContractCompatibility.OLDER_SERVER ->
                    CapabilityState.OlderServer(capability, profile, "Hermes capability schema ${result.schemaVersion} is older than this client.")
                com.nousresearch.hermes.protocol.CapabilityContractCompatibility.NEWER_SERVER ->
                    CapabilityState.NewerServer(capability, profile, "Hermes capability schema ${result.schemaVersion} is newer than this client.")
            }
            is CapabilityContractParseResult.Valid -> resolveValidState(
                checkNotNull(document), capability, profile, clientContractVersion,
            )
        }

        private fun resolveValidState(
            document: CapabilityContractDocument,
            capability: HermesBackendCapability,
            profile: String?,
            clientContractVersion: Int,
        ): CapabilityState {
            val declaration = document.capabilities[capability.wireName]
                ?: return CapabilityState.Absent(capability, profile)
            if (!document.authorized || !declaration.advertised) {
                return CapabilityState.Unauthorized(
                    capability,
                    profile,
                    if (!document.authorized) {
                        "Hermes did not authorize this client for ${capability.wireName}."
                    } else {
                        "Hermes reported ${capability.wireName} as unavailable."
                    },
                )
            }
            if (declaration.minClientContract != null && clientContractVersion < declaration.minClientContract) {
                return CapabilityState.OlderServer(
                    capability,
                    profile,
                    "${capability.wireName} requires client contract ${declaration.minClientContract}.",
                )
            }
            if (declaration.maxClientContract != null && clientContractVersion > declaration.maxClientContract) {
                return CapabilityState.NewerServer(
                    capability,
                    profile,
                    "${capability.wireName} only supports client contract ${declaration.maxClientContract}.",
                )
            }
            if (capability.requiresProfileScope) {
                if (profile.isNullOrBlank() || document.resolvedProfile != profile) {
                    return CapabilityState.WrongProfile(
                        capability,
                        profile,
                        "Hermes resolved ${document.resolvedProfile ?: "no profile"}, not the selected ${profile ?: "unknown profile"}.",
                    )
                }
                if (declaration.profileScope != com.nousresearch.hermes.protocol.CapabilityProfileScope.PROFILE) {
                    return CapabilityState.WrongProfile(capability, profile, "Hermes did not advertise explicit profile scope.")
                }
                if (declaration.profiles.isNotEmpty() && profile !in declaration.profiles) {
                    return CapabilityState.WrongProfile(capability, profile, "Hermes did not authorize this profile.")
                }
            }
            val missingMethods = capability.minimumMethods.keys - declaration.methods.keys
            if (missingMethods.isNotEmpty()) {
                return CapabilityState.Malformed(
                    capability,
                    profile,
                    "${capability.wireName} is missing advertised methods: ${missingMethods.joinToString()}.",
                )
            }
            val missingParameters = capability.minimumMethods.flatMap { (method, parameters) ->
                val missing = parameters - declaration.methods.getValue(method).requiredParameters
                if (missing.isEmpty()) emptyList() else listOf("$method(${missing.joinToString()})")
            }
            if (missingParameters.isNotEmpty()) {
                return CapabilityState.Malformed(
                    capability,
                    profile,
                    "${capability.wireName} is missing advertised parameters: ${missingParameters.joinToString()}.",
                )
            }
            val missingEvents = capability.minimumEvents - declaration.events
            if (missingEvents.isNotEmpty()) {
                return CapabilityState.Malformed(
                    capability,
                    profile,
                    "${capability.wireName} is missing advertised events: ${missingEvents.joinToString()}.",
                )
            }
            return CapabilityState.Advertised(capability, profile, declaration)
        }
    }
}

enum class CurrentHermesFeature {
    DASHBOARD_SESSION_AUTH,
    SESSION_REHYDRATION,
    FOREGROUND_PROMPTS,
    BOUNDED_FOREGROUND_ATTACHMENTS,
    MCP_CATALOG,
    LEGACY_ROLLBACK,
}

data class CurrentHermesCompatibilityAdapter(
    val feature: CurrentHermesFeature,
    val supported: Boolean,
    val methods: Set<String>,
    val fallback: String,
)

private object CurrentHermesCompatibility {
    val adapters: Map<CurrentHermesFeature, CurrentHermesCompatibilityAdapter> =
        CurrentHermesFeature.entries.associateWith { feature ->
            CurrentHermesCompatibilityAdapter(
                feature = feature,
                supported = true,
                methods = when (feature) {
                    CurrentHermesFeature.DASHBOARD_SESSION_AUTH -> setOf("/api/auth/login", "/api/status")
                    CurrentHermesFeature.SESSION_REHYDRATION -> setOf("session.resume", "session.history")
                    CurrentHermesFeature.FOREGROUND_PROMPTS -> setOf("prompt.submit", "approval.response", "clarification.response")
                    CurrentHermesFeature.BOUNDED_FOREGROUND_ATTACHMENTS -> setOf("file.attach", "image.attach_bytes", "pdf.attach")
                    CurrentHermesFeature.MCP_CATALOG -> setOf("/api/mcp/servers", "/api/mcp/catalog")
                    CurrentHermesFeature.LEGACY_ROLLBACK -> setOf("rollback.list", "rollback.diff", "rollback.restore")
                },
                fallback = when (feature) {
                    CurrentHermesFeature.DASHBOARD_SESSION_AUTH -> "Use password-backed renewable Dashboard sessions."
                    CurrentHermesFeature.SESSION_REHYDRATION -> "Restore authoritative history without claiming exact replay."
                    CurrentHermesFeature.FOREGROUND_PROMPTS -> "Keep blocking prompts visible while the app is foregrounded."
                    CurrentHermesFeature.BOUNDED_FOREGROUND_ATTACHMENTS -> "Keep bounded foreground file and image attachments."
                    CurrentHermesFeature.MCP_CATALOG -> "Keep reviewed MCP catalog operations only."
                    CurrentHermesFeature.LEGACY_ROLLBACK -> "Recheck the diff and confirm immediately before restore."
                },
            )
        }
}

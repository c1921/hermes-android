package com.nousresearch.hermes.domain

import com.nousresearch.hermes.protocol.CapabilityMutationDecision
import com.nousresearch.hermes.protocol.CapabilityMutationRejection
import com.nousresearch.hermes.protocol.CapabilityState
import com.nousresearch.hermes.protocol.CapabilityStateKind
import com.nousresearch.hermes.protocol.HermesBackendCapability
import com.nousresearch.hermes.protocol.StatusResponse
import com.nousresearch.hermes.protocol.capabilityContract
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRegistryTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `advertised document resolves every backend owned blocker`() {
        val registry = registry("capabilities-advertised-3f5e8d1a.json")

        HermesBackendCapability.entries.forEach { capability ->
            assertTrue("${capability.wireName} was not advertised", registry.state(capability) is CapabilityState.Advertised)
        }
    }

    @Test
    fun `absent document supplies safe fallback for every blocker`() {
        val registry = registry("capabilities-absent-0c1a9b7e.json")

        HermesBackendCapability.entries.forEach { capability ->
            val state = registry.state(capability)
            assertTrue(state is CapabilityState.Absent)
            assertTrue(state.fallback.isNotBlank())
        }
    }

    @Test
    fun `malformed unauthorized wrong profile older and newer documents fail closed`() {
        assertAllStates("capabilities-malformed-2c7e64a1.json", CapabilityStateKind.MALFORMED)
        assertAllStates("capabilities-unauthorized-41d8b3c9.json", CapabilityStateKind.UNAUTHORIZED)
        assertProfileScopedStates("capabilities-wrong-profile-7a2f0d11.json", CapabilityStateKind.WRONG_PROFILE)
        assertAllStates("capabilities-older-1e94ad22.json", CapabilityStateKind.OLDER_SERVER)
        assertAllStates("capabilities-newer-8f0b21c4.json", CapabilityStateKind.NEWER_SERVER)
    }

    @Test
    fun `mutation requires advertised method parameters scopes and resolved profile`() {
        val registry = registry("capabilities-advertised-3f5e8d1a.json", profile = "work")

        val allowed = registry.authorizeMutation(
            HermesBackendCapability.EVENT_REPLAY_V1,
            profile = "work",
            method = "session.events.resume",
            parameters = setOf("session_id", "profile", "cursor"),
            scopes = setOf("session.read"),
        )
        assertTrue(allowed is CapabilityMutationDecision.Allowed)

        val wrongProfile = registry.authorizeMutation(
            HermesBackendCapability.EVENT_REPLAY_V1,
            profile = "personal",
            method = "session.events.resume",
            parameters = setOf("session_id", "profile", "cursor"),
            scopes = setOf("session.read"),
        )
        assertTrue(wrongProfile is CapabilityMutationDecision.Rejected)
        assertEquals(CapabilityMutationRejection.WRONG_PROFILE, (wrongProfile as CapabilityMutationDecision.Rejected).reason)

        val missingParameter = registry.authorizeMutation(
            HermesBackendCapability.EVENT_REPLAY_V1,
            profile = "work",
            method = "session.events.resume",
            parameters = setOf("session_id", "profile"),
            scopes = setOf("session.read"),
        )
        assertEquals(CapabilityMutationRejection.MISSING_PARAMETER, (missingParameter as CapabilityMutationDecision.Rejected).reason)

        val unknownMethod = registry.authorizeMutation(
            HermesBackendCapability.EVENT_REPLAY_V1,
            profile = "work",
            method = "session.events.fake",
            parameters = emptySet(),
            scopes = emptySet(),
        )
        assertEquals(CapabilityMutationRejection.METHOD_NOT_ADVERTISED, (unknownMethod as CapabilityMutationDecision.Rejected).reason)

        val missingScope = registry.authorizeMutation(
            HermesBackendCapability.EVENT_REPLAY_V1,
            profile = "work",
            method = "session.events.resume",
            parameters = setOf("session_id", "profile", "cursor"),
            scopes = emptySet(),
        )
        assertEquals(CapabilityMutationRejection.UNAUTHORIZED, (missingScope as CapabilityMutationDecision.Rejected).reason)
    }

    @Test
    fun `current Hermes behavior is available through named compatibility adapters`() {
        val registry = registry("capabilities-absent-0c1a9b7e.json")

        assertTrue(registry.compatibilityAdapter(CurrentHermesFeature.SESSION_REHYDRATION).supported)
        assertTrue(registry.compatibilityAdapter(CurrentHermesFeature.FOREGROUND_PROMPTS).supported)
        assertEquals(
            setOf(
                "/api/files",
                "/api/files/read",
                "/api/files/download",
                "/api/fs/read-text",
                "/api/fs/read-data-url",
            ),
            registry.compatibilityAdapter(CurrentHermesFeature.MANAGED_FILES).methods,
        )
        assertFalse(registry.state(HermesBackendCapability.EVENT_REPLAY_V1).isAvailable)
    }

    private fun registry(name: String, profile: String = "work"): CapabilityRegistry {
        val status = json.decodeFromString<StatusResponse>(
            checkNotNull(javaClass.getResource("/fixtures/capabilities/$name")).readText(),
        )
        return CapabilityRegistry.resolve(status.capabilityContract(), profile)
    }

    private fun assertAllStates(name: String, expected: CapabilityStateKind) {
        val registry = registry(name)
        HermesBackendCapability.entries.forEach { capability ->
            assertEquals(expected, registry.state(capability).kind)
        }
    }

    private fun assertProfileScopedStates(name: String, expected: CapabilityStateKind) {
        val registry = registry(name)
        HermesBackendCapability.entries.filter { it.requiresProfileScope }.forEach { capability ->
            assertEquals(expected, registry.state(capability).kind)
        }
    }
}

package com.nousresearch.hermes.protocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityContractParserTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `parses the advertised contract while ignoring future fields`() {
        val status = statusFixture("capabilities-advertised-3f5e8d1a.json")

        val parsed = status.capabilityContract()

        assertTrue(parsed is CapabilityContractParseResult.Valid)
        val document = (parsed as CapabilityContractParseResult.Valid).document
        assertEquals(1, document.schemaVersion)
        assertEquals("work", document.resolvedProfile)
        assertEquals(setOf("session.read", "session.write", "device.manage", "admin.write"), document.scopes)
        assertEquals(
            setOf("session_id", "profile", "cursor"),
            document.capabilities.getValue("event_replay_v1")
                .methods.getValue("session.events.resume").requiredParameters,
        )
    }

    @Test
    fun `missing capabilities remain a valid empty document`() {
        val status = statusFixture("capabilities-absent-0c1a9b7e.json")

        val parsed = status.capabilityContract()

        assertTrue(parsed is CapabilityContractParseResult.Valid)
        assertTrue((parsed as CapabilityContractParseResult.Valid).document.capabilities.isEmpty())
    }

    @Test
    fun `malformed capability payload fails closed without throwing`() {
        val status = statusFixture("capabilities-malformed-2c7e64a1.json")

        val parsed = status.capabilityContract()

        assertTrue(parsed is CapabilityContractParseResult.Malformed)
        assertTrue((parsed as CapabilityContractParseResult.Malformed).reason.isNotBlank())
    }

    @Test
    fun `unsupported schema is classified by direction`() {
        val older = statusFixture("capabilities-older-1e94ad22.json").capabilityContract()
        val newer = statusFixture("capabilities-newer-8f0b21c4.json").capabilityContract()

        assertEquals(
            CapabilityContractCompatibility.OLDER_SERVER,
            (older as CapabilityContractParseResult.Unsupported).compatibility,
        )
        assertEquals(
            CapabilityContractCompatibility.NEWER_SERVER,
            (newer as CapabilityContractParseResult.Unsupported).compatibility,
        )
    }

    @Test
    fun `legacy direct capability map is adapted explicitly`() {
        val status = json.decodeFromString<StatusResponse>(
            """{"status":"ready","capabilities":{"session_replay":true,"future_feature":{"enabled":true}}}""",
        )

        val parsed = status.capabilityContract()

        assertTrue(parsed is CapabilityContractParseResult.Valid)
        assertEquals(
            CapabilityDocumentSource.LEGACY_STATUS,
            (parsed as CapabilityContractParseResult.Valid).document.source,
        )
        assertTrue((parsed as CapabilityContractParseResult.Valid).document.capabilities.isEmpty())
    }

    private fun statusFixture(name: String): StatusResponse = json.decodeFromString(
        checkNotNull(javaClass.getResource("/fixtures/capabilities/$name")).readText(),
    )
}

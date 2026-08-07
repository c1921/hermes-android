package com.nousresearch.hermes.provenance

import com.nousresearch.hermes.security.DiagnosticReportInput
import com.nousresearch.hermes.security.buildDiagnosticReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildProvenanceTest {
    @Test
    fun `current source is generated from the variant build metadata`() {
        val provenance = BuildProvenanceSource.current

        assertEquals("0.2.0-debug", provenance.androidVersion)
        assertEquals(2, provenance.versionCode)
        assertEquals("debug", provenance.channel)
        assertEquals("eaa53de4eb00ac2686438f4d5e4c674158059ba9", provenance.auditedHermesCommit)
        assertEquals("0.20.0", provenance.hermesAgentVersion)
        assertEquals("0.17.0", provenance.hermesDesktopVersion)
        assertEquals("luinbytes", provenance.author)
    }

    @Test
    fun `fallbacks keep local builds honest and identify the project owner`() {
        val provenance = BuildProvenance.from(
            BuildProvenanceInputs(
                androidVersion = "0.2.0",
                versionCode = 2,
                channel = "debug",
                androidCommit = "unknown",
                auditedHermesCommit = "eaa53de4eb00ac2686438f4d5e4c674158059ba9",
                hermesAgentVersion = "0.20.0",
                hermesAgentVersionRange = "=0.20.0",
                hermesDesktopVersion = "0.17.0",
                hermesDesktopVersionRange = "=0.17.0",
                toolchainDigest = "unknown",
                buildIdentity = "local",
                packageName = "com.nousresearch.hermes.debug",
                signingFingerprint = "unsigned",
                author = "",
            ),
        )

        assertEquals("luinbytes", provenance.author)
        assertEquals("unknown", provenance.androidCommit)
        assertEquals("local", provenance.buildIdentity)
        assertEquals("unsigned", provenance.signingFingerprint)
    }

    @Test
    fun `report entries contain the complete generated provenance`() {
        val provenance = BuildProvenance.from(
            BuildProvenanceInputs(
                androidVersion = "0.2.0",
                versionCode = 2,
                channel = "release",
                androidCommit = "a".repeat(40),
                auditedHermesCommit = "eaa53de4eb00ac2686438f4d5e4c674158059ba9",
                hermesAgentVersion = "0.20.0",
                hermesAgentVersionRange = "=0.20.0",
                hermesDesktopVersion = "0.17.0",
                hermesDesktopVersionRange = "=0.17.0",
                toolchainDigest = "sha256:toolchain",
                buildIdentity = "luinbytes/hermes-android#42",
                packageName = "com.nousresearch.hermes",
                signingFingerprint = "sha256:signing",
                author = "luinbytes",
            ),
        )

        val entries = provenance.reportEntries().toMap()

        assertEquals("0.2.0 (2)", entries.getValue("Android version"))
        assertEquals("release", entries.getValue("Channel"))
        assertEquals("sha256:toolchain", entries.getValue("Toolchain digest"))
        assertEquals("sha256:signing", entries.getValue("Signing fingerprint"))
        assertEquals("luinbytes", entries.getValue("Build author"))
        assertTrue(entries.getValue("Android commit").length == 40)
    }

    @Test
    fun `support report consumes the same provenance source`() {
        val provenance = BuildProvenance.from(
            BuildProvenanceInputs(
                androidVersion = "0.2.0",
                versionCode = 2,
                channel = "debug",
                androidCommit = "a".repeat(40),
                auditedHermesCommit = "eaa53de4eb00ac2686438f4d5e4c674158059ba9",
                hermesAgentVersion = "0.20.0",
                hermesAgentVersionRange = "=0.20.0",
                hermesDesktopVersion = "0.17.0",
                hermesDesktopVersionRange = "=0.17.0",
                toolchainDigest = "sha256:toolchain",
                buildIdentity = "local",
                packageName = "com.nousresearch.hermes.debug",
                signingFingerprint = "unsigned",
                author = "luinbytes",
            ),
        )

        val report = buildDiagnosticReport(
            DiagnosticReportInput(
                generatedAt = "2026-08-07T00:00:00Z",
                appVersion = provenance.androidVersion,
                auditedCommit = provenance.auditedHermesCommit,
                backendLabel = null,
                endpoint = null,
                connection = "Offline",
                hermesVersion = null,
                serverState = null,
                authRequired = null,
                desktopContract = null,
                capabilities = null,
                sections = emptyList(),
                provenance = provenance,
            ),
        )

        assertTrue(report.contains("provenance_android_commit: ${"a".repeat(40)}"))
        assertTrue(report.contains("provenance_toolchain_digest: sha256:toolchain"))
        assertTrue(report.contains("provenance_build_author: luinbytes"))
    }
}

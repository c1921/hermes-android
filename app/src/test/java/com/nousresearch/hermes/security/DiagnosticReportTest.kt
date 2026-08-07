package com.nousresearch.hermes.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportTest {
    @Test
    fun `report contains allowlisted support context and redacted diagnostic output`() {
        val report = buildDiagnosticReport(
            DiagnosticReportInput(
                generatedAt = "2026-07-18T15:00:00Z",
                appVersion = "0.2.0-debug",
                auditedCommit = "eaa53de4eb00ac2686438f4d5e4c674158059ba9",
                backendLabel = "Home Hermes",
                endpoint = "https://user:password@example.test/dashboard?token=secret-token",
                connection = "Live / JSON-RPC",
                hermesVersion = "0.18.2",
                serverState = "ok",
                authRequired = true,
                desktopContract = 3,
                capabilities = "{\"voice\":true}",
                sections = listOf(
                    DiagnosticReportSection(
                        title = "Hermes doctor",
                        status = "COMPLETED / EXIT 0",
                        lines = listOf(
                            "provider ready",
                            "Authorization: Bearer live-secret",
                            "api_key=provider-secret",
                            "-----BEGIN PRIVATE KEY-----\nprivate-material\n-----END PRIVATE KEY-----",
                        ),
                    ),
                ),
            ),
        )

        assertTrue(report.contains("Hermes Android diagnostic report"))
        assertTrue(report.contains("Home Hermes"))
        assertTrue(report.contains("0.18.2"))
        assertTrue(report.contains("Hermes doctor"))
        assertTrue(report.contains("provider ready"))
        assertTrue(report.contains("[REDACTED]"))
        assertFalse(report.contains("password"))
        assertFalse(report.contains("secret-token"))
        assertFalse(report.contains("live-secret"))
        assertFalse(report.contains("provider-secret"))
        assertFalse(report.contains("private-material"))
    }

    @Test
    fun `report bounds attacker controlled labels fields and action output`() {
        val report = buildDiagnosticReport(
            DiagnosticReportInput(
                generatedAt = "now\nforged: true",
                appVersion = "v".repeat(5_000),
                auditedCommit = "commit",
                backendLabel = "backend\nforged: true",
                endpoint = null,
                connection = "open",
                hermesVersion = null,
                serverState = null,
                authRequired = null,
                desktopContract = null,
                capabilities = "c".repeat(20_000),
                sections = listOf(
                    DiagnosticReportSection(
                        title = "doctor\nforged",
                        status = "done\nforged",
                        lines = List(1_000) { "line-$it " + "x".repeat(500) },
                    ),
                ),
            ),
        )

        assertTrue(report.length <= 50_000)
        assertFalse(report.contains("\nforged: true"))
        assertFalse(report.contains("doctor\nforged"))
        assertTrue(report.contains("[output truncated]"))
    }
}

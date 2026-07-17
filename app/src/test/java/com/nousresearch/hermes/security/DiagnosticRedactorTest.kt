package com.nousresearch.hermes.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactorTest {
    @Test
    fun `redacts credentials private keys and user home paths`() {
        val input = """
            Authorization: Bearer bearer-value
            api_key=sk-secret-value
            {"refresh_token":"refresh-value","password":"hunter2"}
            https://alice:bad-password@example.test/path?access_token=query-value&mode=doctor
            config: /home/alice/.hermes/config.yaml
            -----BEGIN PRIVATE KEY-----
            private-material
            -----END PRIVATE KEY-----
        """.trimIndent()

        val redacted = DiagnosticRedactor.redact(input)

        listOf("bearer-value", "sk-secret-value", "refresh-value", "hunter2", "bad-password", "query-value", "alice", "private-material")
            .forEach { secret -> assertFalse("Leaked $secret", redacted.contains(secret)) }
        assertTrue(redacted.contains("[REDACTED]"))
        assertTrue(redacted.contains("[REDACTED PRIVATE KEY]"))
        assertTrue(redacted.contains("[REDACTED HOME]"))
    }

    @Test
    fun `bounds rendered diagnostic history`() {
        val lines = (1..450).map { "line-$it" }

        val redacted = DiagnosticRedactor.redactLines(lines)

        assertFalse(redacted.contains("line-1"))
        assertTrue(redacted.contains("line-450"))
        assertTrue(redacted.size <= 400)
    }
}

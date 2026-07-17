package com.nousresearch.hermes.security

/** Defense-in-depth for diagnostics rendered on-device. Hermes remains the authority for server-side redaction. */
object DiagnosticRedactor {
    private const val MAX_OUTPUT_CHARS = 80_000
    private const val MAX_LINES = 400

    private val privateKey = Regex(
        "-----BEGIN(?: [A-Z]+)* PRIVATE KEY-----.*?-----END(?: [A-Z]+)* PRIVATE KEY-----",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val bearer = Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+")
    private val urlCredential = Regex("(?i)(://)[^@/\\s]+@")
    private val querySecret = Regex("(?i)([?&](?:access_token|refresh_token|token|api_key|key)=)[^&\\s]+")
    private val namedSecret = Regex(
        "(?i)((?:api[_-]?key|access[_-]?token|refresh[_-]?token|auth[_-]?token|password|passwd|client[_-]?secret|secret)" +
            "(?:\\\"|'|\\s)*[:=](?:\\\"|'|\\s)*)([^\\s,;\\\"']+)",
    )
    private val unixHome = Regex("(?i)(?:/home|/Users)/[^/\\s]+")

    fun redact(text: String): String {
        var safe = privateKey.replace(text, "[REDACTED PRIVATE KEY]")
        safe = bearer.replace(safe) { it.groupValues[1] + "[REDACTED]" }
        safe = urlCredential.replace(safe) { it.groupValues[1] + "[REDACTED]@" }
        safe = querySecret.replace(safe) { it.groupValues[1] + "[REDACTED]" }
        safe = namedSecret.replace(safe) { it.groupValues[1] + "[REDACTED]" }
        safe = unixHome.replace(safe, "[REDACTED HOME]")
        return if (safe.length <= MAX_OUTPUT_CHARS) safe else "[earlier diagnostic output omitted]\n" + safe.takeLast(MAX_OUTPUT_CHARS)
    }

    fun redactLines(lines: List<String>): List<String> = redact(lines.takeLast(MAX_LINES).joinToString("\n")).lines()
}

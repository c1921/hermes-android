package com.nousresearch.hermes.security

data class DiagnosticReportInput(
    val generatedAt: String,
    val appVersion: String,
    val auditedCommit: String,
    val backendLabel: String?,
    val endpoint: String?,
    val connection: String,
    val hermesVersion: String?,
    val serverState: String?,
    val authRequired: Boolean?,
    val desktopContract: Int?,
    val capabilities: String?,
    val sections: List<DiagnosticReportSection>,
)

data class DiagnosticReportSection(
    val title: String,
    val status: String,
    val lines: List<String>,
)

fun buildDiagnosticReport(input: DiagnosticReportInput): String = buildString {
    appendLine("Hermes Android diagnostic report")
    appendLine("format: 1")
    appendReportValue("generated_at", input.generatedAt)
    appendReportValue("app_version", input.appVersion)
    appendReportValue("audited_upstream", input.auditedCommit)
    appendReportValue("backend", input.backendLabel)
    appendReportValue("endpoint", input.endpoint)
    appendReportValue("connection", input.connection)
    appendReportValue("hermes_version", input.hermesVersion)
    appendReportValue("server_state", input.serverState)
    appendReportValue("authentication_required", input.authRequired?.toString())
    appendReportValue("desktop_contract", input.desktopContract?.toString())
    appendReportValue("capabilities", input.capabilities, MAX_CAPABILITIES_CHARACTERS)

    input.sections.take(MAX_SECTIONS).forEach { section ->
        appendLine()
        appendLine("## ${safeInline(section.title)}")
        appendReportValue("status", section.status)
        appendLine(boundedDiagnosticOutput(section.lines))
    }
}.take(MAX_REPORT_CHARACTERS)

private fun StringBuilder.appendReportValue(label: String, value: String?, limit: Int = MAX_FIELD_CHARACTERS) {
    appendLine("$label: ${safeInline(value ?: "Not reported", limit)}")
}

private fun safeInline(value: String, limit: Int = MAX_FIELD_CHARACTERS): String = DiagnosticRedactor
    .redact(value)
    .replace('\r', ' ')
    .replace('\n', ' ')
    .take(limit)
    .ifBlank { "Not reported" }

private fun boundedDiagnosticOutput(lines: List<String>): String {
    val redacted = DiagnosticRedactor.redact(lines.takeLast(MAX_SECTION_LINES).joinToString("\n"))
    val bounded = if (redacted.length <= MAX_SECTION_OUTPUT_CHARACTERS) {
        redacted
    } else {
        OUTPUT_TRUNCATED + redacted.takeLast(MAX_SECTION_OUTPUT_CHARACTERS - OUTPUT_TRUNCATED.length)
    }
    return bounded.ifBlank { "No output captured" }
}

private const val MAX_FIELD_CHARACTERS = 1_000
private const val MAX_CAPABILITIES_CHARACTERS = 4_000
private const val MAX_SECTIONS = 2
private const val MAX_SECTION_LINES = 200
private const val MAX_SECTION_OUTPUT_CHARACTERS = 15_000
private const val MAX_REPORT_CHARACTERS = 50_000
private const val OUTPUT_TRUNCATED = "[output truncated]\n"

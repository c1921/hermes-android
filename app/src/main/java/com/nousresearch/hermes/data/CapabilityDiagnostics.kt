package com.nousresearch.hermes.data

import com.nousresearch.hermes.domain.CapabilityRegistry

/** Redacted, bounded data for Diagnostics and support export. */
data class CapabilityDiagnosticEntry(
    val capability: String,
    val status: String,
    val profile: String?,
    val reason: String,
    val fallback: String,
)

data class CapabilityDiagnostics(
    val profile: String?,
    val entries: List<CapabilityDiagnosticEntry>,
) {
    fun render(): String = buildString {
        appendLine("profile: ${profile ?: "Not resolved"}")
        entries.take(MAX_ENTRIES).forEach { entry ->
            appendLine("${entry.capability}: ${entry.status}")
            appendLine("reason: ${safe(entry.reason)}")
            appendLine("fallback: ${safe(entry.fallback)}")
        }
    }.take(MAX_RENDERED_CHARACTERS)

    companion object {
        const val MAX_RENDERED_CHARACTERS = 8_000
        private const val MAX_ENTRIES = 16

        fun from(registry: CapabilityRegistry): CapabilityDiagnostics = CapabilityDiagnostics(
            profile = registry.resolvedProfile,
            entries = registry.states.values.map { state ->
                CapabilityDiagnosticEntry(
                    capability = state.capability.wireName,
                    status = state.kind.name.lowercase(),
                    profile = state.profile,
                    reason = state.reason,
                    fallback = state.fallback,
                )
            },
        )

        private fun safe(value: String): String = value
            .replace('\r', ' ')
            .replace('\n', ' ')
            .take(600)
            .ifBlank { "Not reported" }
    }
}

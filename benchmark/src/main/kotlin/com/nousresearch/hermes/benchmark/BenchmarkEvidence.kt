package com.nousresearch.hermes.benchmark

data class BenchmarkEvidence(
    val benchmark: String,
    val commit: String,
    val device: String,
    val androidApi: Int,
    val toolchain: String,
    val profileState: String,
    val repetitions: Int,
    val environment: Map<String, String> = emptyMap(),
    val metrics: Map<String, Double> = emptyMap(),
) {
    init {
        require(androidApi > 0) { "androidApi must be positive" }
        require(repetitions > 0) { "repetitions must be positive" }
        require(metrics.values.all { it.isFinite() && it >= 0.0 }) {
            "metrics must contain finite, non-negative values"
        }
    }

    fun toJsonLine(): String = buildString {
        append('{')
        appendJsonString("benchmark", benchmark)
        append(',')
        appendJsonString("commit", commit)
        append(',')
        appendJsonString("device", device)
        append(",\"androidApi\":").append(androidApi)
        append(',')
        appendJsonString("toolchain", toolchain)
        append(',')
        appendJsonString("profileState", profileState)
        append(",\"repetitions\":").append(repetitions)
        append(",\"environment\":")
        appendJsonMap(environment)
        append(",\"metrics\":")
        appendJsonMap(metrics)
        append('}')
    }

    private fun StringBuilder.appendJsonString(name: String, value: String) {
        append('"').append(name).append("\":\"").append(value.jsonEscaped()).append('"')
    }

    private fun StringBuilder.appendJsonMap(values: Map<String, *>) {
        append('{')
        values.toSortedMap().entries.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            append('"').append(key.jsonEscaped()).append("\":")
            when (value) {
                is Number -> append(value)
                is String -> append('"').append(value.jsonEscaped()).append('"')
                else -> error("Unsupported JSON value: ${value?.let { it::class.simpleName }}")
            }
        }
        append('}')
    }
}

private fun String.jsonEscaped(): String = buildString(length) {
    for (character in this@jsonEscaped) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) {
                append("\\u").append(character.code.toString(16).padStart(4, '0'))
            } else {
                append(character)
            }
        }
    }
}

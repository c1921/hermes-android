package com.nousresearch.hermes.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ToolPresentation(
    val title: String,
    val summary: String?,
    val state: ToolState,
    val transcript: String,
)

@OptIn(ExperimentalSerializationApi::class)
private val toolTranscriptJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

fun TimelineItem.Tool.presentation(includeTranscript: Boolean = true): ToolPresentation {
    val title = name.humanToolName()
    val shortSummary = summary
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.take(120)

    return ToolPresentation(
        title = title,
        summary = shortSummary,
        state = state,
        transcript = if (includeTranscript) beautifyToolTranscript(detail.orEmpty()) else "",
    )
}

internal fun beautifyToolTranscript(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    if (trimmed.first() !in setOf('{', '[', '"')) return raw
    val parsed = runCatching { Json.parseToJsonElement(trimmed) }.getOrNull() ?: return raw
    if (parsed is JsonPrimitive && parsed.isString) return parsed.content
    if (parsed !is JsonObject) return toolTranscriptJson.encodeToString(JsonElement.serializer(), parsed)

    val transcript = buildList {
        parsed["output"]?.takeUnless { it is JsonNull }?.let { output ->
            add("Output\n${output.displayPrimitiveOrJson()}")
        }
        parsed["exit_code"]?.takeUnless { it is JsonNull }?.let { exitCode ->
            add("Exit code\n${exitCode.displayPrimitiveOrJson()}")
        }
        parsed["error"]?.takeUnless { it is JsonNull }?.let { error ->
            add("Error\n${error.displayPrimitiveOrJson()}")
        }
        val remaining = JsonObject(parsed.filterKeys { it !in setOf("output", "exit_code", "error") })
        if (remaining.isNotEmpty()) {
            add("Details\n${toolTranscriptJson.encodeToString(JsonElement.serializer(), remaining)}")
        }
    }
    return transcript.joinToString("\n\n").ifBlank {
        toolTranscriptJson.encodeToString(JsonElement.serializer(), parsed)
    }
}

private fun JsonElement.displayPrimitiveOrJson(): String =
    (this as? JsonPrimitive)?.contentOrNull
        ?: toolTranscriptJson.encodeToString(JsonElement.serializer(), this)

private fun String.humanToolName(): String =
    trim()
        .ifBlank { "Tool" }
        .replace(Regex("[_./:-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .lowercase()
        .replaceFirstChar(Char::titlecase)

package com.nousresearch.hermes.data

import com.nousresearch.hermes.protocol.ServerConfigSchemaResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class ServerConfigType { BOOLEAN, NUMBER, SELECT, STRING, TEXT }

data class ServerConfigField(
    val key: String,
    val category: String,
    val description: String,
    val type: ServerConfigType,
    val options: List<String>,
    val value: JsonElement,
)

data class ServerConfigSnapshot(
    val categories: List<String> = emptyList(),
    val fields: List<ServerConfigField> = emptyList(),
)

private val CONFIG_SEGMENT = Regex("[A-Za-z0-9_-]{1,64}")
private val SENSITIVE_SEGMENT = Regex("(?:password|passwd|token|secret|api[_-]?key|credential|private[_-]?key|cookie)", RegexOption.IGNORE_CASE)
private val SAFE_CONFIG_KEYS = setOf(
    "timezone",
    "display.personality",
    "display.show_reasoning",
    "agent.image_input_mode",
    "approvals.timeout",
    "checkpoints.enabled",
    "checkpoints.max_snapshots",
    "file_read_max_chars",
    "memory.memory_enabled",
    "memory.user_profile_enabled",
    "memory.memory_char_limit",
    "memory.user_char_limit",
    "memory.provider",
    "context.engine",
    "compression.enabled",
    "compression.threshold",
    "compression.target_ratio",
    "compression.protect_last_n",
    "voice.auto_tts",
    "voice.record_key",
    "voice.max_recording_seconds",
    "tts.provider",
    "tts.edge.voice",
    "tts.openai.model",
    "tts.openai.voice",
    "tts.elevenlabs.voice_id",
    "tts.elevenlabs.model_id",
    "tts.xai.voice_id",
    "tts.xai.language",
    "tts.minimax.model",
    "tts.minimax.voice_id",
    "tts.mistral.model",
    "tts.mistral.voice_id",
    "tts.gemini.model",
    "tts.gemini.voice",
    "tts.neutts.model",
    "tts.neutts.device",
    "tts.kittentts.model",
    "tts.kittentts.voice",
    "tts.piper.voice",
    "stt.enabled",
    "stt.echo_transcripts",
    "stt.provider",
    "stt.local.model",
    "stt.local.language",
    "stt.openai.model",
    "stt.groq.model",
    "stt.mistral.model",
    "stt.elevenlabs.model_id",
    "stt.elevenlabs.language_code",
    "stt.elevenlabs.tag_audio_events",
    "stt.elevenlabs.diarize",
    "logging.level",
    "tool_output.max_bytes",
    "tool_output.max_lines",
    "tool_output.max_line_length",
    "agent.max_turns",
    "agent.api_max_retries",
    "agent.service_tier",
    "agent.tool_use_enforcement",
    "delegation.model",
    "delegation.provider",
    "delegation.max_iterations",
    "delegation.max_concurrent_children",
    "delegation.child_timeout_seconds",
    "delegation.reasoning_effort",
)

fun parseServerConfig(schema: ServerConfigSchemaResponse, config: JsonObject): ServerConfigSnapshot {
    val fields = schema.fields.mapNotNull { (key, fieldSchema) ->
        val type = fieldSchema.type.toServerConfigType() ?: return@mapNotNull null
        if (!isSafeServerConfigKey(key)) return@mapNotNull null
        val value = nestedConfigValue(config, key) ?: return@mapNotNull null
        val options = fieldSchema.options.mapNotNull { option -> option.content.takeIf { it.length <= MAX_VALUE_CHARACTERS } }
        val candidate = ServerConfigField(
            key = key,
            category = fieldSchema.category.takeIf { it.isNotBlank() && it.length <= MAX_CATEGORY_CHARACTERS } ?: "general",
            description = fieldSchema.description.take(MAX_DESCRIPTION_CHARACTERS),
            type = type,
            options = options.distinct().take(MAX_OPTIONS),
            value = value,
        )
        runCatching { candidate.copy(value = validateServerConfigValue(candidate, value)) }.getOrNull()
    }.sortedWith(compareBy<ServerConfigField> { it.category }.thenBy { it.key })

    val present = fields.map(ServerConfigField::category).toSet()
    val categories = (schema.categoryOrder + fields.map(ServerConfigField::category))
        .filter { it in present }
        .distinct()
    val order = categories.withIndex().associate { it.value to it.index }
    return ServerConfigSnapshot(
        categories = categories,
        fields = fields.sortedWith(compareBy<ServerConfigField> { order[it.category] ?: Int.MAX_VALUE }.thenBy { it.key }),
    )
}

fun buildServerConfigPatch(key: String, value: JsonElement): JsonObject {
    require(isSafeServerConfigKey(key)) { "This Hermes configuration field cannot be edited safely" }
    val segments = key.split('.')
    var nested: JsonElement = value
    for (segment in segments.asReversed()) {
        nested = buildJsonObject { put(segment, nested) }
    }
    return nested.jsonObject
}

fun validateServerConfigValue(field: ServerConfigField, value: JsonElement): JsonElement = when (field.type) {
    ServerConfigType.BOOLEAN -> {
        require(value is JsonPrimitive && value.booleanOrNull != null) { "${field.key} requires a boolean" }
        value
    }
    ServerConfigType.NUMBER -> {
        val number = (value as? JsonPrimitive)?.doubleOrNull
        require(number != null && number.isFinite() && value.content.length <= MAX_NUMBER_CHARACTERS) {
            "${field.key} requires a finite number"
        }
        value
    }
    ServerConfigType.SELECT -> {
        val selected = (value as? JsonPrimitive)?.content
        require(selected != null && selected in field.options) { "${field.key} requires an advertised option" }
        value
    }
    ServerConfigType.STRING, ServerConfigType.TEXT -> {
        val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        require(text != null && text.length <= MAX_VALUE_CHARACTERS) { "${field.key} requires bounded text" }
        value
    }
}

private fun String.toServerConfigType(): ServerConfigType? = when (lowercase()) {
    "boolean", "bool" -> ServerConfigType.BOOLEAN
    "number" -> ServerConfigType.NUMBER
    "select" -> ServerConfigType.SELECT
    "string" -> ServerConfigType.STRING
    "text" -> ServerConfigType.TEXT
    else -> null
}

private fun isSafeServerConfigKey(key: String): Boolean {
    val segments = key.split('.')
    return key.length <= MAX_KEY_CHARACTERS &&
        segments.size in 1..MAX_KEY_SEGMENTS &&
        segments.all(CONFIG_SEGMENT::matches) &&
        segments.none(SENSITIVE_SEGMENT::containsMatchIn) &&
        key in SAFE_CONFIG_KEYS
}

private fun nestedConfigValue(config: JsonObject, key: String): JsonElement? {
    var current: JsonElement = config
    for (segment in key.split('.')) {
        current = (current as? JsonObject)?.get(segment) ?: return null
    }
    return current
}

private const val MAX_KEY_CHARACTERS = 256
private const val MAX_KEY_SEGMENTS = 8
private const val MAX_CATEGORY_CHARACTERS = 64
private const val MAX_DESCRIPTION_CHARACTERS = 500
private const val MAX_VALUE_CHARACTERS = 4_096
private const val MAX_NUMBER_CHARACTERS = 64
private const val MAX_OPTIONS = 100

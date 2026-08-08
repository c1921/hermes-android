package com.nousresearch.hermes.domain

import com.nousresearch.hermes.protocol.ManagedFileEntry
import com.nousresearch.hermes.protocol.ProtocolMessage
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** The only provenance a detector is allowed to assign. This is not a server artifact record. */
enum class DetectedArtifactProvenance { DETECTED }

enum class DetectedArtifactKind { IMAGE, FILE, LINK, CODE, HTML, SVG }

enum class DetectedArtifactSource {
    MESSAGE_TEXT,
    MEDIA_MARKER,
    INLINE_IMAGE,
    FENCED_CODE,
    GENERATED_IMAGE,
    TOOL_OUTPUT,
    MANAGED_FILE_FALLBACK,
}

/** Scope used for identity. Values are intentionally kept out of the public id. */
data class DetectedArtifactScope(
    val backendId: String,
    val profileId: String,
    val sessionId: String,
)

/** A pointer back into the shipped session/message shape, without copying secret content into identity fields. */
data class DetectedArtifactOrigin(
    val backendId: String,
    val profileId: String,
    val sessionId: String,
    val messageId: String? = null,
    val partId: String? = null,
    val turnId: String? = null,
)

/**
 * An artifact found in already-loaded transcript data. The descriptor is deliberately not a canonical
 * inventory entry; callers must keep server inventory capability gates separate from this fallback.
 */
data class DetectedArtifact(
    val id: String,
    val kind: DetectedArtifactKind,
    val value: String,
    val label: String,
    val mimeType: String? = null,
    val href: String? = null,
    val provenance: DetectedArtifactProvenance = DetectedArtifactProvenance.DETECTED,
    val source: DetectedArtifactSource,
    val origin: DetectedArtifactOrigin,
)

object DetectedArtifactLimits {
    const val MAX_MESSAGES = 512
    const val MAX_MANAGED_FILES = 256
    const val MAX_ARTIFACTS = 256
    const val MAX_INPUT_CHARACTERS = 64_000
    const val MAX_TOOL_RESULT_CHARACTERS = 64_000
    const val MAX_VALUE_CHARACTERS = 16_384
    const val MAX_CODE_CHARACTERS = 32_768
    const val MAX_INLINE_IMAGE_CHARACTERS = 2_000_000
    const val MAX_LABEL_CHARACTERS = 160
}

/**
 * Pure transcript detector for the shapes emitted by Hermes Desktop. It does not inspect arbitrary
 * JSON keys and never turns a local path into a file:// URL.
 */
object DetectedArtifactRepository {
    fun detect(
        scope: DetectedArtifactScope,
        messages: List<ProtocolMessage>,
        managedFiles: List<ManagedFileEntry> = emptyList(),
    ): List<DetectedArtifact> = detect(scope.backendId, scope.profileId, scope.sessionId, messages, managedFiles)

    fun detect(
        backendId: String,
        profileId: String,
        sessionId: String,
        messages: List<ProtocolMessage>,
        managedFiles: List<ManagedFileEntry> = emptyList(),
    ): List<DetectedArtifact> {
        val normalizedScope = DetectedArtifactScope(
            backendId.trim(),
            profileId.trim(),
            sessionId.trim(),
        )
        require(normalizedScope.backendId.isNotEmpty()) { "Artifact backend scope is required" }
        require(normalizedScope.profileId.isNotEmpty()) { "Artifact profile scope is required" }
        require(normalizedScope.sessionId.isNotEmpty()) { "Artifact session scope is required" }
        val found = LinkedHashMap<String, DetectedArtifact>()
        val boundedMessages = messages.take(DetectedArtifactLimits.MAX_MESSAGES)
        val generatedImages = completedGeneratedImages(boundedMessages)
        val generatedEchoes = completedGeneratedImageEchoes(boundedMessages)

        boundedMessages.forEachIndexed { messageIndex, message ->
            if (found.size >= DetectedArtifactLimits.MAX_ARTIFACTS) return@forEachIndexed
            val messageId = message.id?.trim()?.takeIf(String::isNotEmpty)
            val messagePartPrefix = messageId ?: "message:$messageIndex"
            val textSource = if (message.role.equals("tool", ignoreCase = true)) {
                DetectedArtifactSource.TOOL_OUTPUT
            } else {
                DetectedArtifactSource.MESSAGE_TEXT
            }

            textFragments(message).forEach { fragment ->
                if (found.size >= DetectedArtifactLimits.MAX_ARTIFACTS) return@forEach
                val text = if (message.role.equals("assistant", ignoreCase = true)) {
                    generatedEchoes
                        .filterKeys { completedAt -> completedAt < messageIndex }
                        .values
                        .flatten()
                        .fold(fragment.text) { remaining, generated -> remaining.replace(generated, "") }
                } else {
                    fragment.text
                }
                detectText(
                    text,
                    normalizedScope,
                    DetectedArtifactOrigin(
                        backendId = normalizedScope.backendId,
                        profileId = normalizedScope.profileId,
                        sessionId = normalizedScope.sessionId,
                        messageId = messageId,
                        partId = "$messagePartPrefix:${fragment.partId}",
                        turnId = null,
                    ),
                    textSource,
                    allowImageDirective = message.role.equals("user", ignoreCase = true),
                    found,
                )
            }

            generatedImages[messageIndex].orEmpty().forEach { generated ->
                if (found.size >= DetectedArtifactLimits.MAX_ARTIFACTS) return@forEach
                addImageCandidate(
                    generated.value,
                    normalizedScope,
                    DetectedArtifactOrigin(
                        backendId = normalizedScope.backendId,
                        profileId = normalizedScope.profileId,
                        sessionId = normalizedScope.sessionId,
                        messageId = messageId,
                        partId = "$messagePartPrefix:tool-call:${generated.callId}",
                        turnId = null,
                    ),
                    DetectedArtifactSource.GENERATED_IMAGE,
                    found,
                )
            }
        }

        managedFiles.take(DetectedArtifactLimits.MAX_MANAGED_FILES).forEachIndexed { index, entry ->
            if (found.size >= DetectedArtifactLimits.MAX_ARTIFACTS || entry.isDirectory) return@forEachIndexed
            val path = normalizePath(entry.path) ?: return@forEachIndexed
            val kind = kindForPath(path, entry.mimeType)
            addCandidate(
                kind = kind,
                value = path,
                label = entry.name.trim().takeIf(String::isNotEmpty)?.take(DetectedArtifactLimits.MAX_LABEL_CHARACTERS)
                    ?: displayLabel(path),
                mimeType = safeMimeType(entry.mimeType) ?: mimeFor(kind, path),
                href = null,
                scope = normalizedScope,
                origin = DetectedArtifactOrigin(
                    backendId = normalizedScope.backendId,
                    profileId = normalizedScope.profileId,
                    sessionId = normalizedScope.sessionId,
                    partId = "managed:$index",
                ),
                source = DetectedArtifactSource.MANAGED_FILE_FALLBACK,
                found = found,
            )
        }
        return found.values.toList()
    }
}

/** Short facade for callers that do not need to retain the repository terminology. */
object DetectedArtifactDetector {
    fun detect(
        scope: DetectedArtifactScope,
        messages: List<ProtocolMessage>,
        managedFiles: List<ManagedFileEntry> = emptyList(),
    ): List<DetectedArtifact> = DetectedArtifactRepository.detect(scope, messages, managedFiles)
}

fun detectDetectedArtifacts(
    scope: DetectedArtifactScope,
    messages: List<ProtocolMessage>,
    managedFiles: List<ManagedFileEntry> = emptyList(),
): List<DetectedArtifact> = DetectedArtifactRepository.detect(scope, messages, managedFiles)

private data class TextFragment(val partId: String, val text: String)

private data class GeneratedImage(val callId: String, val value: String)

private fun textFragments(message: ProtocolMessage): List<TextFragment> {
    val result = ArrayList<TextFragment>()
    when (val content = message.content) {
        is JsonPrimitive -> addPrimitiveFragment(result, "content", content.contentOrNull, message.role)
        is JsonArray -> content.forEachIndexed { index, part ->
            val objectPart = part as? JsonObject ?: return@forEachIndexed
            val type = objectPart.string("type").lowercase()
            if (type in TEXT_PART_TYPES) {
                objectPart.string("text").takeIf(String::isNotBlank)?.let {
                    result += TextFragment("content:$index", it)
                }
            }
        }
        is JsonObject -> {
            // Structured message objects are not recursively walked. Only the shipped text field is prose.
            content.string("text").takeIf(String::isNotBlank)?.let {
                result += TextFragment("content", it)
            }
            if (message.role.equals("tool", ignoreCase = true)) {
                content.string("output").takeIf(String::isNotBlank)?.let {
                    result += TextFragment("output", it)
                }
            }
        }
        null -> Unit
    }
    addPrimitiveFragment(result, "text", message.text, message.role)
    return result
}

private fun addPrimitiveFragment(
    result: MutableList<TextFragment>,
    partId: String,
    raw: String?,
    role: String,
) {
    val text = raw?.takeIf(String::isNotBlank) ?: return
    val parsed = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(text) }.getOrNull()
    if (parsed is JsonObject || parsed is JsonArray) {
        // JSON objects are not prose. The one shipped tool-result prose field is output.
        if (role.equals("tool", ignoreCase = true)) {
            (parsed as? JsonObject)?.string("output")?.takeIf(String::isNotBlank)?.let {
                result += TextFragment("$partId:output", it)
            }
        }
        return
    }
    result += TextFragment(partId, text)
}

private fun detectText(
    raw: String,
    scope: DetectedArtifactScope,
    origin: DetectedArtifactOrigin,
    defaultSource: DetectedArtifactSource,
    allowImageDirective: Boolean,
    found: LinkedHashMap<String, DetectedArtifact>,
) {
    val bounded = raw.take(DetectedArtifactLimits.MAX_INPUT_CHARACTERS)
    if (bounded.isBlank()) return

    FENCED_CODE_RE.findAll(bounded).forEachIndexed { index, match ->
        val body = match.groupValues.getOrNull(2).orEmpty().trim()
        if (body.isBlank() || body.length > DetectedArtifactLimits.MAX_CODE_CHARACTERS) return@forEachIndexed
        val language = sanitizeLanguageTag(match.groupValues.getOrNull(1).orEmpty())
        val kind = classifyFence(language, body) ?: return@forEachIndexed
        addCandidate(
            kind = kind,
            value = body,
            label = when (kind) {
                DetectedArtifactKind.HTML -> "HTML snippet"
                DetectedArtifactKind.SVG -> "SVG image"
                else -> language.takeIf(String::isNotBlank)?.let { "$it code" } ?: "Code snippet"
            },
            mimeType = mimeForFence(kind),
            href = null,
            scope = scope,
            origin = origin.copy(partId = "${origin.partId}:fence:$index"),
            source = DetectedArtifactSource.FENCED_CODE,
            found = found,
        )
    }

    detectInlineImages(bounded).forEachIndexed { index, value ->
        addImageCandidate(
            value,
            scope,
            origin.copy(partId = "${origin.partId}:inline:$index"),
            DetectedArtifactSource.INLINE_IMAGE,
            found,
        )
    }

    MEDIA_RE.findAll(bounded).forEachIndexed { index, match ->
        addMediaCandidate(
            match.groupValues.getOrNull(1).orEmpty(),
            scope,
            origin.copy(partId = "${origin.partId}:media:$index"),
            found,
        )
    }

    MEDIA_HREF_RE.findAll(bounded).forEachIndexed { index, match ->
        decodeMediaPath(match.groupValues.getOrNull(1).orEmpty())?.let { path ->
            addMediaCandidate(
                path,
                scope,
                origin.copy(partId = "${origin.partId}:media-href:$index"),
                found,
            )
        }
    }

    if (allowImageDirective) {
        IMAGE_DIRECTIVE_RE.findAll(bounded).forEachIndexed { index, match ->
            addImageCandidate(
                match.groupValues.getOrNull(1).orEmpty(),
                scope,
                origin.copy(partId = "${origin.partId}:image:$index"),
                DetectedArtifactSource.MEDIA_MARKER,
                found,
            )
        }
    }

    MARKDOWN_LINK_RE.findAll(bounded).forEachIndexed { index, match ->
        val value = unquote(match.groupValues.getOrNull(2).orEmpty()) ?: return@forEachIndexed
        addUrlOrKnownPath(
            value,
            scope,
            origin.copy(partId = "${origin.partId}:link:$index"),
            defaultSource,
            found,
            imageHint = match.value.startsWith("!"),
        )
    }

    HTTP_URL_RE.findAll(bounded).forEachIndexed { index, match ->
        val value = trimUrlPunctuation(match.value)
        addUrlOrKnownPath(
            value,
            scope,
            origin.copy(partId = "${origin.partId}:url:$index"),
            defaultSource,
            found,
        )
    }
}

private fun detectInlineImages(text: String): List<String> {
    val values = ArrayList<String>()
    var searchFrom = 0
    while (searchFrom < text.length && values.size < DetectedArtifactLimits.MAX_ARTIFACTS) {
        val marker = DATA_IMAGE_MARKER_RE.find(text, searchFrom) ?: break
        val start = marker.range.first
        var end = marker.range.last + 1
        while (end < text.length && !text[end].isWhitespace() && text[end] !in setOf(')', ']', '>', '"', '\'', '`')) {
            end += 1
        }
        val candidate = text.substring(start, end)
        val bounded = candidate.length <= DetectedArtifactLimits.MAX_INLINE_IMAGE_CHARACTERS &&
            DATA_IMAGE_RE.matches(candidate)
        // A bounded source ending in the middle of a data URL is intentionally not accepted.
        val complete = end < text.length || text.length < DetectedArtifactLimits.MAX_INPUT_CHARACTERS
        if (bounded && complete) values += candidate
        searchFrom = maxOf(end, start + 1)
    }
    return values
}

private fun addUrlOrKnownPath(
    raw: String,
    scope: DetectedArtifactScope,
    origin: DetectedArtifactOrigin,
    source: DetectedArtifactSource,
    found: LinkedHashMap<String, DetectedArtifact>,
    imageHint: Boolean = false,
) {
    val value = raw.trim().takeIf(String::isNotEmpty) ?: return
    safeHttpUrl(value)?.let { url ->
        val kind = when {
            isSvgPath(url) -> DetectedArtifactKind.SVG
            imageHint || isImagePath(url) -> DetectedArtifactKind.IMAGE
            else -> DetectedArtifactKind.LINK
        }
        addCandidate(
            kind = kind,
            value = url,
            label = displayLabel(url),
            mimeType = mimeFor(kind, url),
            href = url,
            scope = scope,
            origin = origin,
            source = source,
            found = found,
        )
        return
    }
    if (!imageHint) return
    val path = normalizePath(value) ?: return
    addCandidate(
        kind = if (isSvgPath(path)) DetectedArtifactKind.SVG else DetectedArtifactKind.IMAGE,
        value = path,
        label = displayLabel(path),
        mimeType = mimeFor(DetectedArtifactKind.IMAGE, path),
        href = null,
        scope = scope,
        origin = origin,
        source = source,
        found = found,
    )
}

private fun addMediaCandidate(
    raw: String,
    scope: DetectedArtifactScope,
    origin: DetectedArtifactOrigin,
    found: LinkedHashMap<String, DetectedArtifact>,
) {
    val value = unquote(raw).orEmpty().trim()
    safeHttpUrl(value)?.let { url ->
        val kind = kindForPath(url, null)
        addCandidate(
            kind = kind,
            value = url,
            label = displayLabel(url),
            mimeType = mimeFor(kind, url),
            href = url,
            scope = scope,
            origin = origin,
            source = DetectedArtifactSource.MEDIA_MARKER,
            found = found,
        )
        return
    }
    if (DATA_MEDIA_RE.matches(value) && value.length <= DetectedArtifactLimits.MAX_INLINE_IMAGE_CHARACTERS) {
        val mimeType = value.substringAfter("data:").substringBefore(';').lowercase()
        val kind = when {
            mimeType == "image/svg+xml" -> DetectedArtifactKind.SVG
            mimeType.startsWith("image/") -> DetectedArtifactKind.IMAGE
            else -> DetectedArtifactKind.FILE
        }
        addCandidate(
            kind = kind,
            value = value,
            label = "Inline ${mimeType.substringBefore('/')}",
            mimeType = mimeType,
            href = null,
            scope = scope,
            origin = origin,
            source = DetectedArtifactSource.MEDIA_MARKER,
            found = found,
        )
        return
    }
    val path = normalizePath(value) ?: return
    val kind = kindForPath(path, null)
    addCandidate(
        kind = kind,
        value = path,
        label = displayLabel(path),
        mimeType = mimeFor(kind, path),
        href = null,
        scope = scope,
        origin = origin,
        source = DetectedArtifactSource.MEDIA_MARKER,
        found = found,
    )
}

private fun addImageCandidate(
    raw: String,
    scope: DetectedArtifactScope,
    origin: DetectedArtifactOrigin,
    source: DetectedArtifactSource,
    found: LinkedHashMap<String, DetectedArtifact>,
) {
    val value = unquote(raw)?.trim()?.takeIf(String::isNotEmpty) ?: return
    if (value.startsWith("data:image/", ignoreCase = true)) {
        if (value.length > DetectedArtifactLimits.MAX_INLINE_IMAGE_CHARACTERS || !DATA_IMAGE_RE.matches(value)) return
        val mimeType = value.substringAfter("data:").substringBefore(';').lowercase()
        val kind = if (mimeType == "image/svg+xml") DetectedArtifactKind.SVG else DetectedArtifactKind.IMAGE
        addCandidate(
            kind = kind,
            value = value,
            label = if (kind == DetectedArtifactKind.SVG) "Inline SVG" else "Inline image",
            mimeType = mimeType,
            href = value,
            scope = scope,
            origin = origin,
            source = source,
            found = found,
        )
        return
    }
    val path = normalizePath(value) ?: safeHttpUrl(value) ?: return
    val safeUrl = safeHttpUrl(path)
    val kind = if (isSvgPath(path)) DetectedArtifactKind.SVG else DetectedArtifactKind.IMAGE
    addCandidate(
        kind = kind,
        value = path,
        label = displayLabel(path),
        mimeType = mimeFor(DetectedArtifactKind.IMAGE, path),
        href = safeUrl,
        scope = scope,
        origin = origin,
        source = source,
        found = found,
    )
}

private fun addCandidate(
    kind: DetectedArtifactKind,
    value: String,
    label: String,
    mimeType: String?,
    href: String?,
    scope: DetectedArtifactScope,
    origin: DetectedArtifactOrigin,
    source: DetectedArtifactSource,
    found: LinkedHashMap<String, DetectedArtifact>,
) {
    if (found.size >= DetectedArtifactLimits.MAX_ARTIFACTS) return
    val boundedValue = value.takeIf { it.isNotBlank() && it.length <= valueLimit(kind, it) } ?: return
    if (boundedValue.contains(Char.MIN_VALUE)) return
    if (found.values.any { it.value == boundedValue }) return
    val safeHref = href?.let(::safeHttpUrl)
    val id = detectedArtifactId(scope, origin.messageId ?: origin.partId, kind, boundedValue)
    found.putIfAbsent(
        id,
        DetectedArtifact(
            id = id,
            kind = kind,
            value = boundedValue,
            label = label.trim().ifBlank { displayLabel(boundedValue) }.take(DetectedArtifactLimits.MAX_LABEL_CHARACTERS),
            mimeType = safeMimeType(mimeType),
            href = safeHref,
            provenance = DetectedArtifactProvenance.DETECTED,
            source = source,
            origin = origin,
        ),
    )
}

private fun completedGeneratedImages(messages: List<ProtocolMessage>): Map<Int, List<GeneratedImage>> {
    val resultsByCallId = HashMap<String, String>()
    messages.forEach { message ->
        if (!message.role.equals("tool", ignoreCase = true)) return@forEach
        val record = toolResultRecord(message) ?: return@forEach
        val callId = message.toolCallId.orEmpty()
            .ifBlank { record.string("tool_call_id") }
            .ifBlank { record.string("toolCallId") }
        val toolName = message.toolName.orEmpty()
            .ifBlank { record.string("tool_name") }
            .ifBlank { record.string("toolName") }
        if (callId.isBlank() || !toolName.equals("image_generate", ignoreCase = true)) return@forEach
        val result = (record["result"] as? JsonObject) ?: record
        if (record.boolean("success") == false || result.boolean("success") == false) return@forEach
        val image = IMAGE_RESULT_KEYS.asSequence().map { result.string(it) }.firstOrNull(String::isNotBlank) ?: return@forEach
        resultsByCallId.putIfAbsent(callId, image)
    }

    val completed = HashMap<Int, MutableList<GeneratedImage>>()
    messages.forEachIndexed { index, message ->
        if (!message.role.equals("assistant", ignoreCase = true)) return@forEachIndexed
        val calls = message.toolCalls as? JsonArray ?: return@forEachIndexed
        calls.forEach { element ->
            val call = element as? JsonObject ?: return@forEach
            val callId = call.string("id").ifBlank { call.string("tool_call_id") }
            val function = call["function"] as? JsonObject
            val toolName = function.string("name").ifBlank { call.string("tool_name") }.ifBlank { call.string("name") }
            if (callId.isBlank() || !toolName.equals("image_generate", ignoreCase = true)) return@forEach
            resultsByCallId[callId]?.let { value ->
                completed.getOrPut(index) { ArrayList() }.add(GeneratedImage(callId, value))
            }
        }
    }
    return completed
}

private fun completedGeneratedImageEchoes(messages: List<ProtocolMessage>): Map<Int, List<String>> = buildMap {
    messages.forEachIndexed { index, message ->
        if (!message.role.equals("tool", ignoreCase = true)) return@forEachIndexed
        val record = toolResultRecord(message) ?: return@forEachIndexed
        val toolName = message.toolName.orEmpty()
            .ifBlank { record.string("tool_name") }
            .ifBlank { record.string("toolName") }
        if (!toolName.equals("image_generate", ignoreCase = true)) return@forEachIndexed
        val result = (record["result"] as? JsonObject) ?: record
        if (record.boolean("success") == false || result.boolean("success") == false) return@forEachIndexed
        val values = IMAGE_ECHO_KEYS.map { result.string(it) }.filter(String::isNotBlank).distinct()
        if (values.isNotEmpty()) put(index, values)
    }
}

private fun toolResultRecord(message: ProtocolMessage): JsonObject? {
    val content = message.content
    if (content is JsonObject) return content
    val raw = when (content) {
        is JsonPrimitive -> content.contentOrNull
        else -> message.text
    }?.trim().orEmpty()
    if (!raw.startsWith("{")) return null
    return runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(raw) as? JsonObject
    }.getOrNull()
}

private fun detectedArtifactId(
    scope: DetectedArtifactScope,
    messageId: String?,
    kind: DetectedArtifactKind,
    content: String,
): String {
    val input = listOf(
        "hermes-detected-artifact-v1",
        scope.backendId,
        scope.profileId,
        scope.sessionId,
        messageId.orEmpty(),
        kind.name,
        content,
    ).joinToString("\u001f")
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return "detected:sha256:$digest"
}

private fun safeHttpUrl(raw: String): String? = runCatching {
    val value = raw.trim()
    require(value.length in 1..2_048 && value.none(Char::isISOControl))
    val uri = URI(value)
    require(uri.scheme?.lowercase() in setOf("http", "https"))
    require(!uri.host.isNullOrBlank() && uri.userInfo == null)
    uri.toASCIIString()
}.getOrNull()

private fun normalizePath(raw: String): String? {
    val value = raw.trim().trimEnd(',', '.', ';', ':', ')', ']', '}')
    if (value.isBlank() || value.length > DetectedArtifactLimits.MAX_VALUE_CHARACTERS || value.any(Char::isISOControl)) return null
    if (value.startsWith("file:", ignoreCase = true)) return null
    if (value.matches(Regex("^[a-z][a-z0-9+.-]*:.*", RegexOption.IGNORE_CASE)) &&
        !value.matches(Regex("^[a-z]:[\\\\/].*", RegexOption.IGNORE_CASE))
    ) return null
    return value
}

private fun decodeMediaPath(raw: String): String? = runCatching {
    val encoded = raw.trim()
    require(encoded.length <= DetectedArtifactLimits.MAX_VALUE_CHARACTERS)
    normalizePath(URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8.name()))
}.getOrNull()

private fun unquote(raw: String): String? {
    val value = raw.trim()
    if (value.length < 2) return value
    val quote = value.first()
    return if (quote == value.last() && quote in setOf('`', '\'', '"')) value.substring(1, value.length - 1) else value
}

private fun trimUrlPunctuation(value: String): String = value.trimEnd(',', ';', ':', '.', '!', '?', ')', ']', '}')

private fun displayLabel(value: String): String {
    return runCatching { URI(value).path?.split('/')?.filter(String::isNotBlank)?.lastOrNull() }
        .getOrNull()?.takeIf(String::isNotBlank)?.take(DetectedArtifactLimits.MAX_LABEL_CHARACTERS)
        ?: value.split('/', '\\').filter(String::isNotBlank).lastOrNull()
            ?.take(DetectedArtifactLimits.MAX_LABEL_CHARACTERS)
        ?: value.take(DetectedArtifactLimits.MAX_LABEL_CHARACTERS)
}

private fun kindForPath(path: String, mimeType: String?): DetectedArtifactKind {
    val mime = mimeType.orEmpty().lowercase()
    return when {
        mime == "text/html" || path.substringBefore('?').substringBefore('#').lowercase().endsWith(".html") ||
            path.substringBefore('?').substringBefore('#').lowercase().endsWith(".htm") -> DetectedArtifactKind.HTML
        mime == "image/svg+xml" || isSvgPath(path) -> DetectedArtifactKind.SVG
        mime.startsWith("image/") -> DetectedArtifactKind.IMAGE
        isImagePath(path) -> DetectedArtifactKind.IMAGE
        else -> DetectedArtifactKind.FILE
    }
}

private fun isImagePath(value: String): Boolean = value.substringBefore('?').substringBefore('#')
    .lowercase().matches(Regex(".*\\.(?:png|jpe?g|gif|webp|bmp|svg)$"))

private fun isSvgPath(value: String): Boolean = value.substringBefore('?').substringBefore('#')
    .lowercase().endsWith(".svg")

private fun mimeFor(kind: DetectedArtifactKind, value: String): String? = when (kind) {
    DetectedArtifactKind.IMAGE -> when {
        value.startsWith("data:image/", ignoreCase = true) -> value.substringAfter("data:").substringBefore(';').lowercase()
        value.lowercase().endsWith(".svg") -> "image/svg+xml"
        value.lowercase().endsWith(".jpg") || value.lowercase().endsWith(".jpeg") -> "image/jpeg"
        value.lowercase().endsWith(".png") -> "image/png"
        value.lowercase().endsWith(".gif") -> "image/gif"
        value.lowercase().endsWith(".webp") -> "image/webp"
        else -> "image/*"
    }
    DetectedArtifactKind.HTML -> "text/html"
    DetectedArtifactKind.SVG -> "image/svg+xml"
    DetectedArtifactKind.CODE -> "text/plain"
    DetectedArtifactKind.FILE -> when {
        value.substringBefore('?').substringBefore('#').lowercase().endsWith(".mp3") -> "audio/mpeg"
        value.substringBefore('?').substringBefore('#').lowercase().endsWith(".opus") -> "audio/ogg; codecs=opus"
        value.substringBefore('?').substringBefore('#').lowercase().endsWith(".ogg") -> "audio/ogg"
        value.substringBefore('?').substringBefore('#').lowercase().endsWith(".wav") -> "audio/wav"
        value.substringBefore('?').substringBefore('#').lowercase().endsWith(".m4a") -> "audio/mp4"
        value.substringBefore('?').substringBefore('#').lowercase().endsWith(".avi") -> "video/x-msvideo"
        value.substringBefore('?').substringBefore('#').lowercase().endsWith(".mkv") -> "video/x-matroska"
        value.substringBefore('?').substringBefore('#').lowercase().endsWith(".mp4") -> "video/mp4"
        value.substringBefore('?').substringBefore('#').lowercase().endsWith(".webm") -> "video/webm"
        value.substringBefore('?').substringBefore('#').lowercase().endsWith(".mov") -> "video/quicktime"
        else -> null
    }
    else -> null
}

private fun mimeForFence(kind: DetectedArtifactKind): String? = when (kind) {
    DetectedArtifactKind.HTML -> "text/html"
    DetectedArtifactKind.SVG -> "image/svg+xml"
    DetectedArtifactKind.CODE -> "text/plain"
    else -> null
}

private fun safeMimeType(raw: String?): String? {
    val value = raw?.trim()?.lowercase() ?: return null
    return value.takeIf {
        it.length <= 100 && it.matches(Regex("[a-z0-9.+-]+/[a-z0-9.+-]+(?:; codecs=opus)?"))
    }
}

private fun classifyFence(language: String, body: String): DetectedArtifactKind? {
    if (language in HTML_LANGUAGES) {
        val document = HTML_DOC_RE.containsMatchIn(body)
        return if (
            (document && body.length >= HTML_DOCUMENT_MIN_CHARACTERS) ||
            (!document && body.length >= HTML_FRAGMENT_MIN_CHARACTERS && HTML_TAG_RE.containsMatchIn(body))
        ) {
            DetectedArtifactKind.HTML
        } else {
            null
        }
    }
    if (language == "svg") {
        return DetectedArtifactKind.SVG.takeIf {
            body.length >= SVG_MIN_CHARACTERS && body.contains("<svg", ignoreCase = true)
        }
    }
    if (language in NON_ARTIFACT_LANGUAGES) return null
    val lineCount = body.count { it == '\n' } + 1
    if (body.length < CODE_MIN_CHARACTERS && lineCount < CODE_MIN_LINES) return null
    if (isLikelyProseCodeBlock(language, body)) return null
    return DetectedArtifactKind.CODE
}

private fun sanitizeLanguageTag(raw: String): String {
    val first = raw.trim().split(Regex("\\s+"), limit = 2).firstOrNull().orEmpty()
    return first.lowercase().takeIf { it.length <= 16 && VALID_LANGUAGE_RE.matches(it) }.orEmpty()
}

private fun isLikelyProseCodeBlock(language: String, body: String): Boolean {
    val lines = body.lines()
    val codeSignals = CODE_SIGNAL_PATTERNS.sumOf { pattern -> pattern.findAll(body).count() }
    if (body.isBlank() || codeSignals >= 3) return false
    val bulletLines = lines.count { BULLET_LINE_RE.containsMatchIn(it) }
    val hasMarkdown = MARKDOWN_SIGNAL_RE.containsMatchIn(body)
    val proseLines = lines.count { line ->
        val trimmed = line.trim()
        trimmed.isNotEmpty() && PROSE_LINE_START_RE.containsMatchIn(trimmed)
    }
    if (bulletLines >= 1 && (hasMarkdown || proseLines >= 2)) return true
    if (language in NON_CODE_FENCE_LANGUAGES) return proseLines >= 3 && codeSignals == 0
    return language !in COMMON_CODE_LANGUAGES && proseLines >= 2 && codeSignals <= 1
}

private fun valueLimit(kind: DetectedArtifactKind, value: String): Int = when {
    value.startsWith("data:", ignoreCase = true) -> DetectedArtifactLimits.MAX_INLINE_IMAGE_CHARACTERS
    kind in setOf(DetectedArtifactKind.CODE, DetectedArtifactKind.HTML, DetectedArtifactKind.SVG) ->
        DetectedArtifactLimits.MAX_CODE_CHARACTERS
    else -> DetectedArtifactLimits.MAX_VALUE_CHARACTERS
}

private fun JsonObject?.string(key: String): String = (this?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject?.boolean(key: String): Boolean? = (this?.get(key) as? JsonPrimitive)?.let { primitive ->
    primitive.booleanOrNull ?: primitive.contentOrNull?.toBooleanStrictOrNull()
}

private val TEXT_PART_TYPES = setOf("text", "output_text", "input_text", "markdown")
private val HTML_LANGUAGES = setOf("html", "htm", "xhtml")
private val NON_ARTIFACT_LANGUAGES = setOf(
    "", "console", "diff", "log", "logs", "markdown", "md", "mermaid", "output", "patch", "plain",
    "plaintext", "shell-session", "stdout", "text", "txt",
)
private val NON_CODE_FENCE_LANGUAGES = setOf("", "text", "plain", "plaintext", "md", "markdown")
private val COMMON_CODE_LANGUAGES = setOf(
    "bash", "c", "cpp", "css", "diff", "go", "html", "java", "javascript", "js", "json", "jsx",
    "markdown", "md", "php", "python", "py", "ruby", "rust", "rs", "sh", "sql", "swift", "tsx",
    "ts", "typescript", "xml", "yaml", "yml",
)
private val IMAGE_RESULT_KEYS = listOf("host_image", "image")
private val IMAGE_ECHO_KEYS = listOf("host_image", "image", "agent_visible_image")
private val FENCED_CODE_RE = Regex("(?s)```([^\\r\\n`]*)\\r?\\n(.*?)(?:\\r?\\n)?```")
private val MARKDOWN_LINK_RE = Regex("!?(\\[[^\\]]*\\])\\(([^)\\s]+)\\)")
private val HTTP_URL_RE = Regex("https?://[^\\s<>\\\"'`()\\[\\]]+", RegexOption.IGNORE_CASE)
private val MEDIA_RE = Regex("(?i)(?:^|[\\s])MEDIA:\\s*(`[^`\\r\\n]+`|\\\"[^\\\"\\r\\n]+\\\"|'[^'\\r\\n]+'|[^\\s<>]+)")
private val MEDIA_HREF_RE = Regex("(?i)#media:([^)\\s]+)")
private val IMAGE_DIRECTIVE_RE = Regex("(?i)(?:^|[\\s])@image:\\s*(`[^`\\r\\n]+`|\\\"[^\\\"\\r\\n]+\\\"|'[^'\\r\\n]+'|[^\\s<>]+)")
private val DATA_IMAGE_MARKER_RE = Regex("(?i)data:image/[a-z0-9.+-]+;base64,")
private val DATA_IMAGE_RE = Regex("(?i)^data:image/[a-z0-9.+-]+;base64,[a-z0-9+/=]{64,}$")
private val DATA_MEDIA_RE = Regex("(?i)^data:(?:image|audio|video)/[a-z0-9.+-]+(?:;[^,;]{1,128})*;base64,[a-z0-9+/=]{64,}$")
private val HTML_DOC_RE = Regex("<!doctype\\s+html|<html[\\s>]|<head[\\s>]|<body[\\s>]", RegexOption.IGNORE_CASE)
private val HTML_TAG_RE = Regex("<[a-z][a-z0-9-]*(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
private val VALID_LANGUAGE_RE = Regex("[a-z0-9][a-z0-9+#-]*", RegexOption.IGNORE_CASE)
private val BULLET_LINE_RE = Regex("^\\s*[-*]\\s+\\S+")
private val MARKDOWN_SIGNAL_RE = Regex("\\*\\*[^*]+\\*\\*|`[^`]+`")
private val PROSE_LINE_START_RE = Regex("^[A-Za-z0-9\"'`*-]")
private val CODE_SIGNAL_PATTERNS = listOf(
    Regex("(^|\\s)(const|let|var|function|class|import|export|return|if|for|while|switch)\\b", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
    Regex("=>|==|===|!=|!==|\\{|}|;|</?[a-z][^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
    Regex("^\\s*(#include|SELECT|INSERT|UPDATE|DELETE|CREATE|DROP)\\b", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
)
private const val HTML_DOCUMENT_MIN_CHARACTERS = 160
private const val HTML_FRAGMENT_MIN_CHARACTERS = 1_200
private const val SVG_MIN_CHARACTERS = 2_000
private const val CODE_MIN_LINES = 48
private const val CODE_MIN_CHARACTERS = 3_000

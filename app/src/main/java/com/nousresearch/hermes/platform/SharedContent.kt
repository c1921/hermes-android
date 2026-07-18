package com.nousresearch.hermes.platform

import java.net.URI

data class SharedContent(
    val id: String,
    val text: String,
    val uriStrings: List<String>,
)

fun sanitizeSharedContent(
    id: String,
    text: String?,
    uriStrings: List<String>,
): SharedContent? {
    val safeText = text.orEmpty()
        .replace("\u0000", "")
        .trim()
        .take(MAX_SHARED_TEXT_CHARACTERS)
    val safeUris = uriStrings.asSequence()
        .map(String::trim)
        .filter { it.length in 1..MAX_SHARED_URI_CHARACTERS }
        .filter(::isSafeSharedContentUri)
        .distinct()
        .take(MAX_SHARED_URIS)
        .toList()
    if (safeText.isEmpty() && safeUris.isEmpty()) return null
    return SharedContent(
        id = id.take(MAX_SHARE_ID_CHARACTERS),
        text = safeText,
        uriStrings = safeUris,
    )
}

fun mergeSharedText(existing: String, shared: String, maxCharacters: Int): String {
    require(maxCharacters > 0) { "Draft capacity must be positive" }
    val combined = when {
        existing.isEmpty() -> shared
        shared.isEmpty() -> existing
        else -> "$existing\n\n$shared"
    }
    return combined.take(maxCharacters)
}

private fun isSafeSharedContentUri(value: String): Boolean = runCatching {
    val uri = URI(value)
    val authority = uri.rawAuthority
    uri.scheme.equals("content", ignoreCase = true) &&
        !authority.isNullOrBlank() &&
        authority.length <= MAX_SHARED_AUTHORITY_CHARACTERS &&
        uri.rawUserInfo == null
}.getOrDefault(false)

private const val MAX_SHARED_TEXT_CHARACTERS = 10_000
private const val MAX_SHARED_URIS = 5
private const val MAX_SHARED_URI_CHARACTERS = 4_096
private const val MAX_SHARED_AUTHORITY_CHARACTERS = 255
private const val MAX_SHARE_ID_CHARACTERS = 100

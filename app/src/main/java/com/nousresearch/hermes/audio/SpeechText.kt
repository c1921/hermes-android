package com.nousresearch.hermes.audio

private val fencedCode = Regex("```[\\s\\S]*?(?:```|$)")
private val thinkingPrefix = Regex(
    "^\\s*(?:\\([^)\\n]{1,48}\\)\\s*)?(?:processing|thinking|reasoning|analyzing|pondering|contemplating|musing|cogitating|ruminating|deliberating|mulling|reflecting|computing|synthesizing|formulating|brainstorming)\\.\\.\\.\\s*",
    RegexOption.IGNORE_CASE,
)
private val markdownLink = Regex("\\[([^]]+)]\\([^)]+\\)")
private val inlineCode = Regex("`([^`]+)`")
private val url = Regex("\\bhttps?://\\S+", RegexOption.IGNORE_CASE)
private val emoji = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}\\x{E0020}-\\x{E007F}]+")

internal fun sanitizeTextForSpeech(text: String): String = text
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .replace(Regex("(\\p{L})-\\n(\\p{L})"), "$1$2")
    .replace(Regex("[ \\t]*\\n{2,}[ \\t]*"), ". ")
    .replace(Regex("[ \\t]*\\n[ \\t]*"), " ")
    .replace(fencedCode, " ")
    .replace(thinkingPrefix, " ")
    .replace(markdownLink, "$1")
    .replace(inlineCode, "$1")
    .replace(url, " link ")
    .replace(emoji, " ")
    .replace(Regex("(?m)^#{1,6}\\s+"), "")
    .replace(Regex("[*_~>#]"), "")
    .replace(Regex("(?m)^\\s*[-+*]\\s+"), "")
    .replace(Regex("\\s+"), " ")
    .trim()

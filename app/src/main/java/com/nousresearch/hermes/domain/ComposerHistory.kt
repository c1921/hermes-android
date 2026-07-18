package com.nousresearch.hermes.domain

data class ComposerBrowseState(
    val cursor: Int = -1,
    val draftSnapshot: String = "",
)

data class ComposerBrowseResult(
    val state: ComposerBrowseState,
    val text: String,
    val returnedToPresent: Boolean = false,
)

object ComposerHistory {
    fun derive(timeline: TimelineState): List<String> = timeline.items
        .asReversed()
        .asSequence()
        .filterIsInstance<TimelineItem.Message>()
        .filter { it.role == MessageRole.USER }
        .map { it.text.trim() }
        .filter(String::isNotBlank)
        .take(MAX_HISTORY_ENTRIES)
        .toList()

    fun backward(
        state: ComposerBrowseState,
        currentDraft: String,
        history: List<String>,
    ): ComposerBrowseResult? {
        if (history.isEmpty()) return null
        val nextCursor = when {
            state.cursor < 0 -> 0
            state.cursor < history.lastIndex -> state.cursor + 1
            else -> return null
        }
        val snapshot = if (state.cursor < 0) currentDraft else state.draftSnapshot
        return ComposerBrowseResult(
            state = ComposerBrowseState(nextCursor, snapshot),
            text = history[nextCursor],
        )
    }

    fun forward(
        state: ComposerBrowseState,
        history: List<String>,
    ): ComposerBrowseResult? {
        if (state.cursor < 0) return null
        if (state.cursor > 0) {
            val nextCursor = state.cursor - 1
            val text = history.getOrNull(nextCursor) ?: return null
            return ComposerBrowseResult(
                state = state.copy(cursor = nextCursor),
                text = text,
            )
        }
        return ComposerBrowseResult(
            state = ComposerBrowseState(),
            text = state.draftSnapshot,
            returnedToPresent = true,
        )
    }

    private const val MAX_HISTORY_ENTRIES = 100
}

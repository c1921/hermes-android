package com.nousresearch.hermes.data

import com.nousresearch.hermes.protocol.SlashCommandCatalog
import com.nousresearch.hermes.protocol.SlashCommandCategory
import com.nousresearch.hermes.protocol.SlashCompletionItem
import com.nousresearch.hermes.protocol.SlashCompletionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandCatalogTest {
    @Test
    fun `mobile catalogue keeps supported commands user commands and skills`() {
        val catalogue = SlashCommandCatalog(
            pairs = listOf(
                listOf("/retry", "Retry the last user message"),
                listOf("/mouse", "Toggle terminal mouse mode"),
                listOf("/brief", "Run my daily brief"),
                listOf("/gif-search", "Search GIFs"),
            ),
            categories = listOf(
                SlashCommandCategory("Session", listOf(listOf("/retry", "Retry the last user message"))),
                SlashCommandCategory("TUI", listOf(listOf("/mouse", "Toggle terminal mouse mode"))),
                SlashCommandCategory("User commands", listOf(listOf("/brief", "Run my daily brief"))),
            ),
            skillCount = 1,
        )

        val suggestions = mobileCatalogSuggestions(catalogue)

        assertTrue(suggestions.any { it.text == "/retry" && it.group == "Session" })
        assertTrue(suggestions.any { it.text == "/brief" && it.group == "User commands" })
        assertTrue(suggestions.any { it.text == "/gif-search" && it.group == "Skills" })
        assertFalse(suggestions.any { it.text == "/mouse" })
    }

    @Test
    fun `argument completion replaces only the active argument`() {
        val completion = SlashCompletionResult(
            items = listOf(SlashCompletionItem("focused", "focused", "Focused personality")),
            replaceFrom = 13,
        )

        val suggestions = mobileCompletionSuggestions("/personality fo", completion, emptySet())

        assertEquals("/personality focused", suggestions.single().text)
        assertEquals("Options", suggestions.single().group)
    }

    @Test
    fun `typed command completion filters terminal only commands`() {
        val completion = SlashCompletionResult(
            items = listOf(
                SlashCompletionItem("/usage", "/usage", "Show token usage"),
                SlashCompletionItem("/mouse", "/mouse", "Toggle terminal mouse mode"),
                SlashCompletionItem("/codex", "/codex", "Load Codex skill"),
            ),
        )

        val suggestions = mobileCompletionSuggestions("/", completion, setOf("/codex"))

        assertEquals(listOf("/usage", "/codex"), suggestions.map(SlashSuggestion::text))
    }
}

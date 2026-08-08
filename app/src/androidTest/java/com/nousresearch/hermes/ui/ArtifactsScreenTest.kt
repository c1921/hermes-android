package com.nousresearch.hermes.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nousresearch.hermes.data.ArtifactIndexFilter
import com.nousresearch.hermes.data.ArtifactPreviewContent
import com.nousresearch.hermes.data.AuthMode
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.data.DetectedArtifactIndexEntry
import com.nousresearch.hermes.data.DetectedArtifactIndexSnapshot
import com.nousresearch.hermes.domain.DetectedArtifact
import com.nousresearch.hermes.domain.DetectedArtifactKind
import com.nousresearch.hermes.domain.DetectedArtifactOrigin
import com.nousresearch.hermes.domain.DetectedArtifactSource
import com.nousresearch.hermes.ui.theme.HermesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ArtifactsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val backend = BackendConfig(
        id = "backend",
        label = "Hermes",
        baseUrl = "https://hermes.example",
        authMode = AuthMode.DASHBOARD_SESSION,
    )
    private val entry = DetectedArtifactIndexEntry(
        artifact = DetectedArtifact(
            id = "artifact-1",
            kind = DetectedArtifactKind.LINK,
            value = "https://example.com/report",
            label = "architecture-report.html",
            mimeType = "text/html",
            source = DetectedArtifactSource.MESSAGE_TEXT,
            origin = DetectedArtifactOrigin("backend", "default", "session-1", messageId = "message-9"),
        ),
        sessionTitle = "Fork Deletion and Branch Merging",
        sessionTimestamp = 1_800_000_000.0,
    )
    private val index = ArtifactIndexUiState(
        backendId = "backend",
        profileId = "default",
        snapshot = DetectedArtifactIndexSnapshot("backend", "default", 1, listOf(entry)),
    )

    @Test
    fun phoneListUsesDesktopFiltersAndOpensSelectedArtifact() {
        var selected: String? = null
        compose.setContent {
            HermesTheme {
                ArtifactsScreen(
                    backend = backend,
                    profileId = "default",
                    indexState = index,
                    preferences = ArtifactBrowserPreferences(scope = "backend\u0000default"),
                    selectedArtifactId = null,
                    expanded = false,
                    onRefresh = {},
                    onQueryChange = {},
                    onFilterChange = {},
                    onSelect = { selected = it.artifact.id },
                    onOpenChat = {},
                    onBack = {},
                    loadPreview = { ArtifactPreviewContent.External(it.artifact.label, it.artifact.value) },
                )
            }
        }

        listOf("All", "Images", "Files", "Links", "architecture-report.html").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText("architecture-report.html").performClick()
        compose.runOnIdle { assertEquals("artifact-1", selected) }
    }

    @Test
    fun expandedWindowShowsListAndSelectedDetailFromTheSameSnapshot() {
        compose.setContent {
            HermesTheme {
                ArtifactsScreen(
                    backend = backend,
                    profileId = "default",
                    indexState = index,
                    preferences = ArtifactBrowserPreferences(
                        scope = "backend\u0000default",
                        filter = ArtifactIndexFilter.ALL,
                    ),
                    selectedArtifactId = "artifact-1",
                    expanded = true,
                    onRefresh = {},
                    onQueryChange = {},
                    onFilterChange = {},
                    onSelect = {},
                    onOpenChat = {},
                    onBack = {},
                    loadPreview = { ArtifactPreviewContent.External(it.artifact.label, it.artifact.value) },
                )
            }
        }

        compose.onNodeWithText("Search artifacts").assertExists()
        compose.onNodeWithText("Open chat").assertExists()
        compose.onNodeWithText("Export").assertExists()
        compose.onNodeWithText("Share").assertExists()
        compose.onNodeWithText("Open with").assertExists()
    }
}

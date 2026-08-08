package com.nousresearch.hermes.benchmark

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
import com.nousresearch.hermes.domain.MessageRole
import com.nousresearch.hermes.domain.TimelineItem
import com.nousresearch.hermes.ui.ArtifactBrowserPreferences
import com.nousresearch.hermes.ui.ArtifactIndexUiState
import com.nousresearch.hermes.ui.ArtifactsScreen
import com.nousresearch.hermes.ui.ManagementHeader
import com.nousresearch.hermes.ui.SpeechUiState
import com.nousresearch.hermes.ui.Timeline
import com.nousresearch.hermes.ui.theme.HermesTheme
import kotlinx.coroutines.delay

private const val FIXTURE_RESET_EXTRA = "hermes.benchmark.fixture_reset"

class BenchmarkFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setBenchmarkContent(intent.fixtureResetKey())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setBenchmarkContent(intent.fixtureResetKey())
    }

    private fun setBenchmarkContent(resetKey: Long) {
        setContent { HermesTheme { BenchmarkFixture(resetKey) } }
    }
}

private fun Intent.fixtureResetKey(): Long = getLongExtra(FIXTURE_RESET_EXTRA, 0L)

private enum class FixtureSurface(val label: String) {
    ATLAS("Atlas"),
    CHATS("Chats"),
    FILES("Files"),
    ARTIFACTS("Artifacts"),
    MANAGE("Manage"),
}

@Composable
private fun BenchmarkFixture(resetKey: Long) {
    var surface by remember(resetKey) { mutableStateOf(FixtureSurface.CHATS) }
    var streamText by remember(resetKey) { mutableStateOf("") }

    LaunchedEffect(surface) {
        if (surface == FixtureSurface.CHATS) {
            streamText = ""
            fixtureStreamChunks().forEach { chunk ->
                streamText = "$streamText $chunk".trim()
                delay(FIXTURE_STREAM_INTERVAL_MILLIS)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            FixtureSurface.entries.forEach { candidate ->
                TextButton(
                    onClick = { surface = candidate },
                    modifier = Modifier.semantics { contentDescription = candidate.label },
                ) {
                    Text(candidate.label)
                }
            }
        }
        HorizontalDivider()
        when (surface) {
            FixtureSurface.ATLAS -> FixtureAtlas()
            FixtureSurface.CHATS -> FixtureChats(streamText)
            FixtureSurface.FILES -> FixtureFiles()
            FixtureSurface.ARTIFACTS -> FixtureArtifacts()
            FixtureSurface.MANAGE -> FixtureManage()
        }
    }
}

@Composable
private fun FixtureAtlas() {
    Column(Modifier.fillMaxSize()) {
        Text("Atlas", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp).semantics { heading() })
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(List(60) { "Benchmark session ${it.toString().padStart(3, '0')}" }) { session ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Text(session, modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun FixtureChats(streamText: String) {
    val items = remember(streamText) { fixtureTranscript(streamText) }
    Column(Modifier.fillMaxSize()) {
        Text("Chats", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() })
        Text("Transcript", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(4.dp))
        Column(Modifier.weight(1f)) {
            Timeline(
                items = items,
                speechState = SpeechUiState(),
                onSpeak = { _, _ -> },
                onStopSpeaking = {},
                expandedToolIds = emptySet(),
                toolDisclosureKey = { it.id },
                onToolExpandedChange = null,
                focusMessageId = null,
            )
        }
        Text("Composer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        var draft by rememberSaveable(resetKey) { mutableStateOf("") }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(MAX_COMPOSER_CHARACTERS) },
                modifier = Modifier.weight(1f).semantics { contentDescription = "benchmark-composer" },
                label = { Text("Message Hermes") },
                singleLine = true,
            )
            Button(onClick = { draft = "" }, enabled = draft.isNotBlank()) { Text("Send") }
        }
        Text(
            text = "Draft: $draft",
            modifier = Modifier.semantics { contentDescription = "benchmark-composer-value" },
        )
    }
}

@Composable
private fun FixtureFiles() {
    val files = remember { List(FIXTURE_FILE_COUNT) { index -> "/Users/benchmark/Documents/file-${index.toString().padStart(3, '0')}.md" } }
    Column(Modifier.fillMaxSize()) {
        ManagementHeader("Files", "Credential-free managed files fixture", loading = false, onRefresh = null, onBack = null)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Files", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            items(files, key = { it }) { path ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Text(path, modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun FixtureArtifacts() {
    val backend = remember { BenchmarkBackend }
    val snapshot = remember { fixtureArtifactSnapshot() }
    ArtifactsScreen(
        backend = backend,
        profileId = FIXTURE_PROFILE,
        indexState = ArtifactIndexUiState(
            backendId = backend.id,
            profileId = FIXTURE_PROFILE,
            snapshot = snapshot,
        ),
        preferences = ArtifactBrowserPreferences(scope = "${backend.id}\u0000$FIXTURE_PROFILE", filter = ArtifactIndexFilter.ALL),
        selectedArtifactId = null,
        expanded = false,
        onRefresh = {},
        onQueryChange = {},
        onFilterChange = {},
        onSelect = {},
        onOpenChat = {},
        onBack = {},
        loadPreview = { entry ->
            ArtifactPreviewContent.Text(
                name = entry.artifact.label,
                mimeType = "text/plain",
                text = "Deterministic benchmark artifact ${entry.artifact.id}",
                renderMode = com.nousresearch.hermes.data.ArtifactTextRenderMode.SOURCE,
            )
        },
    )
}

@Composable
private fun FixtureManage() {
    Column(Modifier.fillMaxSize()) {
        ManagementHeader("Manage", "Credential-free management fixture", loading = false, onRefresh = null, onBack = null)
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Manage", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
            items(listOf("Profiles", "Providers", "Automations", "Diagnostics", "App settings")) { section ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Text(section, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }
        }
    }
}

private fun fixtureTranscript(streamText: String): List<TimelineItem> = List(FIXTURE_MESSAGE_COUNT) { index ->
    when (index % 4) {
        0 -> TimelineItem.Message("fixture-message-${index.toString().padStart(3, '0')}", MessageRole.USER, "User request ${index + 1}")
        1 -> TimelineItem.Message(
            id = "fixture-message-${index.toString().padStart(3, '0')}",
            role = MessageRole.ASSISTANT,
            text = "Assistant response ${index + 1}" + if (index == FIXTURE_STREAM_MESSAGE_INDEX) " $streamText" else "",
            streaming = index == FIXTURE_STREAM_MESSAGE_INDEX && streamText.isNotBlank(),
        )
        2 -> TimelineItem.Tool(
            id = "fixture-message-${index.toString().padStart(3, '0')}",
            name = "benchmark-tool",
            state = com.nousresearch.hermes.domain.ToolState.COMPLETE,
            summary = "Deterministic tool result ${index + 1}",
        )
        else -> TimelineItem.Error(
            id = "fixture-message-${index.toString().padStart(3, '0')}",
            message = "Recoverable fixture error ${index + 1}",
            recoverable = true,
        )
    }
}

private fun fixtureStreamChunks(): List<String> = List(FIXTURE_STREAM_CHUNK_COUNT) { index ->
    "stream-chunk-${index.toString().padStart(3, '0')}"
}

private fun fixtureArtifactSnapshot(): DetectedArtifactIndexSnapshot = DetectedArtifactIndexSnapshot(
    backendId = BenchmarkBackend.id,
    profileId = FIXTURE_PROFILE,
    sessionsLoaded = 1,
    entries = List(FIXTURE_ARTIFACT_COUNT) { index ->
        DetectedArtifactIndexEntry(
            artifact = DetectedArtifact(
                id = "fixture-artifact-$index",
                kind = if (index % 2 == 0) DetectedArtifactKind.FILE else DetectedArtifactKind.LINK,
                value = "https://benchmark.invalid/artifact/$index",
                label = "fixture-artifact-${index.toString().padStart(3, '0')}",
                mimeType = "text/plain",
                source = DetectedArtifactSource.MESSAGE_TEXT,
                origin = DetectedArtifactOrigin(BenchmarkBackend.id, FIXTURE_PROFILE, "fixture-session", "fixture-message-${index.toString().padStart(3, '0')}"),
            ),
            sessionTitle = "Benchmark fixture transcript",
            sessionTimestamp = index.toDouble(),
        )
    },
)

private val BenchmarkBackend = BackendConfig(
    id = "benchmark-fixture",
    label = "Hermes benchmark fixture",
    baseUrl = "https://benchmark.invalid",
    authMode = AuthMode.TOKEN,
)

private const val FIXTURE_PROFILE = "benchmark"
private const val FIXTURE_MESSAGE_COUNT = 500
private const val FIXTURE_STREAM_MESSAGE_INDEX = 497
private const val FIXTURE_STREAM_CHUNK_COUNT = 120
private const val FIXTURE_STREAM_INTERVAL_MILLIS = 25L
private const val FIXTURE_FILE_COUNT = 60
private const val FIXTURE_ARTIFACT_COUNT = 24
private const val MAX_COMPOSER_CHARACTERS = 4_096

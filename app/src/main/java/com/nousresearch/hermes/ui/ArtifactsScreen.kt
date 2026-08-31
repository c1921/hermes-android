package com.nousresearch.hermes.ui

import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.data.ArtifactIndexFilter
import com.nousresearch.hermes.data.ArtifactPreviewContent
import com.nousresearch.hermes.data.ArtifactTextRenderMode
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.data.DetectedArtifactIndexEntry
import com.nousresearch.hermes.data.exportBytes
import com.nousresearch.hermes.domain.DetectedArtifactKind
import com.nousresearch.hermes.platform.fileOpenIntent
import com.nousresearch.hermes.platform.fileShareIntent
import com.nousresearch.hermes.platform.sharedFileUri
import com.nousresearch.hermes.platform.textShareIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nousresearch.hermes.R

@Composable
internal fun ArtifactsScreen(
    backend: BackendConfig,
    profileId: String,
    indexState: ArtifactIndexUiState,
    preferences: ArtifactBrowserPreferences,
    selectedArtifactId: String?,
    expanded: Boolean,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ArtifactIndexFilter) -> Unit,
    onSelect: (DetectedArtifactIndexEntry) -> Unit,
    onOpenChat: (DetectedArtifactIndexEntry) -> Unit,
    onBack: () -> Unit,
    loadPreview: suspend (DetectedArtifactIndexEntry) -> ArtifactPreviewContent,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(backend.id, profileId) { onRefresh() }
    val snapshot = indexState.snapshot?.takeIf { it.backendId == backend.id && it.profileId == profileId }
    val entries = remember(snapshot, preferences.filter, preferences.query) {
        snapshot?.search(preferences.filter, preferences.query).orEmpty()
    }
    val selected = snapshot?.entries?.firstOrNull { it.artifact.id == selectedArtifactId }

    if (expanded) {
        Row(modifier.fillMaxSize()) {
            ArtifactListPane(
                profileId = profileId,
                entries = entries,
                indexState = indexState,
                preferences = preferences,
                selectedArtifactId = selectedArtifactId,
                onRefresh = onRefresh,
                onQueryChange = onQueryChange,
                onFilterChange = onFilterChange,
                onSelect = onSelect,
                onBack = onBack,
                modifier = Modifier.weight(0.46f).fillMaxHeight(),
            )
            HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))
            if (selected != null) {
                ArtifactDetailPane(
                    entry = selected,
                    onBack = onBack,
                    onOpenChat = { onOpenChat(selected) },
                    loadPreview = { loadPreview(selected) },
                    modifier = Modifier.weight(0.54f).fillMaxHeight(),
                )
            } else {
                ArtifactEmptyDetail(Modifier.weight(0.54f).fillMaxHeight())
            }
        }
    } else if (selectedArtifactId != null) {
        when {
            indexState.loading && selected == null -> ArtifactLoading(onBack, modifier)
            selected != null -> ArtifactDetailPane(
                entry = selected,
                onBack = onBack,
                onOpenChat = { onOpenChat(selected) },
                loadPreview = { loadPreview(selected) },
                modifier = modifier,
            )
            else -> ArtifactMissing(onBack, onRefresh, modifier)
        }
    } else {
        ArtifactListPane(
            profileId = profileId,
            entries = entries,
            indexState = indexState,
            preferences = preferences,
            selectedArtifactId = null,
            onRefresh = onRefresh,
            onQueryChange = onQueryChange,
            onFilterChange = onFilterChange,
            onSelect = onSelect,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun ArtifactListPane(
    profileId: String,
    entries: List<DetectedArtifactIndexEntry>,
    indexState: ArtifactIndexUiState,
    preferences: ArtifactBrowserPreferences,
    selectedArtifactId: String?,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ArtifactIndexFilter) -> Unit,
    onSelect: (DetectedArtifactIndexEntry) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        ArtifactsHeader(stringResource(R.string.ui_artifacts_title_5b7c9d), profileId, onBack, onRefresh)
        OutlinedTextField(
            value = preferences.query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.ui_search_artifacts_200f88)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArtifactIndexFilter.entries.forEach { filter ->
                FilterChip(
                    selected = preferences.filter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(stringResource(filter.labelRes)) },
                )
            }
        }
        when {
            indexState.loading && entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            indexState.error != null && entries.isEmpty() -> ArtifactError(indexState.error, onRefresh, Modifier.fillMaxSize())
            entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (preferences.query.isBlank()) stringResource(R.string.ui_no_artifacts_in_profile_1c2d3e)
                    else stringResource(R.string.ui_no_artifacts_match_search_2d3e4f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(entries, key = { it.artifact.id }) { entry ->
                    ArtifactRow(entry, selectedArtifactId == entry.artifact.id) { onSelect(entry) }
                }
            }
        }
    }
}

@Composable
private fun ArtifactsHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.a11y_back_b52b36)) }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.semantics { heading() },
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        onRefresh?.let { refresh ->
            IconButton(onClick = refresh) { Icon(Icons.Outlined.Refresh, stringResource(R.string.a11y_refresh_detected_artifacts_5c614a)) }
        }
    }
    HorizontalDivider()
}

@Composable
private fun ArtifactRow(entry: DetectedArtifactIndexEntry, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val artifact = entry.artifact
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics { contentDescription = context.getString(R.string.artifact_detected_description, artifact.label, artifact.kind.name.lowercase(), entry.sessionTitle) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(artifact.icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    artifact.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.ui_artifact_from_session_3e4f5a, entry.sessionTitle),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        R.string.ui_artifact_relative_detected_4f5a6b,
                        relativeTime(entry.sessionTimestamp, stringResource(R.string.ui_unknown_time_7e8f9a)),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ArtifactDetailPane(
    entry: DetectedArtifactIndexEntry,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    loadPreview: suspend () -> ArtifactPreviewContent,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var actionError by remember(entry.artifact.id) { mutableStateOf<String?>(null) }
    val result by produceState<Result<ArtifactPreviewContent>?>(null, entry.artifact.id) {
        value = withContext(Dispatchers.IO) { runCatching { loadPreview() } }
    }
    val preview = result?.getOrNull()
    val latestPreview by rememberUpdatedState(preview)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val content = latestPreview ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(content.exportBytes()) }
                        ?: error(context.getString(R.string.ui_export_destination_open_failed_8b9c0d))
                }
            }.onFailure { actionError = it.message ?: context.getString(R.string.ui_artifact_export_failed_9c0d1e) }
        }
    }

    fun launchShared(openWith: Boolean) {
        val content = preview ?: return
        scope.launch {
            runCatching {
                if (content is ArtifactPreviewContent.External) {
                    if (openWith) Intent(Intent.ACTION_VIEW, android.net.Uri.parse(content.url)) else textShareIntent(content.url)
                } else {
                    withContext(Dispatchers.IO) {
                        val uri = sharedFileUri(context, content.name, content.exportBytes())
                        if (openWith) fileOpenIntent(uri, content.mimeType, content.name)
                        else fileShareIntent(uri, content.mimeType, content.name)
                    }
                }
            }.onSuccess { intent ->
                runCatching { context.startActivity(Intent.createChooser(intent, if (openWith) context.getString(R.string.ui_open_artifact_2f3a4b) else context.getString(R.string.ui_share_artifact_3a4b5c))) }
                    .onFailure { actionError = it.message ?: context.getString(R.string.ui_no_compatible_app_installed_0d1e2f) }
            }.onFailure { actionError = it.message ?: context.getString(R.string.ui_artifact_share_failed_1e2f3a) }
        }
    }

    Column(modifier.fillMaxSize()) {
        ArtifactsHeader(
            title = entry.artifact.label,
            subtitle = stringResource(R.string.ui_artifact_detected_in_session_5a6b7c, entry.sessionTitle),
            onBack = onBack,
            onRefresh = null,
        )
        Text(
            stringResource(
                R.string.ui_artifact_meta_6b7c8d,
                entry.artifact.kind.name,
                entry.artifact.mimeType ?: context.getString(R.string.ui_artifact_unknown_type_7c8d9e),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                result == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                result?.isFailure == true -> PreviewFailure(
                    result?.exceptionOrNull()?.message ?: stringResource(R.string.ui_artifact_preview_unavailable_8d9e0f),
                )
                preview != null -> ArtifactPreview(preview)
            }
        }
        actionError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            ArtifactAction(stringResource(R.string.ui_artifact_open_chat_0f1a2b), Icons.Outlined.ChatBubbleOutline, onOpenChat, Modifier.weight(1f))
            ArtifactAction(stringResource(R.string.ui_artifact_export_1a2b3c), Icons.Outlined.Download, { preview?.let { exportLauncher.launch(it.name) } }, Modifier.weight(1f), preview != null)
            ArtifactAction(stringResource(R.string.ui_artifact_share_2b3c4d), Icons.Outlined.Share, { launchShared(false) }, Modifier.weight(1f), preview != null)
            ArtifactAction(stringResource(R.string.ui_artifact_open_with_3c4d5e), Icons.AutoMirrored.Outlined.OpenInNew, { launchShared(true) }, Modifier.weight(1f), preview != null)
        }
    }
}

@Composable
private fun ArtifactPreview(content: ArtifactPreviewContent) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        when (content) {
            is ArtifactPreviewContent.External -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                SelectionContainer { Text(content.url, style = MaterialTheme.typography.bodyMedium) }
            }
            is ArtifactPreviewContent.Text -> when (content.renderMode) {
                ArtifactTextRenderMode.HTML,
                ArtifactTextRenderMode.SVG,
                -> SandboxedHtmlPreview(content.text)
                ArtifactTextRenderMode.SOURCE -> SelectionContainer {
                    Text(
                        content.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    )
                }
            }
            is ArtifactPreviewContent.Binary -> when {
                content.mimeType.startsWith("image/") -> RasterPreview(content.bytes, content.name)
                content.mimeType == "application/pdf" -> PdfPreview(content.bytes)
                else -> PreviewFailure(stringResource(R.string.ui_artifact_binary_inspect_hint_9e0f1a))
            }
        }
    }
}

@Composable
private fun ArtifactAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.height(56.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun ArtifactError(message: String, onRetry: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.ui_artifacts_unavailable_a4e165), style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.ui_retry_9f5cd8)) }
    }
}

@Composable
private fun ArtifactLoading(onBack: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        ArtifactsHeader(stringResource(R.string.ui_artifact_title_2f3a4b), stringResource(R.string.ui_artifact_loading_subtitle_4b5c6d), onBack, null)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
private fun ArtifactMissing(onBack: () -> Unit, onRefresh: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        ArtifactsHeader(stringResource(R.string.ui_artifact_title_2f3a4b), stringResource(R.string.ui_artifact_missing_subtitle_5c6d7e), onBack, onRefresh)
        ArtifactError(stringResource(R.string.ui_artifact_missing_body_6d7e8f), onRefresh, Modifier.fillMaxSize())
    }
}

@Composable
private fun ArtifactEmptyDetail(modifier: Modifier) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.ui_select_a_detected_artifact_to_preview_it_fdf933), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val ArtifactIndexFilter.labelRes: Int
    get() = when (this) {
        ArtifactIndexFilter.ALL -> R.string.ui_filter_all_4d5e6f
        ArtifactIndexFilter.IMAGES -> R.string.ui_filter_images_5e6f7a
        ArtifactIndexFilter.FILES -> R.string.ui_filter_files_6f7a8b
        ArtifactIndexFilter.LINKS -> R.string.ui_filter_links_7a8b9c
    }

private val com.nousresearch.hermes.domain.DetectedArtifact.icon: ImageVector
    get() = when (kind) {
        DetectedArtifactKind.IMAGE -> Icons.Outlined.Image
        DetectedArtifactKind.LINK -> Icons.Outlined.Link
        DetectedArtifactKind.CODE,
        DetectedArtifactKind.HTML,
        DetectedArtifactKind.SVG,
        -> Icons.Outlined.Code
        DetectedArtifactKind.FILE -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }

private fun relativeTime(timestampSeconds: Double, unknown: String): String {
    val millis = (timestampSeconds * 1_000.0).toLong().coerceAtLeast(0L)
    return if (millis == 0L) unknown else DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
}

package com.nousresearch.hermes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.domain.TimelineItem

internal enum class TimelineRendererKind {
    MESSAGE,
    TOOL,
    REASONING,
    STATUS,
    REFERENCE,
    ARTIFACT,
    ERROR,
    BLOCKING_REQUEST,
    GENERIC,
}

internal data class TimelineRenderContext(
    val speechState: SpeechUiState,
    val onSpeak: (String, String) -> Unit,
    val onStopSpeaking: () -> Unit,
    val expandedToolIds: Set<String>,
    val toolDisclosureKey: (TimelineItem.Tool) -> String,
    val onToolExpandedChange: ((TimelineItem.Tool, Boolean) -> Unit)?,
)

internal object TimelineRendererRegistry {
    fun resolve(item: TimelineItem): TimelineRendererKind = when (item) {
        is TimelineItem.Message -> TimelineRendererKind.MESSAGE
        is TimelineItem.Tool -> TimelineRendererKind.TOOL
        is TimelineItem.Reasoning -> TimelineRendererKind.REASONING
        is TimelineItem.Status -> TimelineRendererKind.STATUS
        is TimelineItem.Reference -> TimelineRendererKind.REFERENCE
        is TimelineItem.Artifact -> TimelineRendererKind.ARTIFACT
        is TimelineItem.Error -> TimelineRendererKind.ERROR
        is TimelineItem.BlockingRequest -> TimelineRendererKind.BLOCKING_REQUEST
        is TimelineItem.Unknown -> TimelineRendererKind.GENERIC
    }

    @Composable
    fun Render(item: TimelineItem, context: TimelineRenderContext) {
        when (resolve(item)) {
            TimelineRendererKind.MESSAGE -> (item as? TimelineItem.Message)?.let { message ->
                MessageBlock(
                    message = message,
                    speechState = context.speechState,
                    onSpeak = { context.onSpeak(message.id, message.text) },
                    onStopSpeaking = context.onStopSpeaking,
                )
            }
            TimelineRendererKind.TOOL -> (item as? TimelineItem.Tool)?.let { tool ->
                ToolBlock(
                    tool = tool,
                    expanded = tool.id in context.expandedToolIds,
                    disclosureKey = context.toolDisclosureKey(tool),
                    onExpandedChange = context.onToolExpandedChange,
                )
            }
            TimelineRendererKind.REASONING -> (item as? TimelineItem.Reasoning)?.let { ReasoningBlock(it) }
            TimelineRendererKind.STATUS -> (item as? TimelineItem.Status)?.let { StatusBlock(it) }
            TimelineRendererKind.REFERENCE -> (item as? TimelineItem.Reference)?.let { ReferenceBlock(it) }
            TimelineRendererKind.ARTIFACT -> (item as? TimelineItem.Artifact)?.let { ArtifactTimelineBlock(it) }
            TimelineRendererKind.ERROR -> (item as? TimelineItem.Error)?.let { ErrorTimelineBlock(it) }
            TimelineRendererKind.BLOCKING_REQUEST -> (item as? TimelineItem.BlockingRequest)?.let { BlockingRequestBlock(it) }
            TimelineRendererKind.GENERIC -> GenericTimelineBlock(item)
        }
    }
}

@Composable
private fun ReferenceBlock(reference: TimelineItem.Reference) {
    TimelineCard(
        label = listOfNotNull(
            "REFERENCE",
            reference.index?.let { index -> reference.count?.let { "$index/$it" } ?: index.toString() },
            reference.label,
        ).joinToString(" · "),
        text = reference.text,
        contentDescription = "Reference, ${reference.label}",
    )
}

@Composable
private fun ArtifactTimelineBlock(artifact: TimelineItem.Artifact) {
    TimelineCard(
        label = "ARTIFACT · ${artifact.label}",
        text = listOfNotNull(artifact.description, artifact.mimeType, artifact.reference).joinToString(" · ")
            .ifBlank { "Available from Hermes" },
        contentDescription = "Artifact, ${artifact.label}",
    )
}

@Composable
private fun ErrorTimelineBlock(error: TimelineItem.Error) {
    TimelineCard(
        label = if (error.recoverable) "ERROR · RECOVERABLE" else "ERROR",
        text = error.message,
        contentDescription = "Hermes error",
        error = true,
    )
}

@Composable
private fun BlockingRequestBlock(request: TimelineItem.BlockingRequest) {
    TimelineCard(
        label = "ACTION REQUIRED · ${request.kind.name.replace('_', ' ')}",
        text = request.prompt,
        contentDescription = "Action required, ${request.kind.name.replace('_', ' ').lowercase()}",
    )
}

@Composable
private fun GenericTimelineBlock(item: TimelineItem) {
    val unknown = item as? TimelineItem.Unknown
    TimelineCard(
        label = "HERMES EVENT",
        text = unknown?.summary?.take(512) ?: "This Hermes version returned an unsupported conversation part.",
        contentDescription = "Unsupported Hermes conversation part",
    )
}

@Composable
private fun TimelineCard(
    label: String,
    text: String,
    contentDescription: String,
    error: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { this.contentDescription = contentDescription },
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            SelectionContainer { Text(text.take(4_096), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

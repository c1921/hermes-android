package com.nousresearch.hermes.data

import com.nousresearch.hermes.domain.DetectedArtifact
import com.nousresearch.hermes.domain.DetectedArtifactKind
import com.nousresearch.hermes.domain.DetectedArtifactSource
import com.nousresearch.hermes.platform.safeExternalUrl
import com.nousresearch.hermes.protocol.ManagedFileEntry
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class ArtifactTextRenderMode { SOURCE, HTML, SVG }

sealed interface ArtifactPreviewContent {
    val name: String
    val mimeType: String

    data class Text(
        override val name: String,
        override val mimeType: String,
        val text: String,
        val renderMode: ArtifactTextRenderMode,
    ) : ArtifactPreviewContent

    data class Binary(
        override val name: String,
        override val mimeType: String,
        val bytes: ByteArray,
    ) : ArtifactPreviewContent

    data class External(
        override val name: String,
        val url: String,
    ) : ArtifactPreviewContent {
        override val mimeType: String = "text/uri-list"
    }
}

@Singleton
class ArtifactPreviewRepository @Inject constructor(
    private val mediaCache: AuthenticatedArtifactMediaCache,
) {
    suspend fun load(config: BackendConfig, artifact: DetectedArtifact): ArtifactPreviewContent {
        inlineArtifactPreview(artifact)?.let { return it }
        val path = artifact.value.trim()
        require(path.startsWith('/')) { "Artifact content is not an authenticated Hermes path" }

        val entry = ManagedFileEntry(
            name = artifact.label,
            path = path,
            isDirectory = false,
            mimeType = artifact.mimeType,
        )
        return when (previewKind(entry)) {
            WorkspacePreviewKind.TEXT,
            WorkspacePreviewKind.HTML,
            -> mediaCache.readText(config, path).let { preview ->
                ArtifactPreviewContent.Text(
                    name = artifact.label,
                    mimeType = preview.mimeType,
                    text = preview.text,
                    renderMode = when {
                        artifact.kind == DetectedArtifactKind.SVG || preview.mimeType.normalizedMime() == "image/svg+xml" ->
                            ArtifactTextRenderMode.SVG
                        artifact.kind == DetectedArtifactKind.HTML || preview.mimeType.normalizedMime() == "text/html" ->
                            ArtifactTextRenderMode.HTML
                        else -> ArtifactTextRenderMode.SOURCE
                    },
                )
            }
            WorkspacePreviewKind.IMAGE,
            WorkspacePreviewKind.PDF,
            null,
            -> mediaCache.load(
                config,
                ArtifactMediaRequest(
                    profileId = artifact.origin.profileId,
                    sessionId = artifact.origin.sessionId,
                    contentIdentity = artifact.id,
                    path = path,
                    expectedMimeType = artifact.mimeType,
                ),
            ).let { cached ->
                ArtifactPreviewContent.Binary(
                    name = artifact.label,
                    mimeType = cached.mimeType,
                    bytes = cached.file.readBytes(),
                )
            }
        }
    }
}

internal fun inlineArtifactPreview(artifact: DetectedArtifact): ArtifactPreviewContent? {
    val value = artifact.value.trim()
    val mimeType = artifact.mimeType?.normalizedMime()
    if (
        artifact.source == DetectedArtifactSource.FENCED_CODE &&
        artifact.kind in setOf(DetectedArtifactKind.CODE, DetectedArtifactKind.HTML, DetectedArtifactKind.SVG)
    ) {
        return ArtifactPreviewContent.Text(
            name = artifact.label,
            mimeType = mimeType ?: when (artifact.kind) {
                DetectedArtifactKind.HTML -> "text/html"
                DetectedArtifactKind.SVG -> "image/svg+xml"
                else -> "text/plain"
            },
            text = value,
            renderMode = when (artifact.kind) {
                DetectedArtifactKind.HTML -> ArtifactTextRenderMode.HTML
                DetectedArtifactKind.SVG -> ArtifactTextRenderMode.SVG
                else -> ArtifactTextRenderMode.SOURCE
            },
        )
    }
    if (value.startsWith("data:", ignoreCase = true)) {
        val header = value.substringBefore(',', missingDelimiterValue = "")
        val dataMime = header.removePrefix("data:").substringBefore(';').lowercase().trim()
        if (dataMime.isBlank()) throw IOException("Artifact data has no media type")
        val bytes = decodeDataUrl(value, MAX_INLINE_ARTIFACT_BYTES)
        return ArtifactPreviewContent.Binary(artifact.label, dataMime, bytes)
    }
    safeExternalUrl(value)?.let { safe ->
        return ArtifactPreviewContent.External(artifact.label, safe)
    }
    return null
}

internal fun ArtifactPreviewContent.exportBytes(): ByteArray = when (this) {
    is ArtifactPreviewContent.Text -> text.toByteArray(Charsets.UTF_8)
    is ArtifactPreviewContent.Binary -> bytes
    is ArtifactPreviewContent.External -> url.toByteArray(Charsets.UTF_8)
}

private fun String.normalizedMime(): String = lowercase().substringBefore(';').trim()

private const val MAX_INLINE_ARTIFACT_BYTES = 16L * 1024L * 1024L

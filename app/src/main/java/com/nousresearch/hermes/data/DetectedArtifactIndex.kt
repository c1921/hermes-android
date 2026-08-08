package com.nousresearch.hermes.data

import com.nousresearch.hermes.domain.DetectedArtifact
import com.nousresearch.hermes.domain.DetectedArtifactKind
import com.nousresearch.hermes.domain.DetectedArtifactRepository
import com.nousresearch.hermes.domain.DetectedArtifactScope
import com.nousresearch.hermes.protocol.ManagedFileEntry
import com.nousresearch.hermes.protocol.ProtocolMessage
import com.nousresearch.hermes.network.HermesRestClient

private const val MAX_INDEX_SESSIONS = 30

enum class ArtifactIndexFilter { ALL, IMAGES, FILES, LINKS }

data class ArtifactIndexScope(
    val backendId: String,
    val profileId: String,
)

data class DetectedArtifactSession(
    val sessionId: String,
    val title: String,
    val timestamp: Double,
    val messages: List<ProtocolMessage>,
    val managedFiles: List<ManagedFileEntry> = emptyList(),
)

data class DetectedArtifactIndexEntry(
    val artifact: DetectedArtifact,
    val sessionTitle: String,
    val sessionTimestamp: Double,
)

data class DetectedArtifactIndexSnapshot(
    val backendId: String,
    val profileId: String,
    val sessionsLoaded: Int,
    val entries: List<DetectedArtifactIndexEntry>,
) {
    fun search(
        filter: ArtifactIndexFilter = ArtifactIndexFilter.ALL,
        query: String = "",
    ): List<DetectedArtifactIndexEntry> = DetectedArtifactIndex.search(entries, filter, query)
}

fun interface DetectedArtifactSessionLoader {
    suspend fun load(
        config: BackendConfig,
        selectedProfile: String,
        credential: String,
        limit: Int,
    ): List<DetectedArtifactSession>
}

/** Pure projection plus the authenticated, bounded loading seam used by the Artifacts destination. */
class DetectedArtifactIndex(
    private val loader: DetectedArtifactSessionLoader,
) {
    suspend fun load(
        config: BackendConfig,
        selectedProfile: String,
        credential: String,
    ): DetectedArtifactIndexSnapshot {
        require(config.id.isNotBlank()) { "Backend identity is required" }
        require(selectedProfile.isNotBlank()) { "Selected profile is required" }
        require(credential.isNotBlank()) { "Authenticated credential is required" }
        val profile = selectedProfile.trim()
        val sessions = loader.load(config, profile, credential, MAX_INDEX_SESSIONS)
            .take(MAX_INDEX_SESSIONS)
        return project(ArtifactIndexScope(config.id, profile), sessions)
    }

    companion object {
        fun project(
            scope: ArtifactIndexScope,
            sessions: List<DetectedArtifactSession>,
        ): DetectedArtifactIndexSnapshot {
            require(scope.backendId.isNotBlank()) { "Backend identity is required" }
            require(scope.profileId.isNotBlank()) { "Profile identity is required" }
            val normalizedScope = ArtifactIndexScope(scope.backendId.trim(), scope.profileId.trim())
            val boundedSessions = sessions
                .asSequence()
                .filter { it.sessionId.isNotBlank() }
                .sortedWith(
                    compareByDescending<DetectedArtifactSession> { it.timestamp.finiteOrZero() }
                        .thenBy { it.sessionId },
                )
                .take(MAX_INDEX_SESSIONS)
                .toList()
            val entries = boundedSessions.flatMap { session ->
                val sessionId = session.sessionId.trim()
                val title = session.title.trim().ifBlank { "Untitled session" }
                DetectedArtifactRepository.detect(
                    DetectedArtifactScope(normalizedScope.backendId, normalizedScope.profileId, sessionId),
                    session.messages,
                    session.managedFiles,
                ).map { artifact ->
                    DetectedArtifactIndexEntry(
                        artifact = artifact,
                        sessionTitle = title,
                        sessionTimestamp = session.timestamp.finiteOrZero(),
                    )
                }
            }.sortedWith(
                compareByDescending<DetectedArtifactIndexEntry> { it.sessionTimestamp }
                    .thenBy { it.sessionTitle }
                    .thenBy { it.artifact.id },
            )
            return DetectedArtifactIndexSnapshot(
                backendId = normalizedScope.backendId,
                profileId = normalizedScope.profileId,
                sessionsLoaded = boundedSessions.size,
                entries = entries,
            )
        }

        fun search(
            entries: List<DetectedArtifactIndexEntry>,
            filter: ArtifactIndexFilter = ArtifactIndexFilter.ALL,
            query: String = "",
        ): List<DetectedArtifactIndexEntry> {
            val normalizedQuery = query.trim()
            return entries.filter { entry ->
                filter.matches(entry.artifact) && (
                    normalizedQuery.isEmpty() ||
                        entry.artifact.label.contains(normalizedQuery, ignoreCase = true) ||
                        entry.artifact.value.contains(normalizedQuery, ignoreCase = true) ||
                        entry.sessionTitle.contains(normalizedQuery, ignoreCase = true)
                    )
            }
        }
    }
}

/** Adapter for the shipped REST session and message endpoints. Credentials never enter the projection. */
class HermesArtifactSessionLoader(
    private val rest: HermesRestClient,
) : DetectedArtifactSessionLoader {
    override suspend fun load(
        config: BackendConfig,
        selectedProfile: String,
        credential: String,
        limit: Int,
    ): List<DetectedArtifactSession> {
        require(selectedProfile.isNotBlank()) { "Selected profile is required" }
        require(credential.isNotBlank()) { "Authenticated credential is required" }
        val profile = selectedProfile.trim()
        val boundedLimit = limit.coerceIn(1, MAX_INDEX_SESSIONS)
        val sessions = rest.sessions(
            config,
            credential,
            limit = boundedLimit,
            offset = 0,
            profile = profile,
        ).sessions
            .asSequence()
            .filter { it.profile.normalizedFor(profile) == profile }
            .take(MAX_INDEX_SESSIONS)
            .toList()
        return buildList {
            sessions.forEach { session ->
                val sessionId = session.durableId.trim().takeIf(String::isNotEmpty) ?: return@forEach
                val messages = rest.sessionMessages(config, credential, sessionId, profile).messages
                add(
                    DetectedArtifactSession(
                        sessionId = sessionId,
                        title = session.displayTitle,
                        timestamp = maxOf(session.lastActive, session.startedAt).finiteOrZero(),
                        messages = messages,
                    ),
                )
            }
        }
    }
}

private fun String?.normalizedFor(selectedProfile: String): String =
    this?.trim()?.takeIf(String::isNotEmpty) ?: selectedProfile

private fun ArtifactIndexFilter.matches(artifact: DetectedArtifact): Boolean = when (this) {
    ArtifactIndexFilter.ALL -> true
    ArtifactIndexFilter.IMAGES -> artifact.kind == DetectedArtifactKind.IMAGE
    ArtifactIndexFilter.LINKS -> artifact.kind == DetectedArtifactKind.LINK
    // Desktop's file tab is the non-image/non-link artifact bucket. This keeps fenced code and HTML/SVG visible.
    ArtifactIndexFilter.FILES -> artifact.kind != DetectedArtifactKind.IMAGE && artifact.kind != DetectedArtifactKind.LINK
}

private fun Double.finiteOrZero(): Double = takeIf { it.isFinite() } ?: 0.0

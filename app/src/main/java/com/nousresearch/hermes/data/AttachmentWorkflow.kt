package com.nousresearch.hermes.data

enum class AttachmentPhase { VALIDATING, READING, UPLOADING, READY, ERROR }

data class AttachmentScope(
    val backendId: String,
    val profile: String,
    val runtimeSessionId: String?,
)

data class PendingAttachment(
    val id: String,
    val label: String,
    val mimeType: String = "",
    val byteCount: Int = 0,
    val bytesRead: Int = 0,
    val declaredSize: Long? = null,
    val phase: AttachmentPhase = AttachmentPhase.VALIDATING,
    val error: String? = null,
    val refText: String? = null,
    val queuedImagePaths: List<String> = emptyList(),
    val sourceUri: String? = null,
    val scope: AttachmentScope,
) {
    val active: Boolean
        get() = phase == AttachmentPhase.VALIDATING || phase == AttachmentPhase.READING || phase == AttachmentPhase.UPLOADING
    val ready: Boolean get() = phase == AttachmentPhase.READY
}

internal fun List<PendingAttachment>.readyToSend(scope: AttachmentScope): Boolean =
    all { it.ready && it.matches(scope) }

internal fun PendingAttachment.matches(scope: AttachmentScope): Boolean = this.scope == scope

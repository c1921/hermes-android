package com.nousresearch.hermes.domain

import kotlinx.serialization.Serializable

@Serializable
data class QueuedPrompt(
    val id: String,
    val text: String,
    val queuedAtEpochMillis: Long,
    val autoDrainFailures: Int = 0,
)

object ComposerQueue {
    const val MAX_ENTRIES = 20
    const val MAX_TEXT_CHARACTERS = 20_000
    const val MAX_AUTO_DRAIN_ATTEMPTS = 4

    fun enqueue(queue: List<QueuedPrompt>, entry: QueuedPrompt): List<QueuedPrompt> =
        requireNotNull(tryEnqueue(queue, entry)) { "Pending-message queue rejected the entry" }

    fun requireValid(queue: List<QueuedPrompt>): List<QueuedPrompt> {
        require(queue.size <= MAX_ENTRIES) { "Pending-message queue is too large" }
        return queue.fold(emptyList()) { valid, entry ->
            val next = requireNotNull(tryEnqueue(valid, entry)) { "Pending-message queue contains an invalid entry" }
            require(next.last() == entry) { "Pending-message queue contains a modified entry" }
            next
        }
    }

    fun tryEnqueue(queue: List<QueuedPrompt>, entry: QueuedPrompt): List<QueuedPrompt>? {
        val normalized = entry.copy(
            text = entry.text.trim().take(MAX_TEXT_CHARACTERS),
            autoDrainFailures = entry.autoDrainFailures.coerceIn(0, MAX_AUTO_DRAIN_ATTEMPTS),
        )
        if (
            queue.size >= MAX_ENTRIES ||
            normalized.id.isBlank() ||
            normalized.text.isBlank() ||
            queue.any { it.id == normalized.id }
        ) {
            return null
        }
        return queue + normalized
    }

    fun updateText(queue: List<QueuedPrompt>, id: String, text: String): List<QueuedPrompt> {
        val normalized = text.trim().take(MAX_TEXT_CHARACTERS)
        require(normalized.isNotEmpty()) { "Queued messages cannot be blank" }
        require(queue.any { it.id == id }) { "Queued message was not found" }
        return queue.map { entry ->
            if (entry.id == id) entry.copy(text = normalized, autoDrainFailures = 0) else entry
        }
    }

    fun remove(queue: List<QueuedPrompt>, id: String): List<QueuedPrompt> = queue.filterNot { it.id == id }

    fun promote(queue: List<QueuedPrompt>, id: String): List<QueuedPrompt> {
        val index = queue.indexOfFirst { it.id == id }
        if (index <= 0) return queue
        val entry = queue[index]
        return listOf(entry) + queue.take(index) + queue.drop(index + 1)
    }

    fun markAutoDrainFailure(queue: List<QueuedPrompt>, id: String): List<QueuedPrompt> = queue.map { entry ->
        if (entry.id == id) {
            entry.copy(autoDrainFailures = (entry.autoDrainFailures + 1).coerceAtMost(MAX_AUTO_DRAIN_ATTEMPTS))
        } else {
            entry
        }
    }

    fun resetFailures(queue: List<QueuedPrompt>, id: String): List<QueuedPrompt> = queue.map { entry ->
        if (entry.id == id) entry.copy(autoDrainFailures = 0) else entry
    }

    fun shouldAutoDrain(isBusy: Boolean, queue: List<QueuedPrompt>): Boolean =
        !isBusy && queue.firstOrNull()?.autoDrainFailures?.let { it < MAX_AUTO_DRAIN_ATTEMPTS } == true
}

package com.nousresearch.hermes.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nousresearch.hermes.domain.ComposerQueue
import com.nousresearch.hermes.domain.QueuedPrompt
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.composerQueueDataStore by preferencesDataStore("hermes_composer_queue")

data class ComposerQueueContext(
    val backendId: String,
    val profile: String,
    val sessionId: String,
) {
    val backendPrefix: String
        get() = "queue.v1.${backendId.queueSha256().take(16)}."

    val storageKey: String
        get() = backendPrefix + "$backendId\u0000$profile\u0000$sessionId".queueSha256()
}

private fun String.queueSha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray())
    .joinToString("") { "%02x".format(it) }

@Singleton
class ComposerQueueStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val serializer = ListSerializer(QueuedPrompt.serializer())

    suspend fun get(queue: ComposerQueueContext): List<QueuedPrompt> {
        val raw = context.composerQueueDataStore.data.first()[stringPreferencesKey(queue.storageKey)] ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
            .take(ComposerQueue.MAX_ENTRIES)
            .mapNotNull { entry -> ComposerQueue.tryEnqueue(emptyList(), entry)?.singleOrNull() }
            .distinctBy(QueuedPrompt::id)
    }

    suspend fun put(queue: ComposerQueueContext, entries: List<QueuedPrompt>) {
        require(entries.size <= ComposerQueue.MAX_ENTRIES) { "Pending-message queue is too large" }
        val normalized = entries.fold(emptyList<QueuedPrompt>()) { result, entry ->
            ComposerQueue.tryEnqueue(result, entry) ?: error("Pending-message queue contains an invalid entry")
        }
        context.composerQueueDataStore.edit { preferences ->
            val key = stringPreferencesKey(queue.storageKey)
            if (normalized.isEmpty()) preferences.remove(key) else preferences[key] = json.encodeToString(serializer, normalized)
        }
    }

    suspend fun remove(queue: ComposerQueueContext) {
        context.composerQueueDataStore.edit { it.remove(stringPreferencesKey(queue.storageKey)) }
    }

    suspend fun removeBackend(backendId: String) {
        val prefix = ComposerQueueContext(backendId, "", "").backendPrefix
        context.composerQueueDataStore.edit { preferences ->
            preferences.asMap().keys.map { it.name }.filter { it.startsWith(prefix) }.forEach {
                preferences.remove(stringPreferencesKey(it))
            }
        }
    }
}

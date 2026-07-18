package com.nousresearch.hermes.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.draftDataStore by preferencesDataStore("hermes_drafts")

data class DraftContext(
    val backendId: String,
    val profile: String,
    val sessionId: String?,
) {
    val backendPrefix: String
        get() = "draft.v1.${backendId.sha256().take(16)}."

    val storageKey: String
        get() {
            val sessionIdentity = sessionId?.let { "stored:$it" } ?: "new:"
            val identity = "$backendId\u0000$profile\u0000$sessionIdentity"
            return backendPrefix + identity.sha256()
        }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray())
    .joinToString("") { "%02x".format(it) }

@Singleton
class DraftStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun get(draft: DraftContext): String =
        context.draftDataStore.data.first()[stringPreferencesKey(draft.storageKey)].orEmpty()

    suspend fun put(draft: DraftContext, value: String) {
        require(value.length <= MAX_DRAFT_CHARACTERS) { "Draft is too large to persist" }
        context.draftDataStore.edit { preferences ->
            val key = stringPreferencesKey(draft.storageKey)
            if (value.isBlank()) preferences.remove(key) else preferences[key] = value
        }
    }

    suspend fun remove(draft: DraftContext) {
        context.draftDataStore.edit { it.remove(stringPreferencesKey(draft.storageKey)) }
    }

    suspend fun removeBackend(backendId: String) {
        val prefix = DraftContext(backendId, "", null).backendPrefix
        context.draftDataStore.edit { preferences ->
            preferences.asMap().keys.map { it.name }.filter { it.startsWith(prefix) }.forEach {
                preferences.remove(stringPreferencesKey(it))
            }
        }
    }

    companion object {
        const val MAX_DRAFT_CHARACTERS = 20_000
    }
}

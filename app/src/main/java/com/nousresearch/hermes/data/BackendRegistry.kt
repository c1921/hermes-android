package com.nousresearch.hermes.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.backendDataStore by preferencesDataStore("hermes_backends")

@Singleton
class BackendRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    val backends: Flow<List<BackendConfig>> = context.backendDataStore.data.map { preferences ->
        preferences[BACKENDS]?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(BackendConfig.serializer()), raw) }
                .getOrDefault(emptyList())
        } ?: emptyList()
    }

    val activeBackendId: Flow<String?> = context.backendDataStore.data.map { it[ACTIVE] }

    suspend fun save(config: BackendConfig) {
        context.backendDataStore.edit { preferences ->
            val current = preferences[BACKENDS]?.let { raw ->
                runCatching { json.decodeFromString(ListSerializer(BackendConfig.serializer()), raw) }
                    .getOrDefault(emptyList())
            }.orEmpty()
            val next = current.filterNot { it.id == config.id } + config
            preferences[BACKENDS] = json.encodeToString(ListSerializer(BackendConfig.serializer()), next)
            preferences[ACTIVE] = config.id
        }
    }

    suspend fun select(id: String) {
        context.backendDataStore.edit { it[ACTIVE] = id }
    }

    suspend fun remove(id: String) {
        context.backendDataStore.edit { preferences ->
            val current = preferences[BACKENDS]?.let { raw ->
                runCatching { json.decodeFromString(ListSerializer(BackendConfig.serializer()), raw) }
                    .getOrDefault(emptyList())
            }.orEmpty()
            preferences[BACKENDS] = json.encodeToString(
                ListSerializer(BackendConfig.serializer()),
                current.filterNot { it.id == id },
            )
            if (preferences[ACTIVE] == id) preferences.remove(ACTIVE)
        }
    }

    private companion object {
        val BACKENDS = stringPreferencesKey("backends.v1")
        val ACTIVE = stringPreferencesKey("active_backend.v1")
    }
}


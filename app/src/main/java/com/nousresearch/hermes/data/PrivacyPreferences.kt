package com.nousresearch.hermes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.privacyDataStore by preferencesDataStore("hermes_privacy")

@Singleton
class PrivacyPreferences internal constructor(
    private val store: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.privacyDataStore)

    val secureScreen: Flow<Boolean> = store.data
        .map { it[SECURE_SCREEN] ?: false }
        .catch { emit(true) }

    suspend fun setSecureScreen(enabled: Boolean) {
        store.edit { it[SECURE_SCREEN] = enabled }
    }

    private companion object {
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
    }
}

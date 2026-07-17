package com.gongpx.aistockanalyst.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gongpx.aistockanalyst.model.AppLanguage
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

interface AppLanguageSettingsStore {
    val language: Flow<AppLanguage>

    suspend fun setLanguage(language: AppLanguage)
}

class DataStoreAppLanguageSettings(
    private val dataStore: DataStore<Preferences>,
) : AppLanguageSettingsStore {
    override val language: Flow<AppLanguage> = dataStore.data
        .catch { failure ->
            if (failure is IOException) {
                throw SettingsStorageException("Unable to read language settings", failure)
            }
            throw failure
        }
        .map { preferences ->
            preferences[APP_LANGUAGE]
                ?.let(::parseLanguage)
                ?: AppLanguage.SYSTEM
        }

    override suspend fun setLanguage(language: AppLanguage) {
        try {
            dataStore.edit { preferences ->
                preferences[APP_LANGUAGE] = language.name
            }
        } catch (failure: IOException) {
            throw SettingsStorageException("Unable to persist language settings", failure)
        }
    }

    companion object {
        private val APP_LANGUAGE = stringPreferencesKey("app_language")
    }
}

private fun parseLanguage(value: String): AppLanguage = try {
    AppLanguage.valueOf(value)
} catch (failure: IllegalArgumentException) {
    throw SettingsStorageException("Stored language setting is not supported", failure)
}

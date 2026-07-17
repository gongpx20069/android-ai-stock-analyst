package com.gongpx.aistockanalyst.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gongpx.aistockanalyst.model.AppLanguage
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AppLanguageSettingsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `language defaults to system and persists explicit selection`() = runTest {
        val dataStore = testDataStore(backgroundScope)
        val store = DataStoreAppLanguageSettings(dataStore)

        assertEquals(AppLanguage.SYSTEM, store.language.first())

        store.setLanguage(AppLanguage.SIMPLIFIED_CHINESE)

        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, store.language.first())
    }

    @Test
    fun `resetting data sources preserves unrelated language preference`() {
        val languageKey = stringPreferencesKey("app_language")
        val preferences = mutablePreferencesOf(
            languageKey to AppLanguage.ENGLISH.name,
            stringPreferencesKey("quote_provider") to "SINA",
            stringPreferencesKey("chart_provider") to "ALPACA_IEX",
            stringPreferencesKey("valuation_provider") to "YAHOO_FINANCE",
        )

        clearDataSourcePreferences(preferences)

        assertEquals(AppLanguage.ENGLISH.name, preferences[languageKey])
        assertEquals(1, preferences.asMap().size)
    }

    private fun testDataStore(scope: CoroutineScope) =
        File.createTempFile("settings-", ".preferences_pb", temporaryFolder.root).let { file ->
            file.delete()
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            )
        }
}

package com.gongpx.aistockanalyst.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gongpx.aistockanalyst.model.ChartProvider
import com.gongpx.aistockanalyst.model.MarketDataSourceSettings
import com.gongpx.aistockanalyst.model.QuoteProvider
import com.gongpx.aistockanalyst.model.ValuationProvider
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface MarketDataSourceSettingsStore {
    val settings: Flow<MarketDataSourceSettings>

    suspend fun current(): MarketDataSourceSettings = settings.first()

    suspend fun setQuoteProvider(provider: QuoteProvider)

    suspend fun setChartProvider(provider: ChartProvider)

    suspend fun setValuationProvider(provider: ValuationProvider)

    suspend fun resetToDefaults()
}

class DataStoreMarketDataSourceSettings(
    private val dataStore: DataStore<Preferences>,
) : MarketDataSourceSettingsStore {
    override val settings: Flow<MarketDataSourceSettings> = dataStore.data
        .catch { failure ->
            if (failure is IOException) {
                throw SettingsStorageException("Unable to read data source settings", failure)
            }
            throw failure
        }
        .map(::marketDataSourceSettings)

    override suspend fun setQuoteProvider(provider: QuoteProvider) {
        update(QUOTE_PROVIDER, provider.name)
    }

    override suspend fun setChartProvider(provider: ChartProvider) {
        update(CHART_PROVIDER, provider.name)
    }

    override suspend fun setValuationProvider(provider: ValuationProvider) {
        update(VALUATION_PROVIDER, provider.name)
    }

    override suspend fun resetToDefaults() {
        try {
            dataStore.edit { preferences ->
                clearDataSourcePreferences(preferences)
            }
        } catch (failure: IOException) {
            throw SettingsStorageException("Unable to reset data source settings", failure)
        }
    }

    private suspend fun update(
        key: Preferences.Key<String>,
        value: String,
    ) {
        try {
            dataStore.edit { preferences ->
                preferences[key] = value
            }
        } catch (failure: IOException) {
            throw SettingsStorageException("Unable to persist data source settings", failure)
        }
    }

    companion object {
        private val QUOTE_PROVIDER = stringPreferencesKey("quote_provider")
        private val CHART_PROVIDER = stringPreferencesKey("chart_provider")
        private val VALUATION_PROVIDER = stringPreferencesKey("valuation_provider")
    }
}

internal fun marketDataSourceSettings(preferences: Preferences): MarketDataSourceSettings =
    try {
        MarketDataSourceSettings(
            quoteProvider = preferences[stringPreferencesKey("quote_provider")]
                ?.let(QuoteProvider::valueOf)
                ?: QuoteProvider.AUTO,
            chartProvider = preferences[stringPreferencesKey("chart_provider")]
                ?.let(::parseChartProvider)
                ?: ChartProvider.EASTMONEY_EXPERIMENTAL,
            valuationProvider = preferences[stringPreferencesKey("valuation_provider")]
                ?.let(ValuationProvider::valueOf)
                ?: ValuationProvider.YAHOO_FINANCE,
        )
    } catch (failure: IllegalArgumentException) {
        throw SettingsStorageException(
            "Stored data source setting is not supported",
            failure,
        )
    }

internal fun clearDataSourcePreferences(preferences: MutablePreferences) {
    preferences.remove(stringPreferencesKey("quote_provider"))
    preferences.remove(stringPreferencesKey("chart_provider"))
    preferences.remove(stringPreferencesKey("valuation_provider"))
}

private fun parseChartProvider(storedValue: String): ChartProvider = when (storedValue) {
    "TENCENT" -> ChartProvider.NOT_CONFIGURED
    else -> ChartProvider.valueOf(storedValue)
}

class SettingsStorageException(
    message: String,
    cause: Throwable,
) : IOException(message, cause)

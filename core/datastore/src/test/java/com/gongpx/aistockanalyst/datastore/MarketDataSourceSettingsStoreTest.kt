package com.gongpx.aistockanalyst.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gongpx.aistockanalyst.model.AppLanguage
import com.gongpx.aistockanalyst.model.ValuationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketDataSourceSettingsStoreTest {
    @Test
    fun `stored valuation providers are restored and reset preserves language`() {
        val languageKey = stringPreferencesKey("app_language")
        val valuationKey = stringPreferencesKey("valuation_provider")
        val preferences = mutablePreferencesOf(
            languageKey to AppLanguage.SIMPLIFIED_CHINESE.name,
            valuationKey to ValuationProvider.FINNHUB.name,
        )

        assertEquals(
            ValuationProvider.FINNHUB,
            marketDataSourceSettings(preferences).valuationProvider,
        )

        preferences[valuationKey] = ValuationProvider.FMP.name
        assertEquals(
            ValuationProvider.FMP,
            marketDataSourceSettings(preferences).valuationProvider,
        )

        clearDataSourcePreferences(preferences)
        assertEquals(
            ValuationProvider.YAHOO_FINANCE,
            marketDataSourceSettings(preferences).valuationProvider,
        )
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE.name, preferences[languageKey])
    }
}

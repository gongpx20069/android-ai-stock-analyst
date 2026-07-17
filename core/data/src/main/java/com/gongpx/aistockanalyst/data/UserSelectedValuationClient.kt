package com.gongpx.aistockanalyst.data

import com.gongpx.aistockanalyst.datastore.MarketDataSourceSettingsStore
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.model.ValuationProvider
import com.gongpx.aistockanalyst.model.ValuationSnapshot
import com.gongpx.aistockanalyst.network.ValuationClient

class UserSelectedValuationClient(
    private val settingsStore: MarketDataSourceSettingsStore,
    private val yahooClient: ValuationClient,
    private val finnhubClient: ValuationClient,
    private val fmpClient: ValuationClient,
) : ValuationClient {
    override suspend fun fetchValuation(symbol: StockSymbol): ValuationSnapshot =
        when (settingsStore.current().valuationProvider) {
            ValuationProvider.YAHOO_FINANCE -> yahooClient.fetchValuation(symbol)
            ValuationProvider.FINNHUB -> finnhubClient.fetchValuation(symbol)
            ValuationProvider.FMP -> fmpClient.fetchValuation(symbol)
        }
}

package com.gongpx.aistockanalyst.data

import com.gongpx.aistockanalyst.datastore.MarketDataSourceSettingsStore
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.QuoteProvider
import com.gongpx.aistockanalyst.model.QuoteSnapshot
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.network.FallbackQuoteClient
import com.gongpx.aistockanalyst.network.QuoteClient

class UserSelectedQuoteClient(
    private val settingsStore: MarketDataSourceSettingsStore,
    private val tencentClient: QuoteClient,
    private val sinaClient: QuoteClient,
) : QuoteClient {
    private val automaticClient = FallbackQuoteClient(
        primary = tencentClient,
        fallback = sinaClient,
    )

    override suspend fun fetchQuote(
        symbol: StockSymbol,
        exchange: Exchange,
    ): QuoteSnapshot = when (settingsStore.current().quoteProvider) {
        QuoteProvider.AUTO -> automaticClient.fetchQuote(symbol, exchange)
        QuoteProvider.TENCENT -> tencentClient.fetchQuote(symbol, exchange)
        QuoteProvider.SINA -> sinaClient.fetchQuote(symbol, exchange)
    }
}

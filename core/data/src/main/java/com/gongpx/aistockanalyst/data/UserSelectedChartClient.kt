package com.gongpx.aistockanalyst.data

import com.gongpx.aistockanalyst.datastore.MarketDataSourceSettingsStore
import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.ChartProvider
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.network.ChartClient
import java.io.IOException
import java.time.Instant

class UserSelectedChartClient(
    private val settingsStore: MarketDataSourceSettingsStore,
    private val alpacaClient: ChartClient,
) : ChartClient {
    override suspend fun fetchBars(
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        start: Instant,
        endExclusive: Instant,
    ): List<PriceBar> = when (settingsStore.current().chartProvider) {
        ChartProvider.NOT_CONFIGURED -> throw ChartProviderNotConfiguredException()
        ChartProvider.ALPACA_IEX -> alpacaClient.fetchBars(
            symbol = symbol,
            exchange = exchange,
            interval = interval,
            start = start,
            endExclusive = endExclusive,
        )
    }
}

class ChartProviderNotConfiguredException : IOException(
    "Chart provider is not configured",
)

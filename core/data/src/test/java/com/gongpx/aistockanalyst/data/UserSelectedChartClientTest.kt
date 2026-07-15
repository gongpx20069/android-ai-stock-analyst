package com.gongpx.aistockanalyst.data

import com.gongpx.aistockanalyst.datastore.MarketDataSourceSettingsStore
import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.ChartProvider
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.MarketDataSourceSettings
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.QuoteProvider
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.model.ValuationProvider
import com.gongpx.aistockanalyst.network.ChartClient
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UserSelectedChartClientTest {
    private val symbol = StockSymbol.of("AAPL")
    private val start = Instant.parse("2026-07-15T13:30:00Z")
    private val end = Instant.parse("2026-07-15T13:31:00Z")

    @Test
    fun `Alpaca selection delegates only to Alpaca client`() = runBlocking {
        val settings = FakeChartSettingsStore(ChartProvider.ALPACA_IEX)
        val alpaca = RecordingChartClient()
        val client = UserSelectedChartClient(settings, alpaca)

        client.fetchBars(
            symbol,
            Exchange.NASDAQ,
            BarInterval.ONE_MINUTE,
            start,
            end,
        )

        assertEquals(1, alpaca.callCount)
    }

    @Test(expected = ChartProviderNotConfiguredException::class)
    fun `unconfigured selection fails explicitly`() {
        runBlocking {
            UserSelectedChartClient(
                settingsStore = FakeChartSettingsStore(ChartProvider.NOT_CONFIGURED),
                alpacaClient = RecordingChartClient(),
            ).fetchBars(
                symbol,
                Exchange.NASDAQ,
                BarInterval.ONE_MINUTE,
                start,
                end,
            )
        }
    }
}

private class FakeChartSettingsStore(
    chartProvider: ChartProvider,
) : MarketDataSourceSettingsStore {
    private val state = MutableStateFlow(
        MarketDataSourceSettings(chartProvider = chartProvider),
    )
    override val settings: Flow<MarketDataSourceSettings> = state

    override suspend fun setQuoteProvider(provider: QuoteProvider) {
        state.value = state.value.copy(quoteProvider = provider)
    }

    override suspend fun setChartProvider(provider: ChartProvider) {
        state.value = state.value.copy(chartProvider = provider)
    }

    override suspend fun setValuationProvider(provider: ValuationProvider) {
        state.value = state.value.copy(valuationProvider = provider)
    }

    override suspend fun resetToDefaults() {
        state.value = MarketDataSourceSettings()
    }
}

private class RecordingChartClient : ChartClient {
    var callCount: Int = 0

    override suspend fun fetchBars(
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        start: Instant,
        endExclusive: Instant,
    ): List<PriceBar> {
        callCount += 1
        return emptyList()
    }
}

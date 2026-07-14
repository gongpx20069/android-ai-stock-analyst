package com.gongpx.aistockanalyst.data

import com.gongpx.aistockanalyst.datastore.MarketDataSourceSettingsStore
import com.gongpx.aistockanalyst.model.ChartProvider
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.MarketDataSourceSettings
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.QuoteProvider
import com.gongpx.aistockanalyst.model.QuoteSnapshot
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.model.ValuationProvider
import com.gongpx.aistockanalyst.network.QuoteClient
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UserSelectedQuoteClientTest {
    private val symbol = StockSymbol.of("AAPL")

    @Test
    fun `automatic selection falls back from Tencent to Sina`() = runBlocking {
        val settings = FakeSettingsStore(QuoteProvider.AUTO)
        val client = UserSelectedQuoteClient(
            settingsStore = settings,
            tencentClient = FailingQuoteClient,
            sinaClient = FixedQuoteClient(DataSource.SINA),
        )

        val result = client.fetchQuote(symbol, Exchange.NASDAQ)

        assertEquals(DataSource.SINA, result.source)
    }

    @Test
    fun `explicit Tencent selection does not silently use Sina`() {
        val settings = FakeSettingsStore(QuoteProvider.TENCENT)
        val client = UserSelectedQuoteClient(
            settingsStore = settings,
            tencentClient = FailingQuoteClient,
            sinaClient = FixedQuoteClient(DataSource.SINA),
        )

        assertThrows(IOException::class.java) {
            runBlocking {
                client.fetchQuote(symbol, Exchange.NASDAQ)
            }
        }
    }
}

private class FakeSettingsStore(
    quoteProvider: QuoteProvider,
) : MarketDataSourceSettingsStore {
    private val state = MutableStateFlow(
        MarketDataSourceSettings(quoteProvider = quoteProvider),
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

private class FixedQuoteClient(
    private val source: DataSource,
) : QuoteClient {
    override suspend fun fetchQuote(
        symbol: StockSymbol,
        exchange: Exchange,
    ): QuoteSnapshot = QuoteSnapshot(
        symbol = symbol,
        exchange = exchange,
        currentPrice = 200.0,
        previousClose = 198.0,
        open = 199.0,
        dayHigh = 201.0,
        dayLow = 197.0,
        change = 2.0,
        changePercent = 0.0101,
        volume = 10_000L,
        ttmPe = 30.0,
        marketCap = 3_000_000_000_000.0,
        fiftyTwoWeekLow = 150.0,
        fiftyTwoWeekHigh = 220.0,
        asOf = Instant.parse("2026-07-14T15:00:00Z"),
        fetchedAt = Instant.parse("2026-07-14T15:00:05Z"),
        staleAfter = Instant.parse("2026-07-14T15:01:05Z"),
        parseStatus = ParseStatus.VALID,
        source = source,
    )
}

private object FailingQuoteClient : QuoteClient {
    override suspend fun fetchQuote(
        symbol: StockSymbol,
        exchange: Exchange,
    ): QuoteSnapshot = throw IOException("provider unavailable")
}

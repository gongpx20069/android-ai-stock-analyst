package com.gongpx.aistockanalyst.data

import com.gongpx.aistockanalyst.datastore.MarketDataSourceSettingsStore
import com.gongpx.aistockanalyst.model.AnalystTargets
import com.gongpx.aistockanalyst.model.ChartProvider
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.MarketDataSourceSettings
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.QuoteProvider
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.model.ValuationProvider
import com.gongpx.aistockanalyst.model.ValuationSnapshot
import com.gongpx.aistockanalyst.network.ValuationClient
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UserSelectedValuationClientTest {
    @Test
    fun `explicit provider routes only to selected client`() = runBlocking {
        val settings = MutableValuationSettingsStore(ValuationProvider.FINNHUB)
        val calls = mutableListOf<DataSource>()
        val client = UserSelectedValuationClient(
            settingsStore = settings,
            yahooClient = recordingClient(DataSource.YAHOO_FINANCE, calls),
            finnhubClient = recordingClient(DataSource.FINNHUB, calls),
            fmpClient = recordingClient(DataSource.FMP, calls),
        )

        val snapshot = client.fetchValuation(StockSymbol.of("AAPL"))

        assertEquals(DataSource.FINNHUB, snapshot.source)
        assertEquals(listOf(DataSource.FINNHUB), calls)
    }

    @Test(expected = IOException::class)
    fun `explicit provider failure does not fall back`() {
        runBlocking {
            val calls = mutableListOf<DataSource>()
            UserSelectedValuationClient(
                settingsStore = MutableValuationSettingsStore(ValuationProvider.FMP),
                yahooClient = recordingClient(DataSource.YAHOO_FINANCE, calls),
                finnhubClient = recordingClient(DataSource.FINNHUB, calls),
                fmpClient = object : ValuationClient {
                    override suspend fun fetchValuation(
                        symbol: StockSymbol,
                    ): ValuationSnapshot = throw IOException("FMP failed")
                },
            ).fetchValuation(StockSymbol.of("AAPL"))
        }
    }

    private fun recordingClient(
        source: DataSource,
        calls: MutableList<DataSource>,
    ) = object : ValuationClient {
        override suspend fun fetchValuation(symbol: StockSymbol): ValuationSnapshot {
            calls += source
            return snapshot(symbol, source)
        }
    }

    private fun snapshot(
        symbol: StockSymbol,
        source: DataSource,
    ) = ValuationSnapshot(
        symbol = symbol,
        targets = AnalystTargets(null, null, null),
        forwardPe = null,
        recommendationKey = null,
        analystCount = null,
        fiftyDayAverage = null,
        twoHundredDayAverage = null,
        fiftyTwoWeekLow = null,
        fiftyTwoWeekHigh = null,
        marketCap = null,
        averageDailyVolume3Month = null,
        sector = null,
        industry = null,
        asOf = Instant.EPOCH,
        fetchedAt = Instant.EPOCH,
        staleAfter = Instant.EPOCH,
        parseStatus = ParseStatus.PARTIAL,
        source = source,
    )
}

private class MutableValuationSettingsStore(
    valuationProvider: ValuationProvider,
) : MarketDataSourceSettingsStore {
    private val state = MutableStateFlow(
        MarketDataSourceSettings(valuationProvider = valuationProvider),
    )
    override val settings: Flow<MarketDataSourceSettings> = state

    override suspend fun setQuoteProvider(provider: QuoteProvider) = Unit

    override suspend fun setChartProvider(provider: ChartProvider) = Unit

    override suspend fun setValuationProvider(provider: ValuationProvider) {
        state.value = state.value.copy(valuationProvider = provider)
    }

    override suspend fun resetToDefaults() {
        state.value = MarketDataSourceSettings()
    }
}

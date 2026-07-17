package com.gongpx.aistockanalyst.ui

import com.gongpx.aistockanalyst.data.MarketRepository
import com.gongpx.aistockanalyst.data.RefreshResult
import com.gongpx.aistockanalyst.model.AnalystTargets
import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.PriceLevelSnapshot
import com.gongpx.aistockanalyst.model.QuoteSnapshot
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.model.TechnicalIndicatorSnapshot
import com.gongpx.aistockanalyst.model.ValuationSnapshot
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StockDetailViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-07-16T13:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opening stock observes selection and refreshes 400 days of daily history`() =
        runTest(dispatcher) {
            val repository = FakeMarketRepository(now)
            val viewModel = viewModel(repository)

            viewModel.openStock(" aapl ", Exchange.NASDAQ)
            advanceUntilIdle()

            assertEquals("AAPL", viewModel.uiState.value.selection?.symbol?.value)
            assertEquals(BarInterval.ONE_DAY, viewModel.uiState.value.interval)
            assertEquals(listOf(BarInterval.ONE_DAY), repository.observedIntervals)
            assertEquals(
                BarRefresh(
                    interval = BarInterval.ONE_DAY,
                    start = now.minus(Duration.ofDays(400)),
                    endExclusive = now,
                ),
                repository.barRefreshes.single(),
            )
            assertFalse(viewModel.uiState.value.isRefreshing)
            viewModel.closeStock()
        }

    @Test
    fun `changing interval observes and refreshes its configured history range`() =
        runTest(dispatcher) {
            val repository = FakeMarketRepository(now)
            val viewModel = viewModel(repository)
            viewModel.openStock("MSFT", Exchange.NASDAQ)
            advanceUntilIdle()
            repository.barRefreshes.clear()

            viewModel.selectInterval(BarInterval.FIFTEEN_MINUTES)
            advanceUntilIdle()

            assertEquals(BarInterval.FIFTEEN_MINUTES, viewModel.uiState.value.interval)
            assertEquals(
                listOf(BarInterval.ONE_DAY, BarInterval.FIFTEEN_MINUTES),
                repository.observedIntervals,
            )
            assertEquals(
                BarRefresh(
                    interval = BarInterval.FIFTEEN_MINUTES,
                    start = now.minus(Duration.ofDays(30)),
                    endExclusive = now,
                ),
                repository.barRefreshes.single(),
            )
            viewModel.closeStock()
        }

    @Test
    fun `changing interval does not cancel quote valuation and daily refresh`() =
        runTest(dispatcher) {
            val quoteGate = CompletableDeferred<Unit>()
            val repository = FakeMarketRepository(now).apply {
                quoteRefreshGate = quoteGate
            }
            val viewModel = viewModel(repository)

            viewModel.openStock("MSFT", Exchange.NASDAQ)
            runCurrent()
            viewModel.selectInterval(BarInterval.FIFTEEN_MINUTES)
            runCurrent()

            assertTrue(viewModel.uiState.value.isRefreshing)
            quoteGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, repository.quoteRefreshCount)
            assertEquals(
                setOf(BarInterval.ONE_DAY, BarInterval.FIFTEEN_MINUTES),
                repository.barRefreshes.map(BarRefresh::interval).toSet(),
            )
            assertFalse(viewModel.uiState.value.isRefreshing)
            viewModel.closeStock()
        }

    @Test
    fun `cached refresh warning remains visible after refresh completes`() =
        runTest(dispatcher) {
            val repository = FakeMarketRepository(now).apply {
                barsResult = RefreshResult.Cached(
                    value = emptyList(),
                    isStale = true,
                    failureMessage = "IEX unavailable; showing cached bars",
                )
            }
            val viewModel = viewModel(repository)

            viewModel.openStock("NVDA", Exchange.NASDAQ)
            advanceUntilIdle()

            assertEquals(
                "IEX unavailable; showing cached bars",
                viewModel.uiState.value.message,
            )
            assertFalse(viewModel.uiState.value.isRefreshing)
            viewModel.closeStock()
        }

    @Test
    fun `invalid symbol reports error without starting refresh`() = runTest(dispatcher) {
        val repository = FakeMarketRepository(now)
        val viewModel = viewModel(repository)

        viewModel.openStock("\$bad", Exchange.NYSE)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.invalidSymbol)
        assertEquals(null, viewModel.uiState.value.message)
        assertTrue(repository.barRefreshes.isEmpty())
        assertTrue(repository.observedIntervals.isEmpty())
        viewModel.closeStock()
    }

    @Test
    fun `missing chart cache exposes refresh failure`() = runTest(dispatcher) {
        val repository = FakeMarketRepository(now).apply {
            barsFailure = IOException("No chart cache is available")
        }
        val viewModel = viewModel(repository)

        viewModel.openStock("AMD", Exchange.NASDAQ)
        advanceUntilIdle()

        assertEquals("No chart cache is available", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isRefreshing)
        viewModel.closeStock()
    }

    private fun viewModel(repository: MarketRepository) = StockDetailViewModel(
        repository = repository,
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )
}

private data class BarRefresh(
    val interval: BarInterval,
    val start: Instant,
    val endExclusive: Instant,
)

private class FakeMarketRepository(
    private val now: Instant,
) : MarketRepository {
    val observedIntervals = mutableListOf<BarInterval>()
    val barRefreshes = mutableListOf<BarRefresh>()
    var barsResult: RefreshResult<List<PriceBar>> = RefreshResult.Fresh(emptyList())
    var barsFailure: IOException? = null
    var quoteRefreshGate: CompletableDeferred<Unit>? = null
    var quoteRefreshCount: Int = 0

    private val quote = MutableStateFlow<QuoteSnapshot?>(null)
    private val valuation = MutableStateFlow<ValuationSnapshot?>(null)
    private val bars = MutableStateFlow<List<PriceBar>>(emptyList())
    private val technicals = MutableStateFlow<TechnicalIndicatorSnapshot?>(null)
    private val priceLevels = MutableStateFlow<PriceLevelSnapshot?>(null)

    override fun observeQuote(
        symbol: StockSymbol,
        exchange: Exchange,
    ): Flow<QuoteSnapshot?> = quote

    override fun observeValuation(symbol: StockSymbol): Flow<ValuationSnapshot?> = valuation

    override fun observeBars(
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        limit: Int,
    ): Flow<List<PriceBar>> {
        observedIntervals += interval
        return bars
    }

    override fun observeTechnicalIndicators(
        symbol: StockSymbol,
        exchange: Exchange,
    ): Flow<TechnicalIndicatorSnapshot?> = technicals

    override fun observePriceLevels(
        symbol: StockSymbol,
        exchange: Exchange,
    ): Flow<PriceLevelSnapshot?> = priceLevels

    override suspend fun refreshQuote(
        symbol: StockSymbol,
        exchange: Exchange,
    ): RefreshResult<QuoteSnapshot> {
        quoteRefreshCount++
        quoteRefreshGate?.await()
        return RefreshResult.Fresh(
            QuoteSnapshot(
                symbol = symbol,
                exchange = exchange,
                currentPrice = 100.0,
                previousClose = 99.0,
                open = 99.5,
                dayHigh = 101.0,
                dayLow = 98.5,
                change = 1.0,
                changePercent = 1.01,
                volume = 1_000,
                ttmPe = null,
                marketCap = null,
                fiftyTwoWeekLow = null,
                fiftyTwoWeekHigh = null,
                asOf = now,
                fetchedAt = now,
                staleAfter = now.plusSeconds(60),
                parseStatus = ParseStatus.VALID,
                source = DataSource.TENCENT,
            ),
        )
    }

    override suspend fun refreshValuation(
        symbol: StockSymbol,
    ): RefreshResult<ValuationSnapshot> = RefreshResult.Fresh(
        ValuationSnapshot(
            symbol = symbol,
            targets = AnalystTargets(low = 90.0, median = 110.0, high = 130.0),
            forwardPe = 20.0,
            recommendationKey = "buy",
            analystCount = 20,
            fiftyDayAverage = 98.0,
            twoHundredDayAverage = 95.0,
            fiftyTwoWeekLow = 80.0,
            fiftyTwoWeekHigh = 120.0,
            marketCap = 1_000_000L,
            averageDailyVolume3Month = 1_000L,
            sector = "Technology",
            industry = "Semiconductors",
            asOf = now,
            fetchedAt = now,
            staleAfter = now.plus(Duration.ofDays(1)),
            parseStatus = ParseStatus.VALID,
        ),
    )

    override suspend fun refreshBars(
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        start: Instant,
        endExclusive: Instant,
    ): RefreshResult<List<PriceBar>> {
        barRefreshes += BarRefresh(interval, start, endExclusive)
        barsFailure?.let { throw it }
        return barsResult
    }
}

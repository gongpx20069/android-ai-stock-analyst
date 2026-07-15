package com.gongpx.aistockanalyst.data

import com.gongpx.aistockanalyst.database.PriceBarDao
import com.gongpx.aistockanalyst.database.PriceBarEntity
import com.gongpx.aistockanalyst.database.QuoteDao
import com.gongpx.aistockanalyst.database.QuoteEntity
import com.gongpx.aistockanalyst.database.ValuationDao
import com.gongpx.aistockanalyst.database.ValuationEntity
import com.gongpx.aistockanalyst.database.toEntity
import com.gongpx.aistockanalyst.database.toModel
import com.gongpx.aistockanalyst.datastore.MarketDataSourceSettingsStore
import com.gongpx.aistockanalyst.model.AnalystTargets
import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.ChartProvider
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.MarketDataSourceSettings
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.QuoteProvider
import com.gongpx.aistockanalyst.model.QuoteSnapshot
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.model.ValuationProvider
import com.gongpx.aistockanalyst.model.ValuationSnapshot
import com.gongpx.aistockanalyst.network.ChartClient
import com.gongpx.aistockanalyst.network.QuoteClient
import com.gongpx.aistockanalyst.network.ValuationClient
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMarketRepositoryTest {
    @Test
    fun `refresh returns stale cache when all quote providers fail`() = runBlocking {
        val symbol = StockSymbol.of("AAPL")
        val cached = quote(symbol).toEntity()
        val quoteDao = FakeQuoteDao(cached)
        val repository = RoomMarketRepository(
            quoteClient = object : QuoteClient {
                override suspend fun fetchQuote(
                    symbol: StockSymbol,
                    exchange: Exchange,
                ): QuoteSnapshot = throw IOException("offline")
            },
            chartClient = UnusedChartClient,
            valuationClient = UnusedValuationClient,
            settingsStore = AlpacaSettingsStore,
            quoteDao = quoteDao,
            valuationDao = FakeValuationDao(),
            priceBarDao = FakePriceBarDao(),
            clock = Clock.fixed(
                Instant.parse("2026-07-14T15:05:00Z"),
                ZoneOffset.UTC,
            ),
        )

        val result = repository.refreshQuote(symbol, Exchange.NASDAQ)

        assertTrue(result is RefreshResult.Cached)
        result as RefreshResult.Cached
        assertTrue(result.isStale)
        assertEquals(cached.currentPrice, result.value.currentPrice, 0.0)
        assertEquals("offline", result.failureMessage)
    }

    @Test
    fun `partial valuation does not replace last valid cache`() = runBlocking {
        val symbol = StockSymbol.of("AAPL")
        val cached = valuation(symbol, ParseStatus.VALID)
        val valuationDao = FakeValuationDao(cached.toEntity())
        val repository = RoomMarketRepository(
            quoteClient = UnusedQuoteClient,
            chartClient = UnusedChartClient,
            valuationClient = object : ValuationClient {
                override suspend fun fetchValuation(
                    symbol: StockSymbol,
                ): ValuationSnapshot = valuation(symbol, ParseStatus.PARTIAL)
            },
            settingsStore = AlpacaSettingsStore,
            quoteDao = FakeQuoteDao(),
            valuationDao = valuationDao,
            priceBarDao = FakePriceBarDao(),
            clock = Clock.fixed(
                Instant.parse("2026-07-14T15:05:00Z"),
                ZoneOffset.UTC,
            ),
        )

        val result = repository.refreshValuation(symbol)

        assertTrue(result is RefreshResult.Cached)
        assertEquals(ParseStatus.VALID, result.value.parseStatus)
        assertEquals(cached, valuationDao.get(symbol.value)?.toModel())
    }

    @Test
    fun `bar refresh persists completed provider history`() = runBlocking {
        val symbol = StockSymbol.of("AAPL")
        val expected = priceBar(symbol)
        val priceBarDao = FakePriceBarDao().apply {
            upsertAll(listOf(expected.copy(source = DataSource.TENCENT).toEntity()))
        }
        val repository = RoomMarketRepository(
            quoteClient = UnusedQuoteClient,
            chartClient = object : ChartClient {
                override suspend fun fetchBars(
                    symbol: StockSymbol,
                    exchange: Exchange,
                    interval: BarInterval,
                    start: Instant,
                    endExclusive: Instant,
                ): List<PriceBar> = listOf(expected)
            },
            valuationClient = UnusedValuationClient,
            settingsStore = AlpacaSettingsStore,
            quoteDao = FakeQuoteDao(),
            valuationDao = FakeValuationDao(),
            priceBarDao = priceBarDao,
            clock = Clock.fixed(
                Instant.parse("2026-07-14T15:05:00Z"),
                ZoneOffset.UTC,
            ),
        )

        val result = repository.refreshBars(
            symbol = symbol,
            exchange = Exchange.NASDAQ,
            interval = BarInterval.ONE_MINUTE,
            start = expected.start,
            endExclusive = expected.endExclusive,
        )

        assertTrue(result is RefreshResult.Fresh)
        assertEquals(
            listOf(expected),
            priceBarDao.getRange(
                symbol = symbol.value,
                exchange = Exchange.NASDAQ.name,
                interval = BarInterval.ONE_MINUTE.name,
                source = DataSource.ALPACA_IEX.name,
                startEpochMillis = expected.start.toEpochMilli(),
                endExclusiveEpochMillis = expected.endExclusive.toEpochMilli(),
            ).map { it.toModel() },
        )
        assertEquals(
            emptyList<PriceBarEntity>(),
            priceBarDao.getRange(
                symbol = symbol.value,
                exchange = Exchange.NASDAQ.name,
                interval = BarInterval.ONE_MINUTE.name,
                source = DataSource.TENCENT.name,
                startEpochMillis = expected.start.toEpochMilli(),
                endExclusiveEpochMillis = expected.endExclusive.toEpochMilli(),
            ),
        )
        assertEquals(
            listOf(200.0),
            priceBarDao.getRange(
                symbol = symbol.value,
                exchange = Exchange.NASDAQ.name,
                interval = BarInterval.FIVE_MINUTES.name,
                source = DataSource.ALPACA_IEX.name,
                startEpochMillis = expected.start.toEpochMilli(),
                endExclusiveEpochMillis = Instant.parse("2026-07-14T15:05:00Z")
                    .toEpochMilli(),
            ).map(PriceBarEntity::close),
        )
    }

    @Test
    fun `bar refresh returns cached history when provider fails`() = runBlocking {
        val symbol = StockSymbol.of("AAPL")
        val cached = priceBar(symbol)
        val priceBarDao = FakePriceBarDao().apply {
            upsertAll(listOf(cached.toEntity()))
        }
        val repository = RoomMarketRepository(
            quoteClient = UnusedQuoteClient,
            chartClient = object : ChartClient {
                override suspend fun fetchBars(
                    symbol: StockSymbol,
                    exchange: Exchange,
                    interval: BarInterval,
                    start: Instant,
                    endExclusive: Instant,
                ): List<PriceBar> = throw IOException("rate limited")
            },
            valuationClient = UnusedValuationClient,
            settingsStore = AlpacaSettingsStore,
            quoteDao = FakeQuoteDao(),
            valuationDao = FakeValuationDao(),
            priceBarDao = priceBarDao,
            clock = Clock.fixed(
                Instant.parse("2026-07-14T15:05:00Z"),
                ZoneOffset.UTC,
            ),
        )

        val result = repository.refreshBars(
            symbol = symbol,
            exchange = Exchange.NASDAQ,
            interval = BarInterval.ONE_MINUTE,
            start = cached.start,
            endExclusive = cached.endExclusive,
        )

        assertTrue(result is RefreshResult.Cached)
        result as RefreshResult.Cached
        assertEquals(listOf(cached), result.value)
        assertEquals("rate limited", result.failureMessage)
    }

    @Test
    fun `five-minute refresh fetches one-minute bars and persists both intervals`() =
        runBlocking {
            val symbol = StockSymbol.of("AAPL")
            val requestedIntervals = mutableListOf<BarInterval>()
            val oneMinuteBars = (0L..4L).map { minute ->
                priceBar(symbol).copy(
                    start = Instant.parse("2026-07-14T15:00:00Z")
                        .plusSeconds(minute * 60),
                    endExclusive = Instant.parse("2026-07-14T15:01:00Z")
                        .plusSeconds(minute * 60),
                    high = 205.0,
                    close = 200.0 + minute,
                )
            }
            val priceBarDao = FakePriceBarDao()
            val repository = RoomMarketRepository(
                quoteClient = UnusedQuoteClient,
                chartClient = object : ChartClient {
                    override suspend fun fetchBars(
                        symbol: StockSymbol,
                        exchange: Exchange,
                        interval: BarInterval,
                        start: Instant,
                        endExclusive: Instant,
                    ): List<PriceBar> {
                        requestedIntervals += interval
                        return oneMinuteBars
                    }
                },
                valuationClient = UnusedValuationClient,
                settingsStore = AlpacaSettingsStore,
                quoteDao = FakeQuoteDao(),
                valuationDao = FakeValuationDao(),
                priceBarDao = priceBarDao,
                clock = Clock.fixed(
                    Instant.parse("2026-07-14T15:05:00Z"),
                    ZoneOffset.UTC,
                ),
            )

            val result = repository.refreshBars(
                symbol = symbol,
                exchange = Exchange.NASDAQ,
                interval = BarInterval.FIVE_MINUTES,
                start = Instant.parse("2026-07-14T15:00:00Z"),
                endExclusive = Instant.parse("2026-07-14T15:05:00Z"),
            )

            assertEquals(listOf(BarInterval.ONE_MINUTE), requestedIntervals)
            assertTrue(result is RefreshResult.Fresh)
            assertEquals(1, result.value.size)
            assertEquals(BarInterval.FIVE_MINUTES, result.value.single().interval)
            assertEquals(
                5,
                priceBarDao.getRange(
                    symbol = symbol.value,
                    exchange = Exchange.NASDAQ.name,
                    interval = BarInterval.ONE_MINUTE.name,
                    source = DataSource.ALPACA_IEX.name,
                    startEpochMillis = Instant.parse("2026-07-14T15:00:00Z")
                        .toEpochMilli(),
                    endExclusiveEpochMillis = Instant.parse("2026-07-14T15:05:00Z")
                        .toEpochMilli(),
                ).size,
            )
            assertEquals(
                1,
                priceBarDao.getRange(
                    symbol = symbol.value,
                    exchange = Exchange.NASDAQ.name,
                    interval = BarInterval.FIVE_MINUTES.name,
                    source = DataSource.ALPACA_IEX.name,
                    startEpochMillis = Instant.parse("2026-07-14T15:00:00Z")
                        .toEpochMilli(),
                    endExclusiveEpochMillis = Instant.parse("2026-07-14T15:05:00Z")
                        .toEpochMilli(),
                ).size,
            )
        }

    @Test
    fun `five-minute refresh returns compatible derived cache on provider failure`() =
        runBlocking {
            val symbol = StockSymbol.of("AAPL")
            val cached = priceBar(symbol).copy(
                interval = BarInterval.FIVE_MINUTES,
                endExclusive = Instant.parse("2026-07-14T15:05:00Z"),
            )
            val requestedIntervals = mutableListOf<BarInterval>()
            val priceBarDao = FakePriceBarDao().apply {
                upsertAll(listOf(cached.toEntity()))
            }
            val repository = RoomMarketRepository(
                quoteClient = UnusedQuoteClient,
                chartClient = object : ChartClient {
                    override suspend fun fetchBars(
                        symbol: StockSymbol,
                        exchange: Exchange,
                        interval: BarInterval,
                        start: Instant,
                        endExclusive: Instant,
                    ): List<PriceBar> {
                        requestedIntervals += interval
                        throw IOException("offline")
                    }
                },
                valuationClient = UnusedValuationClient,
                settingsStore = AlpacaSettingsStore,
                quoteDao = FakeQuoteDao(),
                valuationDao = FakeValuationDao(),
                priceBarDao = priceBarDao,
                clock = Clock.fixed(
                    Instant.parse("2026-07-14T15:05:00Z"),
                    ZoneOffset.UTC,
                ),
            )

            val result = repository.refreshBars(
                symbol = symbol,
                exchange = Exchange.NASDAQ,
                interval = BarInterval.FIVE_MINUTES,
                start = cached.start,
                endExclusive = cached.endExclusive,
            )

            assertEquals(listOf(BarInterval.ONE_MINUTE), requestedIntervals)
            assertTrue(result is RefreshResult.Cached)
            assertEquals(listOf(cached), result.value)
        }

    @Test
    fun `unaligned five-minute refresh reconciles enclosing boundary buckets`() =
        runBlocking {
            val symbol = StockSymbol.of("AAPL")
            val requestedRanges = mutableListOf<Pair<Instant, Instant>>()
            val oneMinuteBars = (0L..9L).map { minute ->
                priceBar(symbol).copy(
                    start = Instant.parse("2026-07-14T15:00:00Z")
                        .plusSeconds(minute * 60),
                    endExclusive = Instant.parse("2026-07-14T15:01:00Z")
                        .plusSeconds(minute * 60),
                    high = 210.0,
                    close = 200.0 + minute,
                )
            }
            val staleBoundary = priceBar(symbol).copy(
                interval = BarInterval.FIVE_MINUTES,
                endExclusive = Instant.parse("2026-07-14T15:05:00Z"),
                fetchedAt = Instant.parse("2026-07-14T15:00:30Z"),
            )
            val priceBarDao = FakePriceBarDao().apply {
                upsertAll(listOf(staleBoundary.toEntity()))
            }
            val repository = RoomMarketRepository(
                quoteClient = UnusedQuoteClient,
                chartClient = object : ChartClient {
                    override suspend fun fetchBars(
                        symbol: StockSymbol,
                        exchange: Exchange,
                        interval: BarInterval,
                        start: Instant,
                        endExclusive: Instant,
                    ): List<PriceBar> {
                        requestedRanges += start to endExclusive
                        return oneMinuteBars
                    }
                },
                valuationClient = UnusedValuationClient,
                settingsStore = AlpacaSettingsStore,
                quoteDao = FakeQuoteDao(),
                valuationDao = FakeValuationDao(),
                priceBarDao = priceBarDao,
                clock = Clock.fixed(
                    Instant.parse("2026-07-14T15:10:00Z"),
                    ZoneOffset.UTC,
                ),
            )

            val result = repository.refreshBars(
                symbol = symbol,
                exchange = Exchange.NASDAQ,
                interval = BarInterval.FIVE_MINUTES,
                start = Instant.parse("2026-07-14T15:02:00Z"),
                endExclusive = Instant.parse("2026-07-14T15:10:00Z"),
            )

            assertEquals(
                listOf(
                    Instant.parse("2026-07-14T15:00:00Z") to
                        Instant.parse("2026-07-14T15:10:00Z"),
                ),
                requestedRanges,
            )
            assertEquals(1, result.value.size)
            assertEquals(Instant.parse("2026-07-14T15:05:00Z"), result.value.single().start)
            val correctedBoundary = priceBarDao.getRange(
                symbol = symbol.value,
                exchange = Exchange.NASDAQ.name,
                interval = BarInterval.FIVE_MINUTES.name,
                source = DataSource.ALPACA_IEX.name,
                startEpochMillis = Instant.parse("2026-07-14T15:00:00Z").toEpochMilli(),
                endExclusiveEpochMillis = Instant.parse("2026-07-14T15:05:00Z")
                    .toEpochMilli(),
            ).single().toModel()
            assertEquals(204.0, correctedBoundary.close, 0.0)
        }

    @Test
    fun `repository calculates technical indicators from cached daily history`() =
        runBlocking {
            val symbol = StockSymbol.of("AAPL")
            val base = Instant.parse("2026-01-01T05:00:00Z")
            val dailyBars = (1L..15L).map { day ->
                PriceBar(
                    symbol = symbol,
                    exchange = Exchange.NASDAQ,
                    interval = BarInterval.ONE_DAY,
                    start = base.plusSeconds(day * 86_400),
                    endExclusive = base.plusSeconds((day + 1) * 86_400),
                    open = day.toDouble(),
                    high = day.toDouble(),
                    low = day.toDouble(),
                    close = day.toDouble(),
                    volume = 1_000,
                    fetchedAt = Instant.parse("2026-07-14T15:00:00Z"),
                    source = DataSource.ALPACA_IEX,
                )
            }
            val priceBarDao = FakePriceBarDao().apply {
                upsertAll(dailyBars.map(PriceBar::toEntity))
            }
            val repository = RoomMarketRepository(
                quoteClient = UnusedQuoteClient,
                chartClient = UnusedChartClient,
                valuationClient = UnusedValuationClient,
                settingsStore = AlpacaSettingsStore,
                quoteDao = FakeQuoteDao(),
                valuationDao = FakeValuationDao(),
                priceBarDao = priceBarDao,
                clock = Clock.fixed(
                    Instant.parse("2026-07-15T20:00:00Z"),
                    ZoneOffset.UTC,
                ),
            )

            val snapshot = repository.observeTechnicalIndicators(
                symbol = symbol,
                exchange = Exchange.NASDAQ,
            ).first()!!

            assertEquals(15, snapshot.closeCount)
            assertEquals(100.0, snapshot.rsi14!!, 0.0)
            assertEquals(DataSource.LOCAL_CALCULATION, snapshot.source)
        }

    private fun quote(symbol: StockSymbol): QuoteSnapshot = QuoteSnapshot(
        symbol = symbol,
        exchange = Exchange.NASDAQ,
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
        source = DataSource.TENCENT,
    )

    private fun valuation(
        symbol: StockSymbol,
        parseStatus: ParseStatus,
    ): ValuationSnapshot = ValuationSnapshot(
        symbol = symbol,
        targets = AnalystTargets(
            low = if (parseStatus == ParseStatus.VALID) 180.0 else null,
            median = if (parseStatus == ParseStatus.VALID) 220.0 else null,
            high = if (parseStatus == ParseStatus.VALID) 260.0 else null,
        ),
        forwardPe = if (parseStatus == ParseStatus.VALID) 28.0 else null,
        recommendationKey = "buy",
        analystCount = if (parseStatus == ParseStatus.VALID) 40 else null,
        fiftyDayAverage = 195.0,
        twoHundredDayAverage = 180.0,
        fiftyTwoWeekLow = 150.0,
        fiftyTwoWeekHigh = 230.0,
        marketCap = 3_000_000_000_000L,
        averageDailyVolume3Month = 50_000_000L,
        sector = "Technology",
        industry = "Consumer Electronics",
        asOf = Instant.parse("2026-07-14T15:00:00Z"),
        fetchedAt = Instant.parse("2026-07-14T15:00:05Z"),
        staleAfter = Instant.parse("2026-07-15T15:00:05Z"),
        parseStatus = parseStatus,
        source = DataSource.YAHOO_FINANCE,
    )

    private fun priceBar(symbol: StockSymbol): PriceBar = PriceBar(
        symbol = symbol,
        exchange = Exchange.NASDAQ,
        interval = BarInterval.ONE_MINUTE,
        start = Instant.parse("2026-07-14T15:00:00Z"),
        endExclusive = Instant.parse("2026-07-14T15:01:00Z"),
        open = 199.0,
        high = 201.0,
        low = 198.0,
        close = 200.0,
        volume = 10_000L,
        fetchedAt = Instant.parse("2026-07-14T15:01:05Z"),
        source = DataSource.ALPACA_IEX,
    )

}

private class FakeQuoteDao(initial: QuoteEntity? = null) : QuoteDao {
    private val state = MutableStateFlow(initial)

    override fun observe(
        symbol: String,
        exchange: String,
    ): Flow<QuoteEntity?> = state

    override suspend fun get(
        symbol: String,
        exchange: String,
    ): QuoteEntity? = state.value

    override suspend fun upsert(entity: QuoteEntity) {
        state.value = entity
    }
}

private class FakeValuationDao(initial: ValuationEntity? = null) : ValuationDao {
    private val state = MutableStateFlow(initial)

    override fun observe(symbol: String): Flow<ValuationEntity?> = state

    override suspend fun get(symbol: String): ValuationEntity? = state.value

    override suspend fun upsert(entity: ValuationEntity) {
        state.value = entity
    }
}

private class FakePriceBarDao : PriceBarDao {
    private val state = MutableStateFlow<List<PriceBarEntity>>(emptyList())

    override fun observeHistory(
        symbol: String,
        exchange: String,
        interval: String,
        source: String,
    ): Flow<List<PriceBarEntity>> = state.map { entities ->
        entities
            .filter {
                it.symbol == symbol &&
                    it.exchange == exchange &&
                    it.interval == interval &&
                    it.source == source
            }
            .sortedBy(PriceBarEntity::startEpochMillis)
    }

    override fun observeRecent(
        symbol: String,
        exchange: String,
        interval: String,
        source: String,
        limit: Int,
    ): Flow<List<PriceBarEntity>> = MutableStateFlow(
        state.value
            .filter {
                it.symbol == symbol &&
                    it.exchange == exchange &&
                    it.interval == interval &&
                    it.source == source
            }
            .sortedByDescending(PriceBarEntity::startEpochMillis)
            .take(limit)
            .sortedBy(PriceBarEntity::startEpochMillis),
    )

    override suspend fun getRecent(
        symbol: String,
        exchange: String,
        interval: String,
        source: String,
        limit: Int,
    ): List<PriceBarEntity> = state.value
        .filter {
            it.symbol == symbol &&
                it.exchange == exchange &&
                it.interval == interval &&
                it.source == source
        }
        .sortedByDescending(PriceBarEntity::startEpochMillis)
        .take(limit)
        .sortedBy(PriceBarEntity::startEpochMillis)

    override suspend fun getRange(
        symbol: String,
        exchange: String,
        interval: String,
        source: String,
        startEpochMillis: Long,
        endExclusiveEpochMillis: Long,
    ): List<PriceBarEntity> = state.value
        .filter {
            it.symbol == symbol &&
                it.exchange == exchange &&
                it.interval == interval &&
                it.source == source &&
                it.startEpochMillis >= startEpochMillis &&
                it.endExclusiveEpochMillis <= endExclusiveEpochMillis
        }
        .sortedBy(PriceBarEntity::startEpochMillis)

    override suspend fun deleteRange(
        symbol: String,
        exchange: String,
        interval: String,
        startEpochMillis: Long,
        endExclusiveEpochMillis: Long,
    ) {
        state.value = state.value.filterNot {
            it.symbol == symbol &&
                it.exchange == exchange &&
                it.interval == interval &&
                it.startEpochMillis >= startEpochMillis &&
                it.endExclusiveEpochMillis <= endExclusiveEpochMillis
        }
    }

    override suspend fun upsertAll(entities: List<PriceBarEntity>) {
        state.value = (state.value + entities)
            .associateBy {
                listOf(it.symbol, it.exchange, it.interval, it.startEpochMillis)
            }
            .values
            .toList()
    }
}

private object UnusedQuoteClient : QuoteClient {
    override suspend fun fetchQuote(
        symbol: StockSymbol,
        exchange: Exchange,
    ): QuoteSnapshot = error("Not used by this test")
}

private object UnusedValuationClient : ValuationClient {
    override suspend fun fetchValuation(symbol: StockSymbol): ValuationSnapshot =
        error("Not used by this test")
}

private object UnusedChartClient : ChartClient {
    override suspend fun fetchBars(
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        start: Instant,
        endExclusive: Instant,
    ): List<PriceBar> = error("Not used by this test")
}

private object AlpacaSettingsStore : MarketDataSourceSettingsStore {
    override val settings: Flow<MarketDataSourceSettings> = MutableStateFlow(
        MarketDataSourceSettings(chartProvider = ChartProvider.ALPACA_IEX),
    )

    override suspend fun setQuoteProvider(provider: QuoteProvider) =
        error("Not used by this test")

    override suspend fun setChartProvider(provider: ChartProvider) =
        error("Not used by this test")

    override suspend fun setValuationProvider(provider: ValuationProvider) =
        error("Not used by this test")

    override suspend fun resetToDefaults() = error("Not used by this test")
}

package com.gongpx.aistockanalyst.data

import com.gongpx.aistockanalyst.database.QuoteDao
import com.gongpx.aistockanalyst.database.QuoteEntity
import com.gongpx.aistockanalyst.database.ValuationDao
import com.gongpx.aistockanalyst.database.ValuationEntity
import com.gongpx.aistockanalyst.database.toEntity
import com.gongpx.aistockanalyst.database.toModel
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.AnalystTargets
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.QuoteSnapshot
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.model.ValuationSnapshot
import com.gongpx.aistockanalyst.network.QuoteClient
import com.gongpx.aistockanalyst.network.ValuationClient
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
            valuationClient = UnusedValuationClient,
            quoteDao = quoteDao,
            valuationDao = FakeValuationDao(),
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
            valuationClient = object : ValuationClient {
                override suspend fun fetchValuation(
                    symbol: StockSymbol,
                ): ValuationSnapshot = valuation(symbol, ParseStatus.PARTIAL)
            },
            quoteDao = FakeQuoteDao(),
            valuationDao = valuationDao,
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

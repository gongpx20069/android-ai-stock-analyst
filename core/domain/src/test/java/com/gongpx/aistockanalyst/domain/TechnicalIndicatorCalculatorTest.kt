package com.gongpx.aistockanalyst.domain

import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.StockSymbol
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalIndicatorCalculatorTest {
    private val calculatedAt = Instant.parse("2026-07-15T20:00:00Z")

    @Test
    fun `calculator produces MA50 MA200 and rising RSI`() {
        val snapshot = TechnicalIndicatorCalculator.calculateDaily(
            dailyBars = (1..200).map { index ->
                dailyBar(index = index, close = index.toDouble())
            },
            calculatedAt = calculatedAt,
        )!!

        assertEquals(175.5, snapshot.ma50!!, 0.000_001)
        assertEquals(100.5, snapshot.ma200!!, 0.000_001)
        assertEquals(100.0, snapshot.rsi14!!, 0.000_001)
        assertEquals(200, snapshot.closeCount)
        assertEquals(DataSource.ALPACA_IEX, snapshot.barSource)
        assertEquals(DataSource.LOCAL_CALCULATION, snapshot.source)
    }

    @Test
    fun `Wilder RSI smooths changes after initial period`() {
        val closes = buildList {
            add(100.0)
            (1..14).forEach { add(100.0 + it) }
            add(100.0)
        }

        val snapshot = TechnicalIndicatorCalculator.calculateDaily(
            dailyBars = closes.mapIndexed { index, close ->
                dailyBar(index = index, close = close)
            },
            calculatedAt = calculatedAt,
        )!!

        assertEquals(48.148_148, snapshot.rsi14!!, 0.000_001)
    }

    @Test
    fun `flat series has neutral RSI`() {
        val snapshot = TechnicalIndicatorCalculator.calculateDaily(
            dailyBars = (1..15).map { index ->
                dailyBar(index = index, close = 100.0)
            },
            calculatedAt = calculatedAt,
        )!!

        assertEquals(50.0, snapshot.rsi14!!, 0.0)
    }

    @Test
    fun `insufficient history leaves unavailable indicators null`() {
        val snapshot = TechnicalIndicatorCalculator.calculateDaily(
            dailyBars = (1..14).map { index ->
                dailyBar(index = index, close = index.toDouble())
            },
            calculatedAt = calculatedAt,
        )!!

        assertNull(snapshot.ma50)
        assertNull(snapshot.ma200)
        assertNull(snapshot.rsi14)
    }

    @Test
    fun `unfinished and partial daily bars are excluded`() {
        val completed = (1..50).map { index ->
            dailyBar(index = index, close = index.toDouble())
        }
        val partial = dailyBar(index = 51, close = 1_000.0).copy(
            high = 1_000.0,
            parseStatus = ParseStatus.PARTIAL,
        )
        val unfinished = dailyBar(index = 52, close = 2_000.0).copy(
            start = calculatedAt.minus(12, ChronoUnit.HOURS),
            endExclusive = calculatedAt.plus(12, ChronoUnit.HOURS),
            high = 2_000.0,
        )

        val snapshot = TechnicalIndicatorCalculator.calculateDaily(
            dailyBars = completed + partial + unfinished,
            calculatedAt = calculatedAt,
        )!!

        assertEquals(25.5, snapshot.ma50!!, 0.000_001)
        assertEquals(50, snapshot.closeCount)
    }

    @Test
    fun `latest duplicate provider correction is used`() {
        val start = Instant.parse("2026-01-02T05:00:00Z")
        val older = dailyBar(index = 1, close = 100.0).copy(
            start = start,
            endExclusive = start.plus(1, ChronoUnit.DAYS),
            fetchedAt = Instant.parse("2026-01-03T05:01:00Z"),
        )
        val correction = older.copy(
            high = 111.0,
            close = 110.0,
            fetchedAt = Instant.parse("2026-01-03T05:30:00Z"),
        )

        val snapshot = TechnicalIndicatorCalculator.calculateDaily(
            dailyBars = listOf(correction, older),
            calculatedAt = calculatedAt,
        )!!

        assertEquals(1, snapshot.closeCount)
        assertEquals(correction.endExclusive, snapshot.asOf)
        assertEquals(correction.fetchedAt, snapshot.dataFetchedAt)
        assertFalse(snapshot.isStaleAt(correction.fetchedAt.plus(23, ChronoUnit.HOURS)))
        assertTrue(snapshot.isStaleAt(correction.fetchedAt.plus(1, ChronoUnit.DAYS)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculator rejects non-daily input`() {
        TechnicalIndicatorCalculator.calculateDaily(
            dailyBars = listOf(
                dailyBar(index = 1, close = 100.0).copy(
                    interval = BarInterval.FIVE_MINUTES,
                ),
            ),
            calculatedAt = calculatedAt,
        )
    }

    private fun dailyBar(
        index: Int,
        close: Double,
    ): PriceBar {
        val start = Instant.parse("2025-01-01T05:00:00Z")
            .plus(index.toLong(), ChronoUnit.DAYS)
        return PriceBar(
            symbol = StockSymbol.of("AAPL"),
            exchange = Exchange.NASDAQ,
            interval = BarInterval.ONE_DAY,
            start = start,
            endExclusive = start.plus(1, ChronoUnit.DAYS),
            open = close,
            high = close,
            low = close,
            close = close,
            volume = 1_000,
            fetchedAt = start.plus(1, ChronoUnit.DAYS).plusSeconds(60),
            parseStatus = ParseStatus.VALID,
            source = DataSource.ALPACA_IEX,
        )
    }
}

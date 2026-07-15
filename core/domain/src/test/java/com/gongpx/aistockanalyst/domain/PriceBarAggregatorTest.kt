package com.gongpx.aistockanalyst.domain

import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.StockSymbol
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceBarAggregatorTest {
    @Test
    fun `enclosing range expands partial exchange-time buckets`() {
        val range = PriceBarAggregator.enclosingFiveMinuteRange(
            start = Instant.parse("2026-07-15T13:32:00Z"),
            endExclusive = Instant.parse("2026-07-15T13:38:00Z"),
            exchange = Exchange.NASDAQ,
        )

        assertEquals(Instant.parse("2026-07-15T13:30:00Z"), range.start)
        assertEquals(Instant.parse("2026-07-15T13:40:00Z"), range.endExclusive)
    }

    @Test
    fun `enclosing range preserves aligned end boundary`() {
        val range = PriceBarAggregator.enclosingFiveMinuteRange(
            start = Instant.parse("2026-07-15T13:30:00Z"),
            endExclusive = Instant.parse("2026-07-15T13:40:00Z"),
            exchange = Exchange.NASDAQ,
        )

        assertEquals(Instant.parse("2026-07-15T13:30:00Z"), range.start)
        assertEquals(Instant.parse("2026-07-15T13:40:00Z"), range.endExclusive)
    }

    @Test
    fun `aggregation maps OHLCV in exchange-time order`() {
        val bars = (0L..4L).map { offset ->
            bar(
                start = Instant.parse("2026-07-15T13:30:00Z").plusSeconds(offset * 60),
                open = 100.0 + offset,
                high = 102.0 + offset,
                low = 99.0 - offset,
                close = 101.0 + offset,
                volume = 100L + offset,
            )
        }

        val result = PriceBarAggregator.completedFiveMinuteBars(
            oneMinuteBars = bars,
            completedAt = Instant.parse("2026-07-15T13:35:00Z"),
        ).single()

        assertEquals(BarInterval.FIVE_MINUTES, result.interval)
        assertEquals(Instant.parse("2026-07-15T13:30:00Z"), result.start)
        assertEquals(Instant.parse("2026-07-15T13:35:00Z"), result.endExclusive)
        assertEquals(100.0, result.open, 0.0)
        assertEquals(106.0, result.high, 0.0)
        assertEquals(95.0, result.low, 0.0)
        assertEquals(105.0, result.close, 0.0)
        assertEquals(510L, result.volume)
        assertEquals(DataSource.ALPACA_IEX, result.source)
    }

    @Test
    fun `aggregation aligns buckets after daylight saving transition`() {
        val result = PriceBarAggregator.completedFiveMinuteBars(
            oneMinuteBars = listOf(
                bar(start = Instant.parse("2026-03-09T13:32:00Z")),
            ),
            completedAt = Instant.parse("2026-03-09T13:35:00Z"),
        ).single()

        assertEquals(Instant.parse("2026-03-09T13:30:00Z"), result.start)
        assertEquals(Instant.parse("2026-03-09T13:35:00Z"), result.endExclusive)
    }

    @Test
    fun `unfinished five-minute bucket is omitted`() {
        val result = PriceBarAggregator.completedFiveMinuteBars(
            oneMinuteBars = listOf(
                bar(start = Instant.parse("2026-07-15T13:30:00Z")),
                bar(start = Instant.parse("2026-07-15T13:31:00Z")),
            ),
            completedAt = Instant.parse("2026-07-15T13:32:30Z"),
        )

        assertEquals(emptyList<PriceBar>(), result)
    }

    @Test
    fun `missing no-trade minutes are not fabricated`() {
        val result = PriceBarAggregator.completedFiveMinuteBars(
            oneMinuteBars = listOf(
                bar(
                    start = Instant.parse("2026-07-15T13:30:00Z"),
                    open = 100.0,
                    high = 101.0,
                    low = 99.0,
                    close = 100.5,
                    volume = 50,
                ),
                bar(
                    start = Instant.parse("2026-07-15T13:34:00Z"),
                    open = 100.5,
                    high = 103.0,
                    low = 100.0,
                    close = 102.0,
                    volume = 75,
                ),
            ),
            completedAt = Instant.parse("2026-07-15T13:35:00Z"),
        ).single()

        assertEquals(125L, result.volume)
        assertEquals(102.0, result.close, 0.0)
    }

    @Test
    fun `latest duplicate minute replaces earlier provider version`() {
        val start = Instant.parse("2026-07-15T13:30:00Z")
        val result = PriceBarAggregator.completedFiveMinuteBars(
            oneMinuteBars = listOf(
                bar(
                    start = start,
                    high = 102.0,
                    close = 101.0,
                    volume = 200,
                    fetchedAt = Instant.parse("2026-07-15T13:31:30Z"),
                ),
                bar(
                    start = start,
                    close = 100.0,
                    fetchedAt = Instant.parse("2026-07-15T13:31:01Z"),
                ),
            ),
            completedAt = Instant.parse("2026-07-15T13:35:00Z"),
        ).single()

        assertEquals(101.0, result.close, 0.0)
        assertEquals(200L, result.volume)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `aggregation rejects mixed provider sources`() {
        PriceBarAggregator.completedFiveMinuteBars(
            oneMinuteBars = listOf(
                bar(start = Instant.parse("2026-07-15T13:30:00Z")),
                bar(
                    start = Instant.parse("2026-07-15T13:31:00Z"),
                    source = DataSource.TENCENT,
                ),
            ),
            completedAt = Instant.parse("2026-07-15T13:35:00Z"),
        )
    }

    private fun bar(
        start: Instant,
        open: Double = 100.0,
        high: Double = 101.0,
        low: Double = 99.0,
        close: Double = 100.5,
        volume: Long = 100,
        fetchedAt: Instant = start.plusSeconds(65),
        source: DataSource = DataSource.ALPACA_IEX,
    ): PriceBar = PriceBar(
        symbol = StockSymbol.of("AAPL"),
        exchange = Exchange.NASDAQ,
        interval = BarInterval.ONE_MINUTE,
        start = start,
        endExclusive = start.plusSeconds(60),
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume,
        fetchedAt = fetchedAt,
        parseStatus = ParseStatus.VALID,
        source = source,
    )
}

package com.gongpx.aistockanalyst.domain

import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.PriceLevelKind
import com.gongpx.aistockanalyst.model.PriceLevelLabel
import com.gongpx.aistockanalyst.model.QuoteSnapshot
import com.gongpx.aistockanalyst.model.StockSymbol
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceLevelCalculatorTest {
    private val calculatedAt = Instant.parse("2026-07-15T20:00:00Z")

    @Test
    fun `classic pivots use latest completed daily candle`() {
        val snapshot = PriceLevelCalculator.calculateDaily(
            dailyBars = listOf(
                dailyBar(
                    index = 1,
                    open = 100.0,
                    high = 110.0,
                    low = 90.0,
                    close = 100.0,
                ),
            ),
            quote = quote(currentPrice = 105.0),
            calculatedAt = calculatedAt,
        )!!

        assertLabelValue(snapshot, PriceLevelLabel.PIVOT_POINT, 100.0)
        assertLabelValue(snapshot, PriceLevelLabel.PIVOT_R1, 110.0)
        assertLabelValue(snapshot, PriceLevelLabel.PIVOT_R2, 120.0)
        assertLabelValue(snapshot, PriceLevelLabel.PIVOT_R3, 130.0)
        assertLabelValue(snapshot, PriceLevelLabel.PIVOT_S1, 90.0)
        assertLabelValue(snapshot, PriceLevelLabel.PIVOT_S2, 80.0)
        assertLabelValue(snapshot, PriceLevelLabel.PIVOT_S3, 70.0)
        assertEquals(100.0, snapshot.nearestSupport!!.value, 0.000_001)
        assertEquals(110.0, snapshot.nearestResistance!!.value, 0.000_001)
    }

    @Test
    fun `five-day centered window identifies swing high and low`() {
        val highs = listOf(10.0, 12.0, 20.0, 12.0, 10.0)
        val lows = listOf(8.0, 7.0, 5.0, 7.0, 8.0)
        val bars = highs.indices.map { index ->
            dailyBar(
                index = index,
                open = 9.0,
                high = highs[index],
                low = lows[index],
                close = 9.0,
            )
        }

        val snapshot = PriceLevelCalculator.calculateDaily(
            dailyBars = bars,
            quote = quote(currentPrice = 10.0),
            calculatedAt = calculatedAt,
        )!!

        assertLabelValue(snapshot, PriceLevelLabel.SWING_HIGH, 20.0)
        assertLabelValue(snapshot, PriceLevelLabel.SWING_LOW, 5.0)
    }

    @Test
    fun `full annual window produces position Fibonacci and resonant levels`() {
        val completed = (0 until 252).map { index ->
            when (index) {
                0 -> dailyBar(index, open = 100.0, high = 150.0, low = 50.0, close = 100.0)
                else -> dailyBar(index, close = 100.0)
            }
        }
        val unfinished = dailyBar(
            index = 253,
            open = 100.0,
            high = 1_000.0,
            low = 1.0,
            close = 100.0,
        ).copy(
            start = calculatedAt.minus(12, ChronoUnit.HOURS),
            endExclusive = calculatedAt.plus(12, ChronoUnit.HOURS),
        )
        val partial = dailyBar(
            index = 254,
            open = 100.0,
            high = 500.0,
            low = 2.0,
            close = 100.0,
        ).copy(parseStatus = ParseStatus.PARTIAL)

        val snapshot = PriceLevelCalculator.calculateDaily(
            dailyBars = completed + unfinished + partial,
            quote = quote(currentPrice = 110.0),
            calculatedAt = calculatedAt,
        )!!

        assertEquals(50.0, snapshot.fiftyTwoWeekLow!!, 0.0)
        assertEquals(150.0, snapshot.fiftyTwoWeekHigh!!, 0.0)
        assertEquals(0.6, snapshot.fiftyTwoWeekPosition!!, 0.000_001)
        assertLabelValue(snapshot, PriceLevelLabel.FIBONACCI_23_6, 126.4)
        assertLabelValue(snapshot, PriceLevelLabel.FIBONACCI_38_2, 111.8)
        assertLabelValue(snapshot, PriceLevelLabel.FIBONACCI_61_8, 88.2)
        val midpoint = snapshot.levels.single {
            PriceLevelLabel.FIBONACCI_50_0 in it.labels
        }
        assertEquals(100.0, midpoint.value, 0.000_001)
        assertTrue(midpoint.isResonant)
        assertTrue(PriceLevelLabel.MA50 in midpoint.labels)
        assertTrue(PriceLevelLabel.MA200 in midpoint.labels)
    }

    @Test
    fun `annual fields remain unavailable before 252 completed sessions`() {
        val snapshot = PriceLevelCalculator.calculateDaily(
            dailyBars = (0 until 251).map { dailyBar(it, close = 100.0) },
            quote = quote(currentPrice = 100.0),
            calculatedAt = calculatedAt,
        )!!

        assertNull(snapshot.fiftyTwoWeekLow)
        assertNull(snapshot.fiftyTwoWeekHigh)
        assertNull(snapshot.fiftyTwoWeekPosition)
        assertFalse(
            snapshot.levels.any {
                it.labels.any { label ->
                    label.method == com.gongpx.aistockanalyst.model.PriceLevelMethod.FIFTY_TWO_WEEK ||
                        label.method ==
                            com.gongpx.aistockanalyst.model.PriceLevelMethod.FIBONACCI
                }
            },
        )
    }

    @Test
    fun `annual position clamps when live price makes a new high`() {
        val bars = (0 until 252).map { index ->
            dailyBar(index, open = 100.0, high = 120.0, low = 80.0, close = 100.0)
        }

        val snapshot = PriceLevelCalculator.calculateDaily(
            dailyBars = bars,
            quote = quote(currentPrice = 130.0),
            calculatedAt = calculatedAt,
        )!!

        assertEquals(1.0, snapshot.fiftyTwoWeekPosition!!, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `quote must match daily history identity`() {
        PriceLevelCalculator.calculateDaily(
            dailyBars = listOf(dailyBar(index = 1, close = 100.0)),
            quote = quote(currentPrice = 100.0).copy(
                symbol = StockSymbol.of("MSFT"),
            ),
            calculatedAt = calculatedAt,
        )
    }

    private fun assertLabelValue(
        snapshot: com.gongpx.aistockanalyst.model.PriceLevelSnapshot,
        label: PriceLevelLabel,
        expected: Double,
    ) {
        val level = snapshot.levels.single { label in it.labels }
        assertEquals(expected, level.value, 0.000_001)
        assertEquals(
            if (expected < snapshot.currentPrice) {
                PriceLevelKind.SUPPORT
            } else if (expected > snapshot.currentPrice) {
                PriceLevelKind.RESISTANCE
            } else {
                PriceLevelKind.AT_PRICE
            },
            level.kind,
        )
    }

    private fun quote(currentPrice: Double): QuoteSnapshot = QuoteSnapshot(
        symbol = StockSymbol.of("AAPL"),
        exchange = Exchange.NASDAQ,
        currentPrice = currentPrice,
        previousClose = currentPrice,
        open = currentPrice,
        dayHigh = currentPrice,
        dayLow = currentPrice,
        change = 0.0,
        changePercent = 0.0,
        volume = 1_000,
        ttmPe = null,
        marketCap = null,
        fiftyTwoWeekLow = null,
        fiftyTwoWeekHigh = null,
        asOf = Instant.parse("2026-07-15T19:59:00Z"),
        fetchedAt = Instant.parse("2026-07-15T19:59:05Z"),
        staleAfter = Instant.parse("2026-07-15T20:00:05Z"),
        parseStatus = ParseStatus.VALID,
        source = DataSource.TENCENT,
    )

    private fun dailyBar(
        index: Int,
        open: Double = 100.0,
        high: Double = 101.0,
        low: Double = 99.0,
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
            open = open,
            high = high,
            low = low,
            close = close,
            volume = 1_000,
            fetchedAt = start.plus(1, ChronoUnit.DAYS).plusSeconds(60),
            parseStatus = ParseStatus.VALID,
            source = DataSource.ALPACA_IEX,
        )
    }
}

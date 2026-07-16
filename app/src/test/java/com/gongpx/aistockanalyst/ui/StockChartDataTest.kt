package com.gongpx.aistockanalyst.ui

import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.StockSymbol
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class StockChartDataTest {
    @Test
    fun `chart data sorts bars and keeps latest revision`() {
        val first = priceBar(day = 1, close = 101.0, fetchedOffset = 1)
        val correctedFirst = priceBar(day = 1, close = 102.0, fetchedOffset = 2)
        val second = priceBar(day = 2, close = 103.0, fetchedOffset = 1)

        val data = stockChartData(listOf(second, first, correctedFirst))

        assertEquals(listOf(0, 1), data.x)
        assertEquals(listOf(102.0, 103.0), data.closing)
        assertEquals(listOf(1_001L, 1_002L), data.volume)
        assertEquals(listOf(correctedFirst.start, second.start), data.starts)
    }

    @Test
    fun `volume formatter uses compact market units`() {
        assertEquals("950", formatVolume(950.0))
        assertEquals("1.5K", formatVolume(1_500.0))
        assertEquals("12.5M", formatVolume(12_500_000.0))
        assertEquals("1.3B", formatVolume(1_250_000_000.0))
    }

    @Test
    fun `chart price formatter uses US dollars`() {
        assertEquals("$1,234.50", formatChartPrice(1_234.5))
    }

    private fun priceBar(
        day: Long,
        close: Double,
        fetchedOffset: Long,
    ): PriceBar {
        val start = Instant.parse("2025-01-01T05:00:00Z").plus(day, ChronoUnit.DAYS)
        return PriceBar(
            symbol = StockSymbol.of("AAPL"),
            exchange = Exchange.NASDAQ,
            interval = BarInterval.ONE_DAY,
            start = start,
            endExclusive = start.plus(1, ChronoUnit.DAYS),
            open = close - 1.0,
            high = close + 1.0,
            low = close - 2.0,
            close = close,
            volume = 1_000 + day,
            fetchedAt = start.plus(fetchedOffset, ChronoUnit.MINUTES),
            source = DataSource.ALPACA_IEX,
        )
    }
}

package com.gongpx.aistockanalyst.database

import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.QuoteSnapshot
import com.gongpx.aistockanalyst.model.StockSymbol
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketEntityMappersTest {
    @Test
    fun `quote survives entity round trip`() {
        val quote = QuoteSnapshot(
            symbol = StockSymbol.of("AAPL"),
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
            source = DataSource.ALPACA_IEX,
        )

        assertEquals(quote, quote.toEntity().toModel())
    }

    @Test
    fun `price bar survives entity round trip`() {
        val bar = PriceBar(
            symbol = StockSymbol.of("AAPL"),
            exchange = Exchange.NASDAQ,
            interval = BarInterval.FIFTEEN_MINUTES,
            start = Instant.parse("2026-07-14T13:30:00Z"),
            endExclusive = Instant.parse("2026-07-14T13:45:00Z"),
            open = 198.0,
            high = 202.0,
            low = 197.0,
            close = 200.0,
            volume = 10_000L,
            fetchedAt = Instant.parse("2026-07-14T13:50:00Z"),
            source = DataSource.TENCENT,
        )

        assertEquals(bar, bar.toEntity().toModel())
    }
}

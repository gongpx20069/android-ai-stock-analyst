package com.gongpx.aistockanalyst.domain

import com.gongpx.aistockanalyst.model.AnalystTargets
import com.gongpx.aistockanalyst.model.Exchange
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarketCalculationsTest {
    @Test
    fun `upside uses the median analyst target`() {
        val targets = AnalystTargets(
            low = 90.0,
            median = 120.0,
            high = 180.0,
        )

        val result = ValuationCalculator.upsideFraction(
            currentPrice = 100.0,
            targets = targets,
        )

        assertEquals(0.20, result!!, 0.000_001)
    }

    @Test
    fun `upside is unavailable when median target is missing`() {
        val targets = AnalystTargets(
            low = 90.0,
            median = null,
            high = 180.0,
        )

        assertNull(
            ValuationCalculator.upsideFraction(
                currentPrice = 100.0,
                targets = targets,
            ),
        )
    }

    @Test
    fun `exchange calculations and display time use separate zones`() {
        val instant = Instant.parse("2026-07-14T14:30:00Z")

        val exchangeTime = MarketTime.inExchangeTime(instant, Exchange.NASDAQ)
        val deviceTime = MarketTime.inDeviceTime(
            instant = instant,
            deviceZone = ZoneId.of("Asia/Shanghai"),
        )

        assertEquals(10, exchangeTime.hour)
        assertEquals(22, deviceTime.hour)
        assertEquals(ZoneId.of("America/New_York"), exchangeTime.zone)
        assertEquals(ZoneId.of("Asia/Shanghai"), deviceTime.zone)
    }
}

package com.gongpx.aistockanalyst.domain

import com.gongpx.aistockanalyst.model.AnalystTargets
import com.gongpx.aistockanalyst.model.Exchange
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object ValuationCalculator {
    fun upsideFraction(
        currentPrice: Double,
        targets: AnalystTargets,
    ): Double? {
        require(currentPrice > 0.0) { "Current price must be positive" }
        val medianTarget = targets.median ?: return null
        return medianTarget / currentPrice - 1.0
    }
}

object MarketTime {
    fun inExchangeTime(
        instant: Instant,
        exchange: Exchange,
    ): ZonedDateTime = instant.atZone(exchange.zoneId)

    fun inDeviceTime(
        instant: Instant,
        deviceZone: ZoneId = ZoneId.systemDefault(),
    ): ZonedDateTime = instant.atZone(deviceZone)
}

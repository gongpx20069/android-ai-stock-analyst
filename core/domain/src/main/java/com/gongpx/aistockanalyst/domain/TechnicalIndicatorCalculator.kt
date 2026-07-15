package com.gongpx.aistockanalyst.domain

import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.TechnicalIndicatorSnapshot
import java.time.Duration
import java.time.Instant

object TechnicalIndicatorCalculator {
    fun calculateDaily(
        dailyBars: List<PriceBar>,
        calculatedAt: Instant,
    ): TechnicalIndicatorSnapshot? {
        if (dailyBars.isEmpty()) {
            return null
        }
        val completedBars = normalizedCompletedDailyBars(dailyBars, calculatedAt)
        if (completedBars.isEmpty()) {
            return null
        }

        val closes = completedBars.map(PriceBar::close)
        val latest = completedBars.last()
        return TechnicalIndicatorSnapshot(
            symbol = latest.symbol,
            exchange = latest.exchange,
            closeCount = closes.size,
            ma50 = closes.simpleMovingAverage(MA50_PERIOD),
            ma200 = closes.simpleMovingAverage(MA200_PERIOD),
            rsi14 = closes.wilderRsi(RSI_PERIOD),
            asOf = latest.endExclusive,
            calculatedAt = calculatedAt,
            dataFetchedAt = latest.fetchedAt,
            staleAfter = latest.fetchedAt.plus(DAILY_DATA_TTL),
            barSource = latest.source,
        )
    }

    private fun List<Double>.simpleMovingAverage(period: Int): Double? {
        if (size < period) {
            return null
        }
        return takeLast(period).average()
    }

    private fun List<Double>.wilderRsi(period: Int): Double? {
        if (size <= period) {
            return null
        }
        val changes = zipWithNext { previous, current -> current - previous }
        var averageGain = changes.take(period).sumOf { change ->
            change.coerceAtLeast(0.0)
        } / period
        var averageLoss = changes.take(period).sumOf { change ->
            (-change).coerceAtLeast(0.0)
        } / period

        changes.drop(period).forEach { change ->
            val gain = change.coerceAtLeast(0.0)
            val loss = (-change).coerceAtLeast(0.0)
            averageGain = (averageGain * (period - 1) + gain) / period
            averageLoss = (averageLoss * (period - 1) + loss) / period
        }
        return when {
            averageGain == 0.0 && averageLoss == 0.0 -> 50.0
            averageLoss == 0.0 -> 100.0
            averageGain == 0.0 -> 0.0
            else -> 100.0 - 100.0 / (1.0 + averageGain / averageLoss)
        }
    }

    private const val MA50_PERIOD = 50
    private const val MA200_PERIOD = 200
    private const val RSI_PERIOD = 14
    private val DAILY_DATA_TTL: Duration = Duration.ofDays(1)
}

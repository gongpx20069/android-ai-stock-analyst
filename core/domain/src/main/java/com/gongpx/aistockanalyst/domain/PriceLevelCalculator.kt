package com.gongpx.aistockanalyst.domain

import com.gongpx.aistockanalyst.model.PriceLevel
import com.gongpx.aistockanalyst.model.PriceLevelKind
import com.gongpx.aistockanalyst.model.PriceLevelLabel
import com.gongpx.aistockanalyst.model.PriceLevelSnapshot
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.QuoteSnapshot
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

object PriceLevelCalculator {
    fun calculateDaily(
        dailyBars: List<PriceBar>,
        quote: QuoteSnapshot,
        calculatedAt: Instant,
    ): PriceLevelSnapshot? {
        if (dailyBars.isEmpty()) {
            return null
        }
        val completedBars = normalizedCompletedDailyBars(dailyBars, calculatedAt)
        if (completedBars.isEmpty()) {
            return null
        }
        val latest = completedBars.last()
        require(latest.symbol == quote.symbol && latest.exchange == quote.exchange) {
            "Quote and daily history must share a symbol and exchange"
        }

        val rawLevels = buildList {
            addAll(pivotLevels(latest))
            addAll(swingLevels(completedBars.takeLast(SWING_LOOKBACK)))
            val annualBars = completedBars.takeLast(FIFTY_TWO_WEEK_SESSIONS)
            val hasFullAnnualWindow = annualBars.size == FIFTY_TWO_WEEK_SESSIONS
            if (hasFullAnnualWindow) {
                val annualLow = annualBars.minOf(PriceBar::low)
                val annualHigh = annualBars.maxOf(PriceBar::high)
                addAll(fibonacciLevels(annualLow, annualHigh))
                add(RawLevel(annualLow, PriceLevelLabel.FIFTY_TWO_WEEK_LOW))
                add(RawLevel(annualHigh, PriceLevelLabel.FIFTY_TWO_WEEK_HIGH))
            }
            val technicals = TechnicalIndicatorCalculator.calculateDaily(
                dailyBars = completedBars,
                calculatedAt = calculatedAt,
            )
            technicals?.ma50?.let { add(RawLevel(it, PriceLevelLabel.MA50)) }
            technicals?.ma200?.let { add(RawLevel(it, PriceLevelLabel.MA200)) }
        }.filter { it.value > 0.0 && it.value.isFinite() }

        val annualBars = completedBars.takeLast(FIFTY_TWO_WEEK_SESSIONS)
        val annualLow = annualBars
            .takeIf { it.size == FIFTY_TWO_WEEK_SESSIONS }
            ?.minOf(PriceBar::low)
        val annualHigh = annualBars
            .takeIf { it.size == FIFTY_TWO_WEEK_SESSIONS }
            ?.maxOf(PriceBar::high)
        val annualPosition = if (
            annualLow != null &&
            annualHigh != null &&
            annualHigh > annualLow
        ) {
            ((quote.currentPrice - annualLow) / (annualHigh - annualLow))
                .coerceIn(0.0, 1.0)
        } else {
            null
        }
        val levels = mergeLevels(rawLevels, quote.currentPrice)
        return PriceLevelSnapshot(
            symbol = latest.symbol,
            exchange = latest.exchange,
            currentPrice = quote.currentPrice,
            dailyBarCount = completedBars.size,
            referenceAsOf = quote.asOf,
            dailyAsOf = latest.endExclusive,
            calculatedAt = calculatedAt,
            dataFetchedAt = minOf(quote.fetchedAt, latest.fetchedAt),
            staleAfter = minOf(
                quote.staleAfter,
                latest.fetchedAt.plus(DAILY_DATA_TTL),
            ),
            fiftyTwoWeekLow = annualLow,
            fiftyTwoWeekHigh = annualHigh,
            fiftyTwoWeekPosition = annualPosition,
            levels = levels,
            nearestSupport = levels.lastOrNull { it.kind == PriceLevelKind.SUPPORT },
            nearestResistance = levels.firstOrNull {
                it.kind == PriceLevelKind.RESISTANCE
            },
            quoteSource = quote.source,
            barSource = latest.source,
        )
    }

    private fun pivotLevels(previous: PriceBar): List<RawLevel> {
        val pivot = (previous.high + previous.low + previous.close) / 3.0
        val r1 = 2.0 * pivot - previous.low
        val s1 = 2.0 * pivot - previous.high
        val range = previous.high - previous.low
        return listOf(
            RawLevel(pivot, PriceLevelLabel.PIVOT_POINT),
            RawLevel(r1, PriceLevelLabel.PIVOT_R1),
            RawLevel(pivot + range, PriceLevelLabel.PIVOT_R2),
            RawLevel(previous.high + 2.0 * (pivot - previous.low), PriceLevelLabel.PIVOT_R3),
            RawLevel(s1, PriceLevelLabel.PIVOT_S1),
            RawLevel(pivot - range, PriceLevelLabel.PIVOT_S2),
            RawLevel(previous.low - 2.0 * (previous.high - pivot), PriceLevelLabel.PIVOT_S3),
        )
    }

    private fun swingLevels(bars: List<PriceBar>): List<RawLevel> {
        if (bars.size < SWING_WINDOW) {
            return emptyList()
        }
        return buildList {
            for (index in SWING_RADIUS until bars.size - SWING_RADIUS) {
                val candidate = bars[index]
                val window = bars.subList(
                    index - SWING_RADIUS,
                    index + SWING_RADIUS + 1,
                )
                if (candidate.high == window.maxOf(PriceBar::high)) {
                    add(RawLevel(candidate.high, PriceLevelLabel.SWING_HIGH))
                }
                if (candidate.low == window.minOf(PriceBar::low)) {
                    add(RawLevel(candidate.low, PriceLevelLabel.SWING_LOW))
                }
            }
        }
    }

    private fun fibonacciLevels(
        low: Double,
        high: Double,
    ): List<RawLevel> {
        val range = high - low
        if (range <= 0.0) {
            return emptyList()
        }
        return listOf(
            RawLevel(high - 0.236 * range, PriceLevelLabel.FIBONACCI_23_6),
            RawLevel(high - 0.382 * range, PriceLevelLabel.FIBONACCI_38_2),
            RawLevel(high - 0.500 * range, PriceLevelLabel.FIBONACCI_50_0),
            RawLevel(high - 0.618 * range, PriceLevelLabel.FIBONACCI_61_8),
        )
    }

    private fun mergeLevels(
        rawLevels: List<RawLevel>,
        currentPrice: Double,
    ): List<PriceLevel> {
        val clusters = mutableListOf<MutableList<RawLevel>>()
        rawLevels.sortedBy(RawLevel::value).forEach { level ->
            val currentCluster = clusters.lastOrNull()
            val representative = currentCluster?.map(RawLevel::value)?.average()
            if (
                currentCluster != null &&
                representative != null &&
                relativeDistance(level.value, representative) <= RESONANCE_TOLERANCE
            ) {
                currentCluster += level
            } else {
                clusters += mutableListOf(level)
            }
        }
        return clusters.map { cluster ->
            val value = cluster.map(RawLevel::value).median()
            val distanceFraction = abs(value / currentPrice - 1.0)
            val kind = when {
                distanceFraction <= AT_PRICE_TOLERANCE -> PriceLevelKind.AT_PRICE
                value < currentPrice -> PriceLevelKind.SUPPORT
                else -> PriceLevelKind.RESISTANCE
            }
            PriceLevel(
                value = value,
                kind = kind,
                distanceFraction = distanceFraction,
                labels = cluster.mapTo(mutableSetOf(), RawLevel::label),
            )
        }.sortedBy(PriceLevel::value)
    }

    private fun relativeDistance(
        left: Double,
        right: Double,
    ): Double = abs(left - right) / ((left + right) / 2.0)

    private fun List<Double>.median(): Double {
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    private data class RawLevel(
        val value: Double,
        val label: PriceLevelLabel,
    )

    private const val FIFTY_TWO_WEEK_SESSIONS = 252
    private const val SWING_LOOKBACK = 120
    private const val SWING_RADIUS = 2
    private const val SWING_WINDOW = SWING_RADIUS * 2 + 1
    private const val RESONANCE_TOLERANCE = 0.005
    private const val AT_PRICE_TOLERANCE = 0.001
    private val DAILY_DATA_TTL: Duration = Duration.ofDays(1)
}

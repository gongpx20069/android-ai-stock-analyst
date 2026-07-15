package com.gongpx.aistockanalyst.domain

import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.PriceBar
import java.time.Duration
import java.time.Instant

object PriceBarAggregator {
    fun enclosingFiveMinuteRange(
        start: Instant,
        endExclusive: Instant,
        exchange: Exchange,
    ): AggregationRange {
        require(start < endExclusive) { "Aggregation range end must be after its start" }
        val alignedStart = fiveMinuteBucketStart(start, exchange)
        val endBucketStart = fiveMinuteBucketStart(endExclusive, exchange)
        val alignedEnd = if (endExclusive == endBucketStart) {
            endExclusive
        } else {
            endBucketStart.plus(FIVE_MINUTES)
        }
        return AggregationRange(
            start = alignedStart,
            endExclusive = alignedEnd,
        )
    }

    fun completedFiveMinuteBars(
        oneMinuteBars: List<PriceBar>,
        completedAt: Instant,
    ): List<PriceBar> {
        if (oneMinuteBars.isEmpty()) {
            return emptyList()
        }
        validateInput(oneMinuteBars)
        val latestMinutes = oneMinuteBars
            .filter { it.isCompletedAt(completedAt) }
            .groupBy(PriceBar::start)
            .values
            .map { versions -> versions.maxBy(PriceBar::fetchedAt) }
            .sortedBy(PriceBar::start)
        if (latestMinutes.isEmpty()) {
            return emptyList()
        }

        return latestMinutes
            .groupBy(::fiveMinuteBucketStart)
            .mapNotNull { (bucketStart, bars) ->
                aggregateBucket(
                    bucketStart = bucketStart,
                    bars = bars,
                    completedAt = completedAt,
                )
            }
            .sortedBy(PriceBar::start)
    }

    private fun validateInput(bars: List<PriceBar>) {
        val first = bars.first()
        bars.forEach { bar ->
            require(bar.interval == BarInterval.ONE_MINUTE) {
                "Five-minute aggregation requires one-minute bars"
            }
            require(bar.symbol == first.symbol && bar.exchange == first.exchange) {
                "Bars must share a symbol and exchange"
            }
            require(bar.source == first.source) {
                "Bars must share a data source"
            }
            require(bar.endExclusive == bar.start.plus(ONE_MINUTE)) {
                "One-minute bar boundaries are inconsistent"
            }
            val exchangeTime = bar.start.atZone(bar.exchange.zoneId)
            require(exchangeTime.second == 0 && exchangeTime.nano == 0) {
                "One-minute bars must start on an exchange-time minute boundary"
            }
        }
    }

    private fun fiveMinuteBucketStart(bar: PriceBar): Instant =
        fiveMinuteBucketStart(bar.start, bar.exchange)

    private fun fiveMinuteBucketStart(
        instant: Instant,
        exchange: Exchange,
    ): Instant {
        val exchangeTime = instant.atZone(exchange.zoneId)
        val alignedMinute = exchangeTime.minute - exchangeTime.minute % 5
        return exchangeTime
            .withMinute(alignedMinute)
            .withSecond(0)
            .withNano(0)
            .toInstant()
    }

    private fun aggregateBucket(
        bucketStart: Instant,
        bars: List<PriceBar>,
        completedAt: Instant,
    ): PriceBar? {
        val bucketEnd = bucketStart.plus(FIVE_MINUTES)
        if (completedAt < bucketEnd) {
            return null
        }
        val orderedBars = bars.sortedBy(PriceBar::start)
        val first = orderedBars.first()
        val volume = orderedBars.fold(0L) { total, bar ->
            Math.addExact(total, bar.volume)
        }
        return PriceBar(
            symbol = first.symbol,
            exchange = first.exchange,
            interval = BarInterval.FIVE_MINUTES,
            start = bucketStart,
            endExclusive = bucketEnd,
            open = first.open,
            high = orderedBars.maxOf(PriceBar::high),
            low = orderedBars.minOf(PriceBar::low),
            close = orderedBars.last().close,
            volume = volume,
            fetchedAt = orderedBars.maxOf(PriceBar::fetchedAt),
            parseStatus = if (orderedBars.all { it.parseStatus == ParseStatus.VALID }) {
                ParseStatus.VALID
            } else {
                ParseStatus.PARTIAL
            },
            source = first.source,
        )
    }

    private val ONE_MINUTE: Duration = Duration.ofMinutes(1)
    private val FIVE_MINUTES: Duration = Duration.ofMinutes(5)
}

data class AggregationRange(
    val start: Instant,
    val endExclusive: Instant,
)

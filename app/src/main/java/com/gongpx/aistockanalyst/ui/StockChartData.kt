package com.gongpx.aistockanalyst.ui

import com.gongpx.aistockanalyst.model.PriceBar
import java.time.Instant
import java.util.Locale

internal data class StockChartData(
    val x: List<Int>,
    val opening: List<Double>,
    val closing: List<Double>,
    val low: List<Double>,
    val high: List<Double>,
    val volume: List<Long>,
    val starts: List<Instant>,
)

internal fun stockChartData(bars: List<PriceBar>): StockChartData {
    val normalizedBars = bars
        .groupBy(PriceBar::start)
        .values
        .map { revisions -> revisions.maxBy(PriceBar::fetchedAt) }
        .sortedBy(PriceBar::start)
    return StockChartData(
        x = normalizedBars.indices.toList(),
        opening = normalizedBars.map(PriceBar::open),
        closing = normalizedBars.map(PriceBar::close),
        low = normalizedBars.map(PriceBar::low),
        high = normalizedBars.map(PriceBar::high),
        volume = normalizedBars.map(PriceBar::volume),
        starts = normalizedBars.map(PriceBar::start),
    )
}

internal fun formatVolume(value: Double): String {
    val (scaledValue, suffix) = when {
        value >= 1_000_000_000.0 -> value / 1_000_000_000.0 to "B"
        value >= 1_000_000.0 -> value / 1_000_000.0 to "M"
        value >= 1_000.0 -> value / 1_000.0 to "K"
        else -> value to ""
    }
    val pattern = if (scaledValue >= 100.0 || suffix.isEmpty()) "%.0f%s" else "%.1f%s"
    return String.format(Locale.US, pattern, scaledValue, suffix)
}

internal fun formatChartPrice(value: Double): String =
    String.format(Locale.US, "$%,.2f", value)

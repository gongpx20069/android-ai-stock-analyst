package com.gongpx.aistockanalyst.domain

import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.PriceBar
import java.time.Instant

internal fun normalizedCompletedDailyBars(
    dailyBars: List<PriceBar>,
    completedAt: Instant,
): List<PriceBar> {
    if (dailyBars.isEmpty()) {
        return emptyList()
    }
    val first = dailyBars.first()
    dailyBars.forEach { bar ->
        require(bar.interval == BarInterval.ONE_DAY) {
            "Daily analysis requires daily bars"
        }
        require(bar.symbol == first.symbol && bar.exchange == first.exchange) {
            "Daily bars must share a symbol and exchange"
        }
        require(bar.source == first.source) {
            "Daily bars must share a data source"
        }
    }
    return dailyBars
        .groupBy(PriceBar::start)
        .values
        .map { versions -> versions.maxBy(PriceBar::fetchedAt) }
        .filter {
            it.parseStatus == ParseStatus.VALID &&
                it.isCompletedAt(completedAt)
        }
        .sortedBy(PriceBar::start)
}

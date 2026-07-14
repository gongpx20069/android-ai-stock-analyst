package com.gongpx.aistockanalyst.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@JvmInline
value class StockSymbol private constructor(val value: String) {
    companion object {
        private val validSymbol = Regex("[A-Z][A-Z0-9.-]{0,14}")

        fun of(raw: String): StockSymbol {
            val normalized = raw.trim().uppercase(Locale.ROOT)
            require(validSymbol.matches(normalized)) {
                "Invalid US stock symbol: $raw"
            }
            return StockSymbol(normalized)
        }
    }
}

enum class Exchange(val zoneId: ZoneId) {
    NASDAQ(ZoneId.of("America/New_York")),
    NYSE(ZoneId.of("America/New_York")),
    NYSE_AMERICAN(ZoneId.of("America/New_York")),
}

enum class DataSource {
    TENCENT,
    SINA,
    YAHOO_FINANCE,
    LOCAL_CALCULATION,
    ONNX_RUNTIME,
}

enum class QuoteProvider {
    AUTO,
    TENCENT,
    SINA,
}

enum class ChartProvider {
    TENCENT,
}

enum class ValuationProvider {
    YAHOO_FINANCE,
}

data class MarketDataSourceSettings(
    val quoteProvider: QuoteProvider = QuoteProvider.AUTO,
    val chartProvider: ChartProvider = ChartProvider.TENCENT,
    val valuationProvider: ValuationProvider = ValuationProvider.YAHOO_FINANCE,
)

enum class ParseStatus {
    VALID,
    PARTIAL,
}

data class QuoteSnapshot(
    val symbol: StockSymbol,
    val exchange: Exchange,
    val currentPrice: Double,
    val previousClose: Double?,
    val open: Double?,
    val dayHigh: Double?,
    val dayLow: Double?,
    val change: Double?,
    val changePercent: Double?,
    val volume: Long?,
    val ttmPe: Double?,
    val marketCap: Double?,
    val fiftyTwoWeekLow: Double?,
    val fiftyTwoWeekHigh: Double?,
    val asOf: Instant,
    val fetchedAt: Instant,
    val staleAfter: Instant,
    val parseStatus: ParseStatus,
    val source: DataSource,
) {
    init {
        require(currentPrice > 0.0) { "Current price must be positive" }
        require(previousClose == null || previousClose > 0.0) {
            "Previous close must be positive when present"
        }
        require(open == null || open > 0.0) { "Open must be positive when present" }
        require(dayHigh == null || dayHigh > 0.0) {
            "Day high must be positive when present"
        }
        require(dayLow == null || dayLow > 0.0) {
            "Day low must be positive when present"
        }
        if (dayLow != null && dayHigh != null) {
            require(dayLow <= dayHigh) { "Day low cannot exceed day high" }
        }
        require(volume == null || volume >= 0L) {
            "Volume cannot be negative"
        }
        require(marketCap == null || marketCap >= 0.0) {
            "Market cap cannot be negative"
        }
        if (fiftyTwoWeekLow != null && fiftyTwoWeekHigh != null) {
            require(fiftyTwoWeekLow <= fiftyTwoWeekHigh) {
                "52-week low cannot exceed the 52-week high"
            }
        }
        require(!staleAfter.isBefore(fetchedAt)) {
            "Stale time cannot be before fetched time"
        }
    }
}

data class AnalystTargets(
    val low: Double?,
    val median: Double?,
    val high: Double?,
) {
    init {
        listOfNotNull(low, median, high).forEach {
            require(it > 0.0) { "Analyst targets must be positive" }
        }
        if (low != null && median != null) {
            require(low <= median) { "Low target cannot exceed median target" }
        }
        if (median != null && high != null) {
            require(median <= high) { "Median target cannot exceed high target" }
        }
    }
}

data class ValuationSnapshot(
    val symbol: StockSymbol,
    val targets: AnalystTargets,
    val forwardPe: Double?,
    val recommendationKey: String?,
    val analystCount: Int?,
    val fiftyDayAverage: Double?,
    val twoHundredDayAverage: Double?,
    val fiftyTwoWeekLow: Double?,
    val fiftyTwoWeekHigh: Double?,
    val marketCap: Long?,
    val averageDailyVolume3Month: Long?,
    val sector: String?,
    val industry: String?,
    val asOf: Instant,
    val fetchedAt: Instant,
    val staleAfter: Instant,
    val parseStatus: ParseStatus,
    val source: DataSource = DataSource.YAHOO_FINANCE,
) {
    init {
        require(analystCount == null || analystCount >= 0) {
            "Analyst count cannot be negative"
        }
        require(fiftyDayAverage == null || fiftyDayAverage > 0.0) {
            "50-day average must be positive when present"
        }
        require(twoHundredDayAverage == null || twoHundredDayAverage > 0.0) {
            "200-day average must be positive when present"
        }
        if (fiftyTwoWeekLow != null && fiftyTwoWeekHigh != null) {
            require(fiftyTwoWeekLow <= fiftyTwoWeekHigh) {
                "52-week low cannot exceed the 52-week high"
            }
        }
        require(marketCap == null || marketCap >= 0L) {
            "Market cap cannot be negative"
        }
        require(averageDailyVolume3Month == null || averageDailyVolume3Month >= 0L) {
            "Average daily volume cannot be negative"
        }
        require(!staleAfter.isBefore(fetchedAt)) {
            "Stale time cannot be before fetched time"
        }
    }
}

enum class BarInterval(val duration: Duration?) {
    ONE_MINUTE(Duration.ofMinutes(1)),
    FIVE_MINUTES(Duration.ofMinutes(5)),
    FIFTEEN_MINUTES(Duration.ofMinutes(15)),
    THIRTY_MINUTES(Duration.ofMinutes(30)),
    ONE_HOUR(Duration.ofHours(1)),
    FOUR_HOURS(Duration.ofHours(4)),
    ONE_DAY(Duration.ofDays(1)),
    ONE_MONTH(null),
}

data class PriceBar(
    val symbol: StockSymbol,
    val interval: BarInterval,
    val start: Instant,
    val endExclusive: Instant,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
) {
    init {
        require(start < endExclusive) { "Bar end must be after its start" }
        require(min(open, close) >= low) { "Low exceeds open or close" }
        require(max(open, close) <= high) { "High is below open or close" }
        require(low > 0.0) { "Price values must be positive" }
        require(volume >= 0L) { "Volume cannot be negative" }
    }

    fun isCompletedAt(now: Instant): Boolean = !now.isBefore(endExclusive)
}

enum class PredictionHorizon {
    THIRTY_MINUTES,
    FIVE_TRADING_DAYS,
}

data class PredictionSnapshot(
    val symbol: StockSymbol,
    val horizon: PredictionHorizon,
    val probabilityUp: Double,
    val confidence: Double?,
    val asOf: Instant,
    val featureWindow: String,
    val modelVersion: String,
    val source: DataSource = DataSource.ONNX_RUNTIME,
) {
    init {
        require(probabilityUp in 0.0..1.0) {
            "Direction probability must be between 0 and 1"
        }
        require(confidence == null || confidence in 0.0..1.0) {
            "Confidence must be between 0 and 1 when present"
        }
        require(featureWindow.isNotBlank()) { "Feature window is required" }
        require(modelVersion.isNotBlank()) { "Model version is required" }
    }
}

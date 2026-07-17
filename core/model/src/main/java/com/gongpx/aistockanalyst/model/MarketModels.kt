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
    FINNHUB,
    FMP,
    ALPACA_IEX,
    LOCAL_CALCULATION,
    ONNX_RUNTIME,
}

enum class QuoteProvider {
    AUTO,
    TENCENT,
    SINA,
}

enum class ChartProvider {
    NOT_CONFIGURED,
    ALPACA_IEX,
}

enum class ValuationProvider {
    YAHOO_FINANCE,
    FINNHUB,
    FMP,
}

enum class AppLanguage(val languageTag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    SIMPLIFIED_CHINESE("zh-CN"),
}

data class MarketDataSourceSettings(
    val quoteProvider: QuoteProvider = QuoteProvider.AUTO,
    val chartProvider: ChartProvider = ChartProvider.NOT_CONFIGURED,
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
        if (low != null && high != null) {
            require(low <= high) { "Low target cannot exceed high target" }
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
    val exchange: Exchange,
    val interval: BarInterval,
    val start: Instant,
    val endExclusive: Instant,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val fetchedAt: Instant,
    val parseStatus: ParseStatus = ParseStatus.VALID,
    val source: DataSource,
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

data class TechnicalIndicatorSnapshot(
    val symbol: StockSymbol,
    val exchange: Exchange,
    val closeCount: Int,
    val ma50: Double?,
    val ma200: Double?,
    val rsi14: Double?,
    val asOf: Instant,
    val calculatedAt: Instant,
    val dataFetchedAt: Instant,
    val staleAfter: Instant,
    val barSource: DataSource,
    val source: DataSource = DataSource.LOCAL_CALCULATION,
) {
    init {
        require(closeCount > 0) { "Technical indicators require price history" }
        require(ma50 == null || ma50 > 0.0) { "MA50 must be positive when present" }
        require(ma200 == null || ma200 > 0.0) { "MA200 must be positive when present" }
        require(rsi14 == null || rsi14 in 0.0..100.0) {
            "RSI(14) must be between 0 and 100 when present"
        }
        require(!staleAfter.isBefore(dataFetchedAt)) {
            "Technical indicator stale time cannot precede fetch time"
        }
        require(source == DataSource.LOCAL_CALCULATION) {
            "Technical indicators must be calculated locally"
        }
    }

    fun isStaleAt(now: Instant): Boolean = !now.isBefore(staleAfter)
}

enum class PriceLevelMethod {
    PIVOT,
    SWING,
    FIBONACCI,
    MOVING_AVERAGE,
    FIFTY_TWO_WEEK,
}

enum class PriceLevelLabel(
    val method: PriceLevelMethod,
) {
    PIVOT_POINT(PriceLevelMethod.PIVOT),
    PIVOT_R1(PriceLevelMethod.PIVOT),
    PIVOT_R2(PriceLevelMethod.PIVOT),
    PIVOT_R3(PriceLevelMethod.PIVOT),
    PIVOT_S1(PriceLevelMethod.PIVOT),
    PIVOT_S2(PriceLevelMethod.PIVOT),
    PIVOT_S3(PriceLevelMethod.PIVOT),
    SWING_HIGH(PriceLevelMethod.SWING),
    SWING_LOW(PriceLevelMethod.SWING),
    FIBONACCI_23_6(PriceLevelMethod.FIBONACCI),
    FIBONACCI_38_2(PriceLevelMethod.FIBONACCI),
    FIBONACCI_50_0(PriceLevelMethod.FIBONACCI),
    FIBONACCI_61_8(PriceLevelMethod.FIBONACCI),
    MA50(PriceLevelMethod.MOVING_AVERAGE),
    MA200(PriceLevelMethod.MOVING_AVERAGE),
    FIFTY_TWO_WEEK_HIGH(PriceLevelMethod.FIFTY_TWO_WEEK),
    FIFTY_TWO_WEEK_LOW(PriceLevelMethod.FIFTY_TWO_WEEK),
}

enum class PriceLevelKind {
    SUPPORT,
    AT_PRICE,
    RESISTANCE,
}

data class PriceLevel(
    val value: Double,
    val kind: PriceLevelKind,
    val distanceFraction: Double,
    val labels: Set<PriceLevelLabel>,
) {
    val methods: Set<PriceLevelMethod> = labels.mapTo(mutableSetOf()) { it.method }
    val isResonant: Boolean = methods.size >= 2

    init {
        require(value > 0.0 && value.isFinite()) { "Price level must be positive" }
        require(distanceFraction >= 0.0 && distanceFraction.isFinite()) {
            "Price-level distance must be non-negative"
        }
        require(labels.isNotEmpty()) { "Price level requires at least one label" }
    }
}

data class PriceLevelSnapshot(
    val symbol: StockSymbol,
    val exchange: Exchange,
    val currentPrice: Double,
    val dailyBarCount: Int,
    val referenceAsOf: Instant,
    val dailyAsOf: Instant,
    val calculatedAt: Instant,
    val dataFetchedAt: Instant,
    val staleAfter: Instant,
    val fiftyTwoWeekLow: Double?,
    val fiftyTwoWeekHigh: Double?,
    val fiftyTwoWeekPosition: Double?,
    val levels: List<PriceLevel>,
    val nearestSupport: PriceLevel?,
    val nearestResistance: PriceLevel?,
    val quoteSource: DataSource,
    val barSource: DataSource,
    val source: DataSource = DataSource.LOCAL_CALCULATION,
) {
    init {
        require(currentPrice > 0.0) { "Current price must be positive" }
        require(dailyBarCount > 0) { "Price levels require daily history" }
        require((fiftyTwoWeekLow == null) == (fiftyTwoWeekHigh == null)) {
            "52-week high and low must be available together"
        }
        require(fiftyTwoWeekLow == null || fiftyTwoWeekLow > 0.0) {
            "52-week low must be positive when present"
        }
        require(
            fiftyTwoWeekLow == null ||
                fiftyTwoWeekHigh == null ||
                fiftyTwoWeekHigh >= fiftyTwoWeekLow,
        ) {
            "52-week high cannot be below the 52-week low"
        }
        require(fiftyTwoWeekPosition == null || fiftyTwoWeekPosition in 0.0..1.0) {
            "52-week position must be between 0 and 1 when present"
        }
        require(fiftyTwoWeekLow != null || fiftyTwoWeekPosition == null) {
            "52-week position requires a complete 52-week range"
        }
        require(!staleAfter.isBefore(dataFetchedAt)) {
            "Price-level stale time cannot precede fetch time"
        }
        require(levels.zipWithNext().all { (left, right) -> left.value <= right.value }) {
            "Price levels must be sorted"
        }
        require(nearestSupport == null || nearestSupport.kind == PriceLevelKind.SUPPORT) {
            "Nearest support must be a support level"
        }
        require(
            nearestResistance == null ||
                nearestResistance.kind == PriceLevelKind.RESISTANCE,
        ) {
            "Nearest resistance must be a resistance level"
        }
        require(source == DataSource.LOCAL_CALCULATION) {
            "Price levels must be calculated locally"
        }
    }

    fun isStaleAt(now: Instant): Boolean = !now.isBefore(staleAfter)
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

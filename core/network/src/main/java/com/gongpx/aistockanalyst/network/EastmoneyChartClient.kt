package com.gongpx.aistockanalyst.network

import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.StockSymbol
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

data class EastmoneySecId(
    val market: Int,
    val code: String,
) {
    init {
        require(market >= 0) { "Eastmoney market must be non-negative" }
        require(code.isNotBlank()) { "Eastmoney code is required" }
    }

    val value: String = "$market.$code"
}

interface EastmoneySecIdResolver {
    suspend fun resolve(symbol: StockSymbol): EastmoneySecId
}

class EastmoneySymbolResolver(
    private val service: RawHttpService,
    private val json: Json,
) : EastmoneySecIdResolver {
    private val cache = ConcurrentHashMap<StockSymbol, EastmoneySecId>()

    override suspend fun resolve(symbol: StockSymbol): EastmoneySecId =
        cache[symbol] ?: resolveUncached(symbol).also { cache[symbol] = it }

    private suspend fun resolveUncached(symbol: StockSymbol): EastmoneySecId {
        findExact(symbol.value)?.let { return it }
        symbol.aliases().forEach { alias ->
            findExact(alias)?.let { return it }
        }
        throw EastmoneySymbolNotFoundException(symbol)
    }

    private suspend fun findExact(query: String): EastmoneySecId? {
        val response = service.get(
            url = "$SEARCH_URL&keyword=${query.urlEncode()}",
            headers = REQUEST_HEADERS,
        )
        val body = response.requireBody(PROVIDER).use { it.string() }
        return EastmoneySearchParser.parseExact(body, query, json)
    }

    private fun StockSymbol.aliases(): List<String> = buildList {
        val underscored = value.replace('.', '_').replace('-', '_')
        if (underscored != value) {
            add(underscored)
        }
    }

    companion object {
        private const val PROVIDER = "Eastmoney"
        private const val SEARCH_URL =
            "https://search-codetable.eastmoney.com/codetable/search/web" +
                "?client=web&clientType=webSuggest&clientVersion=lastest" +
                "&pageIndex=1&pageSize=5"
    }
}

class EastmoneyChartClient(
    private val service: RawHttpService,
    private val json: Json,
    private val clock: Clock,
    private val symbolResolver: EastmoneySecIdResolver,
) : ChartClient {
    private val requestMutex = Mutex()

    override suspend fun fetchBars(
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        start: Instant,
        endExclusive: Instant,
    ): List<PriceBar> = requestMutex.withLock {
        require(start < endExclusive) { "Bar range end must be after its start" }
        val now = clock.instant()
        EastmoneyChartCapabilities.requireSupported(interval, start, now)
        val secId = symbolResolver.resolve(symbol)
        val providerInterval = if (interval == BarInterval.FOUR_HOURS) {
            BarInterval.ONE_HOUR
        } else {
            interval
        }
        val response = service.get(
            url = buildUrl(
                secId = secId,
                interval = providerInterval,
                start = start,
                endExclusive = endExclusive,
            ),
            headers = REQUEST_HEADERS,
        )
        val body = response.requireBody(PROVIDER).use { it.string() }
        val providerBars = EastmoneyKlineParser.parse(
            body = body,
            symbol = symbol,
            exchange = exchange,
            interval = providerInterval,
            secId = secId,
            fetchedAt = now,
            completedAt = now,
            json = json,
        ).filter { it.isCompletedAt(now) }
        val requestedBars = if (interval == BarInterval.FOUR_HOURS) {
            EastmoneyFourHourAggregator.completedBars(providerBars, now)
        } else {
            providerBars
        }
        return requestedBars
            .filter { !it.start.isBefore(start) && it.endExclusive <= endExclusive }
            .sortedBy(PriceBar::start)
    }

    private fun buildUrl(
        secId: EastmoneySecId,
        interval: BarInterval,
        start: Instant,
        endExclusive: Instant,
    ): String {
        val dateZone = if (interval == BarInterval.ONE_DAY || interval == BarInterval.ONE_MONTH) {
            EASTERN
        } else {
            SHANGHAI
        }
        val begin = start.atZone(dateZone).toLocalDate().format(BASIC_DATE)
        val end = endExclusive.minusNanos(1).atZone(dateZone).toLocalDate().format(BASIC_DATE)
        val parameters = listOf(
            "secid" to secId.value,
            "fields1" to "f1,f2,f3,f4,f5,f6",
            "fields2" to "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",
            "beg" to begin,
            "end" to end,
            "klt" to interval.toEastmoneyKlt(),
            "fqt" to "0",
            "lmt" to "1000000",
        )
        return "$KLINE_URL?" + parameters.joinToString("&") { (name, value) ->
            "$name=${value.urlEncode()}"
        }
    }

    companion object {
        private const val PROVIDER = "Eastmoney"
        private const val KLINE_URL =
            "https://push2his.eastmoney.com/api/qt/stock/kline/get"
    }
}

object EastmoneyChartCapabilities {
    fun preferredHistory(interval: BarInterval): Duration = when (interval) {
        BarInterval.ONE_MINUTE -> Duration.ofDays(1)
        BarInterval.FIVE_MINUTES,
        BarInterval.FIFTEEN_MINUTES,
        BarInterval.THIRTY_MINUTES,
        -> Duration.ofDays(7)
        BarInterval.ONE_HOUR,
        BarInterval.FOUR_HOURS,
        -> Duration.ofDays(30)
        BarInterval.ONE_DAY -> Duration.ofDays(400)
        BarInterval.ONE_MONTH -> Duration.ofDays(3_650)
    }

    internal fun requireSupported(
        interval: BarInterval,
        start: Instant,
        now: Instant,
    ) {
        val maximumAge = when (interval) {
            BarInterval.ONE_MINUTE -> Duration.ofDays(2)
            BarInterval.FIVE_MINUTES,
            BarInterval.FIFTEEN_MINUTES,
            BarInterval.THIRTY_MINUTES,
            -> Duration.ofDays(8)
            BarInterval.ONE_HOUR,
            BarInterval.FOUR_HOURS,
            -> Duration.ofDays(32)
            BarInterval.ONE_DAY,
            BarInterval.ONE_MONTH,
            -> null
        }
        if (maximumAge != null && start < now.minus(maximumAge)) {
            throw EastmoneyChartCapabilityException(interval)
        }
    }
}

class EastmoneyChartCapabilityException(
    interval: BarInterval,
) : ProviderException(
    "Eastmoney experimental ${interval.name.lowercase(Locale.ROOT)} history " +
        "is unavailable for the requested range because observed intraday retention is short",
)

class EastmoneySymbolNotFoundException(
    symbol: StockSymbol,
) : ProviderException("Eastmoney has no unambiguous US symbol match for ${symbol.value}")

internal object EastmoneySearchParser {
    fun parseExact(
        body: String,
        query: String,
        json: Json = Json { ignoreUnknownKeys = true },
    ): EastmoneySecId? {
        val root = parseRoot(body, json, "search")
        val code = (root["code"] as? JsonPrimitive)
            ?.takeIf(JsonPrimitive::isString)
            ?.contentOrNull
        if (code != "0") {
            throw ProviderPayloadException("Eastmoney", "search response code is invalid")
        }
        val result = root["result"] as? JsonArray
            ?: throw ProviderPayloadException("Eastmoney", "search result is missing")
        val matches = result.map { raw ->
            val item = raw as? JsonObject
                ?: throw ProviderPayloadException("Eastmoney", "search item is not an object")
            val itemCode = item.requiredSearchString("code")
            val typeName = item.requiredSearchString("securityTypeName")
            val market = (item["market"] as? JsonPrimitive)
                ?.takeUnless(JsonPrimitive::isString)
                ?.intOrNull
                ?.takeIf { it >= 0 }
                ?: throw ProviderPayloadException("Eastmoney", "search market is invalid")
            Triple(itemCode, typeName, market)
        }.filter { (itemCode, typeName) ->
            itemCode.equals(query, ignoreCase = true) && typeName == US_SECURITY_TYPE
        }
        if (matches.size > 1) {
            throw ProviderPayloadException("Eastmoney", "search result is ambiguous")
        }
        return matches.singleOrNull()?.let { (itemCode, _, market) ->
            EastmoneySecId(market = market, code = itemCode)
        }
    }

    private const val US_SECURITY_TYPE = "美股"
}

internal object EastmoneyKlineParser {
    fun parse(
        body: String,
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        secId: EastmoneySecId,
        fetchedAt: Instant,
        completedAt: Instant,
        json: Json = Json { ignoreUnknownKeys = true },
    ): List<PriceBar> {
        val root = parseRoot(body, json, "k-line")
        val responseCode = (root["rc"] as? JsonPrimitive)
            ?.takeUnless(JsonPrimitive::isString)
            ?.intOrNull
        if (responseCode != 0) {
            throw ProviderPayloadException("Eastmoney", "k-line response code is invalid")
        }
        val data = root["data"] as? JsonObject
            ?: throw ProviderPayloadException("Eastmoney", "k-line data is missing")
        val returnedCode = data.requiredKlineString("code")
        val returnedMarket = (data["market"] as? JsonPrimitive)
            ?.takeUnless(JsonPrimitive::isString)
            ?.intOrNull
            ?: throw ProviderPayloadException("Eastmoney", "k-line market is invalid")
        if (
            !returnedCode.equals(secId.code, ignoreCase = true) ||
            returnedMarket != secId.market
        ) {
            throw ProviderPayloadException("Eastmoney", "k-line symbol identity does not match")
        }
        val klines = data["klines"] as? JsonArray
            ?: throw ProviderPayloadException("Eastmoney", "k-line rows are missing")
        val bars = klines.map { raw ->
            val row = (raw as? JsonPrimitive)
                ?.takeIf(JsonPrimitive::isString)
                ?.contentOrNull
                ?: throw ProviderPayloadException("Eastmoney", "k-line row is not a string")
            parseRow(
                row = row,
                symbol = symbol,
                exchange = exchange,
                interval = interval,
                fetchedAt = fetchedAt,
            )
        }
        if (bars.map(PriceBar::start).distinct().size != bars.size) {
            throw ProviderPayloadException("Eastmoney", "k-line timestamps contain duplicates")
        }
        val currentMonth = YearMonth.from(completedAt.atZone(exchange.zoneId))
        return bars.filterNot { bar ->
            interval == BarInterval.ONE_MONTH &&
                YearMonth.from(bar.start.atZone(exchange.zoneId)) == currentMonth
        }
    }

    private fun parseRow(
        row: String,
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        fetchedAt: Instant,
    ): PriceBar {
        val fields = row.split(',')
        if (fields.size < REQUIRED_CSV_FIELDS) {
            throw ProviderPayloadException("Eastmoney", "k-line row has too few fields")
        }
        if (fields.take(REQUIRED_CSV_FIELDS).any(String::isBlank)) {
            throw ProviderPayloadException("Eastmoney", "k-line row has a blank required field")
        }
        val (start, endExclusive) = parseBoundaries(fields[0], interval, exchange)
        val open = fields[1].requiredPrice("open")
        val close = fields[2].requiredPrice("close")
        val high = fields[3].requiredPrice("high")
        val low = fields[4].requiredPrice("low")
        val volume = fields[5].toLongOrNull()
            ?.takeIf { it >= 0L }
            ?: throw ProviderPayloadException("Eastmoney", "volume is not a non-negative integer")
        return providerModel("Eastmoney") {
            PriceBar(
                symbol = symbol,
                exchange = exchange,
                interval = interval,
                start = start,
                endExclusive = endExclusive,
                open = open,
                high = high,
                low = low,
                close = close,
                volume = volume,
                fetchedAt = fetchedAt,
                parseStatus = ParseStatus.VALID,
                source = DataSource.EASTMONEY_EXPERIMENTAL,
            )
        }
    }

    private fun parseBoundaries(
        rawTimestamp: String,
        interval: BarInterval,
        exchange: Exchange,
    ): Pair<Instant, Instant> = when (interval) {
        BarInterval.ONE_DAY -> {
            val date = rawTimestamp.requiredDate(DAILY_TIMESTAMP)
            val start = date.atStartOfDay(exchange.zoneId).toInstant()
            start to date.plusDays(1).atStartOfDay(exchange.zoneId).toInstant()
        }
        BarInterval.ONE_MONTH -> {
            val stampedDate = rawTimestamp.requiredDate(DAILY_TIMESTAMP)
            val month = YearMonth.from(stampedDate)
            val start = month.atDay(1).atStartOfDay(exchange.zoneId).toInstant()
            start to month.plusMonths(1).atDay(1).atStartOfDay(exchange.zoneId).toInstant()
        }
        BarInterval.FOUR_HOURS -> error("Eastmoney does not return native four-hour rows")
        else -> intradayBoundaries(rawTimestamp, interval, exchange)
    }

    private fun intradayBoundaries(
        rawTimestamp: String,
        interval: BarInterval,
        exchange: Exchange,
    ): Pair<Instant, Instant> {
        val label = try {
            LocalDateTime.parse(rawTimestamp, INTRADAY_TIMESTAMP)
        } catch (failure: DateTimeParseException) {
            throw ProviderPayloadException("Eastmoney", "bar timestamp is invalid", failure)
        }
        val endExclusive = label.atZone(SHANGHAI).toInstant()
        val exchangeLabel = endExclusive.atZone(exchange.zoneId)
        val sessionDate = exchangeLabel.toLocalDate()
        val sessionStart = sessionDate.atTime(REGULAR_OPEN).atZone(exchange.zoneId).toInstant()
        val sessionEnd = sessionDate.atTime(REGULAR_CLOSE).atZone(exchange.zoneId).toInstant()
        if (endExclusive <= sessionStart || endExclusive > sessionEnd) {
            throw ProviderPayloadException(
                "Eastmoney",
                "intraday label is outside the regular US session",
            )
        }
        val duration = requireNotNull(interval.duration)
        val elapsedNanos = Duration.between(sessionStart, endExclusive).toNanos()
        val durationNanos = duration.toNanos()
        val bucketIndex = (elapsedNanos - 1L) / durationNanos
        val start = sessionStart.plusNanos(bucketIndex * durationNanos)
        val expectedEnd = minOf(start.plus(duration), sessionEnd)
        if (expectedEnd != endExclusive) {
            throw ProviderPayloadException(
                "Eastmoney",
                "intraday label is not aligned to its exchange session bucket",
            )
        }
        return start to endExclusive
    }

    private const val REQUIRED_CSV_FIELDS = 6
}

internal object EastmoneyFourHourAggregator {
    fun completedBars(
        hourlyBars: List<PriceBar>,
        completedAt: Instant,
    ): List<PriceBar> {
        if (hourlyBars.isEmpty()) {
            return emptyList()
        }
        val first = hourlyBars.first()
        hourlyBars.forEach { bar ->
            require(bar.interval == BarInterval.ONE_HOUR) {
                "Four-hour aggregation requires Eastmoney hourly bars"
            }
            require(
                bar.symbol == first.symbol &&
                    bar.exchange == first.exchange &&
                    bar.source == first.source,
            ) {
                "Four-hour aggregation requires one symbol, exchange, and source"
            }
        }
        return hourlyBars
            .sortedBy(PriceBar::start)
            .groupBy { bar -> fourHourBucketStart(bar) }
            .mapNotNull { (start, bucket) ->
                val sessionEnd = start.atZone(first.exchange.zoneId)
                    .toLocalDate()
                    .atTime(REGULAR_CLOSE)
                    .atZone(first.exchange.zoneId)
                    .toInstant()
                val endExclusive = minOf(start.plus(Duration.ofHours(4)), sessionEnd)
                val ordered = bucket.sortedBy(PriceBar::start)
                if (
                    completedAt < endExclusive ||
                    !ordered.coversEntireBucket(start, endExclusive)
                ) {
                    null
                } else {
                    providerModel("Eastmoney") {
                        PriceBar(
                            symbol = first.symbol,
                            exchange = first.exchange,
                            interval = BarInterval.FOUR_HOURS,
                            start = start,
                            endExclusive = endExclusive,
                            open = ordered.first().open,
                            high = ordered.maxOf(PriceBar::high),
                            low = ordered.minOf(PriceBar::low),
                            close = ordered.last().close,
                            volume = ordered.fold(0L) { total, bar ->
                                Math.addExact(total, bar.volume)
                            },
                            fetchedAt = ordered.maxOf(PriceBar::fetchedAt),
                            parseStatus = if (
                                ordered.all { it.parseStatus == ParseStatus.VALID }
                            ) {
                                ParseStatus.VALID
                            } else {
                                ParseStatus.PARTIAL
                            },
                            source = first.source,
                        )
                    }
                }
            }
            .sortedBy(PriceBar::start)
    }

    private fun List<PriceBar>.coversEntireBucket(
        start: Instant,
        endExclusive: Instant,
    ): Boolean =
        isNotEmpty() &&
            first().start == start &&
            last().endExclusive == endExclusive &&
            zipWithNext().all { (current, next) ->
                current.endExclusive == next.start
            }

    private fun fourHourBucketStart(bar: PriceBar): Instant {
        val exchangeTime = bar.start.atZone(bar.exchange.zoneId)
        val sessionStart = exchangeTime.toLocalDate()
            .atTime(REGULAR_OPEN)
            .atZone(bar.exchange.zoneId)
            .toInstant()
        val elapsedMinutes = Duration.between(sessionStart, bar.start).toMinutes()
        if (elapsedMinutes !in 0 until REGULAR_SESSION_MINUTES) {
            throw IllegalArgumentException("Hourly bar is outside the regular US session")
        }
        return sessionStart.plus(Duration.ofMinutes((elapsedMinutes / 240L) * 240L))
    }
}

private fun parseRoot(
    body: String,
    json: Json,
    payloadName: String,
): JsonObject = try {
    json.parseToJsonElement(body).jsonObject
} catch (failure: IllegalArgumentException) {
    throw ProviderPayloadException("Eastmoney", "$payloadName JSON is invalid", failure)
}

private fun JsonObject.requiredSearchString(name: String): String =
    (get(name) as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)
        ?: throw ProviderPayloadException("Eastmoney", "search $name is invalid")

private fun JsonObject.requiredKlineString(name: String): String =
    (get(name) as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)
        ?: throw ProviderPayloadException("Eastmoney", "k-line $name is invalid")

private fun String.requiredPrice(name: String): Double =
    toDoubleOrNull()
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?: throw ProviderPayloadException("Eastmoney", "$name is not a finite positive value")

private fun String.requiredDate(formatter: DateTimeFormatter): LocalDate = try {
    LocalDate.parse(this, formatter)
} catch (failure: DateTimeParseException) {
    throw ProviderPayloadException("Eastmoney", "bar date is invalid", failure)
}

private fun BarInterval.toEastmoneyKlt(): String = when (this) {
    BarInterval.ONE_MINUTE -> "1"
    BarInterval.FIVE_MINUTES -> "5"
    BarInterval.FIFTEEN_MINUTES -> "15"
    BarInterval.THIRTY_MINUTES -> "30"
    BarInterval.ONE_HOUR -> "60"
    BarInterval.ONE_DAY -> "101"
    BarInterval.ONE_MONTH -> "103"
    BarInterval.FOUR_HOURS -> error("Eastmoney four-hour bars are aggregated from hourly rows")
}

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private val REQUEST_HEADERS = mapOf(
    "Accept" to "application/json",
    "User-Agent" to "AIStockAnalyst/0.1 Android",
)
private val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")
private val EASTERN: ZoneId = ZoneId.of("America/New_York")
private val BASIC_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
private val DAILY_TIMESTAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(java.time.format.ResolverStyle.STRICT)
private val INTRADAY_TIMESTAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
        .withResolverStyle(java.time.format.ResolverStyle.STRICT)
private val REGULAR_OPEN: LocalTime = LocalTime.of(9, 30)
private val REGULAR_CLOSE: LocalTime = LocalTime.of(16, 0)
private const val REGULAR_SESSION_MINUTES = 390L

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
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

data class AlpacaApiCredentials(
    val keyId: String,
    val secretKey: String,
) {
    init {
        require(keyId.isNotBlank()) { "Alpaca key ID is required" }
        require(secretKey.isNotBlank()) { "Alpaca secret key is required" }
    }
}

interface ChartClient {
    suspend fun fetchBars(
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        start: Instant,
        endExclusive: Instant,
    ): List<PriceBar>
}

class AlpacaChartClient(
    private val service: RawHttpService,
    private val json: Json,
    private val clock: Clock,
    private val credentialsProvider: suspend () -> AlpacaApiCredentials?,
) : ChartClient {
    override suspend fun fetchBars(
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        start: Instant,
        endExclusive: Instant,
    ): List<PriceBar> {
        require(start < endExclusive) { "Bar range end must be after its start" }
        val credentials = credentialsProvider()
            ?: throw AlpacaCredentialsMissingException()
        val headers = mapOf(
            "Accept" to "application/json",
            "User-Agent" to "AIStockAnalyst/0.1 Android",
            "APCA-API-KEY-ID" to credentials.keyId,
            "APCA-API-SECRET-KEY" to credentials.secretKey,
        )
        val fetchedBars = mutableListOf<PriceBar>()
        val seenPageTokens = mutableSetOf<String>()
        var pageToken: String? = null
        var pageCount = 0

        do {
            if (++pageCount > MAX_PAGES) {
                throw ProviderPayloadException(PROVIDER, "pagination limit exceeded")
            }
            val response = service.get(
                url = buildUrl(
                    symbol = symbol,
                    interval = interval,
                    start = start,
                    endExclusive = endExclusive,
                    pageToken = pageToken,
                ),
                headers = headers,
            )
            val body = response.requireBody(PROVIDER).use { it.string() }
            val page = AlpacaBarsParser.parse(
                body = body,
                symbol = symbol,
                exchange = exchange,
                interval = interval,
                fetchedAt = clock.instant(),
                json = json,
            )
            fetchedBars += page.bars
            pageToken = page.nextPageToken
            if (pageToken != null && !seenPageTokens.add(pageToken)) {
                throw ProviderPayloadException(PROVIDER, "pagination token repeated")
            }
        } while (pageToken != null)

        val now = clock.instant()
        return fetchedBars
            .asSequence()
            .filter { !it.start.isBefore(start) && it.endExclusive <= endExclusive }
            .filter { it.isCompletedAt(now) }
            .associateBy(PriceBar::start)
            .values
            .sortedBy(PriceBar::start)
            .toList()
    }

    private fun buildUrl(
        symbol: StockSymbol,
        interval: BarInterval,
        start: Instant,
        endExclusive: Instant,
        pageToken: String?,
    ): String {
        val parameters = buildList {
            add("symbols" to symbol.toAlpacaSymbol())
            add("timeframe" to interval.toAlpacaTimeframe())
            add("start" to start.toString())
            add("end" to endExclusive.minusNanos(1).toString())
            add("limit" to PAGE_SIZE.toString())
            add("sort" to "asc")
            add("feed" to "iex")
            add("adjustment" to "split")
            pageToken?.let { add("page_token" to it) }
        }
        return "$BASE_URL?" + parameters.joinToString("&") { (name, value) ->
            "$name=${value.urlEncode()}"
        }
    }

    companion object {
        private const val PROVIDER = "Alpaca IEX"
        private const val BASE_URL = "https://data.alpaca.markets/v2/stocks/bars"
        private const val PAGE_SIZE = 10_000
        private const val MAX_PAGES = 100
    }
}

class AlpacaCredentialsMissingException : ProviderException(
    "Alpaca IEX credentials are not configured",
)

internal data class AlpacaBarsPage(
    val bars: List<PriceBar>,
    val nextPageToken: String?,
)

internal object AlpacaBarsParser {
    fun parse(
        body: String,
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        fetchedAt: Instant,
        json: Json = Json { ignoreUnknownKeys = true },
    ): AlpacaBarsPage {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (failure: IllegalArgumentException) {
            throw ProviderPayloadException("Alpaca IEX", "invalid JSON", failure)
        }
        val barsBySymbol = root["bars"] as? JsonObject
            ?: throw ProviderPayloadException("Alpaca IEX", "bars object is missing")
        val providerSymbol = symbol.toAlpacaSymbol()
        val rawBars = when (val symbolBars = barsBySymbol[providerSymbol]) {
            null -> JsonArray(emptyList())
            is JsonArray -> symbolBars
            else -> throw ProviderPayloadException(
                "Alpaca IEX",
                "bars for $providerSymbol are not an array",
            )
        }
        val bars = rawBars.map { element ->
            parseBar(
                raw = element as? JsonObject
                    ?: throw ProviderPayloadException(
                        "Alpaca IEX",
                        "bar entry is not an object",
                    ),
                symbol = symbol,
                exchange = exchange,
                interval = interval,
                fetchedAt = fetchedAt,
            )
        }
        val nextPageToken = when (val token = root["next_page_token"]) {
            null, JsonNull -> null
            is JsonPrimitive -> token.contentOrNull
                ?.takeIf { token.isString && it.isNotBlank() }
                ?: throw ProviderPayloadException(
                    "Alpaca IEX",
                    "next_page_token is not a non-empty string or null",
                )
            else -> throw ProviderPayloadException(
                "Alpaca IEX",
                "next_page_token has an invalid type",
            )
        }
        return AlpacaBarsPage(
            bars = bars,
            nextPageToken = nextPageToken,
        )
    }

    private fun parseBar(
        raw: JsonObject,
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        fetchedAt: Instant,
    ): PriceBar {
        val start = try {
            Instant.parse(raw.requiredString("t"))
        } catch (failure: DateTimeParseException) {
            throw ProviderPayloadException("Alpaca IEX", "bar timestamp is invalid", failure)
        }
        return providerModel("Alpaca IEX") {
            PriceBar(
                symbol = symbol,
                exchange = exchange,
                interval = interval,
                start = start,
                endExclusive = interval.endExclusive(start, exchange),
                open = raw.requiredDouble("o"),
                high = raw.requiredDouble("h"),
                low = raw.requiredDouble("l"),
                close = raw.requiredDouble("c"),
                volume = raw.requiredLong("v"),
                fetchedAt = fetchedAt,
                parseStatus = ParseStatus.VALID,
                source = DataSource.ALPACA_IEX,
            )
        }
    }
}

private fun BarInterval.endExclusive(
    start: Instant,
    exchange: Exchange,
): Instant = when (this) {
    BarInterval.ONE_DAY -> start.atZone(exchange.zoneId).plusDays(1).toInstant()
    BarInterval.ONE_MONTH -> start.atZone(exchange.zoneId).plusMonths(1).toInstant()
    else -> start.plus(requireNotNull(duration))
}

private fun BarInterval.toAlpacaTimeframe(): String = when (this) {
    BarInterval.ONE_MINUTE -> "1Min"
    BarInterval.FIVE_MINUTES -> "5Min"
    BarInterval.FIFTEEN_MINUTES -> "15Min"
    BarInterval.THIRTY_MINUTES -> "30Min"
    BarInterval.ONE_HOUR -> "1Hour"
    BarInterval.FOUR_HOURS -> "4Hour"
    BarInterval.ONE_DAY -> "1Day"
    BarInterval.ONE_MONTH -> "1Month"
}

private fun JsonObject.requiredString(name: String): String =
    (get(name) as? JsonPrimitive)
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
        ?: throw ProviderPayloadException("Alpaca IEX", "$name is missing or invalid")

private fun JsonObject.requiredDouble(name: String): Double =
    (get(name) as? JsonPrimitive)
        ?.takeUnless(JsonPrimitive::isString)
        ?.doubleOrNull
        ?.takeIf { it.isFinite() }
        ?: throw ProviderPayloadException("Alpaca IEX", "$name is missing or invalid")

private fun JsonObject.requiredLong(name: String): Long =
    (get(name) as? JsonPrimitive)
        ?.takeUnless(JsonPrimitive::isString)
        ?.longOrNull
        ?: throw ProviderPayloadException("Alpaca IEX", "$name is missing or invalid")

internal fun StockSymbol.toAlpacaSymbol(): String = value

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

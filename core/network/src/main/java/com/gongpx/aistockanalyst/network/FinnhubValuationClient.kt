package com.gongpx.aistockanalyst.network

import com.gongpx.aistockanalyst.model.AnalystTargets
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.model.ValuationSnapshot
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

data class FinnhubApiCredentials(val apiKey: String) {
    init {
        require(apiKey.isNotBlank()) { "Finnhub API key is required" }
    }

    override fun toString(): String = "FinnhubApiCredentials(apiKey=<redacted>)"
}

class FinnhubValuationClient(
    private val service: RawHttpService,
    private val json: Json,
    private val clock: Clock,
    private val credentialsProvider: suspend () -> FinnhubApiCredentials?,
) : ValuationClient {
    override suspend fun fetchValuation(symbol: StockSymbol): ValuationSnapshot {
        val credentials = credentialsProvider()
            ?: throw FinnhubCredentialsMissingException()
        val headers = mapOf(
            "Accept" to "application/json",
            "User-Agent" to "AIStockAnalyst/0.1 Android",
            "X-Finnhub-Token" to credentials.apiKey,
        )
        val targetBody = getBody("$BASE_URL/stock/price-target?symbol=${symbol.value}", headers)
        val recommendationBody =
            getBody("$BASE_URL/stock/recommendation?symbol=${symbol.value}", headers)
        val metricBody =
            getBody("$BASE_URL/stock/metric?symbol=${symbol.value}&metric=all", headers)
        return FinnhubValuationParser.parse(
            priceTargetBody = targetBody,
            recommendationBody = recommendationBody,
            metricBody = metricBody,
            symbol = symbol,
            fetchedAt = clock.instant(),
            json = json,
        )
    }

    private suspend fun getBody(
        url: String,
        headers: Map<String, String>,
    ): String = try {
        val response =
            service.get(url = url, headers = headers)
        response.requireBody(PROVIDER).use { it.string() }
    } catch (failure: ProviderException) {
        throw failure
    } catch (_: IOException) {
        throw ProviderTransportException(PROVIDER)
    }

    companion object {
        private const val PROVIDER = "Finnhub"
        private const val BASE_URL = "https://finnhub.io/api/v1"
    }
}

class FinnhubCredentialsMissingException : ProviderException(
    "Finnhub API key is not configured",
)

internal object FinnhubValuationParser {
    fun parse(
        priceTargetBody: String,
        recommendationBody: String,
        metricBody: String,
        symbol: StockSymbol,
        fetchedAt: Instant,
        json: Json = Json { ignoreUnknownKeys = true },
    ): ValuationSnapshot {
        val target = parseObject(priceTargetBody, "price target", json)
        val recommendations = parseArray(recommendationBody, "recommendation", json)
        val metricRoot = parseObject(metricBody, "metric", json)
        target.requireFinnhubSymbol(symbol, "price target")
        metricRoot.requireFinnhubSymbol(symbol, "metric")
        val metrics = metricRoot.optionalObject("metric")
        val recommendationObjects = recommendations.map { element ->
            (element as? JsonObject
                ?: payloadError("recommendation entry is not an object")).also {
                it.requireFinnhubSymbol(symbol, "recommendation")
            }
        }

        val targets = providerModel(PROVIDER) {
            AnalystTargets(
                low = target.optionalPositiveDouble("targetLow"),
                median = target.optionalPositiveDouble("targetMedian"),
                high = target.optionalPositiveDouble("targetHigh"),
            )
        }
        val analystCount = target.optionalNonNegativeInt("numberAnalysts")
        val forwardPe = metrics.optionalFiniteDouble("forwardPE")
        val recommendationKey = recommendationObjects
            .maxByOrNull { it.optionalPeriod("period") ?: LocalDate.MIN }
            ?.let(::deriveRecommendation)
        val asOf = target.optionalDate("lastUpdated")
            ?.atStartOfDay()
            ?.toInstant(ZoneOffset.UTC)
            ?: fetchedAt
        val requiredCore = listOf(
            targets.low,
            targets.median,
            targets.high,
            forwardPe,
            analystCount,
        )

        return providerModel(PROVIDER) {
            ValuationSnapshot(
                symbol = symbol,
                targets = targets,
                forwardPe = forwardPe,
                recommendationKey = recommendationKey,
                analystCount = analystCount,
                fiftyDayAverage = metrics.optionalPositiveDouble("50DayMovingAverage"),
                twoHundredDayAverage = metrics.optionalPositiveDouble("200DayMovingAverage"),
                fiftyTwoWeekLow = metrics.optionalPositiveDouble("52WeekLow"),
                fiftyTwoWeekHigh = metrics.optionalPositiveDouble("52WeekHigh"),
                marketCap = metrics.optionalMillionsAsBaseLong("marketCapitalization"),
                averageDailyVolume3Month = null,
                sector = null,
                industry = null,
                asOf = asOf,
                fetchedAt = fetchedAt,
                staleAfter = fetchedAt.plus(Duration.ofDays(1)),
                parseStatus = if (requiredCore.all { it != null }) {
                    ParseStatus.VALID
                } else {
                    ParseStatus.PARTIAL
                },
                source = DataSource.FINNHUB,
            )
        }
    }

    internal fun deriveRecommendation(raw: JsonObject): String? {
        val counts = listOf(
            "strongBuy" to raw.optionalNonNegativeInt("strongBuy"),
            "buy" to raw.optionalNonNegativeInt("buy"),
            "hold" to raw.optionalNonNegativeInt("hold"),
            "sell" to raw.optionalNonNegativeInt("sell"),
            "strongSell" to raw.optionalNonNegativeInt("strongSell"),
        )
        if (counts.all { it.second == null }) {
            return null
        }
        val total = counts.sumOf { it.second ?: 0 }
        if (total == 0) {
            return null
        }
        val weighted = listOf(5, 4, 3, 2, 1)
            .zip(counts)
            .sumOf { (weight, entry) -> weight * (entry.second ?: 0) }
            .toDouble() / total
        return when {
            weighted >= 4.5 -> "strong_buy"
            weighted >= 3.5 -> "buy"
            weighted >= 2.5 -> "hold"
            weighted >= 1.5 -> "sell"
            else -> "strong_sell"
        }
    }

    private const val PROVIDER = "Finnhub"

    private fun parseObject(body: String, endpoint: String, json: Json): JsonObject =
        try {
            val root = json.parseToJsonElement(body) as? JsonObject
                ?: payloadError("$endpoint response is not an object")
            if (root.optionalErrorMessage() != null) {
                payloadError("$endpoint provider error")
            }
            root
        } catch (failure: IllegalArgumentException) {
            throw ProviderPayloadException(PROVIDER, "$endpoint response is invalid JSON", failure)
        }

    private fun parseArray(body: String, endpoint: String, json: Json): JsonArray =
        try {
            json.parseToJsonElement(body) as? JsonArray
                ?: payloadError("$endpoint response is not an array")
        } catch (failure: IllegalArgumentException) {
            throw ProviderPayloadException(PROVIDER, "$endpoint response is invalid JSON", failure)
        }

    private fun payloadError(message: String): Nothing =
        throw ProviderPayloadException(PROVIDER, message)
}

private fun JsonObject.requireFinnhubSymbol(
    expected: StockSymbol,
    endpoint: String,
) {
    if (isEmpty()) {
        return
    }
    val actual = when (val value = get("symbol")) {
        is JsonPrimitive -> value.contentOrNull
            ?.takeIf { value.isString && it.isNotBlank() }
        else -> null
    }
    if (actual == null || !actual.equals(expected.value, ignoreCase = true)) {
        throw ProviderPayloadException("Finnhub", "$endpoint symbol does not match the request")
    }
}

private fun JsonObject.optionalErrorMessage(): String? = when (val value = get("error")) {
    null, JsonNull -> null
    is JsonPrimitive -> value.contentOrNull
        ?.takeIf { value.isString && it.isNotBlank() }
        ?: throw ProviderPayloadException("Finnhub", "error is not a non-empty string")
    else -> throw ProviderPayloadException("Finnhub", "error is not a string")
}

private fun JsonObject?.optionalObject(name: String): JsonObject? = when (val value = this?.get(name)) {
    null, JsonNull -> null
    is JsonObject -> value
    else -> throw ProviderPayloadException("Finnhub", "$name is not an object")
}

private fun JsonObject?.optionalPositiveDouble(name: String): Double? =
    optionalFiniteDouble(name)?.also {
        if (it <= 0.0) {
            throw ProviderPayloadException("Finnhub", "$name must be positive")
        }
    }

private fun JsonObject?.optionalFiniteDouble(name: String): Double? = when (val value = this?.get(name)) {
    null, JsonNull -> null
    is JsonPrimitive -> value
        .takeUnless(JsonPrimitive::isString)
        ?.doubleOrNull
        ?.takeIf(Double::isFinite)
        ?: throw ProviderPayloadException("Finnhub", "$name is not a finite number")
    else -> throw ProviderPayloadException("Finnhub", "$name is not a number")
}

private fun JsonObject.optionalNonNegativeInt(name: String): Int? = when (val value = get(name)) {
    null, JsonNull -> null
    is JsonPrimitive -> value
        .takeUnless(JsonPrimitive::isString)
        ?.intOrNull
        ?.takeIf { it >= 0 }
        ?: throw ProviderPayloadException("Finnhub", "$name is not a non-negative integer")
    else -> throw ProviderPayloadException("Finnhub", "$name is not an integer")
}

private fun JsonObject.optionalDate(name: String): LocalDate? = when (val value = get(name)) {
    null, JsonNull -> null
    is JsonPrimitive -> value.contentOrNull
        ?.takeIf { value.isString && it.isNotBlank() }
        ?.let { parseFinnhubDate(it, name) }
        ?: throw ProviderPayloadException("Finnhub", "$name is not a date")
    else -> throw ProviderPayloadException("Finnhub", "$name is not a date")
}

private fun parseFinnhubDate(value: String, name: String): LocalDate = try {
    LocalDate.parse(value)
} catch (_: DateTimeParseException) {
    try {
        LocalDateTime.parse(value, FINNHUB_DATE_TIME_FORMATTER).toLocalDate()
    } catch (failure: DateTimeParseException) {
        throw ProviderPayloadException("Finnhub", "$name is not a supported date", failure)
    }
}

private fun JsonObject.optionalPeriod(name: String): LocalDate? = optionalDate(name)

private val FINNHUB_DATE_TIME_FORMATTER =
    DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")

private fun JsonObject?.optionalMillionsAsBaseLong(name: String): Long? {
    val millions = optionalFiniteDouble(name) ?: return null
    if (millions < 0.0 || millions > Long.MAX_VALUE / 1_000_000.0) {
        throw ProviderPayloadException("Finnhub", "$name is outside the supported range")
    }
    return (millions * 1_000_000.0).toLong()
}

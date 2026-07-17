package com.gongpx.aistockanalyst.network

import com.gongpx.aistockanalyst.model.AnalystTargets
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.model.ValuationSnapshot
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

data class FmpApiCredentials(val apiKey: String) {
    init {
        require(apiKey.isNotBlank()) { "FMP API key is required" }
    }

    override fun toString(): String = "FmpApiCredentials(apiKey=<redacted>)"
}

class FmpValuationClient(
    private val service: RawHttpService,
    private val json: Json,
    private val clock: Clock,
    private val credentialsProvider: suspend () -> FmpApiCredentials?,
) : ValuationClient {
    override suspend fun fetchValuation(symbol: StockSymbol): ValuationSnapshot {
        val credentials = credentialsProvider()
            ?: throw FmpCredentialsMissingException()
        val quote = getBody("/quote", symbol, credentials.apiKey)
        val target = getBody("/price-target-consensus", symbol, credentials.apiKey)
        val grades = getBody("/grades-consensus", symbol, credentials.apiKey)
        return FmpValuationParser.parse(
            quoteBody = quote,
            targetBody = target,
            gradesBody = grades,
            symbol = symbol,
            fetchedAt = clock.instant(),
            json = json,
        )
    }

    private suspend fun getBody(
        endpoint: String,
        symbol: StockSymbol,
        apiKey: String,
    ): String = try {
        val url = "$BASE_URL$endpoint?symbol=${symbol.value}&apikey=${apiKey.urlEncode()}"
        val response = service.get(url = url, headers = DEFAULT_HEADERS)
        response.requireBody(PROVIDER).use { it.string() }
    } catch (failure: ProviderException) {
        throw failure
    } catch (_: IOException) {
        throw ProviderTransportException(PROVIDER)
    }

    companion object {
        private const val PROVIDER = "Financial Modeling Prep"
        private const val BASE_URL = "https://financialmodelingprep.com/stable"
        private val DEFAULT_HEADERS = mapOf(
            "Accept" to "application/json",
            "User-Agent" to "AIStockAnalyst/0.1 Android",
        )
    }
}

class FmpCredentialsMissingException : ProviderException(
    "FMP API key is not configured",
)

internal object FmpValuationParser {
    fun parse(
        quoteBody: String,
        targetBody: String,
        gradesBody: String,
        symbol: StockSymbol,
        fetchedAt: Instant,
        json: Json = Json { ignoreUnknownKeys = true },
    ): ValuationSnapshot {
        val quote = firstObject(quoteBody, "quote", json)
        val target = firstObject(targetBody, "price target consensus", json)
        val grades = firstObject(gradesBody, "grades consensus", json)
        quote.requireFmpSymbol(symbol, "quote")
        target.requireFmpSymbol(symbol, "price target consensus")
        grades.requireFmpSymbol(symbol, "grades consensus")
        val targets = providerModel(PROVIDER) {
            AnalystTargets(
                low = target.optionalFmpPositiveDouble("targetLow"),
                median = target.optionalFmpPositiveDouble("targetMedian")
                    ?: target.optionalFmpPositiveDouble("targetConsensus"),
                high = target.optionalFmpPositiveDouble("targetHigh"),
            )
        }
        validateGradeBuckets(grades)
        val asOf = quote.optionalFmpEpochSeconds("timestamp") ?: fetchedAt

        return providerModel(PROVIDER) {
            ValuationSnapshot(
                symbol = symbol,
                targets = targets,
                forwardPe = null,
                recommendationKey = grades.optionalFmpString("consensus")
                    ?.lowercase()
                    ?.replace(' ', '_'),
                analystCount = null,
                fiftyDayAverage = quote.optionalFmpPositiveDouble("priceAvg50"),
                twoHundredDayAverage = quote.optionalFmpPositiveDouble("priceAvg200"),
                fiftyTwoWeekLow = quote.optionalFmpPositiveDouble("yearLow"),
                fiftyTwoWeekHigh = quote.optionalFmpPositiveDouble("yearHigh"),
                marketCap = quote.optionalFmpNonNegativeLong("marketCap"),
                averageDailyVolume3Month = null,
                sector = null,
                industry = null,
                asOf = asOf,
                fetchedAt = fetchedAt,
                staleAfter = fetchedAt.plus(Duration.ofDays(1)),
                parseStatus = ParseStatus.PARTIAL,
                source = DataSource.FMP,
            )
        }
    }

    private fun firstObject(body: String, endpoint: String, json: Json): JsonObject? {
        val array = try {
            json.parseToJsonElement(body) as? JsonArray
                ?: throw ProviderPayloadException(PROVIDER, "$endpoint response is not an array")
        } catch (failure: IllegalArgumentException) {
            throw ProviderPayloadException(PROVIDER, "$endpoint response is invalid JSON", failure)
        }
        if (array.isEmpty()) {
            return null
        }
        return array.first() as? JsonObject
            ?: throw ProviderPayloadException(PROVIDER, "$endpoint entry is not an object")
    }

    private fun validateGradeBuckets(grades: JsonObject?) {
        listOf("strongBuy", "buy", "hold", "sell", "strongSell").forEach { name ->
            grades.optionalFmpNonNegativeLong(name)
        }
    }

    private const val PROVIDER = "Financial Modeling Prep"
}

private fun JsonObject?.requireFmpSymbol(
    expected: StockSymbol,
    endpoint: String,
) {
    if (this == null) {
        return
    }
    val actual = when (val value = get("symbol")) {
        is JsonPrimitive -> value.contentOrNull
            ?.takeIf { value.isString && it.isNotBlank() }
        else -> null
    }
    if (actual == null || !actual.equals(expected.value, ignoreCase = true)) {
        throw ProviderPayloadException(
            "Financial Modeling Prep",
            "$endpoint symbol does not match the request",
        )
    }
}

private fun JsonObject?.optionalFmpPositiveDouble(name: String): Double? =
    optionalFmpFiniteDouble(name)?.also {
        if (it <= 0.0) {
            throw ProviderPayloadException("Financial Modeling Prep", "$name must be positive")
        }
    }

private fun JsonObject?.optionalFmpFiniteDouble(name: String): Double? =
    when (val value = this?.get(name)) {
        null, JsonNull -> null
        is JsonPrimitive -> value
            .takeUnless(JsonPrimitive::isString)
            ?.doubleOrNull
            ?.takeIf(Double::isFinite)
            ?: throw ProviderPayloadException(
                "Financial Modeling Prep",
                "$name is not a finite number",
            )
        else -> throw ProviderPayloadException("Financial Modeling Prep", "$name is not a number")
    }

private fun JsonObject?.optionalFmpNonNegativeLong(name: String): Long? =
    when (val value = this?.get(name)) {
        null, JsonNull -> null
        is JsonPrimitive -> value
            .takeUnless(JsonPrimitive::isString)
            ?.longOrNull
            ?.takeIf { it >= 0L }
            ?: throw ProviderPayloadException(
                "Financial Modeling Prep",
                "$name is not a non-negative integer",
            )
        else -> throw ProviderPayloadException("Financial Modeling Prep", "$name is not an integer")
    }

private fun JsonObject?.optionalFmpString(name: String): String? =
    when (val value = this?.get(name)) {
        null, JsonNull -> null
        is JsonPrimitive -> value.contentOrNull
            ?.takeIf { value.isString && it.isNotBlank() }
            ?: throw ProviderPayloadException(
                "Financial Modeling Prep",
                "$name is not a non-empty string",
            )
        else -> throw ProviderPayloadException("Financial Modeling Prep", "$name is not a string")
    }

private fun JsonObject?.optionalFmpEpochSeconds(name: String): Instant? {
    val seconds = optionalFmpNonNegativeLong(name) ?: return null
    return try {
        Instant.ofEpochSecond(seconds)
    } catch (failure: RuntimeException) {
        throw ProviderPayloadException(
            "Financial Modeling Prep",
            "$name is outside the supported range",
            failure,
        )
    }
}

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

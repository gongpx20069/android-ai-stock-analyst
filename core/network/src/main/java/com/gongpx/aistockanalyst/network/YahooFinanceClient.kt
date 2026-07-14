package com.gongpx.aistockanalyst.network

import com.gongpx.aistockanalyst.model.AnalystTargets
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.model.ValuationSnapshot
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

interface ValuationClient {
    suspend fun fetchValuation(symbol: StockSymbol): ValuationSnapshot
}

class YahooFinanceClient(
    private val service: RawHttpService,
    private val json: Json,
    private val clock: Clock,
) : ValuationClient {
    private val sessionMutex = Mutex()
    private var crumb: String? = null

    override suspend fun fetchValuation(symbol: StockSymbol): ValuationSnapshot {
        val activeCrumb = getOrCreateCrumb()
        return try {
            fetchValuation(symbol, activeCrumb)
        } catch (failure: ProviderHttpException) {
            if (failure.statusCode !in SESSION_RETRY_STATUS_CODES) {
                throw failure
            }
            fetchValuation(symbol, refreshCrumb())
        }
    }

    private suspend fun fetchValuation(
        symbol: StockSymbol,
        crumb: String,
    ): ValuationSnapshot {
        val response = service.get(
            url = buildUrl(symbol, crumb),
            headers = DEFAULT_HEADERS,
        )
        val body = response.requireBody(PROVIDER).use { it.string() }
        return YahooFinanceParser.parse(
            body = body,
            symbol = symbol,
            fetchedAt = clock.instant(),
            json = json,
        )
    }

    private suspend fun getOrCreateCrumb(): String =
        crumb ?: sessionMutex.withLock {
            crumb ?: requestCrumb().also { crumb = it }
        }

    private suspend fun refreshCrumb(): String = sessionMutex.withLock {
        requestCrumb().also { crumb = it }
    }

    private suspend fun requestCrumb(): String {
        val cookieResponse = service.get(
            url = "https://fc.yahoo.com",
            headers = DEFAULT_HEADERS,
        )
        cookieResponse.body()?.close()
        cookieResponse.errorBody()?.close()

        val crumbResponse = service.get(
            url = "https://query2.finance.yahoo.com/v1/test/getcrumb",
            headers = DEFAULT_HEADERS,
        )
        val value = crumbResponse.requireBody(PROVIDER).use { it.string().trim() }
        if (value.isBlank() || value.startsWith("<!DOCTYPE", ignoreCase = true)) {
            throw ProviderPayloadException(PROVIDER, "crumb response is invalid")
        }
        return value
    }

    companion object {
        private const val PROVIDER = "Yahoo Finance"
        private const val MODULES =
            "price,financialData,summaryDetail,defaultKeyStatistics," +
                "recommendationTrend,assetProfile"
        private val SESSION_RETRY_STATUS_CODES = setOf(401, 403)
        private val DEFAULT_HEADERS = mapOf(
            "Accept" to "application/json",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 15) AIStockAnalyst/0.1",
        )

        private fun buildUrl(
            symbol: StockSymbol,
            crumb: String,
        ): String = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/" +
            "${symbol.toYahooSymbol()}?modules=$MODULES&crumb=" +
            URLEncoder.encode(crumb, StandardCharsets.UTF_8.name())
    }
}

internal object YahooFinanceParser {
    fun parse(
        body: String,
        symbol: StockSymbol,
        fetchedAt: Instant,
        json: Json = Json { ignoreUnknownKeys = true },
    ): ValuationSnapshot {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (failure: IllegalArgumentException) {
            throw ProviderPayloadException("Yahoo Finance", "invalid JSON", failure)
        }
        val summary = root.objectOrNull("quoteSummary")
            ?: throw ProviderPayloadException("Yahoo Finance", "quoteSummary is missing")
        summary["error"]?.takeUnless { it.toString() == "null" }?.let {
            throw ProviderPayloadException("Yahoo Finance", "provider returned $it")
        }
        val result = summary["result"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?: throw ProviderPayloadException("Yahoo Finance", "result is empty")

        val financialData = result.objectOrNull("financialData")
        val summaryDetail = result.objectOrNull("summaryDetail")
        val defaultKeyStatistics = result.objectOrNull("defaultKeyStatistics")
        val price = result.objectOrNull("price")
        val assetProfile = result.objectOrNull("assetProfile")

        val targets = providerModel("Yahoo Finance") {
            AnalystTargets(
                low = financialData.rawDouble("targetLowPrice"),
                median = financialData.rawDouble("targetMedianPrice"),
                high = financialData.rawDouble("targetHighPrice"),
            )
        }
        val analystCount = financialData.rawLong("numberOfAnalystOpinions")?.toIntExactOrNull()
        val requiredValuationFields = listOf(
            targets.low,
            targets.median,
            targets.high,
            financialData.rawDouble("forwardPE")
                ?: defaultKeyStatistics.rawDouble("forwardPE"),
            analystCount,
        )

        return providerModel("Yahoo Finance") {
            ValuationSnapshot(
            symbol = symbol,
            targets = targets,
            forwardPe = financialData.rawDouble("forwardPE")
                ?: defaultKeyStatistics.rawDouble("forwardPE"),
            recommendationKey = financialData.rawString("recommendationKey"),
            analystCount = analystCount,
            fiftyDayAverage = summaryDetail.rawDouble("fiftyDayAverage"),
            twoHundredDayAverage = summaryDetail.rawDouble("twoHundredDayAverage"),
            fiftyTwoWeekLow = summaryDetail.rawDouble("fiftyTwoWeekLow"),
            fiftyTwoWeekHigh = summaryDetail.rawDouble("fiftyTwoWeekHigh"),
            marketCap = price.rawLong("marketCap"),
            averageDailyVolume3Month = summaryDetail.rawLong("averageVolume"),
            sector = assetProfile.rawString("sector"),
            industry = assetProfile.rawString("industry"),
            asOf = fetchedAt,
            fetchedAt = fetchedAt,
            staleAfter = fetchedAt.plus(Duration.ofDays(1)),
            parseStatus = if (requiredValuationFields.all { it != null }) {
                ParseStatus.VALID
            } else {
                ParseStatus.PARTIAL
            },
            source = DataSource.YAHOO_FINANCE,
        )
        }
    }
}

private fun JsonObject?.rawDouble(key: String): Double? =
    this?.get(key).rawPrimitive()?.doubleOrNull?.takeIf { it.isFinite() }

private fun JsonObject?.rawLong(key: String): Long? =
    this?.get(key).rawPrimitive()?.longOrNull

private fun JsonObject?.rawString(key: String): String? =
    this?.get(key).rawPrimitive()?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonElement?.rawPrimitive(): JsonPrimitive? = when (this) {
    is JsonPrimitive -> this
    is JsonObject -> get("raw")?.jsonPrimitive
    else -> null
}

private fun JsonObject.objectOrNull(key: String): JsonObject? =
    get(key) as? JsonObject

private fun Long.toIntExactOrNull(): Int? =
    takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

internal fun StockSymbol.toYahooSymbol(): String = value.replace('.', '-')

package com.gongpx.aistockanalyst.network

import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.QuoteSnapshot
import com.gongpx.aistockanalyst.model.StockSymbol
import java.io.IOException
import java.nio.charset.Charset
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

interface QuoteClient {
    suspend fun fetchQuote(
        symbol: StockSymbol,
        exchange: Exchange,
    ): QuoteSnapshot
}

class TencentQuoteClient(
    private val service: RawHttpService,
    private val clock: Clock,
) : QuoteClient {
    override suspend fun fetchQuote(
        symbol: StockSymbol,
        exchange: Exchange,
    ): QuoteSnapshot {
        val response = service.get(
            url = "https://qt.gtimg.cn/q=us${symbol.toTencentSymbol()}",
            headers = DEFAULT_HEADERS,
        )
        val body = response.requireBody(PROVIDER).use { it.string() }
        return TencentQuoteParser.parse(
            body = body,
            symbol = symbol,
            exchange = exchange,
            fetchedAt = clock.instant(),
        )
    }

    companion object {
        private const val PROVIDER = "Tencent"
        private val DEFAULT_HEADERS = mapOf(
            "Accept" to "text/plain,*/*",
            "User-Agent" to "AIStockAnalyst/0.1 Android",
        )
    }
}

class SinaQuoteClient(
    private val service: RawHttpService,
    private val clock: Clock,
) : QuoteClient {
    override suspend fun fetchQuote(
        symbol: StockSymbol,
        exchange: Exchange,
    ): QuoteSnapshot {
        val response = service.get(
            url = "https://hq.sinajs.cn/list=gb_${symbol.toSinaSymbol()}",
            headers = DEFAULT_HEADERS,
        )
        val body = response.requireBody(PROVIDER).use {
            it.bytes().toString(GBK)
        }
        return SinaQuoteParser.parse(
            body = body,
            symbol = symbol,
            exchange = exchange,
            fetchedAt = clock.instant(),
        )
    }

    companion object {
        private const val PROVIDER = "Sina"
        private val GBK = Charset.forName("GBK")
        private val DEFAULT_HEADERS = mapOf(
            "Accept" to "text/plain,*/*",
            "Referer" to "https://finance.sina.com.cn",
            "User-Agent" to "AIStockAnalyst/0.1 Android",
        )
    }
}

class FallbackQuoteClient(
    private val primary: QuoteClient,
    private val fallback: QuoteClient,
) : QuoteClient {
    override suspend fun fetchQuote(
        symbol: StockSymbol,
        exchange: Exchange,
    ): QuoteSnapshot = try {
        primary.fetchQuote(symbol, exchange)
    } catch (primaryFailure: IOException) {
        try {
            fallback.fetchQuote(symbol, exchange)
        } catch (fallbackFailure: IOException) {
            throw AllQuoteProvidersFailedException(
                symbol = symbol,
                primaryFailure = primaryFailure,
                fallbackFailure = fallbackFailure,
            )
        }
    }
}

class AllQuoteProvidersFailedException(
    symbol: StockSymbol,
    primaryFailure: IOException,
    fallbackFailure: IOException,
) : IOException(
    "All quote providers failed for ${symbol.value}",
    fallbackFailure,
) {
    init {
        addSuppressed(primaryFailure)
    }
}

internal object TencentQuoteParser {
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun parse(
        body: String,
        symbol: StockSymbol,
        exchange: Exchange,
        fetchedAt: Instant,
    ): QuoteSnapshot {
        val fields = extractPayload(body, "Tencent").split('~')
        if (fields.size <= 49 || fields[0] != "200") {
            throw ProviderPayloadException("Tencent", "unexpected field count or status")
        }

        val currentPrice = fields.requiredPositiveDouble(3, "current price", "Tencent")
        val previousClose = fields.positiveDoubleOrNull(4)
        val open = fields.positiveDoubleOrNull(5)
        val volume = fields.nonNegativeLongOrNull(6)
        val dayHigh = fields.positiveDoubleOrNull(33)
        val dayLow = fields.positiveDoubleOrNull(34)
        val asOf = parseExchangeTimestamp(
            value = fields[30],
            formatter = timestampFormatter,
            exchange = exchange,
            provider = "Tencent",
        )

        return providerModel("Tencent") {
            QuoteSnapshot(
            symbol = symbol,
            exchange = exchange,
            currentPrice = currentPrice,
            previousClose = previousClose,
            open = open,
            dayHigh = dayHigh,
            dayLow = dayLow,
            change = fields.doubleOrNull(31),
            changePercent = fields.doubleOrNull(32)?.div(100.0),
            volume = volume,
            ttmPe = fields.positiveDoubleOrNull(39),
            marketCap = fields.positiveDoubleOrNull(45)?.times(100_000_000.0),
            fiftyTwoWeekLow = fields.positiveDoubleOrNull(49),
            fiftyTwoWeekHigh = fields.positiveDoubleOrNull(48),
            asOf = asOf,
            fetchedAt = fetchedAt,
            staleAfter = fetchedAt.plus(QUOTE_TTL),
            parseStatus = quoteParseStatus(previousClose, open, dayHigh, dayLow, volume),
            source = DataSource.TENCENT,
        )
        }
    }
}

internal object SinaQuoteParser {
    private val timestampFormatter = DateTimeFormatter.ofPattern(
        "yyyy MMM d hh:mma",
        Locale.US,
    )

    fun parse(
        body: String,
        symbol: StockSymbol,
        exchange: Exchange,
        fetchedAt: Instant,
    ): QuoteSnapshot {
        val fields = extractPayload(body, "Sina").split(',')
        if (fields.size <= 29) {
            throw ProviderPayloadException("Sina", "unexpected field count")
        }

        val currentPrice = fields.requiredPositiveDouble(1, "current price", "Sina")
        val previousClose = fields.positiveDoubleOrNull(26)
        val open = fields.positiveDoubleOrNull(5)
        val dayHigh = fields.positiveDoubleOrNull(6)
        val dayLow = fields.positiveDoubleOrNull(7)
        val volume = fields.nonNegativeLongOrNull(10)
        val year = fields[29].toIntOrNull()
            ?: throw ProviderPayloadException("Sina", "invalid quote year")
        val exchangeLabel = fields[24].substringBeforeLast(' ')
        val asOf = parseExchangeTimestamp(
            value = "$year $exchangeLabel",
            formatter = timestampFormatter,
            exchange = exchange,
            provider = "Sina",
        )

        return providerModel("Sina") {
            QuoteSnapshot(
            symbol = symbol,
            exchange = exchange,
            currentPrice = currentPrice,
            previousClose = previousClose,
            open = open,
            dayHigh = dayHigh,
            dayLow = dayLow,
            change = fields.doubleOrNull(4),
            changePercent = fields.doubleOrNull(2)?.div(100.0),
            volume = volume,
            ttmPe = fields.positiveDoubleOrNull(14),
            marketCap = fields.positiveDoubleOrNull(12),
            fiftyTwoWeekLow = fields.positiveDoubleOrNull(9),
            fiftyTwoWeekHigh = fields.positiveDoubleOrNull(8),
            asOf = asOf,
            fetchedAt = fetchedAt,
            staleAfter = fetchedAt.plus(QUOTE_TTL),
            parseStatus = quoteParseStatus(previousClose, open, dayHigh, dayLow, volume),
            source = DataSource.SINA,
        )
        }
    }
}

private val QUOTE_TTL: Duration = Duration.ofSeconds(60)

private fun quoteParseStatus(
    previousClose: Double?,
    open: Double?,
    dayHigh: Double?,
    dayLow: Double?,
    volume: Long?,
): ParseStatus = if (
    previousClose != null &&
    open != null &&
    dayHigh != null &&
    dayLow != null &&
    volume != null
) {
    ParseStatus.VALID
} else {
    ParseStatus.PARTIAL
}

private fun extractPayload(body: String, provider: String): String {
    val start = body.indexOf('"')
    val end = body.lastIndexOf('"')
    if (start < 0 || end <= start) {
        throw ProviderPayloadException(provider, "quoted payload is missing")
    }
    return body.substring(start + 1, end)
}

private fun parseExchangeTimestamp(
    value: String,
    formatter: DateTimeFormatter,
    exchange: Exchange,
    provider: String,
): Instant = try {
    LocalDateTime.parse(value.trim(), formatter)
        .atZone(exchange.zoneId)
        .toInstant()
} catch (failure: java.time.DateTimeException) {
    throw ProviderPayloadException(provider, "invalid timestamp: $value", failure)
}

private fun List<String>.requiredPositiveDouble(
    index: Int,
    fieldName: String,
    provider: String,
): Double = positiveDoubleOrNull(index)
    ?: throw ProviderPayloadException(provider, "$fieldName is missing or invalid")

private fun List<String>.doubleOrNull(index: Int): Double? =
    getOrNull(index)?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

private fun List<String>.positiveDoubleOrNull(index: Int): Double? =
    doubleOrNull(index)?.takeIf { it > 0.0 }

private fun List<String>.nonNegativeLongOrNull(index: Int): Long? =
    getOrNull(index)
        ?.trim()
        ?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0.0 && it <= Long.MAX_VALUE.toDouble() }
        ?.let { abs(it).toLong() }

internal fun StockSymbol.toTencentSymbol(): String = value

internal fun StockSymbol.toSinaSymbol(): String =
    value.lowercase(Locale.ROOT).replace('.', '$')

internal inline fun <T> providerModel(
    provider: String,
    factory: () -> T,
): T = try {
    factory()
} catch (failure: IllegalArgumentException) {
    throw ProviderPayloadException(provider, "normalized values are inconsistent", failure)
}

package com.gongpx.aistockanalyst.data

import com.gongpx.aistockanalyst.database.PriceBarDao
import com.gongpx.aistockanalyst.database.QuoteDao
import com.gongpx.aistockanalyst.database.ValuationDao
import com.gongpx.aistockanalyst.database.toEntity
import com.gongpx.aistockanalyst.database.toModel
import com.gongpx.aistockanalyst.datastore.MarketDataSourceSettingsStore
import com.gongpx.aistockanalyst.domain.PriceBarAggregator
import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.ChartProvider
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.QuoteSnapshot
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.model.ValuationSnapshot
import com.gongpx.aistockanalyst.network.QuoteClient
import com.gongpx.aistockanalyst.network.ChartClient
import com.gongpx.aistockanalyst.network.ValuationClient
import java.io.IOException
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

sealed interface RefreshResult<out T> {
    val value: T

    data class Fresh<T>(
        override val value: T,
    ) : RefreshResult<T>

    data class Cached<T>(
        override val value: T,
        val isStale: Boolean,
        val failureMessage: String,
    ) : RefreshResult<T>
}

interface MarketRepository {
    fun observeQuote(
        symbol: StockSymbol,
        exchange: Exchange,
    ): Flow<QuoteSnapshot?>

    fun observeValuation(symbol: StockSymbol): Flow<ValuationSnapshot?>

    fun observeBars(
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        limit: Int,
    ): Flow<List<PriceBar>>

    suspend fun refreshQuote(
        symbol: StockSymbol,
        exchange: Exchange,
    ): RefreshResult<QuoteSnapshot>

    suspend fun refreshValuation(symbol: StockSymbol): RefreshResult<ValuationSnapshot>

    suspend fun refreshBars(
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        start: Instant,
        endExclusive: Instant,
    ): RefreshResult<List<PriceBar>>
}

class RoomMarketRepository(
    private val quoteClient: QuoteClient,
    private val chartClient: ChartClient,
    private val valuationClient: ValuationClient,
    private val settingsStore: MarketDataSourceSettingsStore,
    private val quoteDao: QuoteDao,
    private val valuationDao: ValuationDao,
    private val priceBarDao: PriceBarDao,
    private val clock: Clock,
) : MarketRepository {
    override fun observeQuote(
        symbol: StockSymbol,
        exchange: Exchange,
    ): Flow<QuoteSnapshot?> = quoteDao.observe(symbol.value, exchange.name)
        .map { it?.toModel() }

    override fun observeValuation(symbol: StockSymbol): Flow<ValuationSnapshot?> =
        valuationDao.observe(symbol.value).map { it?.toModel() }

    override fun observeBars(
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        limit: Int,
    ): Flow<List<PriceBar>> {
        require(limit > 0) { "Bar limit must be positive" }
        val alpacaBars = priceBarDao.observeRecent(
            symbol = symbol.value,
            exchange = exchange.name,
            interval = interval.name,
            source = DataSource.ALPACA_IEX.name,
            limit = limit,
        )
        return combine(settingsStore.settings, alpacaBars) { settings, entities ->
            when (settings.chartProvider) {
                ChartProvider.NOT_CONFIGURED -> emptyList()
                ChartProvider.ALPACA_IEX -> entities.map { it.toModel() }
            }
        }
    }

    override suspend fun refreshQuote(
        symbol: StockSymbol,
        exchange: Exchange,
    ): RefreshResult<QuoteSnapshot> = try {
        val quote = quoteClient.fetchQuote(symbol, exchange)
        quoteDao.upsert(quote.toEntity())
        RefreshResult.Fresh(quote)
    } catch (failure: IOException) {
        val cached = quoteDao.get(symbol.value, exchange.name)?.toModel()
            ?: throw failure
        RefreshResult.Cached(
            value = cached,
            isStale = !clock.instant().isBefore(cached.staleAfter),
            failureMessage = failure.message ?: "Quote refresh failed",
        )
    }

    override suspend fun refreshValuation(
        symbol: StockSymbol,
    ): RefreshResult<ValuationSnapshot> = try {
        val valuation = valuationClient.fetchValuation(symbol)
        val cached = valuationDao.get(symbol.value)?.toModel()
        if (
            valuation.parseStatus == ParseStatus.PARTIAL &&
            cached?.parseStatus == ParseStatus.VALID
        ) {
            return RefreshResult.Cached(
                value = cached,
                isStale = !clock.instant().isBefore(cached.staleAfter),
                failureMessage = "Valuation provider returned incomplete core fields",
            )
        }
        valuationDao.upsert(valuation.toEntity())
        RefreshResult.Fresh(valuation)
    } catch (failure: IOException) {
        val cached = valuationDao.get(symbol.value)?.toModel()
            ?: throw failure
        RefreshResult.Cached(
            value = cached,
            isStale = !clock.instant().isBefore(cached.staleAfter),
            failureMessage = failure.message ?: "Valuation refresh failed",
        )
    }

    override suspend fun refreshBars(
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        start: Instant,
        endExclusive: Instant,
    ): RefreshResult<List<PriceBar>> {
        require(start < endExclusive) { "Bar range end must be after its start" }
        val source = settingsStore.current().chartProvider.toDataSource()
        return try {
            val bars = if (
                interval == BarInterval.ONE_MINUTE ||
                interval == BarInterval.FIVE_MINUTES
            ) {
                refreshOneAndFiveMinuteBars(
                    symbol = symbol,
                    exchange = exchange,
                    requestedInterval = interval,
                    source = source,
                    start = start,
                    endExclusive = endExclusive,
                )
            } else {
                refreshProviderBars(
                    symbol = symbol,
                    exchange = exchange,
                    interval = interval,
                    source = source,
                    start = start,
                    endExclusive = endExclusive,
                )
            }
            RefreshResult.Fresh(bars)
        } catch (failure: IOException) {
            val cached = priceBarDao.getRange(
                symbol = symbol.value,
                exchange = exchange.name,
                interval = interval.name,
                source = source.name,
                startEpochMillis = start.toEpochMilli(),
                endExclusiveEpochMillis = endExclusive.toEpochMilli(),
            ).map { it.toModel() }
            if (cached.isEmpty()) {
                throw failure
            }
            RefreshResult.Cached(
                value = cached,
                isStale = true,
                failureMessage = failure.message ?: "Chart refresh failed",
            )
        }
    }

    private suspend fun refreshProviderBars(
        symbol: StockSymbol,
        exchange: Exchange,
        interval: BarInterval,
        source: DataSource,
        start: Instant,
        endExclusive: Instant,
    ): List<PriceBar> {
        val bars = chartClient.fetchBars(
            symbol = symbol,
            exchange = exchange,
            interval = interval,
            start = start,
            endExclusive = endExclusive,
        )
        requireSource(bars, source)
        priceBarDao.replaceRange(
            symbol = symbol.value,
            exchange = exchange.name,
            interval = interval.name,
            startEpochMillis = start.toEpochMilli(),
            endExclusiveEpochMillis = endExclusive.toEpochMilli(),
            entities = bars.map(PriceBar::toEntity),
        )
        return bars
    }

    private suspend fun refreshOneAndFiveMinuteBars(
        symbol: StockSymbol,
        exchange: Exchange,
        requestedInterval: BarInterval,
        source: DataSource,
        start: Instant,
        endExclusive: Instant,
    ): List<PriceBar> {
        val refreshRange = PriceBarAggregator.enclosingFiveMinuteRange(
            start = start,
            endExclusive = endExclusive,
            exchange = exchange,
        )
        val oneMinuteBars = chartClient.fetchBars(
            symbol = symbol,
            exchange = exchange,
            interval = BarInterval.ONE_MINUTE,
            start = refreshRange.start,
            endExclusive = refreshRange.endExclusive,
        )
        requireSource(oneMinuteBars, source)
        val alignedFiveMinuteBars = PriceBarAggregator.completedFiveMinuteBars(
            oneMinuteBars = oneMinuteBars,
            completedAt = clock.instant(),
        )
        priceBarDao.replaceOneAndFiveMinuteRanges(
            symbol = symbol.value,
            exchange = exchange.name,
            oneMinuteInterval = BarInterval.ONE_MINUTE.name,
            fiveMinuteInterval = BarInterval.FIVE_MINUTES.name,
            startEpochMillis = refreshRange.start.toEpochMilli(),
            endExclusiveEpochMillis = refreshRange.endExclusive.toEpochMilli(),
            oneMinuteEntities = oneMinuteBars.map(PriceBar::toEntity),
            fiveMinuteEntities = alignedFiveMinuteBars.map(PriceBar::toEntity),
        )
        val requestedBars = when (requestedInterval) {
            BarInterval.ONE_MINUTE -> oneMinuteBars
            BarInterval.FIVE_MINUTES -> alignedFiveMinuteBars
            else -> error("Only one-minute and five-minute refreshes are coupled")
        }
        return requestedBars.filter {
            !it.start.isBefore(start) && it.endExclusive <= endExclusive
        }
    }

    private fun requireSource(
        bars: List<PriceBar>,
        source: DataSource,
    ) {
        if (bars.any { it.source != source }) {
            throw ChartSourceMismatchException()
        }
    }

    private fun ChartProvider.toDataSource(): DataSource = when (this) {
        ChartProvider.NOT_CONFIGURED -> throw ChartProviderNotConfiguredException()
        ChartProvider.ALPACA_IEX -> DataSource.ALPACA_IEX
    }

    private class ChartSourceMismatchException : IOException(
        "Chart provider returned bars from an unexpected source",
    )
}

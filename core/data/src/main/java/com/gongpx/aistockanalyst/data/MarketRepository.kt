package com.gongpx.aistockanalyst.data

import com.gongpx.aistockanalyst.database.PriceBarDao
import com.gongpx.aistockanalyst.database.QuoteDao
import com.gongpx.aistockanalyst.database.ValuationDao
import com.gongpx.aistockanalyst.database.toEntity
import com.gongpx.aistockanalyst.database.toModel
import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.QuoteSnapshot
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.model.ValuationSnapshot
import com.gongpx.aistockanalyst.network.QuoteClient
import com.gongpx.aistockanalyst.network.ValuationClient
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.flow.Flow
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
}

class RoomMarketRepository(
    private val quoteClient: QuoteClient,
    private val valuationClient: ValuationClient,
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
        return priceBarDao.observeRecent(
            symbol = symbol.value,
            exchange = exchange.name,
            interval = interval.name,
            limit = limit,
        ).map { entities -> entities.map { it.toModel() } }
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
}

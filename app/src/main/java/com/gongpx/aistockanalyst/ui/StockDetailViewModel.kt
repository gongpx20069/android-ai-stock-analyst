package com.gongpx.aistockanalyst.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gongpx.aistockanalyst.data.MarketRepository
import com.gongpx.aistockanalyst.data.RefreshResult
import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.PriceLevelSnapshot
import com.gongpx.aistockanalyst.model.QuoteSnapshot
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.model.TechnicalIndicatorSnapshot
import com.gongpx.aistockanalyst.model.ValuationSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class StockSelection(
    val symbol: StockSymbol,
    val exchange: Exchange,
)

data class StockDetailUiState(
    val selection: StockSelection? = null,
    val interval: BarInterval = BarInterval.ONE_DAY,
    val quote: QuoteSnapshot? = null,
    val valuation: ValuationSnapshot? = null,
    val technicals: TechnicalIndicatorSnapshot? = null,
    val priceLevels: PriceLevelSnapshot? = null,
    val bars: List<PriceBar> = emptyList(),
    val isRefreshing: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class StockDetailViewModel @Inject constructor(
    private val repository: MarketRepository,
    private val clock: Clock,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(StockDetailUiState())
    val uiState: StateFlow<StockDetailUiState> = mutableUiState.asStateFlow()

    private var observationJob: Job? = null
    private var fullRefreshJob: Job? = null
    private var chartRefreshJob: Job? = null
    private var fullRefreshGeneration = 0L
    private var chartRefreshGeneration = 0L
    private var isFullRefreshActive = false
    private var isChartRefreshActive = false
    private var fullRefreshMessage: String? = null
    private var chartRefreshMessage: String? = null

    fun openStock(
        rawSymbol: String,
        exchange: Exchange,
    ) {
        val symbol = try {
            StockSymbol.of(rawSymbol)
        } catch (failure: IllegalArgumentException) {
            mutableUiState.update {
                it.copy(message = failure.message ?: "Invalid US stock symbol")
            }
            return
        }
        cancelFullRefresh()
        cancelChartRefresh()
        mutableUiState.value = StockDetailUiState(
            selection = StockSelection(symbol, exchange),
        )
        observeSelection()
        refreshAll()
    }

    fun closeStock() {
        observationJob?.cancel()
        cancelFullRefresh()
        cancelChartRefresh()
        mutableUiState.value = StockDetailUiState()
    }

    fun selectInterval(interval: BarInterval) {
        val selection = mutableUiState.value.selection ?: return
        if (mutableUiState.value.interval == interval) {
            return
        }
        mutableUiState.update {
            it.copy(
                interval = interval,
                bars = emptyList(),
            )
        }
        cancelChartRefresh()
        publishRefreshState()
        observeSelection()
        refreshChart(selection, interval)
    }

    fun refreshAll() {
        val state = mutableUiState.value
        val selection = state.selection ?: return
        cancelFullRefresh()
        if (state.interval == BarInterval.ONE_DAY) {
            cancelChartRefresh()
        }
        val generation = ++fullRefreshGeneration
        isFullRefreshActive = true
        fullRefreshMessage = null
        publishRefreshState()
        fullRefreshJob = viewModelScope.launch {
            val now = clock.instant()
            val attempts = buildList<suspend () -> String?> {
                add {
                    refreshAttempt {
                        repository.refreshQuote(selection.symbol, selection.exchange)
                    }
                }
                add {
                    refreshAttempt {
                        repository.refreshValuation(selection.symbol)
                    }
                }
                add {
                    refreshAttempt {
                        repository.refreshBars(
                            symbol = selection.symbol,
                            exchange = selection.exchange,
                            interval = BarInterval.ONE_DAY,
                            start = now.minus(DAILY_HISTORY),
                            endExclusive = now,
                        )
                    }
                }
            }
            var message: String? = null
            try {
                message = supervisorScope {
                    attempts.map { attempt -> async { attempt() } }
                        .awaitAll()
                        .filterNotNull()
                        .distinct()
                        .takeIf(List<String>::isNotEmpty)
                        ?.joinToString(separator = "\n")
                }
            } finally {
                if (generation == fullRefreshGeneration) {
                    isFullRefreshActive = false
                    fullRefreshMessage = message
                    publishRefreshState()
                }
            }
        }
        if (state.interval != BarInterval.ONE_DAY) {
            refreshChart(selection, state.interval)
        }
    }

    private fun refreshChart(
        selection: StockSelection,
        interval: BarInterval,
    ) {
        cancelChartRefresh()
        val generation = ++chartRefreshGeneration
        isChartRefreshActive = true
        chartRefreshMessage = null
        publishRefreshState()
        chartRefreshJob = viewModelScope.launch {
            var message: String? = null
            try {
                message = refreshAttempt {
                    refreshBars(
                        selection = selection,
                        interval = interval,
                        now = clock.instant(),
                    )
                }
            } finally {
                if (generation == chartRefreshGeneration) {
                    isChartRefreshActive = false
                    chartRefreshMessage = message
                    publishRefreshState()
                }
            }
        }
    }

    private fun cancelFullRefresh() {
        fullRefreshGeneration++
        fullRefreshJob?.cancel()
        fullRefreshJob = null
        isFullRefreshActive = false
        fullRefreshMessage = null
    }

    private fun cancelChartRefresh() {
        chartRefreshGeneration++
        chartRefreshJob?.cancel()
        chartRefreshJob = null
        isChartRefreshActive = false
        chartRefreshMessage = null
    }

    private fun publishRefreshState() {
        val message = listOfNotNull(fullRefreshMessage, chartRefreshMessage)
            .distinct()
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString(separator = "\n")
        mutableUiState.update {
            it.copy(
                isRefreshing = isFullRefreshActive || isChartRefreshActive,
                message = message,
            )
        }
    }

    private suspend fun refreshBars(
        selection: StockSelection,
        interval: BarInterval,
        now: Instant,
    ): RefreshResult<List<PriceBar>> = repository.refreshBars(
        symbol = selection.symbol,
        exchange = selection.exchange,
        interval = interval,
        start = now.minus(interval.historyDuration()),
        endExclusive = now,
    )

    private suspend fun refreshAttempt(
        refresh: suspend () -> RefreshResult<*>,
    ): String? = try {
        when (val result = refresh()) {
            is RefreshResult.Fresh -> null
            is RefreshResult.Cached -> result.failureMessage
        }
    } catch (failure: IOException) {
        failure.message ?: "Market data refresh failed"
    }

    private fun observeSelection() {
        val state = mutableUiState.value
        val selection = state.selection ?: return
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            val quoteAndValuation = combine(
                repository.observeQuote(selection.symbol, selection.exchange),
                repository.observeValuation(selection.symbol),
            ) { quote, valuation -> quote to valuation }
            val analysis = combine(
                repository.observeTechnicalIndicators(selection.symbol, selection.exchange),
                repository.observePriceLevels(selection.symbol, selection.exchange),
            ) { technicals, priceLevels -> technicals to priceLevels }
            combine(
                quoteAndValuation,
                analysis,
                repository.observeBars(
                    symbol = selection.symbol,
                    exchange = selection.exchange,
                    interval = state.interval,
                    limit = state.interval.visibleBarLimit(),
                ),
            ) { primary, calculated, bars ->
                ObservedStockData(
                    quote = primary.first,
                    valuation = primary.second,
                    technicals = calculated.first,
                    priceLevels = calculated.second,
                    bars = bars,
                )
            }.collect { observed ->
                mutableUiState.update {
                    it.copy(
                        quote = observed.quote,
                        valuation = observed.valuation,
                        technicals = observed.technicals,
                        priceLevels = observed.priceLevels,
                        bars = observed.bars,
                    )
                }
            }
        }
    }

    private data class ObservedStockData(
        val quote: QuoteSnapshot?,
        val valuation: ValuationSnapshot?,
        val technicals: TechnicalIndicatorSnapshot?,
        val priceLevels: PriceLevelSnapshot?,
        val bars: List<PriceBar>,
    )

    companion object {
        private val DAILY_HISTORY: Duration = Duration.ofDays(400)
    }
}

private fun BarInterval.historyDuration(): Duration = when (this) {
    BarInterval.ONE_MINUTE -> Duration.ofDays(1)
    BarInterval.FIVE_MINUTES -> Duration.ofDays(5)
    BarInterval.FIFTEEN_MINUTES,
    BarInterval.THIRTY_MINUTES,
    -> Duration.ofDays(30)
    BarInterval.ONE_HOUR,
    BarInterval.FOUR_HOURS,
    -> Duration.ofDays(180)
    BarInterval.ONE_DAY -> Duration.ofDays(400)
    BarInterval.ONE_MONTH -> Duration.ofDays(3_650)
}

private fun BarInterval.visibleBarLimit(): Int = when (this) {
    BarInterval.ONE_MINUTE -> 390
    BarInterval.FIVE_MINUTES -> 390
    BarInterval.FIFTEEN_MINUTES -> 260
    BarInterval.THIRTY_MINUTES -> 260
    BarInterval.ONE_HOUR -> 260
    BarInterval.FOUR_HOURS -> 260
    BarInterval.ONE_DAY -> 300
    BarInterval.ONE_MONTH -> 120
}

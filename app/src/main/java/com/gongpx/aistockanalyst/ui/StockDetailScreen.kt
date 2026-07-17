package com.gongpx.aistockanalyst.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.gongpx.aistockanalyst.R
import com.gongpx.aistockanalyst.designsystem.theme.AppColors
import com.gongpx.aistockanalyst.designsystem.theme.AppSpacing
import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.PriceLevel
import com.gongpx.aistockanalyst.model.PriceLevelLabel
import java.text.NumberFormat
import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun WatchlistDestination(
    contentPadding: PaddingValues,
    state: StockDetailUiState,
    onOpenStock: (String, Exchange) -> Unit,
    onCloseStock: () -> Unit,
    onRefresh: () -> Unit,
    onIntervalSelected: (BarInterval) -> Unit,
) {
    if (state.selection == null) {
        StockEntryScreen(
            contentPadding = contentPadding,
            message = state.message,
            invalidSymbol = state.invalidSymbol,
            onOpenStock = onOpenStock,
        )
    } else {
        StockDetailScreen(
            contentPadding = contentPadding,
            state = state,
            onCloseStock = onCloseStock,
            onRefresh = onRefresh,
            onIntervalSelected = onIntervalSelected,
        )
    }
}

@Composable
private fun StockEntryScreen(
    contentPadding: PaddingValues,
    message: String?,
    invalidSymbol: Boolean,
    onOpenStock: (String, Exchange) -> Unit,
) {
    var symbol by rememberSaveable { mutableStateOf("") }
    var exchangeName by rememberSaveable { mutableStateOf(Exchange.NASDAQ.name) }
    val exchange = Exchange.valueOf(exchangeName)
    val submit = { onOpenStock(symbol, exchange) }

    ScreenContainer(contentPadding) {
        ScreenTitle(stringResource(R.string.watchlist_title))
        InformationCard {
            Text(
                text = stringResource(R.string.stock_lookup_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AppSpacing.small))
            Text(
                text = stringResource(R.string.stock_lookup_body),
                color = AppColors.onSurfaceMuted,
            )
            Spacer(Modifier.height(AppSpacing.medium))
            OutlinedTextField(
                value = symbol,
                onValueChange = { symbol = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.stock_symbol)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (symbol.isNotBlank()) submit() },
                ),
            )
            Spacer(Modifier.height(AppSpacing.small))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
            ) {
                items(Exchange.entries) { option ->
                    FilterChip(
                        selected = option == exchange,
                        onClick = { exchangeName = option.name },
                        label = { Text(option.displayName()) },
                    )
                }
            }
            val displayedMessage = if (invalidSymbol) {
                stringResource(R.string.invalid_stock_symbol)
            } else {
                message
            }
            displayedMessage?.let {
                Spacer(Modifier.height(AppSpacing.small))
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(AppSpacing.medium))
            Button(
                onClick = submit,
                enabled = symbol.isNotBlank(),
            ) {
                Text(stringResource(R.string.open_stock_detail))
            }
        }
        InformationCard {
            Text(
                text = stringResource(R.string.stock_data_requirements),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AppSpacing.small))
            Text(
                text = stringResource(R.string.stock_data_requirements_body),
                color = AppColors.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun StockDetailScreen(
    contentPadding: PaddingValues,
    state: StockDetailUiState,
    onCloseStock: () -> Unit,
    onRefresh: () -> Unit,
    onIntervalSelected: (BarInterval) -> Unit,
) {
    val selection = requireNotNull(state.selection)
    ScreenContainer(contentPadding) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ScreenTitle(selection.symbol.value)
                Text(
                    text = selection.exchange.displayName(),
                    color = AppColors.onSurfaceMuted,
                )
            }
            TextButton(onClick = onCloseStock) {
                Text(stringResource(R.string.change_stock))
            }
            Button(
                onClick = onRefresh,
                enabled = !state.isRefreshing,
            ) {
                Text(stringResource(R.string.refresh))
            }
        }
        if (state.isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.message?.let {
            InformationCard {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
        QuoteCard(state)
        ValuationBlock(state)
        PriceLevelBlock(state)
        TechnicalBlock(state)
        MarketChartBlock(
            state = state,
            onIntervalSelected = onIntervalSelected,
        )
    }
}

@Composable
private fun QuoteCard(state: StockDetailUiState) {
    InformationCard {
        Text(
            text = stringResource(R.string.latest_quote),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(AppSpacing.small))
        val quote = state.quote
        if (quote == null) {
            Text(stringResource(R.string.no_cached_quote))
        } else {
            val isStale = rememberIsStale(quote.staleAfter)
            Text(
                text = quote.currentPrice.asPrice(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            DataRow(
                stringResource(R.string.previous_close),
                quote.previousClose.asPrice(),
            )
            DataRow(
                stringResource(R.string.quote_source),
                quote.source.displayName(),
            )
            DataRow(
                stringResource(R.string.as_of),
                quote.asOf.asDeviceTime(),
            )
            if (isStale) {
                Text(
                    text = stringResource(R.string.stale_data),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun rememberIsStale(staleAfter: Instant): Boolean {
    var isStale by rememberSaveable(staleAfter.toEpochMilli()) {
        mutableStateOf(!Instant.now().isBefore(staleAfter))
    }
    LaunchedEffect(staleAfter) {
        if (!isStale) {
            val delayMillis = Duration.between(Instant.now(), staleAfter)
                .toMillis()
                .coerceAtLeast(0L)
            delay(delayMillis)
            isStale = true
        }
    }
    return isStale
}

@Composable
private fun ValuationBlock(state: StockDetailUiState) {
    val quote = state.quote
    val valuation = state.valuation
    val medianTarget = valuation?.targets?.median
    val upside = if (quote != null && medianTarget != null) {
        medianTarget / quote.currentPrice - 1.0
    } else {
        null
    }
    ExpandableInformationCard(
        title = stringResource(R.string.valuation_snapshot),
        summary = upside?.let {
            stringResource(R.string.median_target_distance, it.asPercent())
        } ?: stringResource(R.string.valuation_unavailable),
        initiallyExpanded = true,
    ) {
        if (valuation == null) {
            Text(stringResource(R.string.valuation_unavailable))
        } else {
            DataRow(stringResource(R.string.target_low), valuation.targets.low.asPrice())
            DataRow(stringResource(R.string.target_median), valuation.targets.median.asPrice())
            DataRow(stringResource(R.string.target_high), valuation.targets.high.asPrice())
            DataRow(stringResource(R.string.forward_pe), valuation.forwardPe.asNumber())
            DataRow(
                stringResource(R.string.analyst_count),
                valuation.analystCount?.toString() ?: MISSING_VALUE,
            )
            DataRow(
                stringResource(R.string.rating),
                valuation.recommendationKey.recommendationDisplayName(),
            )
            DataRow(stringResource(R.string.as_of), valuation.asOf.asDeviceTime())
        }
    }
}

@Composable
private fun PriceLevelBlock(state: StockDetailUiState) {
    val snapshot = state.priceLevels
    ExpandableInformationCard(
        title = stringResource(R.string.support_resistance),
        summary = if (snapshot == null) {
            stringResource(R.string.price_levels_unavailable)
        } else {
            stringResource(
                R.string.nearest_level_summary,
                snapshot.nearestSupport?.distanceFraction.asPercent(),
                snapshot.nearestResistance?.distanceFraction.asPercent(),
            )
        },
        initiallyExpanded = true,
    ) {
        if (snapshot == null) {
            Text(stringResource(R.string.price_levels_unavailable))
        } else {
            DataRow(
                stringResource(R.string.nearest_support),
                snapshot.nearestSupport.levelSummary(),
            )
            DataRow(
                stringResource(R.string.nearest_resistance),
                snapshot.nearestResistance.levelSummary(),
            )
            DataRow(
                stringResource(R.string.resonant_levels),
                snapshot.levels.count(PriceLevel::isResonant).toString(),
            )
            snapshot.levels
                .sortedBy(PriceLevel::distanceFraction)
                .take(6)
                .forEach { level ->
                    DataRow(
                        level.labels.map { it.displayName() }.joinToString(),
                        level.levelSummary(),
                    )
                }
        }
    }
}

@Composable
private fun TechnicalBlock(state: StockDetailUiState) {
    val technicals = state.technicals
    val positioning = state.priceLevels
    ExpandableInformationCard(
        title = stringResource(R.string.technicals),
        summary = technicals?.rsi14?.let {
            stringResource(R.string.rsi_summary, it.asNumber())
        } ?: stringResource(R.string.technicals_unavailable),
        initiallyExpanded = false,
    ) {
        if (technicals == null) {
            Text(stringResource(R.string.technicals_unavailable))
        } else {
            DataRow(stringResource(R.string.ma50), technicals.ma50.asPrice())
            DataRow(stringResource(R.string.ma200), technicals.ma200.asPrice())
            DataRow(stringResource(R.string.rsi14), technicals.rsi14.asNumber())
            DataRow(
                stringResource(R.string.fifty_two_week_position),
                positioning?.fiftyTwoWeekPosition.asPercent(),
            )
            DataRow(
                stringResource(R.string.completed_daily_bars),
                technicals.closeCount.toString(),
            )
            DataRow(stringResource(R.string.as_of), technicals.asOf.asDeviceTime())
        }
    }
}

@Composable
private fun MarketChartBlock(
    state: StockDetailUiState,
    onIntervalSelected: (BarInterval) -> Unit,
) {
    ExpandableInformationCard(
        title = stringResource(R.string.market_chart),
        summary = stringResource(
            R.string.completed_bar_count,
            state.bars.size,
            state.interval.shortLabel(),
        ),
        initiallyExpanded = true,
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
        ) {
            items(BarInterval.entries) { interval ->
                FilterChip(
                    selected = interval == state.interval,
                    onClick = { onIntervalSelected(interval) },
                    label = { Text(interval.shortLabel()) },
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.small))
        Text(
            text = stringResource(R.string.alpaca_iex_chart_disclosure),
            color = AppColors.onSurfaceMuted,
        )
        Spacer(Modifier.height(AppSpacing.medium))
        if (state.bars.isEmpty()) {
            Text(stringResource(R.string.no_completed_bars))
        } else {
            StockCandlestickChart(state.bars)
            Spacer(Modifier.height(AppSpacing.small))
            CompletedBarsSummary(state.bars)
        }
    }
}

@Composable
private fun CompletedBarsSummary(bars: List<PriceBar>) {
    val latest = bars.last()
    DataRow(stringResource(R.string.bar_time), latest.start.asDeviceTime())
    DataRow(stringResource(R.string.open), latest.open.asPrice())
    DataRow(stringResource(R.string.high), latest.high.asPrice())
    DataRow(stringResource(R.string.low), latest.low.asPrice())
    DataRow(stringResource(R.string.close), latest.close.asPrice())
    DataRow(stringResource(R.string.volume), INTEGER_FORMAT.format(latest.volume))
}

@Composable
private fun ExpandableInformationCard(
    title: String,
    summary: String,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    InformationCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(text = summary, color = AppColors.onSurfaceMuted)
            }
            TextButton(onClick = { expanded = !expanded }) {
                androidx.compose.material3.Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) {
                        stringResource(R.string.collapse)
                    } else {
                        stringResource(R.string.expand)
                    },
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(AppSpacing.medium))
            content()
        }
    }
}

@Composable
private fun DataRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = AppColors.onSurfaceMuted,
        )
        Text(text = value, fontWeight = FontWeight.Medium)
    }
    Spacer(Modifier.height(AppSpacing.extraSmall))
}

private fun Exchange.displayName(): String = when (this) {
    Exchange.NASDAQ -> "NASDAQ"
    Exchange.NYSE -> "NYSE"
    Exchange.NYSE_AMERICAN -> "NYSE American"
}

private fun BarInterval.shortLabel(): String = when (this) {
    BarInterval.ONE_MINUTE -> "1m"
    BarInterval.FIVE_MINUTES -> "5m"
    BarInterval.FIFTEEN_MINUTES -> "15m"
    BarInterval.THIRTY_MINUTES -> "30m"
    BarInterval.ONE_HOUR -> "1h"
    BarInterval.FOUR_HOURS -> "4h"
    BarInterval.ONE_DAY -> "1D"
    BarInterval.ONE_MONTH -> "1M"
}

@Composable
private fun DataSource.displayName(): String = when (this) {
    DataSource.TENCENT -> stringResource(R.string.source_tencent)
    DataSource.SINA -> stringResource(R.string.source_sina)
    DataSource.YAHOO_FINANCE -> stringResource(R.string.source_yahoo_finance)
    DataSource.FINNHUB -> stringResource(R.string.source_finnhub)
    DataSource.FMP -> stringResource(R.string.source_fmp)
    DataSource.ALPACA_IEX -> stringResource(R.string.source_alpaca_iex)
    DataSource.LOCAL_CALCULATION -> stringResource(R.string.source_local_calculation)
    DataSource.ONNX_RUNTIME -> stringResource(R.string.source_onnx_runtime)
}

@Composable
private fun PriceLevelLabel.displayName(): String = when (this) {
    PriceLevelLabel.PIVOT_POINT -> stringResource(R.string.level_pivot_point)
    PriceLevelLabel.PIVOT_R1 -> stringResource(R.string.level_pivot_r1)
    PriceLevelLabel.PIVOT_R2 -> stringResource(R.string.level_pivot_r2)
    PriceLevelLabel.PIVOT_R3 -> stringResource(R.string.level_pivot_r3)
    PriceLevelLabel.PIVOT_S1 -> stringResource(R.string.level_pivot_s1)
    PriceLevelLabel.PIVOT_S2 -> stringResource(R.string.level_pivot_s2)
    PriceLevelLabel.PIVOT_S3 -> stringResource(R.string.level_pivot_s3)
    PriceLevelLabel.SWING_HIGH -> stringResource(R.string.level_swing_high)
    PriceLevelLabel.SWING_LOW -> stringResource(R.string.level_swing_low)
    PriceLevelLabel.FIBONACCI_23_6 -> stringResource(R.string.level_fibonacci_23_6)
    PriceLevelLabel.FIBONACCI_38_2 -> stringResource(R.string.level_fibonacci_38_2)
    PriceLevelLabel.FIBONACCI_50_0 -> stringResource(R.string.level_fibonacci_50_0)
    PriceLevelLabel.FIBONACCI_61_8 -> stringResource(R.string.level_fibonacci_61_8)
    PriceLevelLabel.MA50 -> stringResource(R.string.level_ma50)
    PriceLevelLabel.MA200 -> stringResource(R.string.level_ma200)
    PriceLevelLabel.FIFTY_TWO_WEEK_HIGH -> stringResource(R.string.level_52_week_high)
    PriceLevelLabel.FIFTY_TWO_WEEK_LOW -> stringResource(R.string.level_52_week_low)
}

@Composable
private fun String?.recommendationDisplayName(): String = when (
    this?.lowercase(Locale.US)
) {
    "strong_buy", "strongbuy" -> stringResource(R.string.rating_strong_buy)
    "buy" -> stringResource(R.string.rating_buy)
    "hold" -> stringResource(R.string.rating_hold)
    "underperform" -> stringResource(R.string.rating_underperform)
    "sell" -> stringResource(R.string.rating_sell)
    null -> MISSING_VALUE
    else -> this
}

private fun PriceLevel?.levelSummary(): String = this?.let {
    "${value.asPrice()} (${distanceFraction.asPercent()})"
} ?: MISSING_VALUE

private fun Double?.asPrice(): String =
    this?.let(CURRENCY_FORMAT::format) ?: MISSING_VALUE

private fun Double?.asNumber(): String =
    this?.let { NUMBER_FORMAT.format(it) } ?: MISSING_VALUE

private fun Double?.asPercent(): String =
    this?.let { PERCENT_FORMAT.format(it) } ?: MISSING_VALUE

private fun Instant.asDeviceTime(): String =
    TIME_FORMAT.format(atZone(ZoneId.systemDefault()))

private const val MISSING_VALUE = "--"
private val CURRENCY_FORMAT: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)
private val NUMBER_FORMAT: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    maximumFractionDigits = 2
}
private val INTEGER_FORMAT: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)
private val PERCENT_FORMAT: NumberFormat = NumberFormat.getPercentInstance(Locale.US).apply {
    maximumFractionDigits = 1
}
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "yyyy-MM-dd HH:mm z",
)

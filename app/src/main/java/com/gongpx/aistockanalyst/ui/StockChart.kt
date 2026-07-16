package com.gongpx.aistockanalyst.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gongpx.aistockanalyst.R
import com.gongpx.aistockanalyst.model.PriceBar
import com.patrykandpatrick.vico.compose.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.candlestickModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.CandlestickCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.absolute
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberCandlestickCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CandlestickCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
internal fun StockCandlestickChart(
    bars: List<PriceBar>,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val coroutineScope = rememberCoroutineScope()
    val chartData = remember(bars) { stockChartData(bars) }
    LaunchedEffect(chartData) {
        modelProducer.runTransaction {
            candlestickModel(
                x = chartData.x,
                opening = chartData.opening,
                closing = chartData.closing,
                low = chartData.low,
                high = chartData.high,
            )
            columnSeries { series(x = chartData.x, y = chartData.volume) }
        }
    }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        val timestampFormatter = remember {
            DateTimeFormatter.ofPattern("MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
        }
        val bottomAxisFormatter = remember(chartData.starts, timestampFormatter) {
            CartesianValueFormatter { _, x, _ ->
                chartData.starts.getOrNull(x.toInt())
                    ?.let(timestampFormatter::format)
                    ?: "--"
            }
        }
        val priceFormatter = remember {
            CartesianValueFormatter.decimal(
                decimalCount = 2,
                thousandsSeparator = ",",
                prefix = "$",
            )
        }
        val wick = rememberLineComponent(
            fill = Fill(NEUTRAL_CANDLE_COLOR),
            thickness = 1.dp,
        )
        val hollowCandle = CandlestickCartesianLayer.Candle(
            body = rememberLineComponent(
                fill = Fill.Transparent,
                thickness = 8.dp,
                strokeFill = Fill(NEUTRAL_CANDLE_COLOR),
                strokeThickness = 1.dp,
            ),
            topWick = wick,
            bottomWick = wick,
        )
        val filledCandle = CandlestickCartesianLayer.Candle(
            body = rememberLineComponent(
                fill = Fill(NEUTRAL_CANDLE_COLOR),
                thickness = 8.dp,
            ),
            topWick = wick,
            bottomWick = wick,
        )
        val candleProvider = CandlestickCartesianLayer.CandleProvider.absolute(
            bullish = hollowCandle,
            neutral = hollowCandle,
            bearish = filledCandle,
        )
        val volumeProvider = ColumnCartesianLayer.ColumnProvider.series(
            rememberLineComponent(
                fill = Fill(VOLUME_COLOR),
                thickness = 6.dp,
            ),
        )
        val volumeFormatter = remember {
            CartesianValueFormatter { _, value, _ -> formatVolume(value) }
        }
        val volumeRangeProvider = remember(chartData.volume) {
            CartesianLayerRangeProvider.fixed(
                minY = 0.0,
                maxY = chartData.volume.maxOrNull()
                    ?.takeIf { it > 0L }
                    ?.times(VOLUME_SCALE_MULTIPLIER),
            )
        }
        val markerValueFormatter = remember {
            DefaultCartesianMarker.ValueFormatter { _, targets ->
                targets.joinToString(separator = "\n") { target ->
                    when (target) {
                        is CandlestickCartesianLayerMarkerTarget -> with(target.entry) {
                            "O ${formatChartPrice(opening)}  H ${formatChartPrice(high)}  " +
                                "L ${formatChartPrice(low)}  C ${formatChartPrice(closing)}"
                        }
                        is ColumnCartesianLayerMarkerTarget -> {
                            "V ${formatVolume(target.columns.sumOf { it.entry.y })}"
                        }
                        else -> ""
                    }
                }
            }
        }
        val marker = rememberDefaultCartesianMarker(
            label = rememberTextComponent(
                style = MaterialTheme.typography.labelSmall,
            ),
            valueFormatter = markerValueFormatter,
        )
        val initialZoom = remember { Zoom.x(INITIAL_VISIBLE_CANDLES) }
        val scrollState = rememberVicoScrollState(
            initialScroll = Scroll.Absolute.End,
            autoScroll = Scroll.Absolute.End,
            autoScrollCondition = AutoScrollCondition.OnModelGrowth,
        )
        val zoomState = rememberVicoZoomState(
            initialZoom = initialZoom,
            minZoom = Zoom.Content,
            maxZoom = Zoom.max(Zoom.fixed(8f), Zoom.Content),
        )
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(280.dp),
        ) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberCandlestickCartesianLayer(
                        candleProvider = candleProvider,
                        candleSpacing = 2.dp,
                        scaleCandleWicks = false,
                    ),
                    rememberColumnCartesianLayer(
                        columnProvider = volumeProvider,
                        columnCollectionSpacing = 2.dp,
                        mergeMode = { ColumnCartesianLayer.MergeMode.Grouped() },
                        dataLabel = null,
                        dataLabelPosition = Position.Vertical.Top,
                        rangeProvider = volumeRangeProvider,
                        verticalAxisPosition = Axis.Position.Vertical.End,
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        valueFormatter = priceFormatter,
                    ),
                    endAxis = VerticalAxis.rememberEnd(
                        valueFormatter = volumeFormatter,
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = bottomAxisFormatter,
                    ),
                    marker = marker,
                    getXStep = { _, _, _ -> 1.0 },
                ),
                modelProducer = modelProducer,
                modifier = Modifier.matchParentSize(),
                scrollState = scrollState,
                zoomState = zoomState,
            )
            TextButton(
                modifier = Modifier.align(Alignment.TopEnd),
                onClick = {
                    coroutineScope.launch {
                        zoomState.zoom(initialZoom)
                        scrollState.scroll(Scroll.Absolute.End)
                    }
                },
            ) {
                Text(stringResource(R.string.chart_reset))
            }
        }
    }
}

private const val INITIAL_VISIBLE_CANDLES = 48.0
private const val VOLUME_SCALE_MULTIPLIER = 4.0
private val NEUTRAL_CANDLE_COLOR = Color(0xFFCBD5E1)
private val VOLUME_COLOR = Color(0x66CBD5E1)

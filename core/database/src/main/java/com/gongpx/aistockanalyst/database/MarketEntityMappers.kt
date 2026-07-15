package com.gongpx.aistockanalyst.database

import com.gongpx.aistockanalyst.model.AnalystTargets
import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.DataSource
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.ParseStatus
import com.gongpx.aistockanalyst.model.PriceBar
import com.gongpx.aistockanalyst.model.QuoteSnapshot
import com.gongpx.aistockanalyst.model.StockSymbol
import com.gongpx.aistockanalyst.model.ValuationSnapshot
import java.time.Instant

fun QuoteSnapshot.toEntity(): QuoteEntity = QuoteEntity(
    symbol = symbol.value,
    exchange = exchange.name,
    currentPrice = currentPrice,
    previousClose = previousClose,
    open = open,
    dayHigh = dayHigh,
    dayLow = dayLow,
    change = change,
    changePercent = changePercent,
    volume = volume,
    ttmPe = ttmPe,
    marketCap = marketCap,
    fiftyTwoWeekLow = fiftyTwoWeekLow,
    fiftyTwoWeekHigh = fiftyTwoWeekHigh,
    asOfEpochMillis = asOf.toEpochMilli(),
    fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
    staleAfterEpochMillis = staleAfter.toEpochMilli(),
    parseStatus = parseStatus.name,
    source = source.name,
)

fun QuoteEntity.toModel(): QuoteSnapshot = QuoteSnapshot(
    symbol = StockSymbol.of(symbol),
    exchange = enumValueOf(exchange),
    currentPrice = currentPrice,
    previousClose = previousClose,
    open = open,
    dayHigh = dayHigh,
    dayLow = dayLow,
    change = change,
    changePercent = changePercent,
    volume = volume,
    ttmPe = ttmPe,
    marketCap = marketCap,
    fiftyTwoWeekLow = fiftyTwoWeekLow,
    fiftyTwoWeekHigh = fiftyTwoWeekHigh,
    asOf = Instant.ofEpochMilli(asOfEpochMillis),
    fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMillis),
    staleAfter = Instant.ofEpochMilli(staleAfterEpochMillis),
    parseStatus = enumValueOf(parseStatus),
    source = enumValueOf(source),
)

fun ValuationSnapshot.toEntity(): ValuationEntity = ValuationEntity(
    symbol = symbol.value,
    targetLow = targets.low,
    targetMedian = targets.median,
    targetHigh = targets.high,
    forwardPe = forwardPe,
    recommendationKey = recommendationKey,
    analystCount = analystCount,
    fiftyDayAverage = fiftyDayAverage,
    twoHundredDayAverage = twoHundredDayAverage,
    fiftyTwoWeekLow = fiftyTwoWeekLow,
    fiftyTwoWeekHigh = fiftyTwoWeekHigh,
    marketCap = marketCap,
    averageDailyVolume3Month = averageDailyVolume3Month,
    sector = sector,
    industry = industry,
    asOfEpochMillis = asOf.toEpochMilli(),
    fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
    staleAfterEpochMillis = staleAfter.toEpochMilli(),
    parseStatus = parseStatus.name,
    source = source.name,
)

fun ValuationEntity.toModel(): ValuationSnapshot = ValuationSnapshot(
    symbol = StockSymbol.of(symbol),
    targets = AnalystTargets(
        low = targetLow,
        median = targetMedian,
        high = targetHigh,
    ),
    forwardPe = forwardPe,
    recommendationKey = recommendationKey,
    analystCount = analystCount,
    fiftyDayAverage = fiftyDayAverage,
    twoHundredDayAverage = twoHundredDayAverage,
    fiftyTwoWeekLow = fiftyTwoWeekLow,
    fiftyTwoWeekHigh = fiftyTwoWeekHigh,
    marketCap = marketCap,
    averageDailyVolume3Month = averageDailyVolume3Month,
    sector = sector,
    industry = industry,
    asOf = Instant.ofEpochMilli(asOfEpochMillis),
    fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMillis),
    staleAfter = Instant.ofEpochMilli(staleAfterEpochMillis),
    parseStatus = ParseStatus.valueOf(parseStatus),
    source = DataSource.valueOf(source),
)

fun PriceBar.toEntity(): PriceBarEntity = PriceBarEntity(
    symbol = symbol.value,
    exchange = exchange.name,
    interval = interval.name,
    startEpochMillis = start.toEpochMilli(),
    endExclusiveEpochMillis = endExclusive.toEpochMilli(),
    open = open,
    high = high,
    low = low,
    close = close,
    volume = volume,
    fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
    parseStatus = parseStatus.name,
    source = source.name,
)

fun PriceBarEntity.toModel(): PriceBar = PriceBar(
    symbol = StockSymbol.of(symbol),
    exchange = Exchange.valueOf(exchange),
    interval = BarInterval.valueOf(interval),
    start = Instant.ofEpochMilli(startEpochMillis),
    endExclusive = Instant.ofEpochMilli(endExclusiveEpochMillis),
    open = open,
    high = high,
    low = low,
    close = close,
    volume = volume,
    fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMillis),
    parseStatus = ParseStatus.valueOf(parseStatus),
    source = DataSource.valueOf(source),
)

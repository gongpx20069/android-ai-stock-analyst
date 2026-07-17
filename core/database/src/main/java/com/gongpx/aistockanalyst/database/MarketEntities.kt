package com.gongpx.aistockanalyst.database

import androidx.room.Entity

@Entity(
    tableName = "quote_snapshots",
    primaryKeys = ["symbol", "exchange"],
)
data class QuoteEntity(
    val symbol: String,
    val exchange: String,
    val currentPrice: Double,
    val previousClose: Double?,
    val open: Double?,
    val dayHigh: Double?,
    val dayLow: Double?,
    val change: Double?,
    val changePercent: Double?,
    val volume: Long?,
    val ttmPe: Double?,
    val marketCap: Double?,
    val fiftyTwoWeekLow: Double?,
    val fiftyTwoWeekHigh: Double?,
    val asOfEpochMillis: Long,
    val fetchedAtEpochMillis: Long,
    val staleAfterEpochMillis: Long,
    val parseStatus: String,
    val source: String,
)

@Entity(
    tableName = "valuation_snapshots",
    primaryKeys = ["symbol", "source"],
)
data class ValuationEntity(
    val symbol: String,
    val targetLow: Double?,
    val targetMedian: Double?,
    val targetHigh: Double?,
    val forwardPe: Double?,
    val recommendationKey: String?,
    val analystCount: Int?,
    val fiftyDayAverage: Double?,
    val twoHundredDayAverage: Double?,
    val fiftyTwoWeekLow: Double?,
    val fiftyTwoWeekHigh: Double?,
    val marketCap: Long?,
    val averageDailyVolume3Month: Long?,
    val sector: String?,
    val industry: String?,
    val asOfEpochMillis: Long,
    val fetchedAtEpochMillis: Long,
    val staleAfterEpochMillis: Long,
    val parseStatus: String,
    val source: String,
)

@Entity(
    tableName = "price_bars",
    primaryKeys = ["symbol", "exchange", "interval", "startEpochMillis"],
)
data class PriceBarEntity(
    val symbol: String,
    val exchange: String,
    val interval: String,
    val startEpochMillis: Long,
    val endExclusiveEpochMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val fetchedAtEpochMillis: Long,
    val parseStatus: String,
    val source: String,
)

package com.gongpx.aistockanalyst.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao {
    @Query(
        """
        SELECT * FROM quote_snapshots
        WHERE symbol = :symbol AND exchange = :exchange
        LIMIT 1
        """,
    )
    fun observe(
        symbol: String,
        exchange: String,
    ): Flow<QuoteEntity?>

    @Query(
        """
        SELECT * FROM quote_snapshots
        WHERE symbol = :symbol AND exchange = :exchange
        LIMIT 1
        """,
    )
    suspend fun get(
        symbol: String,
        exchange: String,
    ): QuoteEntity?

    @Upsert
    suspend fun upsert(entity: QuoteEntity)
}

@Dao
interface ValuationDao {
    @Query(
        """
        SELECT * FROM valuation_snapshots
        WHERE symbol = :symbol AND source = :source
        LIMIT 1
        """,
    )
    fun observe(
        symbol: String,
        source: String,
    ): Flow<ValuationEntity?>

    @Query(
        """
        SELECT * FROM valuation_snapshots
        WHERE symbol = :symbol AND source = :source
        LIMIT 1
        """,
    )
    suspend fun get(
        symbol: String,
        source: String,
    ): ValuationEntity?

    @Upsert
    suspend fun upsert(entity: ValuationEntity)
}

@Dao
interface PriceBarDao {
    @Query(
        """
        SELECT * FROM price_bars
        WHERE symbol = :symbol
            AND exchange = :exchange
            AND interval = :interval
            AND source = :source
        ORDER BY startEpochMillis ASC
        """,
    )
    fun observeHistory(
        symbol: String,
        exchange: String,
        interval: String,
        source: String,
    ): Flow<List<PriceBarEntity>>

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM price_bars
            WHERE symbol = :symbol
                AND exchange = :exchange
                AND interval = :interval
                AND source = :source
            ORDER BY startEpochMillis DESC
            LIMIT :limit
        )
        ORDER BY startEpochMillis ASC
        """,
    )
    fun observeRecent(
        symbol: String,
        exchange: String,
        interval: String,
        source: String,
        limit: Int,
    ): Flow<List<PriceBarEntity>>

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM price_bars
            WHERE symbol = :symbol
                AND exchange = :exchange
                AND interval = :interval
                AND source = :source
            ORDER BY startEpochMillis DESC
            LIMIT :limit
        )
        ORDER BY startEpochMillis ASC
        """,
    )
    suspend fun getRecent(
        symbol: String,
        exchange: String,
        interval: String,
        source: String,
        limit: Int,
    ): List<PriceBarEntity>

    @Query(
        """
        SELECT * FROM price_bars
        WHERE symbol = :symbol
            AND exchange = :exchange
            AND interval = :interval
            AND source = :source
            AND startEpochMillis >= :startEpochMillis
            AND endExclusiveEpochMillis <= :endExclusiveEpochMillis
        ORDER BY startEpochMillis ASC
        """,
    )
    suspend fun getRange(
        symbol: String,
        exchange: String,
        interval: String,
        source: String,
        startEpochMillis: Long,
        endExclusiveEpochMillis: Long,
    ): List<PriceBarEntity>

    @Query(
        """
        DELETE FROM price_bars
        WHERE symbol = :symbol
            AND exchange = :exchange
            AND interval = :interval
            AND source = :source
            AND startEpochMillis >= :startEpochMillis
            AND endExclusiveEpochMillis <= :endExclusiveEpochMillis
        """,
    )
    suspend fun deleteRange(
        symbol: String,
        exchange: String,
        interval: String,
        source: String,
        startEpochMillis: Long,
        endExclusiveEpochMillis: Long,
    )

    @Upsert
    suspend fun upsertAll(entities: List<PriceBarEntity>)

    @Transaction
    suspend fun replaceRange(
        symbol: String,
        exchange: String,
        interval: String,
        source: String,
        startEpochMillis: Long,
        endExclusiveEpochMillis: Long,
        entities: List<PriceBarEntity>,
    ) {
        deleteRange(
            symbol = symbol,
            exchange = exchange,
            interval = interval,
            source = source,
            startEpochMillis = startEpochMillis,
            endExclusiveEpochMillis = endExclusiveEpochMillis,
        )
        upsertAll(entities)
    }

    @Transaction
    suspend fun replaceOneAndFiveMinuteRanges(
        symbol: String,
        exchange: String,
        source: String,
        oneMinuteInterval: String,
        fiveMinuteInterval: String,
        startEpochMillis: Long,
        endExclusiveEpochMillis: Long,
        oneMinuteEntities: List<PriceBarEntity>,
        fiveMinuteEntities: List<PriceBarEntity>,
    ) {
        deleteRange(
            symbol = symbol,
            exchange = exchange,
            interval = oneMinuteInterval,
            source = source,
            startEpochMillis = startEpochMillis,
            endExclusiveEpochMillis = endExclusiveEpochMillis,
        )
        deleteRange(
            symbol = symbol,
            exchange = exchange,
            interval = fiveMinuteInterval,
            source = source,
            startEpochMillis = startEpochMillis,
            endExclusiveEpochMillis = endExclusiveEpochMillis,
        )
        upsertAll(oneMinuteEntities + fiveMinuteEntities)
    }
}

package com.gongpx.aistockanalyst.database

import androidx.room.Dao
import androidx.room.Query
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
        WHERE symbol = :symbol
        LIMIT 1
        """,
    )
    fun observe(symbol: String): Flow<ValuationEntity?>

    @Query(
        """
        SELECT * FROM valuation_snapshots
        WHERE symbol = :symbol
        LIMIT 1
        """,
    )
    suspend fun get(symbol: String): ValuationEntity?

    @Upsert
    suspend fun upsert(entity: ValuationEntity)
}

@Dao
interface PriceBarDao {
    @Query(
        """
        SELECT * FROM (
            SELECT * FROM price_bars
            WHERE symbol = :symbol
                AND exchange = :exchange
                AND interval = :interval
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
        limit: Int,
    ): Flow<List<PriceBarEntity>>

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM price_bars
            WHERE symbol = :symbol
                AND exchange = :exchange
                AND interval = :interval
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
        limit: Int,
    ): List<PriceBarEntity>

    @Upsert
    suspend fun upsertAll(entities: List<PriceBarEntity>)
}

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

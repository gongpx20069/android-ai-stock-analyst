package com.gongpx.aistockanalyst.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        QuoteEntity::class,
        ValuationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MarketDatabase : RoomDatabase() {
    abstract fun quoteDao(): QuoteDao

    abstract fun valuationDao(): ValuationDao
}

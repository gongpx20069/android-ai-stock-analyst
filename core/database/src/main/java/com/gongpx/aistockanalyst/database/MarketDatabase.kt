package com.gongpx.aistockanalyst.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        QuoteEntity::class,
        ValuationEntity::class,
        PriceBarEntity::class,
    ],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
    exportSchema = true,
)
abstract class MarketDatabase : RoomDatabase() {
    abstract fun quoteDao(): QuoteDao

    abstract fun valuationDao(): ValuationDao

    abstract fun priceBarDao(): PriceBarDao
}

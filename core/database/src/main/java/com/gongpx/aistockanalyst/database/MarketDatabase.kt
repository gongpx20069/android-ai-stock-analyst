package com.gongpx.aistockanalyst.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        QuoteEntity::class,
        ValuationEntity::class,
        PriceBarEntity::class,
    ],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
    exportSchema = true,
)
abstract class MarketDatabase : RoomDatabase() {
    abstract fun quoteDao(): QuoteDao

    abstract fun valuationDao(): ValuationDao

    abstract fun priceBarDao(): PriceBarDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS valuation_snapshots_new (
                        symbol TEXT NOT NULL,
                        targetLow REAL,
                        targetMedian REAL,
                        targetHigh REAL,
                        forwardPe REAL,
                        recommendationKey TEXT,
                        analystCount INTEGER,
                        fiftyDayAverage REAL,
                        twoHundredDayAverage REAL,
                        fiftyTwoWeekLow REAL,
                        fiftyTwoWeekHigh REAL,
                        marketCap INTEGER,
                        averageDailyVolume3Month INTEGER,
                        sector TEXT,
                        industry TEXT,
                        asOfEpochMillis INTEGER NOT NULL,
                        fetchedAtEpochMillis INTEGER NOT NULL,
                        staleAfterEpochMillis INTEGER NOT NULL,
                        parseStatus TEXT NOT NULL,
                        source TEXT NOT NULL,
                        PRIMARY KEY(symbol, source)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO valuation_snapshots_new
                    SELECT * FROM valuation_snapshots
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE valuation_snapshots")
                db.execSQL(
                    "ALTER TABLE valuation_snapshots_new RENAME TO valuation_snapshots",
                )
            }
        }
    }
}

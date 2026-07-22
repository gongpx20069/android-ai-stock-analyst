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
    version = 4,
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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS price_bars_new (
                        symbol TEXT NOT NULL,
                        exchange TEXT NOT NULL,
                        interval TEXT NOT NULL,
                        startEpochMillis INTEGER NOT NULL,
                        endExclusiveEpochMillis INTEGER NOT NULL,
                        open REAL NOT NULL,
                        high REAL NOT NULL,
                        low REAL NOT NULL,
                        close REAL NOT NULL,
                        volume INTEGER NOT NULL,
                        fetchedAtEpochMillis INTEGER NOT NULL,
                        parseStatus TEXT NOT NULL,
                        source TEXT NOT NULL,
                        PRIMARY KEY(symbol, exchange, interval, startEpochMillis, source)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO price_bars_new
                    SELECT * FROM price_bars
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE price_bars")
                db.execSQL("ALTER TABLE price_bars_new RENAME TO price_bars")
            }
        }
    }
}

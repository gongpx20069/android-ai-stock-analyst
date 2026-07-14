package com.gongpx.aistockanalyst.di

import android.content.Context
import androidx.room.Room
import com.gongpx.aistockanalyst.data.MarketRepository
import com.gongpx.aistockanalyst.data.RoomMarketRepository
import com.gongpx.aistockanalyst.database.MarketDatabase
import com.gongpx.aistockanalyst.database.QuoteDao
import com.gongpx.aistockanalyst.database.ValuationDao
import com.gongpx.aistockanalyst.network.FallbackQuoteClient
import com.gongpx.aistockanalyst.network.InMemoryCookieJar
import com.gongpx.aistockanalyst.network.MarketNetworkFactory
import com.gongpx.aistockanalyst.network.QuoteClient
import com.gongpx.aistockanalyst.network.RawHttpService
import com.gongpx.aistockanalyst.network.SinaQuoteClient
import com.gongpx.aistockanalyst.network.TencentQuoteClient
import com.gongpx.aistockanalyst.network.ValuationClient
import com.gongpx.aistockanalyst.network.YahooFinanceClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object MarketDataModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(InMemoryCookieJar())
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideRawHttpService(client: OkHttpClient): RawHttpService =
        MarketNetworkFactory.createRawHttpService(client)

    @Provides
    @Singleton
    fun provideQuoteClient(
        service: RawHttpService,
        clock: Clock,
    ): QuoteClient = FallbackQuoteClient(
        primary = TencentQuoteClient(service, clock),
        fallback = SinaQuoteClient(service, clock),
    )

    @Provides
    @Singleton
    fun provideValuationClient(
        service: RawHttpService,
        json: Json,
        clock: Clock,
    ): ValuationClient = YahooFinanceClient(service, json, clock)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MarketDatabase = Room.databaseBuilder(
        context,
        MarketDatabase::class.java,
        "market.db",
    ).build()

    @Provides
    fun provideQuoteDao(database: MarketDatabase): QuoteDao = database.quoteDao()

    @Provides
    fun provideValuationDao(database: MarketDatabase): ValuationDao =
        database.valuationDao()

    @Provides
    @Singleton
    fun provideMarketRepository(
        quoteClient: QuoteClient,
        valuationClient: ValuationClient,
        quoteDao: QuoteDao,
        valuationDao: ValuationDao,
        clock: Clock,
    ): MarketRepository = RoomMarketRepository(
        quoteClient = quoteClient,
        valuationClient = valuationClient,
        quoteDao = quoteDao,
        valuationDao = valuationDao,
        clock = clock,
    )
}

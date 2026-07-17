package com.gongpx.aistockanalyst.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.gongpx.aistockanalyst.data.MarketRepository
import com.gongpx.aistockanalyst.data.RoomMarketRepository
import com.gongpx.aistockanalyst.data.UserSelectedChartClient
import com.gongpx.aistockanalyst.data.UserSelectedQuoteClient
import com.gongpx.aistockanalyst.database.MarketDatabase
import com.gongpx.aistockanalyst.database.PriceBarDao
import com.gongpx.aistockanalyst.database.QuoteDao
import com.gongpx.aistockanalyst.database.ValuationDao
import com.gongpx.aistockanalyst.datastore.DataStoreMarketDataSourceSettings
import com.gongpx.aistockanalyst.datastore.AppLanguageSettingsStore
import com.gongpx.aistockanalyst.datastore.DataStoreAppLanguageSettings
import com.gongpx.aistockanalyst.datastore.EncryptedMarketDataCredentialsStore
import com.gongpx.aistockanalyst.datastore.MarketDataCredentialsStore
import com.gongpx.aistockanalyst.datastore.MarketDataSourceSettingsStore
import com.gongpx.aistockanalyst.network.AlpacaApiCredentials
import com.gongpx.aistockanalyst.network.AlpacaChartClient
import com.gongpx.aistockanalyst.network.ChartClient
import com.gongpx.aistockanalyst.network.InMemoryCookieJar
import com.gongpx.aistockanalyst.network.MarketNetworkFactory
import com.gongpx.aistockanalyst.network.QuoteClient
import com.gongpx.aistockanalyst.network.RawHttpService
import com.gongpx.aistockanalyst.network.SinaQuoteClient
import com.gongpx.aistockanalyst.network.TencentQuoteClient
import com.gongpx.aistockanalyst.network.ValuationClient
import com.gongpx.aistockanalyst.network.YahooFinanceClient
import com.gongpx.aistockanalyst.update.AppUpdateChecker
import com.gongpx.aistockanalyst.update.GitHubAppUpdateChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import javax.inject.Qualifier
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

private val Context.marketDataSourceSettings by preferencesDataStore(
    name = "market_data_sources",
)

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class TencentQuotes

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class SinaQuotes

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class AlpacaCharts

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
    fun provideAppUpdateChecker(
        client: OkHttpClient,
        json: Json,
    ): AppUpdateChecker = GitHubAppUpdateChecker(client, json)

    @Provides
    @Singleton
    fun provideRawHttpService(client: OkHttpClient): RawHttpService =
        MarketNetworkFactory.createRawHttpService(client)

    @Provides
    @Singleton
    @TencentQuotes
    fun provideTencentQuoteClient(
        service: RawHttpService,
        clock: Clock,
    ): QuoteClient = TencentQuoteClient(service, clock)

    @Provides
    @Singleton
    @SinaQuotes
    fun provideSinaQuoteClient(
        service: RawHttpService,
        clock: Clock,
    ): QuoteClient = SinaQuoteClient(service, clock)

    @Provides
    @Singleton
    fun provideQuoteClient(
        settingsStore: MarketDataSourceSettingsStore,
        @TencentQuotes tencentClient: QuoteClient,
        @SinaQuotes sinaClient: QuoteClient,
    ): QuoteClient = UserSelectedQuoteClient(
        settingsStore = settingsStore,
        tencentClient = tencentClient,
        sinaClient = sinaClient,
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
    @AlpacaCharts
    fun provideAlpacaChartClient(
        service: RawHttpService,
        json: Json,
        clock: Clock,
        credentialsStore: MarketDataCredentialsStore,
    ): ChartClient = AlpacaChartClient(
        service = service,
        json = json,
        clock = clock,
        credentialsProvider = {
            credentialsStore.getAlpacaCredentials()?.let { credentials ->
                AlpacaApiCredentials(
                    keyId = credentials.keyId,
                    secretKey = credentials.secretKey,
                )
            }
        },
    )

    @Provides
    @Singleton
    fun provideChartClient(
        settingsStore: MarketDataSourceSettingsStore,
        @AlpacaCharts alpacaClient: ChartClient,
    ): ChartClient = UserSelectedChartClient(
        settingsStore = settingsStore,
        alpacaClient = alpacaClient,
    )

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
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.marketDataSourceSettings

    @Provides
    @Singleton
    fun provideDataSourceSettingsStore(
        dataStore: DataStore<Preferences>,
    ): MarketDataSourceSettingsStore = DataStoreMarketDataSourceSettings(dataStore)

    @Provides
    @Singleton
    fun provideAppLanguageSettingsStore(
        dataStore: DataStore<Preferences>,
    ): AppLanguageSettingsStore = DataStoreAppLanguageSettings(dataStore)

    @Provides
    @Singleton
    fun provideMarketDataCredentialsStore(
        @ApplicationContext context: Context,
    ): MarketDataCredentialsStore = EncryptedMarketDataCredentialsStore(context)

    @Provides
    fun provideQuoteDao(database: MarketDatabase): QuoteDao = database.quoteDao()

    @Provides
    fun provideValuationDao(database: MarketDatabase): ValuationDao =
        database.valuationDao()

    @Provides
    fun providePriceBarDao(database: MarketDatabase): PriceBarDao =
        database.priceBarDao()

    @Provides
    @Singleton
    fun provideMarketRepository(
        quoteClient: QuoteClient,
        chartClient: ChartClient,
        valuationClient: ValuationClient,
        settingsStore: MarketDataSourceSettingsStore,
        quoteDao: QuoteDao,
        valuationDao: ValuationDao,
        priceBarDao: PriceBarDao,
        clock: Clock,
    ): MarketRepository = RoomMarketRepository(
        quoteClient = quoteClient,
        chartClient = chartClient,
        valuationClient = valuationClient,
        settingsStore = settingsStore,
        quoteDao = quoteDao,
        valuationDao = valuationDao,
        priceBarDao = priceBarDao,
        clock = clock,
    )
}

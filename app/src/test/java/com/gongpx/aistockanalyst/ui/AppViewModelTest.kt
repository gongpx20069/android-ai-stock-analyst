package com.gongpx.aistockanalyst.ui

import com.gongpx.aistockanalyst.datastore.AlpacaCredentials
import com.gongpx.aistockanalyst.datastore.AppLanguageSettingsStore
import com.gongpx.aistockanalyst.datastore.MarketDataCredentialsStore
import com.gongpx.aistockanalyst.datastore.FinnhubApiKey
import com.gongpx.aistockanalyst.datastore.FmpApiKey
import com.gongpx.aistockanalyst.datastore.MarketDataSourceSettingsStore
import com.gongpx.aistockanalyst.model.AppLanguage
import com.gongpx.aistockanalyst.model.ChartProvider
import com.gongpx.aistockanalyst.model.MarketDataSourceSettings
import com.gongpx.aistockanalyst.model.QuoteProvider
import com.gongpx.aistockanalyst.model.ValuationProvider
import com.gongpx.aistockanalyst.update.AppUpdate
import com.gongpx.aistockanalyst.update.AppUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `launch check exposes a newer GitHub release`() = runTest(dispatcher) {
        val update = AppUpdate(
            versionName = "1.0.1",
            downloadUrl = "https://github.com/example/app.apk",
            releaseUrl = "https://github.com/example/release",
        )
        val viewModel = viewModel(FakeUpdateChecker(ArrayDeque(listOf(update))))

        advanceUntilIdle()

        assertEquals(UpdateStatus.AVAILABLE, viewModel.uiState.value.updateStatus)
        assertEquals(update, viewModel.uiState.value.availableUpdate)
    }

    @Test
    fun `manual check can confirm the installed version is current`() = runTest(dispatcher) {
        val update = AppUpdate(
            versionName = "1.0.1",
            downloadUrl = "https://github.com/example/app.apk",
            releaseUrl = "https://github.com/example/release",
        )
        val viewModel = viewModel(
            FakeUpdateChecker(ArrayDeque(listOf(update, null))),
        )
        advanceUntilIdle()

        viewModel.checkForUpdates()
        advanceUntilIdle()

        assertEquals(UpdateStatus.UP_TO_DATE, viewModel.uiState.value.updateStatus)
        assertEquals(null, viewModel.uiState.value.availableUpdate)
    }

    private fun viewModel(checker: AppUpdateChecker) = AppViewModel(
        settingsStore = FakeDataSourceSettingsStore(),
        languageSettingsStore = FakeLanguageSettingsStore(),
        credentialsStore = FakeCredentialsStore(),
        appUpdateChecker = checker,
    )
}

private class FakeUpdateChecker(
    private val results: ArrayDeque<AppUpdate?>,
) : AppUpdateChecker {
    override suspend fun check(currentVersion: String): AppUpdate? = results.removeFirst()
}

private class FakeLanguageSettingsStore : AppLanguageSettingsStore {
    private val state = MutableStateFlow(AppLanguage.SYSTEM)
    override val language: Flow<AppLanguage> = state

    override suspend fun setLanguage(language: AppLanguage) {
        state.value = language
    }
}

private class FakeDataSourceSettingsStore : MarketDataSourceSettingsStore {
    private val state = MutableStateFlow(MarketDataSourceSettings())
    override val settings: Flow<MarketDataSourceSettings> = state

    override suspend fun setQuoteProvider(provider: QuoteProvider) {
        state.value = state.value.copy(quoteProvider = provider)
    }

    override suspend fun setChartProvider(provider: ChartProvider) {
        state.value = state.value.copy(chartProvider = provider)
    }

    override suspend fun setValuationProvider(provider: ValuationProvider) {
        state.value = state.value.copy(valuationProvider = provider)
    }

    override suspend fun resetToDefaults() {
        state.value = MarketDataSourceSettings()
    }
}

private class FakeCredentialsStore : MarketDataCredentialsStore {
    override suspend fun getAlpacaCredentials(): AlpacaCredentials? = null

    override suspend fun setAlpacaCredentials(credentials: AlpacaCredentials) = Unit

    override suspend fun clearAlpacaCredentials() = Unit

    override suspend fun getFinnhubApiKey(): FinnhubApiKey? = null

    override suspend fun setFinnhubApiKey(apiKey: FinnhubApiKey) = Unit

    override suspend fun clearFinnhubApiKey() = Unit

    override suspend fun getFmpApiKey(): FmpApiKey? = null

    override suspend fun setFmpApiKey(apiKey: FmpApiKey) = Unit

    override suspend fun clearFmpApiKey() = Unit
}

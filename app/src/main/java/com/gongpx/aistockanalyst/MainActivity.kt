package com.gongpx.aistockanalyst

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gongpx.aistockanalyst.designsystem.theme.AiStockAnalystTheme
import com.gongpx.aistockanalyst.ui.AnalystApp
import com.gongpx.aistockanalyst.ui.AppViewModel
import com.gongpx.aistockanalyst.ui.StockDetailViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val stockDetailViewModel: StockDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
            val stockDetailState = stockDetailViewModel.uiState
                .collectAsStateWithLifecycle()
                .value
            LaunchedEffect(uiState.appLanguage) {
                val locales = LocaleListCompat.forLanguageTags(
                    uiState.appLanguage.languageTag,
                )
                if (AppCompatDelegate.getApplicationLocales() != locales) {
                    AppCompatDelegate.setApplicationLocales(locales)
                }
            }
            AiStockAnalystTheme {
                AnalystApp(
                    settingsState = uiState,
                    stockDetailState = stockDetailState,
                    onOpenStock = stockDetailViewModel::openStock,
                    onCloseStock = stockDetailViewModel::closeStock,
                    onRefreshStock = stockDetailViewModel::refreshAll,
                    onChartIntervalSelected = stockDetailViewModel::selectInterval,
                    onAppLanguageSelected = viewModel::setAppLanguage,
                    onCheckForUpdates = viewModel::checkForUpdates,
                    onQuoteProviderSelected = viewModel::setQuoteProvider,
                    onChartProviderSelected = viewModel::setChartProvider,
                    onValuationProviderSelected = viewModel::setValuationProvider,
                    onResetDataSourceSettings = viewModel::resetDataSourceSettings,
                    onSaveAlpacaCredentials = viewModel::saveAlpacaCredentials,
                    onClearAlpacaCredentials = viewModel::clearAlpacaCredentials,
                    onSaveFinnhubApiKey = viewModel::saveFinnhubApiKey,
                    onClearFinnhubApiKey = viewModel::clearFinnhubApiKey,
                    onSaveFmpApiKey = viewModel::saveFmpApiKey,
                    onClearFmpApiKey = viewModel::clearFmpApiKey,
                )
            }
        }
    }
}

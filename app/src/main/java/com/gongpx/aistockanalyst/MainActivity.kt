package com.gongpx.aistockanalyst

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gongpx.aistockanalyst.designsystem.theme.AiStockAnalystTheme
import com.gongpx.aistockanalyst.ui.AnalystApp
import com.gongpx.aistockanalyst.ui.AppViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
            AiStockAnalystTheme {
                AnalystApp(
                    settingsState = uiState,
                    onQuoteProviderSelected = viewModel::setQuoteProvider,
                    onChartProviderSelected = viewModel::setChartProvider,
                    onValuationProviderSelected = viewModel::setValuationProvider,
                    onResetDataSourceSettings = viewModel::resetDataSourceSettings,
                    onSaveAlpacaCredentials = viewModel::saveAlpacaCredentials,
                    onClearAlpacaCredentials = viewModel::clearAlpacaCredentials,
                )
            }
        }
    }
}

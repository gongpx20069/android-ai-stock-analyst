package com.gongpx.aistockanalyst.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gongpx.aistockanalyst.datastore.MarketDataSourceSettingsStore
import com.gongpx.aistockanalyst.datastore.SettingsStorageException
import com.gongpx.aistockanalyst.model.ChartProvider
import com.gongpx.aistockanalyst.model.MarketDataSourceSettings
import com.gongpx.aistockanalyst.model.QuoteProvider
import com.gongpx.aistockanalyst.model.ValuationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class SettingsUiState(
    val dataSources: MarketDataSourceSettings = MarketDataSourceSettings(),
    val storageError: String? = null,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsStore: MarketDataSourceSettingsStore,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()
    private var settingsObservationJob: Job? = null

    init {
        observeSettings()
    }

    private fun observeSettings() {
        settingsObservationJob?.cancel()
        settingsObservationJob = viewModelScope.launch {
            try {
                settingsStore.settings.collect { settings ->
                    mutableUiState.value = SettingsUiState(dataSources = settings)
                }
            } catch (failure: SettingsStorageException) {
                mutableUiState.value = mutableUiState.value.copy(
                    storageError = failure.message,
                )
            }
        }
    }

    fun setQuoteProvider(provider: QuoteProvider) {
        updateSettings { settingsStore.setQuoteProvider(provider) }
    }

    fun setChartProvider(provider: ChartProvider) {
        updateSettings { settingsStore.setChartProvider(provider) }
    }

    fun setValuationProvider(provider: ValuationProvider) {
        updateSettings { settingsStore.setValuationProvider(provider) }
    }

    fun resetDataSourceSettings() {
        updateSettings(settingsStore::resetToDefaults)
    }

    private fun updateSettings(update: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                update()
                observeSettings()
            } catch (failure: SettingsStorageException) {
                mutableUiState.value = mutableUiState.value.copy(
                    storageError = failure.message,
                )
            }
        }
    }
}

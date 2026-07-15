package com.gongpx.aistockanalyst.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gongpx.aistockanalyst.datastore.AlpacaCredentials
import com.gongpx.aistockanalyst.datastore.CredentialsStorageException
import com.gongpx.aistockanalyst.datastore.MarketDataCredentialsStore
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
    val hasAlpacaCredentials: Boolean = false,
    val storageError: String? = null,
    val credentialsError: String? = null,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsStore: MarketDataSourceSettingsStore,
    private val credentialsStore: MarketDataCredentialsStore,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()
    private var settingsObservationJob: Job? = null

    init {
        observeSettings()
        refreshCredentialsStatus()
    }

    private fun observeSettings() {
        settingsObservationJob?.cancel()
        settingsObservationJob = viewModelScope.launch {
            try {
                settingsStore.settings.collect { settings ->
                    mutableUiState.value = mutableUiState.value.copy(
                        dataSources = settings,
                        storageError = null,
                    )
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

    fun saveAlpacaCredentials(
        keyId: String,
        secretKey: String,
    ) {
        val normalizedKeyId = keyId.trim()
        val normalizedSecret = secretKey.trim()
        if (normalizedKeyId.isEmpty() || normalizedSecret.isEmpty()) {
            mutableUiState.value = mutableUiState.value.copy(
                credentialsError = "Alpaca key ID and secret key are required",
            )
            return
        }
        viewModelScope.launch {
            try {
                credentialsStore.setAlpacaCredentials(
                    AlpacaCredentials(
                        keyId = normalizedKeyId,
                        secretKey = normalizedSecret,
                    ),
                )
                mutableUiState.value = mutableUiState.value.copy(
                    hasAlpacaCredentials = true,
                    credentialsError = null,
                )
            } catch (failure: CredentialsStorageException) {
                mutableUiState.value = mutableUiState.value.copy(
                    credentialsError = failure.message,
                )
            }
        }
    }

    fun clearAlpacaCredentials() {
        viewModelScope.launch {
            try {
                credentialsStore.clearAlpacaCredentials()
                mutableUiState.value = mutableUiState.value.copy(
                    hasAlpacaCredentials = false,
                    credentialsError = null,
                )
            } catch (failure: CredentialsStorageException) {
                mutableUiState.value = mutableUiState.value.copy(
                    credentialsError = failure.message,
                )
            }
        }
    }

    private fun refreshCredentialsStatus() {
        viewModelScope.launch {
            try {
                val hasCredentials = credentialsStore.getAlpacaCredentials() != null
                mutableUiState.value = mutableUiState.value.copy(
                    hasAlpacaCredentials = hasCredentials,
                    credentialsError = null,
                )
            } catch (failure: CredentialsStorageException) {
                mutableUiState.value = mutableUiState.value.copy(
                    credentialsError = failure.message,
                )
            }
        }
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

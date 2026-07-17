package com.gongpx.aistockanalyst.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gongpx.aistockanalyst.BuildConfig
import com.gongpx.aistockanalyst.datastore.AlpacaCredentials
import com.gongpx.aistockanalyst.datastore.AppLanguageSettingsStore
import com.gongpx.aistockanalyst.datastore.CredentialsStorageException
import com.gongpx.aistockanalyst.datastore.MarketDataCredentialsStore
import com.gongpx.aistockanalyst.datastore.FinnhubApiKey
import com.gongpx.aistockanalyst.datastore.FmpApiKey
import com.gongpx.aistockanalyst.datastore.MarketDataSourceSettingsStore
import com.gongpx.aistockanalyst.datastore.SettingsStorageException
import com.gongpx.aistockanalyst.model.ChartProvider
import com.gongpx.aistockanalyst.model.AppLanguage
import com.gongpx.aistockanalyst.model.MarketDataSourceSettings
import com.gongpx.aistockanalyst.model.QuoteProvider
import com.gongpx.aistockanalyst.model.ValuationProvider
import com.gongpx.aistockanalyst.update.AppUpdate
import com.gongpx.aistockanalyst.update.AppUpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class SettingsUiState(
    val dataSources: MarketDataSourceSettings = MarketDataSourceSettings(),
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val hasAlpacaCredentials: Boolean = false,
    val hasFinnhubApiKey: Boolean = false,
    val hasFmpApiKey: Boolean = false,
    val storageError: String? = null,
    val credentialsError: String? = null,
    val credentialsInputMissing: Boolean = false,
    val finnhubApiKeyInputMissing: Boolean = false,
    val fmpApiKeyInputMissing: Boolean = false,
    val updateStatus: UpdateStatus = UpdateStatus.IDLE,
    val availableUpdate: AppUpdate? = null,
)

enum class UpdateStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    FAILED,
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsStore: MarketDataSourceSettingsStore,
    private val languageSettingsStore: AppLanguageSettingsStore,
    private val credentialsStore: MarketDataCredentialsStore,
    private val appUpdateChecker: AppUpdateChecker,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()
    private var settingsObservationJob: Job? = null
    private var languageObservationJob: Job? = null

    init {
        observeSettings()
        observeLanguage()
        refreshCredentialsStatus()
        checkForUpdates()
    }

    private fun observeLanguage() {
        languageObservationJob?.cancel()
        languageObservationJob = viewModelScope.launch {
            try {
                languageSettingsStore.language.collect { language ->
                    mutableUiState.value = mutableUiState.value.copy(
                        appLanguage = language,
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

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            try {
                languageSettingsStore.setLanguage(language)
            } catch (failure: SettingsStorageException) {
                mutableUiState.value = mutableUiState.value.copy(
                    storageError = failure.message,
                )
            }
        }
    }

    fun checkForUpdates() {
        if (mutableUiState.value.updateStatus == UpdateStatus.CHECKING) {
            return
        }
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                updateStatus = UpdateStatus.CHECKING,
            )
            try {
                val update = appUpdateChecker.check(BuildConfig.VERSION_NAME)
                mutableUiState.value = mutableUiState.value.copy(
                    updateStatus = if (update == null) {
                        UpdateStatus.UP_TO_DATE
                    } else {
                        UpdateStatus.AVAILABLE
                    },
                    availableUpdate = update,
                )
            } catch (_: IOException) {
                mutableUiState.value = mutableUiState.value.copy(
                    updateStatus = UpdateStatus.FAILED,
                    availableUpdate = null,
                )
            }
        }
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
                credentialsError = null,
                credentialsInputMissing = true,
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
                    credentialsInputMissing = false,
                )
            } catch (failure: CredentialsStorageException) {
                mutableUiState.value = mutableUiState.value.copy(
                    credentialsError = failure.message,
                    credentialsInputMissing = false,
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
                    credentialsInputMissing = false,
                )
            } catch (failure: CredentialsStorageException) {
                mutableUiState.value = mutableUiState.value.copy(
                    credentialsError = failure.message,
                    credentialsInputMissing = false,
                )
            }
        }
    }

    fun saveFinnhubApiKey(apiKey: String) {
        val normalized = apiKey.trim()
        if (normalized.isEmpty()) {
            mutableUiState.value = mutableUiState.value.copy(
                credentialsError = null,
                finnhubApiKeyInputMissing = true,
            )
            return
        }
        viewModelScope.launch {
            try {
                credentialsStore.setFinnhubApiKey(FinnhubApiKey(normalized))
                mutableUiState.value = mutableUiState.value.copy(
                    hasFinnhubApiKey = true,
                    credentialsError = null,
                    finnhubApiKeyInputMissing = false,
                )
            } catch (failure: CredentialsStorageException) {
                mutableUiState.value = mutableUiState.value.copy(
                    credentialsError = failure.message,
                    finnhubApiKeyInputMissing = false,
                )
            }
        }
    }

    fun clearFinnhubApiKey() {
        viewModelScope.launch {
            try {
                credentialsStore.clearFinnhubApiKey()
                mutableUiState.value = mutableUiState.value.copy(
                    hasFinnhubApiKey = false,
                    credentialsError = null,
                    finnhubApiKeyInputMissing = false,
                )
            } catch (failure: CredentialsStorageException) {
                mutableUiState.value = mutableUiState.value.copy(
                    credentialsError = failure.message,
                    finnhubApiKeyInputMissing = false,
                )
            }
        }
    }

    fun saveFmpApiKey(apiKey: String) {
        val normalized = apiKey.trim()
        if (normalized.isEmpty()) {
            mutableUiState.value = mutableUiState.value.copy(
                credentialsError = null,
                fmpApiKeyInputMissing = true,
            )
            return
        }
        viewModelScope.launch {
            try {
                credentialsStore.setFmpApiKey(FmpApiKey(normalized))
                mutableUiState.value = mutableUiState.value.copy(
                    hasFmpApiKey = true,
                    credentialsError = null,
                    fmpApiKeyInputMissing = false,
                )
            } catch (failure: CredentialsStorageException) {
                mutableUiState.value = mutableUiState.value.copy(
                    credentialsError = failure.message,
                    fmpApiKeyInputMissing = false,
                )
            }
        }
    }

    fun clearFmpApiKey() {
        viewModelScope.launch {
            try {
                credentialsStore.clearFmpApiKey()
                mutableUiState.value = mutableUiState.value.copy(
                    hasFmpApiKey = false,
                    credentialsError = null,
                    fmpApiKeyInputMissing = false,
                )
            } catch (failure: CredentialsStorageException) {
                mutableUiState.value = mutableUiState.value.copy(
                    credentialsError = failure.message,
                    fmpApiKeyInputMissing = false,
                )
            }
        }
    }

    private fun refreshCredentialsStatus() {
        viewModelScope.launch {
            try {
                val hasCredentials = credentialsStore.hasAlpacaCredentials()
                val hasFinnhubApiKey = credentialsStore.hasFinnhubApiKey()
                val hasFmpApiKey = credentialsStore.hasFmpApiKey()
                mutableUiState.value = mutableUiState.value.copy(
                    hasAlpacaCredentials = hasCredentials,
                    hasFinnhubApiKey = hasFinnhubApiKey,
                    hasFmpApiKey = hasFmpApiKey,
                    credentialsError = null,
                    credentialsInputMissing = false,
                )
            } catch (failure: CredentialsStorageException) {
                mutableUiState.value = mutableUiState.value.copy(
                    credentialsError = failure.message,
                    credentialsInputMissing = false,
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

package com.gongpx.aistockanalyst.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.gongpx.aistockanalyst.R
import com.gongpx.aistockanalyst.designsystem.theme.AppColors
import com.gongpx.aistockanalyst.designsystem.theme.AppSpacing
import com.gongpx.aistockanalyst.model.ChartProvider
import com.gongpx.aistockanalyst.model.AppLanguage
import com.gongpx.aistockanalyst.model.BarInterval
import com.gongpx.aistockanalyst.model.Exchange
import com.gongpx.aistockanalyst.model.QuoteProvider
import com.gongpx.aistockanalyst.model.ValuationProvider

private enum class AppDestination(
    val label: Int,
    val icon: ImageVector,
) {
    Watchlist(R.string.watchlist, Icons.Default.Star),
    Screening(R.string.screening, Icons.Default.Search),
    Ai(R.string.ai, Icons.Default.AutoAwesome),
    Me(R.string.me, Icons.Default.Person),
}

@Composable
fun AnalystApp(
    settingsState: SettingsUiState,
    stockDetailState: StockDetailUiState,
    onOpenStock: (String, Exchange) -> Unit,
    onCloseStock: () -> Unit,
    onRefreshStock: () -> Unit,
    onChartIntervalSelected: (BarInterval) -> Unit,
    onAppLanguageSelected: (AppLanguage) -> Unit,
    onCheckForUpdates: () -> Unit,
    onQuoteProviderSelected: (QuoteProvider) -> Unit,
    onChartProviderSelected: (ChartProvider) -> Unit,
    onValuationProviderSelected: (ValuationProvider) -> Unit,
    onResetDataSourceSettings: () -> Unit,
    onSaveAlpacaCredentials: (String, String) -> Unit,
    onClearAlpacaCredentials: () -> Unit,
    onSaveFinnhubApiKey: (String) -> Unit,
    onClearFinnhubApiKey: () -> Unit,
    onSaveFmpApiKey: (String) -> Unit,
    onClearFmpApiKey: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val availableUpdate = settingsState.availableUpdate
    var dismissedUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var destinationName by rememberSaveable {
        mutableStateOf(AppDestination.Watchlist.name)
    }
    val destination = AppDestination.valueOf(destinationName)

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == destination,
                        onClick = { destinationName = item.name },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (destination) {
            AppDestination.Watchlist -> WatchlistDestination(
                contentPadding = innerPadding,
                state = stockDetailState,
                onOpenStock = onOpenStock,
                onCloseStock = onCloseStock,
                onRefresh = onRefreshStock,
                onIntervalSelected = onChartIntervalSelected,
            )
            AppDestination.Screening -> ScreeningScreen(innerPadding)
            AppDestination.Ai -> AiScreen(innerPadding)
            AppDestination.Me -> SettingsScreen(
                contentPadding = innerPadding,
                settingsState = settingsState,
                onAppLanguageSelected = onAppLanguageSelected,
                onCheckForUpdates = onCheckForUpdates,
                onQuoteProviderSelected = onQuoteProviderSelected,
                onChartProviderSelected = onChartProviderSelected,
                onValuationProviderSelected = onValuationProviderSelected,
                onResetDataSourceSettings = onResetDataSourceSettings,
                onSaveAlpacaCredentials = onSaveAlpacaCredentials,
                onClearAlpacaCredentials = onClearAlpacaCredentials,
                onSaveFinnhubApiKey = onSaveFinnhubApiKey,
                onClearFinnhubApiKey = onClearFinnhubApiKey,
                onSaveFmpApiKey = onSaveFmpApiKey,
                onClearFmpApiKey = onClearFmpApiKey,
            )
        }
    }

    if (
        settingsState.updateStatus == UpdateStatus.AVAILABLE &&
        availableUpdate != null &&
        dismissedUpdateVersion != availableUpdate.versionName
    ) {
        AlertDialog(
            onDismissRequest = {
                dismissedUpdateVersion = availableUpdate.versionName
            },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.update_available_body,
                        availableUpdate.versionName,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        openExternalUri(
                            context = context,
                            uriHandler = uriHandler,
                            uri = availableUpdate.downloadUrl,
                        )
                    },
                ) {
                    Text(stringResource(R.string.download_update))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dismissedUpdateVersion = availableUpdate.versionName
                    },
                ) {
                    Text(stringResource(R.string.later))
                }
            },
        )
    }
}

@Composable
private fun ScreeningScreen(contentPadding: PaddingValues) {
    ScreenContainer(contentPadding) {
        ScreenTitle(stringResource(R.string.screening_title))
        InformationCard {
            Text(stringResource(R.string.screening_scope))
            Spacer(Modifier.height(AppSpacing.small))
            Text(
                text = stringResource(R.string.screening_exclusions),
                color = AppColors.onSurfaceMuted,
            )
            Spacer(Modifier.height(AppSpacing.medium))
            Text(
                text = stringResource(R.string.screening_status),
                color = AppColors.primary,
            )
        }
    }
}

@Composable
private fun AiScreen(contentPadding: PaddingValues) {
    ScreenContainer(contentPadding) {
        ScreenTitle(stringResource(R.string.ai_title))
        InformationCard {
            Text(stringResource(R.string.ai_body))
            Spacer(Modifier.height(AppSpacing.medium))
            Text(
                text = stringResource(R.string.ai_safety),
                color = AppColors.aiAccentLight,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    contentPadding: PaddingValues,
    settingsState: SettingsUiState,
    onAppLanguageSelected: (AppLanguage) -> Unit,
    onCheckForUpdates: () -> Unit,
    onQuoteProviderSelected: (QuoteProvider) -> Unit,
    onChartProviderSelected: (ChartProvider) -> Unit,
    onValuationProviderSelected: (ValuationProvider) -> Unit,
    onResetDataSourceSettings: () -> Unit,
    onSaveAlpacaCredentials: (String, String) -> Unit,
    onClearAlpacaCredentials: () -> Unit,
    onSaveFinnhubApiKey: (String) -> Unit,
    onClearFinnhubApiKey: () -> Unit,
    onSaveFmpApiKey: (String) -> Unit,
    onClearFmpApiKey: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    ScreenContainer(contentPadding) {
        ScreenTitle(stringResource(R.string.settings_title))
        InformationCard {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AppSpacing.small))
            Text(
                text = stringResource(R.string.settings_language_body),
                color = AppColors.onSurfaceMuted,
            )
            Spacer(Modifier.height(AppSpacing.medium))
            ProviderSelector(
                title = stringResource(R.string.settings_language_choice),
                options = AppLanguage.entries,
                selected = settingsState.appLanguage,
                label = { language ->
                    when (language) {
                        AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                        AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                        AppLanguage.SIMPLIFIED_CHINESE ->
                            stringResource(R.string.language_simplified_chinese)
                    }
                },
                onSelected = onAppLanguageSelected,
            )
        }
        InformationCard {
            Text(
                text = stringResource(R.string.settings_data_sources),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AppSpacing.small))
            Text(
                text = stringResource(R.string.settings_data_sources_body),
                color = AppColors.onSurfaceMuted,
            )
            Spacer(Modifier.height(AppSpacing.medium))
            ProviderSelector(
                title = stringResource(R.string.settings_quote_provider),
                options = QuoteProvider.entries,
                selected = settingsState.dataSources.quoteProvider,
                label = { provider ->
                    when (provider) {
                        QuoteProvider.AUTO -> stringResource(R.string.provider_auto)
                        QuoteProvider.TENCENT -> stringResource(R.string.provider_tencent)
                        QuoteProvider.SINA -> stringResource(R.string.provider_sina)
                    }
                },
                onSelected = onQuoteProviderSelected,
            )
            Spacer(Modifier.height(AppSpacing.medium))
            ProviderSelector(
                title = stringResource(R.string.settings_chart_provider),
                options = ChartProvider.entries,
                selected = settingsState.dataSources.chartProvider,
                label = { provider ->
                    when (provider) {
                        ChartProvider.NOT_CONFIGURED ->
                            stringResource(R.string.provider_not_configured)
                        ChartProvider.ALPACA_IEX ->
                            stringResource(R.string.provider_alpaca_iex)
                    }
                },
                onSelected = onChartProviderSelected,
            )
            Spacer(Modifier.height(AppSpacing.small))
            Text(
                text = stringResource(R.string.settings_chart_provider_explanation),
                color = AppColors.onSurfaceMuted,
            )
            if (settingsState.dataSources.chartProvider == ChartProvider.ALPACA_IEX) {
                Spacer(Modifier.height(AppSpacing.medium))
                AlpacaCredentialsEditor(
                    hasCredentials = settingsState.hasAlpacaCredentials,
                    error = settingsState.credentialsError,
                    inputMissing = settingsState.credentialsInputMissing,
                    onSave = onSaveAlpacaCredentials,
                    onClear = onClearAlpacaCredentials,
                )
            }
            Spacer(Modifier.height(AppSpacing.medium))
            ProviderSelector(
                title = stringResource(R.string.settings_valuation_provider),
                options = ValuationProvider.entries,
                selected = settingsState.dataSources.valuationProvider,
                label = { provider ->
                    when (provider) {
                        ValuationProvider.YAHOO_FINANCE ->
                            stringResource(R.string.provider_yahoo_finance)
                        ValuationProvider.FINNHUB -> stringResource(R.string.provider_finnhub)
                        ValuationProvider.FMP -> stringResource(R.string.provider_fmp)
                    }
                },
                onSelected = onValuationProviderSelected,
            )
            when (settingsState.dataSources.valuationProvider) {
                ValuationProvider.YAHOO_FINANCE -> Unit
                ValuationProvider.FINNHUB -> {
                    Spacer(Modifier.height(AppSpacing.medium))
                    ValuationApiKeyEditor(
                        title = stringResource(R.string.settings_finnhub_setup_title),
                        body = stringResource(R.string.settings_finnhub_setup_body),
                        disclosure = stringResource(R.string.settings_finnhub_disclosure),
                        keyLabel = stringResource(R.string.settings_finnhub_api_key),
                        hasKey = settingsState.hasFinnhubApiKey,
                        savedText = stringResource(R.string.settings_finnhub_key_saved),
                        missingText = stringResource(R.string.settings_finnhub_key_missing),
                        requiredText = stringResource(R.string.settings_finnhub_key_required),
                        error = settingsState.credentialsError,
                        inputMissing = settingsState.finnhubApiKeyInputMissing,
                        signupUrl = FINNHUB_SIGN_UP_URL,
                        docsUrl = FINNHUB_DOCS_URL,
                        clearTitle = stringResource(R.string.settings_finnhub_clear_title),
                        clearBody = stringResource(R.string.settings_finnhub_clear_body),
                        onSave = onSaveFinnhubApiKey,
                        onClear = onClearFinnhubApiKey,
                    )
                }
                ValuationProvider.FMP -> {
                    Spacer(Modifier.height(AppSpacing.medium))
                    ValuationApiKeyEditor(
                        title = stringResource(R.string.settings_fmp_setup_title),
                        body = stringResource(R.string.settings_fmp_setup_body),
                        disclosure = stringResource(R.string.settings_fmp_disclosure),
                        keyLabel = stringResource(R.string.settings_fmp_api_key),
                        hasKey = settingsState.hasFmpApiKey,
                        savedText = stringResource(R.string.settings_fmp_key_saved),
                        missingText = stringResource(R.string.settings_fmp_key_missing),
                        requiredText = stringResource(R.string.settings_fmp_key_required),
                        error = settingsState.credentialsError,
                        inputMissing = settingsState.fmpApiKeyInputMissing,
                        signupUrl = FMP_SIGN_UP_URL,
                        docsUrl = FMP_DOCS_URL,
                        clearTitle = stringResource(R.string.settings_fmp_clear_title),
                        clearBody = stringResource(R.string.settings_fmp_clear_body),
                        onSave = onSaveFmpApiKey,
                        onClear = onClearFmpApiKey,
                    )
                }
            }
            settingsState.storageError?.let { error ->
                Spacer(Modifier.height(AppSpacing.medium))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(AppSpacing.small))
                Button(onClick = onResetDataSourceSettings) {
                    Text(stringResource(R.string.settings_reset_data_sources))
                }
            }
        }
        InformationCard {
            Text(
                text = stringResource(R.string.settings_azure),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AppSpacing.small))
            Text(
                text = stringResource(R.string.settings_azure_body),
                color = AppColors.onSurfaceMuted,
            )
        }
        InformationCard {
            Text(
                text = stringResource(R.string.settings_runtime),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AppSpacing.small))
            Text(
                text = stringResource(R.string.settings_runtime_body),
                color = AppColors.onSurfaceMuted,
            )
        }
        InformationCard {
            Text(
                text = stringResource(R.string.settings_updates),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AppSpacing.small))
            Text(
                text = when (settingsState.updateStatus) {
                    UpdateStatus.IDLE -> stringResource(R.string.update_not_checked)
                    UpdateStatus.CHECKING -> stringResource(R.string.update_checking)
                    UpdateStatus.UP_TO_DATE -> stringResource(R.string.update_up_to_date)
                    UpdateStatus.AVAILABLE -> stringResource(
                        R.string.update_available_version,
                        settingsState.availableUpdate?.versionName.orEmpty(),
                    )
                    UpdateStatus.FAILED -> stringResource(R.string.update_check_failed)
                },
                color = AppColors.onSurfaceMuted,
            )
            Spacer(Modifier.height(AppSpacing.small))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
            ) {
                Button(
                    onClick = onCheckForUpdates,
                    enabled = settingsState.updateStatus != UpdateStatus.CHECKING,
                ) {
                    Text(stringResource(R.string.check_for_updates))
                }
                settingsState.availableUpdate?.let { update ->
                    TextButton(
                        onClick = {
                            openExternalUri(
                                context = context,
                                uriHandler = uriHandler,
                                uri = update.downloadUrl,
                            )
                        },
                    ) {
                        Text(stringResource(R.string.download_update))
                    }
                }
            }
        }
        InformationCard {
            Text(
                text = stringResource(R.string.settings_feedback),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AppSpacing.small))
            Text(
                text = stringResource(R.string.settings_feedback_body),
                color = AppColors.onSurfaceMuted,
            )
            Spacer(Modifier.height(AppSpacing.small))
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
            ) {
                Button(
                    onClick = {
                        openExternalUri(
                            context = context,
                            uriHandler = uriHandler,
                            uri = FEEDBACK_ISSUE_URL,
                        )
                    },
                ) {
                    Text(stringResource(R.string.feedback_github_issue))
                }
                TextButton(
                    onClick = {
                        openExternalUri(
                            context = context,
                            uriHandler = uriHandler,
                            uri = FEEDBACK_EMAIL_URI,
                        )
                    },
                ) {
                    Text(stringResource(R.string.feedback_email))
                }
            }
            Spacer(Modifier.height(AppSpacing.extraSmall))
            Text(
                text = FEEDBACK_EMAIL,
                color = AppColors.onSurfaceMuted,
            )
        }
    }
}

private const val FEEDBACK_ISSUE_URL =
    "https://github.com/gongpx20069/android-ai-stock-analyst/issues/new"
private const val FEEDBACK_EMAIL = "gongpx20069@vip.qq.com"
private const val FEEDBACK_EMAIL_URI = "mailto:$FEEDBACK_EMAIL"
private const val ALPACA_SIGN_UP_URL = "https://app.alpaca.markets/signup"
private const val ALPACA_DASHBOARD_URL =
    "https://app.alpaca.markets/brokerage/dashboard/overview"
private const val FINNHUB_SIGN_UP_URL = "https://finnhub.io/register"
private const val FINNHUB_DOCS_URL = "https://finnhub.io/docs/api"
private const val FMP_SIGN_UP_URL = "https://site.financialmodelingprep.com/register"
private const val FMP_DOCS_URL =
    "https://site.financialmodelingprep.com/developer/docs"

private fun openExternalUri(
    context: Context,
    uriHandler: UriHandler,
    uri: String,
) {
    try {
        uriHandler.openUri(uri)
    } catch (_: IllegalArgumentException) {
        Toast.makeText(
            context,
            context.getString(R.string.external_app_unavailable),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

@Composable
private fun AlpacaCredentialsEditor(
    hasCredentials: Boolean,
    error: String?,
    inputMissing: Boolean,
    onSave: (String, String) -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var keyId by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.settings_alpaca_setup_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(AppSpacing.extraSmall))
    Text(
        text = stringResource(R.string.settings_alpaca_setup_body),
        color = AppColors.onSurfaceMuted,
    )
    Spacer(Modifier.height(AppSpacing.small))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        Button(
            onClick = {
                openExternalUri(
                    context = context,
                    uriHandler = uriHandler,
                    uri = ALPACA_SIGN_UP_URL,
                )
            },
        ) {
            Text(stringResource(R.string.settings_alpaca_create_account))
        }
        TextButton(
            onClick = {
                openExternalUri(
                    context = context,
                    uriHandler = uriHandler,
                    uri = ALPACA_DASHBOARD_URL,
                )
            },
        ) {
            Text(stringResource(R.string.settings_alpaca_open_dashboard))
        }
    }

    Spacer(Modifier.height(AppSpacing.small))
    Text(
        text = stringResource(R.string.settings_alpaca_security),
        color = AppColors.onSurfaceMuted,
    )
    Spacer(Modifier.height(AppSpacing.medium))
    Text(
        text = stringResource(R.string.settings_alpaca_disclosure),
        color = AppColors.onSurfaceMuted,
    )
    Spacer(Modifier.height(AppSpacing.small))
    Text(
        text = if (hasCredentials) {
            stringResource(R.string.settings_alpaca_credentials_saved)
        } else {
            stringResource(R.string.settings_alpaca_credentials_missing)
        },
        color = if (hasCredentials) AppColors.primary else MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(AppSpacing.small))
    OutlinedTextField(
        value = keyId,
        onValueChange = { keyId = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.settings_alpaca_key_id)) },
        singleLine = true,
    )
    Spacer(Modifier.height(AppSpacing.small))
    OutlinedTextField(
        value = secretKey,
        onValueChange = { secretKey = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.settings_alpaca_secret_key)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
        ),
        singleLine = true,
    )
    val displayedError = if (inputMissing) {
        stringResource(R.string.settings_alpaca_credentials_required)
    } else {
        error
    }
    displayedError?.let {
        Spacer(Modifier.height(AppSpacing.small))
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(AppSpacing.small))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        Button(
            onClick = {
                onSave(keyId, secretKey)
                keyId = ""
                secretKey = ""
            },
            enabled = keyId.isNotBlank() && secretKey.isNotBlank(),
        ) {
            Text(stringResource(R.string.settings_alpaca_save_credentials))
        }
        TextButton(
            onClick = { showClearConfirmation = true },
            enabled = hasCredentials,
        ) {
            Text(stringResource(R.string.settings_alpaca_clear_credentials))
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.settings_alpaca_clear_title)) },
            text = { Text(stringResource(R.string.settings_alpaca_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClear()
                    },
                ) {
                    Text(stringResource(R.string.settings_alpaca_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.settings_alpaca_clear_cancel))
                }
            },
        )
    }
}

@Composable
private fun ValuationApiKeyEditor(
    title: String,
    body: String,
    disclosure: String,
    keyLabel: String,
    hasKey: Boolean,
    savedText: String,
    missingText: String,
    requiredText: String,
    error: String?,
    inputMissing: Boolean,
    signupUrl: String,
    docsUrl: String,
    clearTitle: String,
    clearBody: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var apiKey by remember { mutableStateOf("") }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }

    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(AppSpacing.extraSmall))
    Text(body, color = AppColors.onSurfaceMuted)
    Spacer(Modifier.height(AppSpacing.small))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        Button(onClick = { openExternalUri(context, uriHandler, signupUrl) }) {
            Text(stringResource(R.string.settings_provider_sign_up))
        }
        TextButton(onClick = { openExternalUri(context, uriHandler, docsUrl) }) {
            Text(stringResource(R.string.settings_provider_open_docs))
        }
    }
    Spacer(Modifier.height(AppSpacing.small))
    Text(disclosure, color = AppColors.onSurfaceMuted)
    Spacer(Modifier.height(AppSpacing.small))
    Text(
        text = if (hasKey) savedText else missingText,
        color = if (hasKey) AppColors.primary else MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(AppSpacing.small))
    OutlinedTextField(
        value = apiKey,
        onValueChange = { apiKey = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(keyLabel) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
        ),
        singleLine = true,
    )
    (if (inputMissing) requiredText else error)?.let {
        Spacer(Modifier.height(AppSpacing.small))
        Text(it, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(AppSpacing.small))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        Button(
            onClick = {
                onSave(apiKey)
                apiKey = ""
            },
            enabled = apiKey.isNotBlank(),
        ) {
            Text(stringResource(R.string.settings_provider_save_key))
        }
        TextButton(
            onClick = { showClearConfirmation = true },
            enabled = hasKey,
        ) {
            Text(stringResource(R.string.settings_provider_clear_key))
        }
    }
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(clearTitle) },
            text = { Text(clearBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClear()
                    },
                ) {
                    Text(stringResource(R.string.settings_provider_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.settings_provider_clear_cancel))
                }
            },
        )
    }
}

@Composable
private fun <T> ProviderSelector(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
    )
    Spacer(Modifier.height(AppSpacing.extraSmall))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
internal fun ScreenContainer(
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(AppSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium),
        content = content,
    )
}

@Composable
internal fun ScreenTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
internal fun InformationCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.medium),
            content = content,
        )
    }
}

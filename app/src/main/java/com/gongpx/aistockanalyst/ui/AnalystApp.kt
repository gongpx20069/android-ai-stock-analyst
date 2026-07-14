package com.gongpx.aistockanalyst.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.gongpx.aistockanalyst.R
import com.gongpx.aistockanalyst.designsystem.theme.AppColors
import com.gongpx.aistockanalyst.designsystem.theme.AppSpacing

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
fun AnalystApp() {
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
            AppDestination.Watchlist -> WatchlistScreen(innerPadding)
            AppDestination.Screening -> ScreeningScreen(innerPadding)
            AppDestination.Ai -> AiScreen(innerPadding)
            AppDestination.Me -> SettingsScreen(innerPadding)
        }
    }
}

@Composable
private fun WatchlistScreen(contentPadding: PaddingValues) {
    ScreenContainer(contentPadding) {
        ScreenTitle(stringResource(R.string.watchlist_title))
        InformationCard {
            Text(
                text = stringResource(R.string.watchlist_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AppSpacing.small))
            Text(
                text = stringResource(R.string.watchlist_empty_body),
                color = AppColors.onSurfaceMuted,
            )
            Spacer(Modifier.height(AppSpacing.medium))
            Button(
                onClick = {},
                enabled = false,
            ) {
                Text(stringResource(R.string.add_stock))
            }
        }
        InformationCard {
            Text(
                text = stringResource(R.string.valuation_rule_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AppSpacing.small))
            SemanticRow(
                icon = Icons.Default.KeyboardArrowUp,
                text = stringResource(R.string.valuation_upside),
                color = AppColors.valuationUpside,
            )
            Spacer(Modifier.height(AppSpacing.small))
            SemanticRow(
                icon = Icons.Default.KeyboardArrowDown,
                text = stringResource(R.string.valuation_risk),
                color = AppColors.valuationRisk,
            )
        }
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
private fun SettingsScreen(contentPadding: PaddingValues) {
    ScreenContainer(contentPadding) {
        ScreenTitle(stringResource(R.string.settings_title))
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
    }
}

@Composable
private fun ScreenContainer(
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
private fun ScreenTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun InformationCard(content: @Composable ColumnScope.() -> Unit) {
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

@Composable
private fun SemanticRow(
    icon: ImageVector,
    text: String,
    color: Color,
) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
        )
        Text(
            text = text,
            color = color,
        )
    }
}

package com.gongpx.aistockanalyst.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

object AppColors {
    val background = Color(0xFF0F172A)
    val surface = Color(0xFF222735)
    val surfaceVariant = Color(0xFF272F42)
    val onBackground = Color(0xFFF8FAFC)
    val onSurfaceMuted = Color(0xFF94A3B8)
    val outline = Color(0xFF334155)
    val primary = Color(0xFFF59E0B)
    val secondary = Color(0xFFFBBF24)
    val aiAccent = Color(0xFF8B5CF6)
    val aiAccentLight = Color(0xFFA78BFA)
    val error = Color(0xFFEF4444)
    val valuationUpside = Color(0xFF4ADE80)
    val valuationRisk = Color(0xFFF87171)
}

object AppSpacing {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val extraLarge = 32.dp
}

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.primary,
    onPrimary = AppColors.background,
    secondary = AppColors.secondary,
    onSecondary = AppColors.background,
    tertiary = AppColors.aiAccent,
    onTertiary = Color.White,
    background = AppColors.background,
    onBackground = AppColors.onBackground,
    surface = AppColors.surface,
    onSurface = AppColors.onBackground,
    surfaceVariant = AppColors.surfaceVariant,
    onSurfaceVariant = AppColors.onSurfaceMuted,
    outline = AppColors.outline,
    error = AppColors.error,
    onError = Color.White,
)

private val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF9A5B00),
    onPrimary = Color.White,
    secondary = Color(0xFF7A5900),
    tertiary = Color(0xFF6842C2),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
)

@Composable
fun AiStockAnalystTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        content = content,
    )
}

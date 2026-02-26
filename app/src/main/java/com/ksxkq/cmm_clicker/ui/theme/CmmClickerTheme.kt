package com.ksxkq.cmm_clicker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun monoLightColorScheme() = lightColorSchemeFrom(AppThemeTokens.palette(AppThemeMode.MONO_LIGHT))

private fun monoDarkColorScheme() = darkColorSchemeFrom(AppThemeTokens.palette(AppThemeMode.MONO_DARK))

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.sp,
    ),
)

@Composable
fun CmmClickerTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.MONO_LIGHT -> monoLightColorScheme()
        AppThemeMode.MONO_DARK -> monoDarkColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

private fun lightColorSchemeFrom(palette: AppThemePalette) = lightColorScheme(
    primary = Color(palette.primary),
    onPrimary = Color(palette.onPrimary),
    secondary = Color(palette.secondary),
    onSecondary = Color(palette.onSecondary),
    background = Color(palette.background),
    onBackground = Color(palette.onBackground),
    surface = Color(palette.surface),
    onSurface = Color(palette.onSurface),
    surfaceVariant = Color(palette.surfaceVariant),
    onSurfaceVariant = Color(palette.onSurfaceVariant),
    outline = Color(palette.outline),
    error = Color(palette.error),
    onError = Color(palette.onError),
)

private fun darkColorSchemeFrom(palette: AppThemePalette) = darkColorScheme(
    primary = Color(palette.primary),
    onPrimary = Color(palette.onPrimary),
    secondary = Color(palette.secondary),
    onSecondary = Color(palette.onSecondary),
    background = Color(palette.background),
    onBackground = Color(palette.onBackground),
    surface = Color(palette.surface),
    onSurface = Color(palette.onSurface),
    surfaceVariant = Color(palette.surfaceVariant),
    onSurfaceVariant = Color(palette.onSurfaceVariant),
    outline = Color(palette.outline),
    error = Color(palette.error),
    onError = Color(palette.onError),
)

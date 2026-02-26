package com.ksxkq.cmm_clicker.ui.theme

data class AppThemePalette(
    val primary: Int,
    val onPrimary: Int,
    val secondary: Int,
    val onSecondary: Int,
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val error: Int,
    val onError: Int,
)

object AppThemeTokens {
    private val monoLight = AppThemePalette(
        primary = 0xFF111111.toInt(),
        onPrimary = 0xFFFFFFFF.toInt(),
        secondary = 0xFF333333.toInt(),
        onSecondary = 0xFFFFFFFF.toInt(),
        background = 0xFFFFFFFF.toInt(),
        onBackground = 0xFF111111.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        onSurface = 0xFF111111.toInt(),
        surfaceVariant = 0xFFF6F6F6.toInt(),
        onSurfaceVariant = 0xFF4A4A4A.toInt(),
        outline = 0xFFE0E0E0.toInt(),
        error = 0xFF111111.toInt(),
        onError = 0xFFFFFFFF.toInt(),
    )

    private val monoDark = AppThemePalette(
        primary = 0xFFF5F5F5.toInt(),
        onPrimary = 0xFF111111.toInt(),
        secondary = 0xFFE0E0E0.toInt(),
        onSecondary = 0xFF111111.toInt(),
        background = 0xFF101010.toInt(),
        onBackground = 0xFFF5F5F5.toInt(),
        surface = 0xFF101010.toInt(),
        onSurface = 0xFFF5F5F5.toInt(),
        surfaceVariant = 0xFF1E1E1E.toInt(),
        onSurfaceVariant = 0xFFD0D0D0.toInt(),
        outline = 0xFF2E2E2E.toInt(),
        error = 0xFFF5F5F5.toInt(),
        onError = 0xFF101010.toInt(),
    )

    fun palette(mode: AppThemeMode): AppThemePalette {
        return when (mode) {
            AppThemeMode.MONO_LIGHT -> monoLight
            AppThemeMode.MONO_DARK -> monoDark
        }
    }
}

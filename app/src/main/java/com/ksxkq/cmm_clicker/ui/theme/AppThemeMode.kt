package com.ksxkq.cmm_clicker.ui.theme

enum class AppThemeMode(
    val displayName: String,
) {
    MONO_LIGHT("Mono Light"),
    MONO_DARK("Mono Dark"),
    ;

    fun next(): AppThemeMode {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

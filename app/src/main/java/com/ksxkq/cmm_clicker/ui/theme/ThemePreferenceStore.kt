package com.ksxkq.cmm_clicker.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme_preferences")

class ThemePreferenceStore(
    private val context: Context,
) {
    private val keyThemeMode = stringPreferencesKey("theme_mode")

    val themeModeFlow: Flow<AppThemeMode> = context.themeDataStore.data.map { preferences ->
        val raw = preferences[keyThemeMode]
        runCatching { raw?.let { AppThemeMode.valueOf(it) } }
            .getOrNull()
            ?: AppThemeMode.MONO_LIGHT
    }

    suspend fun saveThemeMode(mode: AppThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[keyThemeMode] = mode.name
        }
    }
}

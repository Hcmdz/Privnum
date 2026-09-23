package com.hcmdz.privnum.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val INCOMING_POPUP = booleanPreferencesKey("incoming_popup")
        val OUTGOING_POPUP = booleanPreferencesKey("outgoing_popup")
    }

    val themeMode: Flow<ThemeMode> =
        context.settingsDataStore.data.map {
            runCatching { ThemeMode.valueOf(it[Keys.THEME_MODE] ?: "SYSTEM") }
                .getOrDefault(ThemeMode.SYSTEM)
        }

    val incomingPopup: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.INCOMING_POPUP] ?: true }

    val outgoingPopup: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.OUTGOING_POPUP] ?: true }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setIncomingPopup(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.INCOMING_POPUP] = enabled }
    }

    suspend fun setOutgoingPopup(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.OUTGOING_POPUP] = enabled }
    }
}

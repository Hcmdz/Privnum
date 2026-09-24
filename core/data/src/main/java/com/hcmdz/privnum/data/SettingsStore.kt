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
        val DEFAULT_REGION = stringPreferencesKey("default_region")
        val RECENT_COUNTRIES = stringPreferencesKey("recent_countries")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
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

    /** ISO-3166 region for number parsing, or null for automatic (SIM). */
    val defaultRegion: Flow<String?> =
        context.settingsDataStore.data.map { it[Keys.DEFAULT_REGION] }

    suspend fun setDefaultRegion(region: String?) {
        context.settingsDataStore.edit {
            if (region == null) it.remove(Keys.DEFAULT_REGION)
            else it[Keys.DEFAULT_REGION] = region
        }
    }

    /** Recently used country codes, most recent first (max 3). */
    val recentCountries: Flow<List<String>> =
        context.settingsDataStore.data.map {
            it[Keys.RECENT_COUNTRIES]?.split(",")?.filter(String::isNotEmpty) ?: emptyList()
        }

    suspend fun pushRecentCountry(code: String) {
        context.settingsDataStore.edit {
            val updated = (listOf(code.uppercase()) +
                (it[Keys.RECENT_COUNTRIES]?.split(",") ?: emptyList()))
                .filter { it.isNotEmpty() }.distinct().take(3)
            it[Keys.RECENT_COUNTRIES] = updated.joinToString(",")
        }
    }

    val dynamicColor: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    val amoledBlack: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.AMOLED_BLACK] ?: false }

    suspend fun setAmoledBlack(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AMOLED_BLACK] = enabled }
    }
}

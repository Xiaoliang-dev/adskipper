package com.adskipper.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "adskipper_settings")

class SettingsManager(private val context: Context) {

    companion object {
        val KEY_AUTO_START = booleanPreferencesKey("auto_start")
        val KEY_SHOW_TOAST = booleanPreferencesKey("show_toast")
        val KEY_VIBRATE = booleanPreferencesKey("vibrate")
        val KEY_SKIP_DELAY = longPreferencesKey("skip_delay")
        val KEY_SHOW_FLOATING = booleanPreferencesKey("show_floating")
        val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        val KEY_SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            autoStart = preferences[KEY_AUTO_START] ?: false,
            showToast = preferences[KEY_SHOW_TOAST] ?: true,
            vibrate = preferences[KEY_VIBRATE] ?: false,
            skipDelay = preferences[KEY_SKIP_DELAY] ?: 0L,
            showFloating = preferences[KEY_SHOW_FLOATING] ?: false,
            darkMode = preferences[KEY_DARK_MODE] ?: "system",
            serviceEnabled = preferences[KEY_SERVICE_ENABLED] ?: false,
            firstLaunch = preferences[KEY_FIRST_LAUNCH] ?: true
        )
    }

    suspend fun setAutoStart(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_START] = enabled }
    }

    suspend fun setShowToast(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_TOAST] = enabled }
    }

    suspend fun setVibrate(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VIBRATE] = enabled }
    }

    suspend fun setSkipDelay(delayMs: Long) {
        context.dataStore.edit { it[KEY_SKIP_DELAY] = delayMs }
    }

    suspend fun setShowFloating(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_FLOATING] = enabled }
    }

    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { it[KEY_DARK_MODE] = mode }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SERVICE_ENABLED] = enabled }
    }

    suspend fun setFirstLaunch(firstLaunch: Boolean) {
        context.dataStore.edit { it[KEY_FIRST_LAUNCH] = firstLaunch }
    }

    data class AppSettings(
        val autoStart: Boolean = false,
        val showToast: Boolean = true,
        val vibrate: Boolean = false,
        val skipDelay: Long = 0L,
        val showFloating: Boolean = false,
        val darkMode: String = "system",
        val serviceEnabled: Boolean = false,
        val firstLaunch: Boolean = true
    )

    val currentSettings: AppSettings
        get() = runBlocking { settings.first() }
}
package com.catkeeper.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val sessionLimitMinutes: Int,
    val cooldownMinutes: Int,
    val dailyLimitMinutes: Int,
    val dailyUsageMs: Long,
    val lastUsageDate: String,
    val pin: String?,
    val isPinEnabled: Boolean
)

class SettingsRepository(private val context: Context) {

    companion object {
        val SESSION_LIMIT = intPreferencesKey("session_limit_mins")
        val COOLDOWN = intPreferencesKey("cooldown_mins")
        val DAILY_LIMIT = intPreferencesKey("daily_limit_mins")
        
        val DAILY_USAGE_MS = longPreferencesKey("daily_usage_ms")
        val LAST_USAGE_DATE = stringPreferencesKey("last_usage_date")
        
        val PIN = stringPreferencesKey("app_pin")
        val IS_PIN_ENABLED = booleanPreferencesKey("is_pin_enabled")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            sessionLimitMinutes = prefs[SESSION_LIMIT] ?: 1,
            cooldownMinutes = prefs[COOLDOWN] ?: 1,
            dailyLimitMinutes = prefs[DAILY_LIMIT] ?: 30,
            dailyUsageMs = prefs[DAILY_USAGE_MS] ?: 0L,
            lastUsageDate = prefs[LAST_USAGE_DATE] ?: "",
            pin = prefs[PIN],
            isPinEnabled = prefs[IS_PIN_ENABLED] ?: false
        )
    }

    suspend fun updateLimits(session: Int, cooldown: Int, daily: Int) {
        context.dataStore.edit { prefs ->
            prefs[SESSION_LIMIT] = session
            prefs[COOLDOWN] = cooldown
            prefs[DAILY_LIMIT] = daily
        }
    }

    suspend fun updateDailyUsage(usageMs: Long, dateString: String) {
        context.dataStore.edit { prefs ->
            prefs[DAILY_USAGE_MS] = usageMs
            prefs[LAST_USAGE_DATE] = dateString
        }
    }

    suspend fun updatePinSettings(isPinEnabled: Boolean, pin: String?) {
        context.dataStore.edit { prefs ->
            prefs[IS_PIN_ENABLED] = isPinEnabled
            if (pin != null) {
                prefs[PIN] = pin
            } else {
                prefs.remove(PIN)
            }
        }
    }
}

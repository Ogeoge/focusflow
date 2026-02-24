package com.focusflow.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focusflow.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed settings repository.
 */
class SettingsDataStore(private val context: Context) {

    private val dataStore: DataStore<Preferences> = context.settingsDataStore

    val settingsFlow: Flow<AppSettings> = dataStore.data
        .map { prefs ->
            AppSettings(
                theme = ThemeModeSetting.fromContractValue(prefs[Keys.THEME] ?: Defaults.THEME),
                soundEnabled = prefs[Keys.SOUND_ENABLED] ?: Defaults.SOUND_ENABLED,
                vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: Defaults.VIBRATION_ENABLED,
                silentNotifications = prefs[Keys.SILENT_NOTIFICATIONS] ?: Defaults.SILENT_NOTIFICATIONS,
                respectDnd = prefs[Keys.RESPECT_DND] ?: Defaults.RESPECT_DND,
                maxTotalPausedMinutes = (prefs[Keys.MAX_TOTAL_PAUSED_MINUTES] ?: Defaults.MAX_TOTAL_PAUSED_MINUTES)
                    .coerceAtLeast(0),
                segmentEndBehavior = SegmentEndBehavior.fromContractValue(
                    prefs[Keys.SEGMENT_END_BEHAVIOR] ?: Defaults.SEGMENT_END_BEHAVIOR
                ),
                defaultTimerPlanId = prefs[Keys.DEFAULT_TIMER_PLAN_ID],
            )
        }
        .distinctUntilChanged()

    /** Convenience flow used by MainActivity for theme selection. */
    val themeModeFlow: Flow<ThemeMode> = settingsFlow
        .map { it.theme.toThemeMode() }
        .distinctUntilChanged()

    suspend fun setTheme(theme: ThemeModeSetting) {
        dataStore.edit { it[Keys.THEME] = theme.toContractValue() }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }
    }

    suspend fun setSilentNotifications(enabled: Boolean) {
        dataStore.edit { it[Keys.SILENT_NOTIFICATIONS] = enabled }
    }

    suspend fun setRespectDnd(enabled: Boolean) {
        dataStore.edit { it[Keys.RESPECT_DND] = enabled }
    }

    suspend fun setMaxTotalPausedMinutes(minutes: Int) {
        dataStore.edit { it[Keys.MAX_TOTAL_PAUSED_MINUTES] = minutes.coerceAtLeast(0) }
    }

    suspend fun setSegmentEndBehavior(behavior: SegmentEndBehavior) {
        dataStore.edit { it[Keys.SEGMENT_END_BEHAVIOR] = behavior.toContractValue() }
    }

    suspend fun setDefaultTimerPlanId(id: String?) {
        dataStore.edit { prefs ->
            if (id.isNullOrBlank()) prefs.remove(Keys.DEFAULT_TIMER_PLAN_ID)
            else prefs[Keys.DEFAULT_TIMER_PLAN_ID] = id
        }
    }

    data class AppSettings(
        val theme: ThemeModeSetting,
        val soundEnabled: Boolean,
        val vibrationEnabled: Boolean,
        val silentNotifications: Boolean,
        val respectDnd: Boolean,
        val maxTotalPausedMinutes: Int,
        val segmentEndBehavior: SegmentEndBehavior,
        val defaultTimerPlanId: String?,
    ) {
        init {
            require(maxTotalPausedMinutes >= 0) { "maxTotalPausedMinutes must be >= 0" }
        }
    }

    enum class SegmentEndBehavior {
        AUTO_ADVANCE,
        PROMPT,
        ASK_EACH_TIME,
        ;

        fun toContractValue(): String = when (this) {
            AUTO_ADVANCE -> "auto_advance"
            PROMPT -> "prompt"
            ASK_EACH_TIME -> "ask_each_time"
        }

        companion object {
            fun fromContractValue(value: String): SegmentEndBehavior = when (value) {
                "auto_advance" -> AUTO_ADVANCE
                "prompt" -> PROMPT
                "ask_each_time" -> ASK_EACH_TIME
                else -> AUTO_ADVANCE
            }
        }
    }

    enum class ThemeModeSetting {
        SYSTEM,
        LIGHT,
        DARK,
        ;

        fun toContractValue(): String = when (this) {
            SYSTEM -> "system"
            LIGHT -> "light"
            DARK -> "dark"
        }

        fun toThemeMode(): ThemeMode = when (this) {
            SYSTEM -> ThemeMode.SYSTEM
            LIGHT -> ThemeMode.LIGHT
            DARK -> ThemeMode.DARK
        }

        companion object {
            fun fromContractValue(value: String): ThemeModeSetting = when (value) {
                "system" -> SYSTEM
                "light" -> LIGHT
                "dark" -> DARK
                else -> SYSTEM
            }
        }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val SILENT_NOTIFICATIONS = booleanPreferencesKey("silent_notifications")
        val RESPECT_DND = booleanPreferencesKey("respect_dnd")
        val MAX_TOTAL_PAUSED_MINUTES = intPreferencesKey("max_total_paused_minutes")
        val SEGMENT_END_BEHAVIOR = stringPreferencesKey("segment_end_behavior")
        val DEFAULT_TIMER_PLAN_ID = stringPreferencesKey("default_timer_plan_id")
    }

    private object Defaults {
        const val THEME: String = "system"
        const val SOUND_ENABLED: Boolean = true
        const val VIBRATION_ENABLED: Boolean = true
        const val SILENT_NOTIFICATIONS: Boolean = false
        const val RESPECT_DND: Boolean = true
        const val MAX_TOTAL_PAUSED_MINUTES: Int = 10
        const val SEGMENT_END_BEHAVIOR: String = "ask_each_time"
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "focusflow_settings")

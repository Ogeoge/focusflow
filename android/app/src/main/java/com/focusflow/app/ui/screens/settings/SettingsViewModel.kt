package com.focusflow.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.app.AppServices
import com.focusflow.app.data.prefs.SettingsDataStore
import com.focusflow.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsDataStore = AppServices.settings,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settings.settingsFlow
        .map { s ->
            SettingsUiState(
                themeMode = s.theme.toThemeMode(),
                soundEnabled = s.soundEnabled,
                vibrationEnabled = s.vibrationEnabled,
                silentNotifications = s.silentNotifications,
                respectDnd = s.respectDnd,
                maxTotalPausedMinutes = s.maxTotalPausedMinutes,
                segmentEndBehavior = s.segmentEndBehavior,
                defaultTimerPlanId = s.defaultTimerPlanId,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState.defaults(),
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            val setting = when (mode) {
                ThemeMode.SYSTEM -> SettingsDataStore.ThemeModeSetting.SYSTEM
                ThemeMode.LIGHT -> SettingsDataStore.ThemeModeSetting.LIGHT
                ThemeMode.DARK -> SettingsDataStore.ThemeModeSetting.DARK
            }
            settings.setTheme(setting)
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setSoundEnabled(enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setVibrationEnabled(enabled) }
    }

    fun setSilentNotifications(enabled: Boolean) {
        viewModelScope.launch { settings.setSilentNotifications(enabled) }
    }

    fun setRespectDnd(enabled: Boolean) {
        viewModelScope.launch { settings.setRespectDnd(enabled) }
    }

    fun setMaxTotalPausedMinutes(minutes: Int) {
        viewModelScope.launch {
            val safe = minutes.coerceAtLeast(0)
            settings.setMaxTotalPausedMinutes(safe)
        }
    }

    fun setSegmentEndBehavior(value: SettingsDataStore.SegmentEndBehavior) {
        viewModelScope.launch { settings.setSegmentEndBehavior(value) }
    }

    fun setDefaultTimerPlanId(id: String?) {
        viewModelScope.launch { settings.setDefaultTimerPlanId(id) }
    }

    fun openCustomSequenceEditor() {
        // Navigation wiring can be added later; for now this is a no-op stub.
    }
}

/**
 * UI-ready snapshot of contract AppSettings fields.
 */
data class SettingsUiState(
    val themeMode: ThemeMode,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val silentNotifications: Boolean,
    val respectDnd: Boolean,
    val maxTotalPausedMinutes: Int,
    val segmentEndBehavior: SettingsDataStore.SegmentEndBehavior,
    val defaultTimerPlanId: String?,
) {
    companion object {
        fun defaults(): SettingsUiState = SettingsUiState(
            themeMode = ThemeMode.SYSTEM,
            soundEnabled = true,
            vibrationEnabled = true,
            silentNotifications = false,
            respectDnd = true,
            maxTotalPausedMinutes = 0,
            segmentEndBehavior = SettingsDataStore.SegmentEndBehavior.ASK_EACH_TIME,
            defaultTimerPlanId = null,
        )
    }
}

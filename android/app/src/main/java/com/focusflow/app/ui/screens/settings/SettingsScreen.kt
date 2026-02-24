package com.focusflow.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.focusflow.app.data.prefs.SettingsDataStore
import com.focusflow.app.ui.theme.ThemeMode

/**
 * Settings screen:
 * - Theme selector (system/light/dark)
 * - Notification toggles: sound, vibration, silent, respect DND
 * - Timer behavior: max pause cap, segment end behavior
 * - Classic defaults (UI only in this minimal version)
 * - Custom sequence editor entry (stub)
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState(initial = SettingsUiState.defaults())

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Theme", style = MaterialTheme.typography.titleMedium)

                ThemeRadioRow(
                    label = "System",
                    selected = uiState.themeMode == ThemeMode.SYSTEM,
                    onSelect = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                )
                ThemeRadioRow(
                    label = "Light",
                    selected = uiState.themeMode == ThemeMode.LIGHT,
                    onSelect = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                )
                ThemeRadioRow(
                    label = "Dark",
                    selected = uiState.themeMode == ThemeMode.DARK,
                    onSelect = { viewModel.setThemeMode(ThemeMode.DARK) },
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Notifications", style = MaterialTheme.typography.titleMedium)

                SettingsSwitchRow(
                    title = "Silent notifications",
                    subtitle = "No sound or vibration regardless of other toggles.",
                    checked = uiState.silentNotifications,
                    onCheckedChange = { viewModel.setSilentNotifications(it) },
                )

                SettingsSwitchRow(
                    title = "Sound",
                    subtitle = "Play sound on segment transitions.",
                    checked = uiState.soundEnabled,
                    enabled = !uiState.silentNotifications,
                    onCheckedChange = { viewModel.setSoundEnabled(it) },
                )

                SettingsSwitchRow(
                    title = "Vibration",
                    subtitle = "Vibrate on segment transitions.",
                    checked = uiState.vibrationEnabled,
                    enabled = !uiState.silentNotifications,
                    onCheckedChange = { viewModel.setVibrationEnabled(it) },
                )

                SettingsSwitchRow(
                    title = "Respect Do Not Disturb (DND)",
                    subtitle = "When enabled, FocusFlow avoids sound/vibration while device is in DND.",
                    checked = uiState.respectDnd,
                    onCheckedChange = { viewModel.setRespectDnd(it) },
                )

                Text(
                    text = "Note: DND behavior is best-effort and depends on device/OS policy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Timer", style = MaterialTheme.typography.titleMedium)

                HorizontalDivider()

                Text(text = "End of segment behavior", style = MaterialTheme.typography.bodyLarge)

                BehaviorRadioRow(
                    label = "Auto-advance",
                    selected = uiState.segmentEndBehavior == SettingsDataStore.SegmentEndBehavior.AUTO_ADVANCE,
                    onSelect = { viewModel.setSegmentEndBehavior(SettingsDataStore.SegmentEndBehavior.AUTO_ADVANCE) },
                )
                BehaviorRadioRow(
                    label = "Prompt",
                    selected = uiState.segmentEndBehavior == SettingsDataStore.SegmentEndBehavior.PROMPT,
                    onSelect = { viewModel.setSegmentEndBehavior(SettingsDataStore.SegmentEndBehavior.PROMPT) },
                )
                BehaviorRadioRow(
                    label = "Ask each time",
                    selected = uiState.segmentEndBehavior == SettingsDataStore.SegmentEndBehavior.ASK_EACH_TIME,
                    onSelect = { viewModel.setSegmentEndBehavior(SettingsDataStore.SegmentEndBehavior.ASK_EACH_TIME) },
                )

                HorizontalDivider()

                MaxPauseCapEditor(
                    valueMinutes = uiState.maxTotalPausedMinutes,
                    onSave = { viewModel.setMaxTotalPausedMinutes(it) },
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Classic defaults", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Classic defaults are 25/5/15 with a long break after 4 work sessions. " +
                        "Advanced per-plan editing can be added later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Minimal v1: informational only.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Work")
                    Text(text = "25 min")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Break")
                    Text(text = "5 min")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Long break")
                    Text(text = "15 min")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Long break cadence")
                    Text(text = "Every 4 work sessions")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Advanced", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Custom sequence editor is a stub in this minimal version. " +
                        "TimerEngine supports custom sequences; UI editor can be built on top.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedButton(
                    onClick = { viewModel.openCustomSequenceEditor() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open custom sequence editor")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ThemeRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label)
    }
}

@Composable
private fun BehaviorRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun MaxPauseCapEditor(
    valueMinutes: Int,
    onSave: (Int) -> Unit,
) {
    var text by remember(valueMinutes) { mutableStateOf(valueMinutes.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Max total paused minutes", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "Caps total paused time per running segment. When exceeded, TimerEngine will stop/force resume per rules.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = text,
            onValueChange = {
                text = it
                error = null
            },
            label = { Text("Minutes (>= 0)") },
            // keyboardOptions removed for compatibility; numeric keyboard is best-effort via input method.
            // (TextField remains functional even without explicit keyboard options.)
            singleLine = true,
            isError = error != null,
            supportingText = {
                if (error != null) Text(text = error!!)
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val parsed = text.trim().toIntOrNull()
                    if (parsed == null || parsed < 0) {
                        error = "Enter a non-negative integer."
                    } else {
                        onSave(parsed)
                        error = null
                    }
                },
            ) {
                Text("Save")
            }

            OutlinedButton(
                onClick = {
                    text = valueMinutes.toString()
                    error = null
                },
            ) {
                Text("Reset")
            }
        }
    }
}

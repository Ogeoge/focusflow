package com.focusflow.app.ui.screens.timer

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focusflow.app.domain.model.SessionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    var showSegmentEndDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.showEndPrompt) {
        if (uiState.showEndPrompt) {
            showSegmentEndDialog = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopAppBar(title = { Text("Timer") })

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = uiState.currentType.toDisplayLabel(),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatRemaining(uiState.remainingMs),
                    fontWeight = FontWeight.Bold,
                )

                Text(text = "Plan: ${uiState.plan.name}")

                val linkedTitle = uiState.selectedTaskId?.let { selectedId ->
                    uiState.availableTasks.firstOrNull { it.id == selectedId }?.title
                }
                Text(text = "Task: ${linkedTitle ?: "none"}")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val startEnabled = !uiState.isRunning && !uiState.isPaused
                    val pauseEnabled = uiState.isRunning && !uiState.isPaused
                    val resumeEnabled = uiState.isPaused
                    val stopEnabled = uiState.isRunning || uiState.isPaused

                    Button(
                        enabled = startEnabled,
                        onClick = { viewModel.start() },
                    ) {
                        Text("Start")
                    }
                    OutlinedButton(
                        enabled = pauseEnabled,
                        onClick = { viewModel.pause() },
                    ) {
                        Text("Pause")
                    }
                    Button(
                        enabled = resumeEnabled,
                        onClick = { viewModel.resume() },
                    ) {
                        Text("Resume")
                    }
                    OutlinedButton(
                        enabled = stopEnabled,
                        onClick = { viewModel.stop() },
                    ) {
                        Text("Stop")
                    }
                }

                TaskPickerSection(
                    tasks = uiState.availableTasks,
                    selectedTaskId = uiState.selectedTaskId,
                    onSelectTaskId = { viewModel.selectTask(it) },
                    enabled = !uiState.isRunning && !uiState.isPaused,
                )

                if (uiState.message != null) {
                    Text(text = "Info: ${uiState.message}")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("End-of-segment behavior: ${uiState.segmentEndBehaviorLabel}")
                Text("Max total paused minutes: ${uiState.maxTotalPausedMinutes}")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showSegmentEndDialog) {
        AlertDialog(
            onDismissRequest = {
                // ask_each_time must be explicitly acknowledged.
                // For other modes we allow dismiss.
                if (uiState.dismissAllowedForEndPrompt) {
                    showSegmentEndDialog = false
                    viewModel.dismissEndOfSegmentPrompt()
                }
            },
            title = { Text("Segment complete") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${uiState.currentType.toDisplayLabel()} finished.")
                    Text("Start next segment?")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSegmentEndDialog = false
                        viewModel.startNextSegmentFromPrompt()
                    },
                ) {
                    Text("Start next")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSegmentEndDialog = false
                        viewModel.stopFromPrompt()
                    },
                ) {
                    Text("Stop")
                }
            },
        )
    }
}

@Composable
private fun TaskPickerSection(
    tasks: List<TimerViewModel.TaskOption>,
    selectedTaskId: String?,
    onSelectTaskId: (String?) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Link task (optional)")

        var expanded by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                enabled = enabled,
                onClick = { expanded = true },
            ) {
                val label = tasks.firstOrNull { it.id == selectedTaskId }?.title ?: "None"
                Text(label)
            }

            if (selectedTaskId != null) {
                TextButton(
                    enabled = enabled,
                    onClick = { onSelectTaskId(null) },
                ) {
                    Text("Clear")
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = {
                        expanded = false
                        onSelectTaskId(null)
                    },
                    enabled = enabled,
                )
                tasks.forEach { task ->
                    DropdownMenuItem(
                        text = { Text(task.title) },
                        onClick = {
                            expanded = false
                            onSelectTaskId(task.id)
                        },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

private fun SessionType.toDisplayLabel(): String = when (this) {
    SessionType.WORK -> "Work"
    SessionType.BREAK -> "Break"
    SessionType.LONG_BREAK -> "Long break"
}

private fun formatRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

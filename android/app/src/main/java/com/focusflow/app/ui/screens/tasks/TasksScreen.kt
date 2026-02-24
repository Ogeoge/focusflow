package com.focusflow.app.ui.screens.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.focusflow.app.domain.model.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    modifier: Modifier = Modifier,
) {
    val tasks by viewModel.tasks.collectAsState(initial = emptyList())

    var editorOpen by remember { mutableStateOf(false) }
    var editingTaskId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                actions = {
                    Button(
                        onClick = {
                            editingTaskId = null
                            editorOpen = true
                        },
                    ) {
                        Text("Add")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (tasks.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No tasks yet",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add a task and track pomodoro estimates and progress.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            editingTaskId = null
                            editorOpen = true
                        },
                    ) {
                        Text("Create first task")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onToggleCompleted = { /* TODO: wire to viewModel */ },
                            onIncrementCompletedPomodoros = { /* TODO: wire to viewModel */ },
                            onClick = {
                                editingTaskId = task.id
                                editorOpen = true
                            },
                            onDelete = { /* TODO: wire to viewModel */ },
                        )
                    }
                }
            }
        }

        if (editorOpen) {
            val taskToEdit = tasks.firstOrNull { it.id == editingTaskId }
            TaskEditorDialog(
                task = taskToEdit,
                onDismiss = { editorOpen = false },
                onSave = { title, notes, estimate ->
                    if (taskToEdit == null) {
                        viewModel.addTask(title = title, notes = notes, estimatePomodoros = estimate)
                    } else {
                        viewModel.updateTask(id = taskToEdit.id, title = title, notes = notes, estimatePomodoros = estimate)
                    }
                    editorOpen = false
                },
            )
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    onToggleCompleted: (Boolean) -> Unit,
    onIncrementCompletedPomodoros: (Int) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = { onToggleCompleted(it) },
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!task.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = task.notes!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Pomodoros: ${task.completedPomodoros} / ${task.estimatePomodoros}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = { onIncrementCompletedPomodoros(-1) },
                        enabled = task.completedPomodoros > 0,
                    ) {
                        Text("-")
                    }
                    Spacer(modifier = Modifier.height(0.dp).padding(horizontal = 6.dp))
                    FilledTonalButton(
                        onClick = { onIncrementCompletedPomodoros(+1) },
                        enabled = !task.isCompleted,
                    ) {
                        Text("+")
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskEditorDialog(
    task: Task?,
    onDismiss: () -> Unit,
    onSave: (title: String, notes: String?, estimatePomodoros: Int) -> Unit,
) {
    var title by remember(task?.id) { mutableStateOf(task?.title ?: "") }
    var notes by remember(task?.id) { mutableStateOf(task?.notes ?: "") }
    var estimateText by remember(task?.id) { mutableStateOf(task?.estimatePomodoros?.toString() ?: "0") }

    val parsedEstimate = estimateText.toIntOrNull() ?: 0
    val safeEstimate = parsedEstimate.coerceAtLeast(0)

    val canSave = title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (task == null) "New task" else "Edit task")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    minLines = 2,
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = estimateText,
                    onValueChange = { estimateText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Estimate (pomodoros)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text("Must be >= 0")
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(title.trim(), notes.trim().ifBlank { "" }.let { it.ifBlank { "" } }.ifBlank { "" }.let { if (it.isBlank()) null else it }, safeEstimate)
                },
                enabled = canSave,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

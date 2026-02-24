package com.focusflow.app.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.app.AppServices
import com.focusflow.app.data.db.entities.TaskEntity
import com.focusflow.app.domain.model.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel : ViewModel() {

    private val taskDao = AppServices.db.taskDao()

    val tasks: StateFlow<List<Task>> = taskDao
        .observeAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }

    fun addTask(
        title: String,
        notes: String?,
        estimatePomodoros: Int,
    ) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Title is required")
            return
        }
        if (estimatePomodoros < 0) {
            _uiState.value = _uiState.value.copy(error = "Estimate must be >= 0")
            return
        }

        val now = System.currentTimeMillis()
        val domain = Task.new(
            title = trimmedTitle,
            notes = notes?.trim()?.takeIf { it.isNotBlank() },
            estimatePomodoros = estimatePomodoros,
            nowEpochMs = now,
        )

        viewModelScope.launch {
            runCatching {
                taskDao.insert(TaskEntity.fromDomain(domain))
            }.onSuccess {
                _uiState.value = _uiState.value.copy(message = "Task added")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to add task")
            }
        }
    }

    fun updateTask(
        id: String,
        title: String,
        notes: String?,
        estimatePomodoros: Int,
    ) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Title is required")
            return
        }
        if (estimatePomodoros < 0) {
            _uiState.value = _uiState.value.copy(error = "Estimate must be >= 0")
            return
        }

        val now = System.currentTimeMillis()
        val normalizedNotes = notes?.trim()?.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            runCatching {
                val existing = taskDao.getById(id) ?: return@runCatching
                taskDao.updateTitleAndNotes(id = id, title = trimmedTitle, notes = normalizedNotes, nowEpochMs = now)
                taskDao.setEstimatePomodoros(id = id, estimatePomodoros = estimatePomodoros, nowEpochMs = now)

                // Ensure completion flag is consistent with progress (simple rule: completed if completed_pomodoros >= estimate and estimate>0)
                val newCompleted = existing.completedPomodoros >= estimatePomodoros && estimatePomodoros > 0
                taskDao.setCompleted(id = id, isCompleted = newCompleted, nowEpochMs = now)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(message = "Task updated")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to update task")
            }
        }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch {
            runCatching {
                taskDao.deleteById(id)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(message = "Task deleted")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to delete task")
            }
        }
    }

    fun toggleCompleted(id: String, completed: Boolean) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            runCatching {
                taskDao.setCompleted(id = id, isCompleted = completed, nowEpochMs = now)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to update completion")
            }
        }
    }

    fun adjustCompletedPomodoros(id: String, delta: Int) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            runCatching {
                taskDao.incrementCompletedPomodoros(id = id, delta = delta, nowEpochMs = now)

                // Keep is_completed in sync with progress.
                val updated = taskDao.getById(id) ?: return@runCatching
                val shouldBeCompleted = updated.estimatePomodoros > 0 && updated.completedPomodoros >= updated.estimatePomodoros
                if (updated.isCompleted != shouldBeCompleted) {
                    taskDao.setCompleted(id = id, isCompleted = shouldBeCompleted, nowEpochMs = now)
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to update progress")
            }
        }
    }

    fun resetProgress(id: String) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            runCatching {
                taskDao.resetProgress(id = id, nowEpochMs = now)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(message = "Progress reset")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to reset progress")
            }
        }
    }
}

data class TasksUiState(
    val message: String? = null,
    val error: String? = null,
)

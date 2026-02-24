package com.focusflow.app.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.focusflow.app.domain.model.Task

/**
 * Room entity mirroring the contract Task model.
 *
 * Table: tasks
 * Indices:
 * - idx_tasks_is_completed ON tasks(is_completed)
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["is_completed"], name = "idx_tasks_is_completed"),
    ],
)
data class TaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "estimate_pomodoros")
    val estimatePomodoros: Int,

    @ColumnInfo(name = "completed_pomodoros")
    val completedPomodoros: Int,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,

    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,

    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(estimatePomodoros >= 0) { "estimatePomodoros must be >= 0" }
        require(completedPomodoros >= 0) { "completedPomodoros must be >= 0" }
        require(createdAtEpochMs > 0L) { "createdAtEpochMs must be > 0" }
        require(updatedAtEpochMs > 0L) { "updatedAtEpochMs must be > 0" }
    }

    fun toDomain(): Task = Task(
        id = id,
        title = title,
        notes = notes,
        estimatePomodoros = estimatePomodoros,
        completedPomodoros = completedPomodoros,
        isCompleted = isCompleted,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    companion object {
        fun fromDomain(task: Task): TaskEntity = TaskEntity(
            id = task.id,
            title = task.title,
            notes = task.notes,
            estimatePomodoros = task.estimatePomodoros,
            completedPomodoros = task.completedPomodoros,
            isCompleted = task.isCompleted,
            createdAtEpochMs = task.createdAtEpochMs,
            updatedAtEpochMs = task.updatedAtEpochMs,
        )
    }
}

package com.focusflow.app.domain.model

import java.util.UUID

/**
 * Domain model for a local Task.
 *
 * Contract mapping:
 * - id -> id (uuid string)
 * - title -> title
 * - notes -> notes (optional)
 * - estimatePomodoros -> estimate_pomodoros
 * - completedPomodoros -> completed_pomodoros
 * - isCompleted -> is_completed
 * - createdAtEpochMs -> created_at_epoch_ms
 * - updatedAtEpochMs -> updated_at_epoch_ms
 */
data class Task(
    val id: String,
    val title: String,
    val notes: String?,
    val estimatePomodoros: Int,
    val completedPomodoros: Int,
    val isCompleted: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(estimatePomodoros >= 0) { "estimatePomodoros must be >= 0" }
        require(completedPomodoros >= 0) { "completedPomodoros must be >= 0" }
        require(createdAtEpochMs > 0L) { "createdAtEpochMs must be > 0" }
        require(updatedAtEpochMs > 0L) { "updatedAtEpochMs must be > 0" }
    }

    fun withUpdatedTitle(newTitle: String, nowEpochMs: Long): Task =
        copy(title = newTitle, updatedAtEpochMs = nowEpochMs)

    fun withUpdatedNotes(newNotes: String?, nowEpochMs: Long): Task =
        copy(notes = newNotes, updatedAtEpochMs = nowEpochMs)

    fun withEstimate(newEstimatePomodoros: Int, nowEpochMs: Long): Task {
        require(newEstimatePomodoros >= 0) { "estimatePomodoros must be >= 0" }
        return copy(estimatePomodoros = newEstimatePomodoros, updatedAtEpochMs = nowEpochMs)
    }

    fun incrementCompletedPomodoros(nowEpochMs: Long, delta: Int = 1): Task {
        require(delta >= 0) { "delta must be >= 0" }
        return copy(
            completedPomodoros = completedPomodoros + delta,
            updatedAtEpochMs = nowEpochMs,
        )
    }

    fun markCompleted(completed: Boolean, nowEpochMs: Long): Task =
        copy(isCompleted = completed, updatedAtEpochMs = nowEpochMs)

    companion object {
        fun new(
            title: String,
            notes: String? = null,
            estimatePomodoros: Int = 0,
            nowEpochMs: Long,
            id: String = UUID.randomUUID().toString(),
        ): Task {
            require(title.isNotBlank()) { "title must not be blank" }
            require(estimatePomodoros >= 0) { "estimatePomodoros must be >= 0" }
            require(nowEpochMs > 0L) { "nowEpochMs must be > 0" }

            return Task(
                id = id,
                title = title,
                notes = notes,
                estimatePomodoros = estimatePomodoros,
                completedPomodoros = 0,
                isCompleted = false,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
            )
        }
    }
}

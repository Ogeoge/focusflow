package com.focusflow.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focusflow.app.data.db.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for Task CRUD.
 *
 * Contract table: tasks
 * Columns:
 * - id (TEXT PK)
 * - title (TEXT)
 * - notes (TEXT nullable)
 * - estimate_pomodoros (INTEGER)
 * - completed_pomodoros (INTEGER)
 * - is_completed (INTEGER as boolean)
 * - created_at_epoch_ms (INTEGER)
 * - updated_at_epoch_ms (INTEGER)
 */
@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY is_completed ASC, updated_at_epoch_ms DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE tasks SET is_completed = :isCompleted, updated_at_epoch_ms = :nowEpochMs WHERE id = :id")
    suspend fun setCompleted(id: String, isCompleted: Boolean, nowEpochMs: Long)

    @Query(
        "UPDATE tasks SET completed_pomodoros = CASE " +
            "WHEN (completed_pomodoros + :delta) < 0 THEN 0 " +
            "ELSE (completed_pomodoros + :delta) END, " +
            "updated_at_epoch_ms = :nowEpochMs " +
            "WHERE id = :id"
    )
    suspend fun incrementCompletedPomodoros(id: String, delta: Int, nowEpochMs: Long)

    @Query(
        "UPDATE tasks SET estimate_pomodoros = CASE " +
            "WHEN :estimatePomodoros < 0 THEN 0 " +
            "ELSE :estimatePomodoros END, " +
            "updated_at_epoch_ms = :nowEpochMs " +
            "WHERE id = :id"
    )
    suspend fun setEstimatePomodoros(id: String, estimatePomodoros: Int, nowEpochMs: Long)

    @Query(
        "UPDATE tasks SET title = :title, notes = :notes, updated_at_epoch_ms = :nowEpochMs " +
            "WHERE id = :id"
    )
    suspend fun updateTitleAndNotes(id: String, title: String, notes: String?, nowEpochMs: Long)

    @Query("UPDATE tasks SET completed_pomodoros = 0, is_completed = 0, updated_at_epoch_ms = :nowEpochMs WHERE id = :id")
    suspend fun resetProgress(id: String, nowEpochMs: Long)
}

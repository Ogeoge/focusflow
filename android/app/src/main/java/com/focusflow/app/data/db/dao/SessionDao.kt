package com.focusflow.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusflow.app.data.db.entities.SessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for Session persistence and range queries.
 *
 * Contract table: sessions
 * Columns:
 * - id (TEXT PK)
 * - type (TEXT; one of: work|break|long_break)
 * - start_epoch_ms (INTEGER)
 * - end_epoch_ms (INTEGER)
 * - duration_ms (INTEGER; must equal end - start and > 0)
 * - linked_task_id (TEXT nullable)
 * - plan_label (TEXT nullable)
 */
@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Query("SELECT * FROM sessions ORDER BY start_epoch_ms DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY start_epoch_ms DESC")
    suspend fun getAll(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionEntity?

    /**
     * Inclusive start, exclusive end: [startEpochMsInclusive, endEpochMsExclusive)
     */
    @Query(
        "SELECT * FROM sessions " +
            "WHERE start_epoch_ms >= :startEpochMsInclusive AND start_epoch_ms < :endEpochMsExclusive " +
            "ORDER BY start_epoch_ms ASC"
    )
    suspend fun getInStartRange(
        startEpochMsInclusive: Long,
        endEpochMsExclusive: Long,
    ): List<SessionEntity>

    /**
     * Inclusive start, exclusive end: [startEpochMsInclusive, endEpochMsExclusive)
     */
    @Query(
        "SELECT * FROM sessions " +
            "WHERE start_epoch_ms >= :startEpochMsInclusive AND start_epoch_ms < :endEpochMsExclusive " +
            "ORDER BY start_epoch_ms ASC"
    )
    fun observeInStartRange(
        startEpochMsInclusive: Long,
        endEpochMsExclusive: Long,
    ): Flow<List<SessionEntity>>

    @Query(
        "SELECT * FROM sessions " +
            "WHERE type = :type " +
            "ORDER BY start_epoch_ms DESC"
    )
    suspend fun getByType(type: String): List<SessionEntity>

    @Query(
        "SELECT * FROM sessions " +
            "WHERE linked_task_id = :taskId " +
            "ORDER BY start_epoch_ms DESC"
    )
    suspend fun getByLinkedTaskId(taskId: String): List<SessionEntity>

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}

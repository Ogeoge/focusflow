package com.focusflow.app.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.focusflow.app.domain.model.Session
import com.focusflow.app.domain.model.SessionType

/**
 * Room entity mirroring the contract Session model.
 *
 * Table: sessions
 *
 * Contract fields:
 * - id
 * - type (work|break|long_break)
 * - start_epoch_ms
 * - end_epoch_ms
 * - duration_ms (must equal end - start and be > 0)
 * - linked_task_id (nullable; v2)
 * - plan_label (nullable)
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_task_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["start_epoch_ms"], name = "idx_sessions_start_epoch_ms"),
        Index(value = ["type"], name = "idx_sessions_type"),
        Index(value = ["linked_task_id"], name = "idx_sessions_linked_task_id"),
    ],
)
data class SessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** Stored as contract string: work|break|long_break */
    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "start_epoch_ms")
    val startEpochMs: Long,

    @ColumnInfo(name = "end_epoch_ms")
    val endEpochMs: Long,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "linked_task_id")
    val linkedTaskId: String?,

    @ColumnInfo(name = "plan_label")
    val planLabel: String?,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(type in setOf("work", "break", "long_break")) { "type must be one of work|break|long_break" }
        require(startEpochMs > 0L) { "startEpochMs must be > 0" }
        require(endEpochMs > 0L) { "endEpochMs must be > 0" }
        require(endEpochMs > startEpochMs) { "endEpochMs must be > startEpochMs" }
        require(durationMs > 0L) { "durationMs must be > 0" }
        require(durationMs == (endEpochMs - startEpochMs)) { "durationMs must equal endEpochMs - startEpochMs" }
        if (linkedTaskId != null) require(linkedTaskId.isNotBlank()) { "linkedTaskId must be blank or null" }
        if (planLabel != null) require(planLabel.isNotBlank()) { "planLabel must be blank or null" }
    }

    fun toDomain(): Session = Session(
        id = id,
        type = SessionType.fromContractValue(type),
        startEpochMs = startEpochMs,
        endEpochMs = endEpochMs,
        durationMs = durationMs,
        linkedTaskId = linkedTaskId,
        planLabel = planLabel,
    )

    companion object {
        fun fromDomain(session: Session): SessionEntity = SessionEntity(
            id = session.id,
            type = session.type.toContractValue(),
            startEpochMs = session.startEpochMs,
            endEpochMs = session.endEpochMs,
            durationMs = session.durationMs,
            linkedTaskId = session.linkedTaskId,
            planLabel = session.planLabel,
        )
    }
}

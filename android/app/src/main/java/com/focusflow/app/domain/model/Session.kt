package com.focusflow.app.domain.model

import java.util.UUID

/**
 * Domain model for a completed timer segment/session.
 *
 * Contract mapping:
 * - id -> id (uuid string)
 * - type -> type (enum string: work|break|long_break)
 * - startEpochMs -> start_epoch_ms
 * - endEpochMs -> end_epoch_ms
 * - durationMs -> duration_ms (must equal endEpochMs - startEpochMs and be > 0)
 * - linkedTaskId -> linked_task_id (optional uuid string)
 * - planLabel -> plan_label (optional string)
 */
data class Session(
    val id: String,
    val type: SessionType,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val durationMs: Long,
    val linkedTaskId: String?,
    val planLabel: String?,
) {
    init {
        require(startEpochMs > 0L) { "startEpochMs must be > 0" }
        require(endEpochMs > 0L) { "endEpochMs must be > 0" }
        require(endEpochMs > startEpochMs) { "endEpochMs must be > startEpochMs" }
        require(durationMs > 0L) { "durationMs must be > 0" }
        require(durationMs == (endEpochMs - startEpochMs)) {
            "durationMs must equal endEpochMs - startEpochMs"
        }
        if (planLabel != null) {
            require(planLabel.isNotBlank()) { "planLabel must be blank or null" }
        }
        if (linkedTaskId != null) {
            require(linkedTaskId.isNotBlank()) { "linkedTaskId must be blank or null" }
        }
    }

    companion object {
        fun completed(
            type: SessionType,
            startEpochMs: Long,
            endEpochMs: Long,
            linkedTaskId: String? = null,
            planLabel: String? = null,
            id: String = UUID.randomUUID().toString(),
        ): Session {
            val duration = endEpochMs - startEpochMs
            require(duration > 0L) { "Session duration must be > 0" }
            return Session(
                id = id,
                type = type,
                startEpochMs = startEpochMs,
                endEpochMs = endEpochMs,
                durationMs = duration,
                linkedTaskId = linkedTaskId,
                planLabel = planLabel,
            )
        }
    }
}

enum class SessionType {
    WORK,
    BREAK,
    LONG_BREAK,
    ;

    fun toContractValue(): String = when (this) {
        WORK -> "work"
        BREAK -> "break"
        LONG_BREAK -> "long_break"
    }

    companion object {
        fun fromContractValue(value: String): SessionType = when (value) {
            "work" -> WORK
            "break" -> BREAK
            "long_break" -> LONG_BREAK
            else -> throw IllegalArgumentException("Unknown session type: $value")
        }
    }
}

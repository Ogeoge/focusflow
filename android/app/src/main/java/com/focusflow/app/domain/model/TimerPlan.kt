package com.focusflow.app.domain.model

import java.util.UUID

/**
 * Timer configuration models.
 *
 * Contract mapping (Android local model):
 * - TimerPlan.id -> id (uuid string)
 * - TimerPlan.name -> name
 * - TimerPlan.mode -> mode (classic|custom_sequence)
 * - classicWorkMinutes -> classic_work_minutes
 * - classicBreakMinutes -> classic_break_minutes
 * - classicLongBreakMinutes -> classic_long_break_minutes
 * - classicLongBreakEveryWorkSessions -> classic_long_break_every_work_sessions
 * - segments -> segments (array<TimerSegment>)
 */
data class TimerPlan(
    val id: String,
    val name: String,
    val mode: TimerPlanMode,
    val classicWorkMinutes: Int?,
    val classicBreakMinutes: Int?,
    val classicLongBreakMinutes: Int?,
    val classicLongBreakEveryWorkSessions: Int?,
    val segments: List<TimerSegment>?,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }

        when (mode) {
            TimerPlanMode.CLASSIC -> {
                require(classicWorkMinutes != null && classicWorkMinutes >= 1) {
                    "classicWorkMinutes must be provided and >= 1 for classic mode"
                }
                require(classicBreakMinutes != null && classicBreakMinutes >= 1) {
                    "classicBreakMinutes must be provided and >= 1 for classic mode"
                }
                require(classicLongBreakMinutes != null && classicLongBreakMinutes >= 1) {
                    "classicLongBreakMinutes must be provided and >= 1 for classic mode"
                }
                require(classicLongBreakEveryWorkSessions != null && classicLongBreakEveryWorkSessions >= 1) {
                    "classicLongBreakEveryWorkSessions must be provided and >= 1 for classic mode"
                }
            }

            TimerPlanMode.CUSTOM_SEQUENCE -> {
                require(!segments.isNullOrEmpty()) {
                    "segments must be provided and non-empty for custom_sequence mode"
                }
            }
        }

        segments?.forEach { seg ->
            require(seg.durationMinutes >= 1) { "segment durationMinutes must be >= 1" }
        }
    }

    fun labelForAnalytics(): String = name

    companion object {
        /**
         * Classic Pomodoro defaults per contract invariant:
         * 25/5/15 with long break after 4 work sessions.
         */
        fun classicDefault(
            name: String = "Classic",
            id: String = UUID.randomUUID().toString(),
            workMinutes: Int = 25,
            breakMinutes: Int = 5,
            longBreakMinutes: Int = 15,
            longBreakEveryWorkSessions: Int = 4,
        ): TimerPlan {
            return TimerPlan(
                id = id,
                name = name,
                mode = TimerPlanMode.CLASSIC,
                classicWorkMinutes = workMinutes,
                classicBreakMinutes = breakMinutes,
                classicLongBreakMinutes = longBreakMinutes,
                classicLongBreakEveryWorkSessions = longBreakEveryWorkSessions,
                segments = null,
            )
        }

        fun customSequence(
            name: String,
            segments: List<TimerSegment>,
            id: String = UUID.randomUUID().toString(),
        ): TimerPlan {
            require(name.isNotBlank()) { "name must not be blank" }
            require(segments.isNotEmpty()) { "segments must not be empty" }
            return TimerPlan(
                id = id,
                name = name,
                mode = TimerPlanMode.CUSTOM_SEQUENCE,
                classicWorkMinutes = null,
                classicBreakMinutes = null,
                classicLongBreakMinutes = null,
                classicLongBreakEveryWorkSessions = null,
                segments = segments,
            )
        }
    }
}

enum class TimerPlanMode {
    CLASSIC,
    CUSTOM_SEQUENCE,
    ;

    fun toContractValue(): String = when (this) {
        CLASSIC -> "classic"
        CUSTOM_SEQUENCE -> "custom_sequence"
    }

    companion object {
        fun fromContractValue(value: String): TimerPlanMode = when (value) {
            "classic" -> CLASSIC
            "custom_sequence" -> CUSTOM_SEQUENCE
            else -> throw IllegalArgumentException("Unknown TimerPlanMode: $value")
        }
    }
}

/**
 * One segment in a custom timer sequence.
 *
 * Contract mapping:
 * - type -> type (work|break|long_break)
 * - durationMinutes -> duration_minutes
 */
data class TimerSegment(
    val type: SessionType,
    val durationMinutes: Int,
) {
    init {
        require(durationMinutes >= 1) { "durationMinutes must be >= 1" }
    }
}

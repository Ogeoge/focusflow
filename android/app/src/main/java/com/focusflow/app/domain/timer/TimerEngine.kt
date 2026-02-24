package com.focusflow.app.domain.timer

import com.focusflow.app.domain.model.SessionType
import com.focusflow.app.domain.model.TimerPlan
import com.focusflow.app.domain.model.TimerPlanMode
import com.focusflow.app.domain.model.TimerSegment
import kotlin.math.max

/**
 * Pure Kotlin timer rules engine.
 *
 * Goals:
 * - Deterministic, testable state transitions (no Android dependencies).
 * - Enforces contract invariants:
 *   - Segment types strictly one of: work, break, long_break (represented by [SessionType]).
 *   - Pause cap: total paused time per active segment must not exceed maxTotalPausedMinutes.
 * - Supports:
 *   - Classic Pomodoro (25/5/15, long break after N work sessions) via [TimerPlan] classic mode.
 *   - Custom sequences (list of segments) via [TimerPlan] custom_sequence mode.
 *   - Pause/resume with cap, stop, and segment completion.
 *
 * IMPORTANT:
 * - A "Session" record must be persisted only for COMPLETED segments. This engine emits [Event.SegmentCompleted]
 *   with start/end timestamps so the caller can persist to Room and ensure duration_ms = end-start (>0).
 */
class TimerEngine(
    initialPlan: TimerPlan,
    initialSettings: Settings,
) {

    data class Settings(
        val maxTotalPausedMinutes: Int,
        val segmentEndBehavior: SegmentEndBehavior,
    ) {
        init {
            require(maxTotalPausedMinutes >= 0) { "maxTotalPausedMinutes must be >= 0" }
        }

        fun withMaxPausedMinutes(minutes: Int): Settings {
            require(minutes >= 0) { "maxTotalPausedMinutes must be >= 0" }
            return copy(maxTotalPausedMinutes = minutes)
        }

        fun withEndBehavior(behavior: SegmentEndBehavior): Settings = copy(segmentEndBehavior = behavior)
    }

    enum class SegmentEndBehavior {
        AUTO_ADVANCE,
        PROMPT,
        ASK_EACH_TIME,
        ;

        fun toContractValue(): String = when (this) {
            AUTO_ADVANCE -> "auto_advance"
            PROMPT -> "prompt"
            ASK_EACH_TIME -> "ask_each_time"
        }

        companion object {
            fun fromContractValue(value: String): SegmentEndBehavior = when (value) {
                "auto_advance" -> AUTO_ADVANCE
                "prompt" -> PROMPT
                "ask_each_time" -> ASK_EACH_TIME
                else -> throw IllegalArgumentException("Unknown SegmentEndBehavior: $value")
            }
        }
    }

    sealed class Event {
        /** Engine started a segment. */
        data class SegmentStarted(
            val segment: PlannedSegment,
            val startedAtEpochMs: Long,
        ) : Event()

        /** Engine paused the running segment. */
        data class Paused(
            val pausedAtEpochMs: Long,
            val totalPausedMs: Long,
            val remainingMs: Long,
        ) : Event()

        /** Engine resumed the running segment. */
        data class Resumed(
            val resumedAtEpochMs: Long,
            val totalPausedMs: Long,
            val remainingMs: Long,
        ) : Event()

        /** Total paused time exceeded cap; engine forced stop. */
        data class PauseCapExceeded(
            val capMs: Long,
            val totalPausedMs: Long,
        ) : Event()

        /** Segment completed (caller should persist as Session). */
        data class SegmentCompleted(
            val segment: PlannedSegment,
            val startEpochMs: Long,
            val endEpochMs: Long,
            val durationMs: Long,
        ) : Event()

        /** Segment ended and engine requires user decision to continue. */
        data class NeedsUserDecisionForNext(
            val nextSegment: PlannedSegment?,
        ) : Event()

        /** Engine advanced to next segment automatically. */
        data class AdvancedToNext(
            val nextSegment: PlannedSegment,
            val startedAtEpochMs: Long,
        ) : Event()

        /** Engine stopped; no active segment. */
        data class Stopped(
            val stoppedAtEpochMs: Long,
        ) : Event()
    }

    data class PlannedSegment(
        val type: SessionType,
        val durationMs: Long,
        val indexInPlan: Int,
        val planId: String,
        val planLabel: String,
        val linkedTaskId: String?,
    ) {
        init {
            require(durationMs > 0) { "durationMs must be > 0" }
            if (linkedTaskId != null) require(linkedTaskId.isNotBlank()) { "linkedTaskId must be blank or null" }
            require(planId.isNotBlank()) { "planId must not be blank" }
            require(planLabel.isNotBlank()) { "planLabel must not be blank" }
        }
    }

    data class State(
        val plan: TimerPlan,
        val settings: Settings,
        val status: Status,
        val currentSegment: PlannedSegment?,
        val segmentStartEpochMs: Long?,
        val remainingMs: Long,
        val totalPausedMs: Long,
        val lastPausedAtEpochMs: Long?,
        val classicWorkSessionsCompletedSinceLongBreak: Int,
        val needsDecisionForNext: Boolean,
        val pendingNextSegment: PlannedSegment?,
        val linkedTaskId: String?,
    )

    enum class Status {
        IDLE,
        RUNNING,
        PAUSED,
        AWAITING_NEXT_DECISION,
    }

    private var state: State = State(
        plan = initialPlan,
        settings = initialSettings,
        status = Status.IDLE,
        currentSegment = null,
        segmentStartEpochMs = null,
        remainingMs = 0L,
        totalPausedMs = 0L,
        lastPausedAtEpochMs = null,
        classicWorkSessionsCompletedSinceLongBreak = 0,
        needsDecisionForNext = false,
        pendingNextSegment = null,
        linkedTaskId = null,
    )

    fun snapshot(): State = state

    fun updateSettings(newSettings: Settings) {
        state = state.copy(settings = newSettings)
    }

    fun updatePlan(newPlan: TimerPlan) {
        // Do not mutate an in-flight segment; caller should stop first if needed.
        state = state.copy(plan = newPlan)
    }

    /**
     * Set/clear optional task link for the next started work segment.
     * Caller should also persist this preference if desired.
     */
    fun setLinkedTaskId(linkedTaskId: String?) {
        if (linkedTaskId != null) require(linkedTaskId.isNotBlank()) { "linkedTaskId must be blank or null" }
        state = state.copy(linkedTaskId = linkedTaskId)
    }

    /** Start a new segment (from IDLE), selecting the first segment in the plan. */
    fun start(nowEpochMs: Long): List<Event> {
        require(nowEpochMs > 0L) { "nowEpochMs must be > 0" }
        if (state.status != Status.IDLE) return emptyList()

        val first = computeFirstSegment(plan = state.plan, linkedTaskId = state.linkedTaskId)
        state = state.copy(
            status = Status.RUNNING,
            currentSegment = first,
            segmentStartEpochMs = nowEpochMs,
            remainingMs = first.durationMs,
            totalPausedMs = 0L,
            lastPausedAtEpochMs = null,
            needsDecisionForNext = false,
            pendingNextSegment = null,
        )
        return listOf(Event.SegmentStarted(segment = first, startedAtEpochMs = nowEpochMs))
    }

    /** Stop the current run; no completion is emitted. */
    fun stop(nowEpochMs: Long): List<Event> {
        require(nowEpochMs > 0L) { "nowEpochMs must be > 0" }
        if (state.status == Status.IDLE) return emptyList()

        state = state.copy(
            status = Status.IDLE,
            currentSegment = null,
            segmentStartEpochMs = null,
            remainingMs = 0L,
            totalPausedMs = 0L,
            lastPausedAtEpochMs = null,
            needsDecisionForNext = false,
            pendingNextSegment = null,
        )
        return listOf(Event.Stopped(stoppedAtEpochMs = nowEpochMs))
    }

    /** Pause a running segment. */
    fun pause(nowEpochMs: Long): List<Event> {
        require(nowEpochMs > 0L) { "nowEpochMs must be > 0" }
        if (state.status != Status.RUNNING) return emptyList()

        state = state.copy(
            status = Status.PAUSED,
            lastPausedAtEpochMs = nowEpochMs,
        )
        return listOf(
            Event.Paused(
                pausedAtEpochMs = nowEpochMs,
                totalPausedMs = state.totalPausedMs,
                remainingMs = state.remainingMs,
            ),
        )
    }

    /** Resume; enforces max total paused cap. */
    fun resume(nowEpochMs: Long): List<Event> {
        require(nowEpochMs > 0L) { "nowEpochMs must be > 0" }
        if (state.status != Status.PAUSED) return emptyList()

        val pausedAt = state.lastPausedAtEpochMs ?: return emptyList()
        val pausedDelta = max(0L, nowEpochMs - pausedAt)
        val newTotalPaused = state.totalPausedMs + pausedDelta
        val capMs = state.settings.maxTotalPausedMinutes.toLong() * 60_000L

        if (newTotalPaused > capMs) {
            // Contract invariant: pause cap enforcement must be in this engine.
            val events = mutableListOf<Event>(Event.PauseCapExceeded(capMs = capMs, totalPausedMs = newTotalPaused))
            events += stop(nowEpochMs)
            return events
        }

        state = state.copy(
            status = Status.RUNNING,
            totalPausedMs = newTotalPaused,
            lastPausedAtEpochMs = null,
        )

        return listOf(
            Event.Resumed(
                resumedAtEpochMs = nowEpochMs,
                totalPausedMs = newTotalPaused,
                remainingMs = state.remainingMs,
            ),
        )
    }

    /**
     * Advance time by computing remaining time. Caller provides authoritative now.
     *
     * If remaining reaches 0, completes segment and applies end-of-segment behavior.
     */
    fun tick(nowEpochMs: Long): List<Event> {
        require(nowEpochMs > 0L) { "nowEpochMs must be > 0" }
        if (state.status != Status.RUNNING) return emptyList()

        val start = state.segmentStartEpochMs ?: return emptyList()
        val seg = state.currentSegment ?: return emptyList()

        val elapsedActiveMs = max(0L, nowEpochMs - start - state.totalPausedMs)
        val newRemaining = max(0L, seg.durationMs - elapsedActiveMs)
        if (newRemaining == state.remainingMs) return emptyList()

        state = state.copy(remainingMs = newRemaining)

        if (newRemaining > 0L) return emptyList()

        // Segment complete.
        val end = nowEpochMs
        val durationMs = end - start - state.totalPausedMs
        if (durationMs <= 0L) {
            // Should be impossible with sane inputs; stop safely.
            return stop(nowEpochMs)
        }

        val completedEvent = Event.SegmentCompleted(
            segment = seg,
            startEpochMs = start,
            endEpochMs = end,
            durationMs = durationMs,
        )

        val updatedClassicCounter = updateClassicCounterAfterCompletion(
            plan = state.plan,
            segmentType = seg.type,
            currentCounter = state.classicWorkSessionsCompletedSinceLongBreak,
        )

        // Decide next.
        val next = computeNextSegmentAfter(
            plan = state.plan,
            current = seg,
            classicWorkCounter = updatedClassicCounter,
            linkedTaskIdForNext = state.linkedTaskId,
        )

        state = state.copy(
            classicWorkSessionsCompletedSinceLongBreak = updatedClassicCounter,
        )

        val events = mutableListOf<Event>(completedEvent)

        val needsDecision = when (state.settings.segmentEndBehavior) {
            SegmentEndBehavior.AUTO_ADVANCE -> false
            SegmentEndBehavior.PROMPT -> true
            SegmentEndBehavior.ASK_EACH_TIME -> true
        }

        if (next == null) {
            // No next segment (e.g., end of custom sequence). Stop.
            events += stop(nowEpochMs)
            return events
        }

        if (needsDecision) {
            state = state.copy(
                status = Status.AWAITING_NEXT_DECISION,
                needsDecisionForNext = true,
                pendingNextSegment = next,
                currentSegment = null,
                segmentStartEpochMs = null,
                remainingMs = 0L,
                totalPausedMs = 0L,
                lastPausedAtEpochMs = null,
            )
            events += Event.NeedsUserDecisionForNext(nextSegment = next)
            return events
        }

        // Auto-advance.
        state = state.copy(
            status = Status.RUNNING,
            currentSegment = next,
            segmentStartEpochMs = nowEpochMs,
            remainingMs = next.durationMs,
            totalPausedMs = 0L,
            lastPausedAtEpochMs = null,
            needsDecisionForNext = false,
            pendingNextSegment = null,
        )
        events += Event.AdvancedToNext(nextSegment = next, startedAtEpochMs = nowEpochMs)
        return events
    }

    /**
     * If awaiting user decision, user chose to start the pending next segment.
     */
    fun userStartNext(nowEpochMs: Long): List<Event> {
        require(nowEpochMs > 0L) { "nowEpochMs must be > 0" }
        if (state.status != Status.AWAITING_NEXT_DECISION) return emptyList()
        val next = state.pendingNextSegment ?: return emptyList()

        state = state.copy(
            status = Status.RUNNING,
            currentSegment = next,
            segmentStartEpochMs = nowEpochMs,
            remainingMs = next.durationMs,
            totalPausedMs = 0L,
            lastPausedAtEpochMs = null,
            needsDecisionForNext = false,
            pendingNextSegment = null,
        )

        return listOf(Event.SegmentStarted(segment = next, startedAtEpochMs = nowEpochMs))
    }

    /**
     * If awaiting user decision, user chose to stop.
     */
    fun userStop(nowEpochMs: Long): List<Event> {
        require(nowEpochMs > 0L) { "nowEpochMs must be > 0" }
        if (state.status != Status.AWAITING_NEXT_DECISION) return emptyList()
        return stop(nowEpochMs)
    }

    private fun computeFirstSegment(plan: TimerPlan, linkedTaskId: String?): PlannedSegment {
        return when (plan.mode) {
            TimerPlanMode.CLASSIC -> {
                val minutes = plan.classicWorkMinutes ?: 25
                PlannedSegment(
                    type = SessionType.WORK,
                    durationMs = minutes.toLong() * 60_000L,
                    indexInPlan = 0,
                    planId = plan.id,
                    planLabel = plan.labelForAnalytics(),
                    linkedTaskId = linkedTaskId,
                )
            }

            TimerPlanMode.CUSTOM_SEQUENCE -> {
                val segs = plan.segments ?: emptyList()
                val first = segs.firstOrNull() ?: TimerSegment(SessionType.WORK, 25)
                PlannedSegment(
                    type = first.type,
                    durationMs = first.durationMinutes.toLong() * 60_000L,
                    indexInPlan = 0,
                    planId = plan.id,
                    planLabel = plan.labelForAnalytics(),
                    linkedTaskId = if (first.type == SessionType.WORK) linkedTaskId else null,
                )
            }
        }
    }

    private fun computeNextSegmentAfter(
        plan: TimerPlan,
        current: PlannedSegment,
        classicWorkCounter: Int,
        linkedTaskIdForNext: String?,
    ): PlannedSegment? {
        return when (plan.mode) {
            TimerPlanMode.CLASSIC -> {
                // Classic alternates work and breaks, with long break cadence.
                if (current.type == SessionType.WORK) {
                    val cadence = plan.classicLongBreakEveryWorkSessions ?: 4
                    val isLongBreakNext = (classicWorkCounter % cadence == 0)
                    val minutes = if (isLongBreakNext) (plan.classicLongBreakMinutes ?: 15) else (plan.classicBreakMinutes
                        ?: 5)
                    PlannedSegment(
                        type = if (isLongBreakNext) SessionType.LONG_BREAK else SessionType.BREAK,
                        durationMs = minutes.toLong() * 60_000L,
                        indexInPlan = 0,
                        planId = plan.id,
                        planLabel = plan.labelForAnalytics(),
                        linkedTaskId = null,
                    )
                } else {
                    val minutes = plan.classicWorkMinutes ?: 25
                    PlannedSegment(
                        type = SessionType.WORK,
                        durationMs = minutes.toLong() * 60_000L,
                        indexInPlan = 0,
                        planId = plan.id,
                        planLabel = plan.labelForAnalytics(),
                        linkedTaskId = linkedTaskIdForNext,
                    )
                }
            }

            TimerPlanMode.CUSTOM_SEQUENCE -> {
                val segs = plan.segments ?: return null
                val nextIndex = current.indexInPlan + 1
                val next = segs.getOrNull(nextIndex) ?: return null
                PlannedSegment(
                    type = next.type,
                    durationMs = next.durationMinutes.toLong() * 60_000L,
                    indexInPlan = nextIndex,
                    planId = plan.id,
                    planLabel = plan.labelForAnalytics(),
                    linkedTaskId = if (next.type == SessionType.WORK) linkedTaskIdForNext else null,
                )
            }
        }
    }

    private fun updateClassicCounterAfterCompletion(
        plan: TimerPlan,
        segmentType: SessionType,
        currentCounter: Int,
    ): Int {
        if (plan.mode != TimerPlanMode.CLASSIC) return currentCounter
        return when (segmentType) {
            SessionType.WORK -> currentCounter + 1
            SessionType.LONG_BREAK -> 0
            SessionType.BREAK -> currentCounter
        }
    }
}

package com.focusflow.app.domain.timer

import com.focusflow.app.domain.model.SessionType
import com.focusflow.app.domain.model.TimerPlan
import com.focusflow.app.domain.model.TimerSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {

    @Test
    fun classic_longBreakAfter4WorkSessions() {
        val plan = TimerPlan.classicDefault(
            workMinutes = 25,
            breakMinutes = 5,
            longBreakMinutes = 15,
            longBreakEveryWorkSessions = 4,
        )

        val engine = TimerEngine.newEngine(
            plan = plan,
            segmentEndBehavior = TimerEngine.SegmentEndBehavior.AUTO_ADVANCE,
            maxTotalPausedMinutes = 10,
            nowEpochMs = 1_000L,
        )

        // Starting state should point to a WORK segment.
        assertEquals(SessionType.WORK, engine.state.activeSegment.type)
        assertEquals(25 * 60_000L, engine.state.activeSegment.plannedDurationMs)

        // Complete 1st work -> should go to short break
        engine.completeSegment(nowEpochMs = 1_000L + 25 * 60_000L)
        assertEquals(SessionType.BREAK, engine.state.activeSegment.type)

        // Complete break -> work
        engine.completeSegment(nowEpochMs = 1_000L + 30 * 60_000L)
        assertEquals(SessionType.WORK, engine.state.activeSegment.type)

        // Complete 2nd work -> break
        engine.completeSegment(nowEpochMs = 1_000L + 55 * 60_000L)
        assertEquals(SessionType.BREAK, engine.state.activeSegment.type)

        // Complete break -> work
        engine.completeSegment(nowEpochMs = 1_000L + 60 * 60_000L)
        assertEquals(SessionType.WORK, engine.state.activeSegment.type)

        // Complete 3rd work -> break
        engine.completeSegment(nowEpochMs = 1_000L + 85 * 60_000L)
        assertEquals(SessionType.BREAK, engine.state.activeSegment.type)

        // Complete break -> work
        engine.completeSegment(nowEpochMs = 1_000L + 90 * 60_000L)
        assertEquals(SessionType.WORK, engine.state.activeSegment.type)

        // Complete 4th work -> should go to LONG_BREAK (classic rule)
        engine.completeSegment(nowEpochMs = 1_000L + 115 * 60_000L)
        assertEquals(SessionType.LONG_BREAK, engine.state.activeSegment.type)
        assertEquals(15 * 60_000L, engine.state.activeSegment.plannedDurationMs)

        // After long break, should return to work
        engine.completeSegment(nowEpochMs = 1_000L + 130 * 60_000L)
        assertEquals(SessionType.WORK, engine.state.activeSegment.type)
    }

    @Test
    fun customSequence_cyclesSegmentsInOrder() {
        val plan = TimerPlan.customSequence(
            name = "Deep Work",
            segments = listOf(
                TimerSegment(SessionType.WORK, 10),
                TimerSegment(SessionType.BREAK, 2),
                TimerSegment(SessionType.WORK, 10),
                TimerSegment(SessionType.LONG_BREAK, 5),
            ),
        )

        val engine = TimerEngine.newEngine(
            plan = plan,
            segmentEndBehavior = TimerEngine.SegmentEndBehavior.AUTO_ADVANCE,
            maxTotalPausedMinutes = 0,
            nowEpochMs = 10_000L,
        )

        assertEquals(SessionType.WORK, engine.state.activeSegment.type)
        assertEquals(10 * 60_000L, engine.state.activeSegment.plannedDurationMs)

        engine.completeSegment(nowEpochMs = 10_000L + 10 * 60_000L)
        assertEquals(SessionType.BREAK, engine.state.activeSegment.type)

        engine.completeSegment(nowEpochMs = 10_000L + 12 * 60_000L)
        assertEquals(SessionType.WORK, engine.state.activeSegment.type)

        engine.completeSegment(nowEpochMs = 10_000L + 22 * 60_000L)
        assertEquals(SessionType.LONG_BREAK, engine.state.activeSegment.type)

        // Complete last segment -> cycles back to first
        engine.completeSegment(nowEpochMs = 10_000L + 27 * 60_000L)
        assertEquals(SessionType.WORK, engine.state.activeSegment.type)
    }

    @Test
    fun pauseResume_enforcesMaxTotalPausedMinutes() {
        val plan = TimerPlan.classicDefault()

        val engine = TimerEngine.newEngine(
            plan = plan,
            segmentEndBehavior = TimerEngine.SegmentEndBehavior.AUTO_ADVANCE,
            maxTotalPausedMinutes = 1, // 60_000ms cap
            nowEpochMs = 1_000L,
        )

        assertFalse(engine.state.isPaused)

        // Pause at t=2_000
        engine.pause(nowEpochMs = 2_000L)
        assertTrue(engine.state.isPaused)
        assertNotNull(engine.state.pausedAtEpochMs)

        // Resume at t=30_000 -> paused 28s, within cap
        engine.resume(nowEpochMs = 30_000L)
        assertFalse(engine.state.isPaused)
        assertNull(engine.state.pausedAtEpochMs)
        assertEquals(28_000L, engine.state.totalPausedMs)

        // Pause again at t=31_000
        engine.pause(nowEpochMs = 31_000L)
        assertTrue(engine.state.isPaused)

        // Resume at t=100_000 -> paused 69s; total paused = 28s + 69s = 97s > 60s cap
        val result = engine.resume(nowEpochMs = 100_000L)
        assertEquals(TimerEngine.ResumeResult.PAUSE_CAP_EXCEEDED, result)
        assertFalse("Engine should stop running when pause cap is exceeded", engine.state.isRunning)
    }

    @Test
    fun askEachTime_behavior_requiresPromptAtEachSegmentEnd() {
        val plan = TimerPlan.classicDefault()

        val engine = TimerEngine.newEngine(
            plan = plan,
            segmentEndBehavior = TimerEngine.SegmentEndBehavior.ASK_EACH_TIME,
            maxTotalPausedMinutes = 5,
            nowEpochMs = 1_000L,
        )

        assertFalse(engine.state.awaitingUserDecision)

        // Completing segment should not auto-advance; it should require a prompt/decision.
        engine.completeSegment(nowEpochMs = 1_000L + 25 * 60_000L)
        assertTrue(engine.state.awaitingUserDecision)
        assertEquals(SessionType.WORK, engine.state.completedSegmentPendingNext?.type)

        // If user chooses to advance, we should move to the next segment.
        engine.userDecisionAdvance(nowEpochMs = 1_000L + 25 * 60_000L + 1)
        assertFalse(engine.state.awaitingUserDecision)
        assertEquals(SessionType.BREAK, engine.state.activeSegment.type)

        // Completing break also requires decision again.
        engine.completeSegment(nowEpochMs = 1_000L + 30 * 60_000L)
        assertTrue(engine.state.awaitingUserDecision)
        assertEquals(SessionType.BREAK, engine.state.completedSegmentPendingNext?.type)

        // If user chooses to stop, engine should stop and no active segment should be running.
        engine.userDecisionStop(nowEpochMs = 1_000L + 30 * 60_000L + 1)
        assertFalse(engine.state.isRunning)
    }

    @Test
    fun prompt_behavior_setsAwaitingDecisionOnSegmentEnd_butKeepsNextReady() {
        val plan = TimerPlan.classicDefault()

        val engine = TimerEngine.newEngine(
            plan = plan,
            segmentEndBehavior = TimerEngine.SegmentEndBehavior.PROMPT,
            maxTotalPausedMinutes = 5,
            nowEpochMs = 1_000L,
        )

        engine.completeSegment(nowEpochMs = 1_000L + 25 * 60_000L)

        assertTrue(engine.state.awaitingUserDecision)
        assertNotNull(engine.state.nextSegmentSuggestion)
        assertEquals(SessionType.BREAK, engine.state.nextSegmentSuggestion?.type)

        engine.userDecisionAdvance(nowEpochMs = 1_000L + 25 * 60_000L + 1)
        assertEquals(SessionType.BREAK, engine.state.activeSegment.type)
        assertFalse(engine.state.awaitingUserDecision)
    }
}

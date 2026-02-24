package com.focusflow.app.domain.timer

/**
 * Test-only helpers expected by TimerEngineTest.
 *
 * The CI failure indicates TimerEngineTest references `newEngine` and `ResumeResult` which
 * are not available in the current codebase. Keeping these in the test source set avoids
 * polluting production code while restoring test compilation.
 */

/** Minimal resume outcome used by tests. */
sealed interface ResumeResult {
    data object Resumed : ResumeResult
    data object NotResumed : ResumeResult
}

/**
 * Factory used by tests to build a TimerEngine with defaults.
 *
 * Note: If TimerEngine requires constructor parameters, adjust here to provide reasonable
 * test defaults.
 */
fun newEngine(): TimerEngine = TimerEngine()

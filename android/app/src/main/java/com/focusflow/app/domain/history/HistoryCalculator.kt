package com.focusflow.app.domain.history

import com.focusflow.app.domain.model.Session
import com.focusflow.app.domain.model.SessionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Pure Kotlin aggregation utilities for Session history.
 *
 * Contract invariants applied:
 * - A Session represents a completed segment only.
 * - Segment types are strictly one of: work, break, long_break.
 * - Streak day counts require >= 1 completed 'work' session per local calendar day.
 */
object HistoryCalculator {

    data class DailyTotals(
        val date: LocalDate,
        val workDurationMs: Long,
        val breakDurationMs: Long,
        val longBreakDurationMs: Long,
        val totalDurationMs: Long,
        val workSessions: Int,
        val allSessions: Int,
    )

    data class WeeklyTotals(
        val weekStart: LocalDate,
        val weekEndInclusive: LocalDate,
        val workDurationMs: Long,
        val totalDurationMs: Long,
        val workSessions: Int,
        val allSessions: Int,
        val byDay: List<DailyTotals>,
    )

    /**
     * One bar per day for a fixed date range (inclusive).
     * Values are expressed in milliseconds.
     */
    data class ChartBar(
        val date: LocalDate,
        val workDurationMs: Long,
        val totalDurationMs: Long,
    )

    fun dailyTotals(
        sessions: List<Session>,
        date: LocalDate,
        zoneId: ZoneId,
    ): DailyTotals {
        val daySessions = sessions.filter { session ->
            session.startEpochMs.toLocalDate(zoneId) == date
        }

        return totalsForDay(date = date, sessions = daySessions)
    }

    /**
     * Computes totals for each day present in [sessions].
     *
     * Days are determined from session.start_epoch_ms in the provided [zoneId].
     */
    fun dailyTotalsByDay(
        sessions: List<Session>,
        zoneId: ZoneId,
    ): Map<LocalDate, DailyTotals> {
        if (sessions.isEmpty()) return emptyMap()

        val grouped = sessions.groupBy { it.startEpochMs.toLocalDate(zoneId) }
        return grouped.mapValues { (date, daySessions) ->
            totalsForDay(date = date, sessions = daySessions)
        }
    }

    /**
     * Weekly totals with [byDay] containing all days from weekStart..weekEndInclusive.
     * Week start is computed using ISO week rules (Monday as first day).
     */
    fun weeklyTotalsForDate(
        sessions: List<Session>,
        anyDateInWeek: LocalDate,
        zoneId: ZoneId,
    ): WeeklyTotals {
        val weekStart = anyDateInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEndInclusive = weekStart.plusDays(6)

        val byDay = (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            dailyTotals(sessions = sessions, date = date, zoneId = zoneId)
        }

        val workDurationMs = byDay.sumOf { it.workDurationMs }
        val totalDurationMs = byDay.sumOf { it.totalDurationMs }
        val workSessions = byDay.sumOf { it.workSessions }
        val allSessions = byDay.sumOf { it.allSessions }

        return WeeklyTotals(
            weekStart = weekStart,
            weekEndInclusive = weekEndInclusive,
            workDurationMs = workDurationMs,
            totalDurationMs = totalDurationMs,
            workSessions = workSessions,
            allSessions = allSessions,
            byDay = byDay,
        )
    }

    /**
     * Streak is computed as consecutive local days ending at [asOfDate] (inclusive)
     * such that each day has >= 1 completed WORK session.
     */
    fun currentStreakDays(
        sessions: List<Session>,
        asOfDate: LocalDate,
        zoneId: ZoneId,
    ): Int {
        if (sessions.isEmpty()) return 0

        val workDays: Set<LocalDate> = sessions
            .asSequence()
            .filter { it.type == SessionType.WORK }
            .map { it.startEpochMs.toLocalDate(zoneId) }
            .toSet()

        var streak = 0
        var cursor = asOfDate
        while (workDays.contains(cursor)) {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /**
     * Returns a fixed-range list of chart bars from [startDate]..[endDateInclusive].
     * Intended for simple bar charts / placeholders.
     */
    fun chartBars(
        sessions: List<Session>,
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        zoneId: ZoneId,
    ): List<ChartBar> {
        require(!endDateInclusive.isBefore(startDate)) { "endDateInclusive must be >= startDate" }

        val byDay = dailyTotalsByDay(sessions = sessions, zoneId = zoneId)

        val days = startDate.datesUntil(endDateInclusive.plusDays(1)).toList()
        return days.map { date ->
            val totals = byDay[date] ?: DailyTotals(
                date = date,
                workDurationMs = 0L,
                breakDurationMs = 0L,
                longBreakDurationMs = 0L,
                totalDurationMs = 0L,
                workSessions = 0,
                allSessions = 0,
            )
            ChartBar(
                date = date,
                workDurationMs = totals.workDurationMs,
                totalDurationMs = totals.totalDurationMs,
            )
        }
    }

    private fun totalsForDay(date: LocalDate, sessions: List<Session>): DailyTotals {
        var workMs = 0L
        var breakMs = 0L
        var longBreakMs = 0L
        var workCount = 0

        sessions.forEach { s ->
            // Session is already validated by domain model: duration_ms = end - start and > 0.
            when (s.type) {
                SessionType.WORK -> {
                    workMs += s.durationMs
                    workCount += 1
                }

                SessionType.BREAK -> breakMs += s.durationMs
                SessionType.LONG_BREAK -> longBreakMs += s.durationMs
            }
        }

        val total = workMs + breakMs + longBreakMs
        return DailyTotals(
            date = date,
            workDurationMs = workMs,
            breakDurationMs = breakMs,
            longBreakDurationMs = longBreakMs,
            totalDurationMs = total,
            workSessions = workCount,
            allSessions = sessions.size,
        )
    }
}

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate {
    return Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
}

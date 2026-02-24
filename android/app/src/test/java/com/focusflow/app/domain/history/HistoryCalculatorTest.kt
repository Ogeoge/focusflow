package com.focusflow.app.domain.history

import com.focusflow.app.domain.model.Session
import com.focusflow.app.domain.model.SessionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HistoryCalculatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    @Test
    fun dailyTotals_sumsByType_andTotal() {
        val date = LocalDate.of(2026, 1, 10)
        val dayStart = epochMsUtc(date, 0, 0)

        val sessions = listOf(
            Session.completed(
                type = SessionType.WORK,
                startEpochMs = dayStart + minutes(10),
                endEpochMs = dayStart + minutes(35),
            ),
            Session.completed(
                type = SessionType.BREAK,
                startEpochMs = dayStart + minutes(40),
                endEpochMs = dayStart + minutes(45),
            ),
            Session.completed(
                type = SessionType.LONG_BREAK,
                startEpochMs = dayStart + minutes(50),
                endEpochMs = dayStart + minutes(65),
            ),
        )

        val totals = HistoryCalculator.dailyTotals(
            sessions = sessions,
            date = date,
            zoneId = zone,
        )

        assertEquals(date, totals.date)
        assertEquals(minutes(25), totals.workDurationMs)
        assertEquals(minutes(5), totals.breakDurationMs)
        assertEquals(minutes(15), totals.longBreakDurationMs)
        assertEquals(minutes(45), totals.totalDurationMs)
        assertEquals(1, totals.workSessions)
        assertEquals(3, totals.allSessions)
    }

    @Test
    fun weeklyTotalsForDate_bucketsMondayToSunday_andIncludesEmptyDays() {
        // Week containing 2026-01-15 (Thursday) => starts Monday 2026-01-12
        val anyDateInWeek = LocalDate.of(2026, 1, 15)
        val monday = LocalDate.of(2026, 1, 12)
        val wednesday = LocalDate.of(2026, 1, 14)
        val sunday = LocalDate.of(2026, 1, 18)

        val sessions = listOf(
            Session.completed(
                type = SessionType.WORK,
                startEpochMs = epochMsUtc(monday, 9, 0),
                endEpochMs = epochMsUtc(monday, 9, 25),
            ),
            Session.completed(
                type = SessionType.BREAK,
                startEpochMs = epochMsUtc(wednesday, 12, 0),
                endEpochMs = epochMsUtc(wednesday, 12, 5),
            ),
            Session.completed(
                type = SessionType.WORK,
                startEpochMs = epochMsUtc(sunday, 18, 0),
                endEpochMs = epochMsUtc(sunday, 18, 25),
            ),
        )

        val weekly = HistoryCalculator.weeklyTotalsForDate(
            sessions = sessions,
            anyDateInWeek = anyDateInWeek,
            zoneId = zone,
        )

        assertEquals(monday, weekly.weekStart)
        assertEquals(sunday, weekly.weekEndInclusive)
        assertEquals(7, weekly.byDay.size)
        assertEquals(listOf(
            LocalDate.of(2026, 1, 12),
            LocalDate.of(2026, 1, 13),
            LocalDate.of(2026, 1, 14),
            LocalDate.of(2026, 1, 15),
            LocalDate.of(2026, 1, 16),
            LocalDate.of(2026, 1, 17),
            LocalDate.of(2026, 1, 18),
        ), weekly.byDay.map { it.date })

        // Work totals: Monday 25 + Sunday 25
        assertEquals(minutes(50), weekly.workDurationMs)
        // Total includes Wednesday break 5
        assertEquals(minutes(55), weekly.totalDurationMs)
        assertEquals(2, weekly.workSessions)
        assertEquals(3, weekly.allSessions)

        val tuesdayTotals = weekly.byDay.first { it.date == LocalDate.of(2026, 1, 13) }
        assertEquals(0L, tuesdayTotals.totalDurationMs)
        assertEquals(0, tuesdayTotals.allSessions)
    }

    @Test
    fun currentStreakDays_countsOnlyDaysWithAtLeastOneWorkSession() {
        // Streak rule per contract: a day counts only if it has >= 1 completed WORK session.
        val day1 = LocalDate.of(2026, 2, 1)
        val day2 = LocalDate.of(2026, 2, 2)
        val day3 = LocalDate.of(2026, 2, 3)
        val day4 = LocalDate.of(2026, 2, 4)

        val sessions = listOf(
            // day1: work => counts
            Session.completed(
                type = SessionType.WORK,
                startEpochMs = epochMsUtc(day1, 9, 0),
                endEpochMs = epochMsUtc(day1, 9, 25),
            ),
            // day2: break only => does NOT count
            Session.completed(
                type = SessionType.BREAK,
                startEpochMs = epochMsUtc(day2, 10, 0),
                endEpochMs = epochMsUtc(day2, 10, 5),
            ),
            // day3: work => counts
            Session.completed(
                type = SessionType.WORK,
                startEpochMs = epochMsUtc(day3, 11, 0),
                endEpochMs = epochMsUtc(day3, 11, 25),
            ),
            // day4: work => counts
            Session.completed(
                type = SessionType.WORK,
                startEpochMs = epochMsUtc(day4, 12, 0),
                endEpochMs = epochMsUtc(day4, 12, 25),
            ),
        )

        // As-of day4: day4 has work, day3 has work, day2 does not => streak = 2
        val streak = HistoryCalculator.currentStreakDays(
            sessions = sessions,
            asOfDate = day4,
            zoneId = zone,
        )
        assertEquals(2, streak)

        // As-of day3: day3 has work, day2 does not => streak = 1
        val streakAsOfDay3 = HistoryCalculator.currentStreakDays(
            sessions = sessions,
            asOfDate = day3,
            zoneId = zone,
        )
        assertEquals(1, streakAsOfDay3)

        // As-of day2: day2 has no work => streak = 0
        val streakAsOfDay2 = HistoryCalculator.currentStreakDays(
            sessions = sessions,
            asOfDate = day2,
            zoneId = zone,
        )
        assertEquals(0, streakAsOfDay2)
    }

    @Test
    fun chartBars_returnsFixedRange_includingDaysWithZeroTotals() {
        val start = LocalDate.of(2026, 3, 1)
        val end = LocalDate.of(2026, 3, 3)

        val sessions = listOf(
            Session.completed(
                type = SessionType.WORK,
                startEpochMs = epochMsUtc(LocalDate.of(2026, 3, 1), 9, 0),
                endEpochMs = epochMsUtc(LocalDate.of(2026, 3, 1), 9, 25),
            ),
            // No sessions on 2026-03-02
            Session.completed(
                type = SessionType.BREAK,
                startEpochMs = epochMsUtc(LocalDate.of(2026, 3, 3), 10, 0),
                endEpochMs = epochMsUtc(LocalDate.of(2026, 3, 3), 10, 5),
            ),
        )

        val bars = HistoryCalculator.chartBars(
            sessions = sessions,
            startDate = start,
            endDateInclusive = end,
            zoneId = zone,
        )

        assertEquals(3, bars.size)
        assertEquals(listOf(
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 2),
            LocalDate.of(2026, 3, 3),
        ), bars.map { it.date })

        val bar1 = bars[0]
        assertEquals(minutes(25), bar1.workDurationMs)
        assertEquals(minutes(25), bar1.totalDurationMs)

        val bar2 = bars[1]
        assertEquals(0L, bar2.workDurationMs)
        assertEquals(0L, bar2.totalDurationMs)

        val bar3 = bars[2]
        assertEquals(0L, bar3.workDurationMs)
        assertEquals(minutes(5), bar3.totalDurationMs)
    }

    private fun minutes(m: Int): Long = m.toLong() * 60_000L

    private fun epochMsUtc(date: LocalDate, hour: Int, minute: Int): Long {
        return date.atTime(hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }
}

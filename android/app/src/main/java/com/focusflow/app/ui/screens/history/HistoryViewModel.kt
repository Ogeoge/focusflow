package com.focusflow.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.app.AppServices
import com.focusflow.app.data.db.entities.SessionEntity
import com.focusflow.app.domain.history.HistoryCalculator
import com.focusflow.app.domain.history.HistoryCalculator.ChartBar
import com.focusflow.app.domain.history.HistoryCalculator.DailyTotals
import com.focusflow.app.domain.history.HistoryCalculator.WeeklyTotals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HistoryViewModel : ViewModel() {

    enum class RangeMode {
        DAY,
        WEEK,
    }

    data class UiState(
        val isLoading: Boolean,
        val rangeMode: RangeMode,
        val selectedDate: LocalDate,
        val today: LocalDate,
        val streakDays: Int,
        val dailyTotals: DailyTotals?,
        val weeklyTotals: WeeklyTotals?,
        val chartBars: List<ChartBar>,
        val errorMessage: String?,
    )

    private val sessionDao = AppServices.db.sessionDao()

    private val zoneId: ZoneId = ZoneId.systemDefault()

    private val _uiState = MutableStateFlow(
        UiState(
            isLoading = true,
            rangeMode = RangeMode.DAY,
            selectedDate = LocalDate.now(zoneId),
            today = LocalDate.now(zoneId),
            streakDays = 0,
            dailyTotals = null,
            weeklyTotals = null,
            chartBars = emptyList(),
            errorMessage = null,
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeAllSessionsAndRecompute()
    }

    fun setRangeMode(mode: RangeMode) {
        _uiState.update { it.copy(rangeMode = mode) }
        recomputeWithLatestSessions()
    }

    fun setSelectedDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        recomputeWithLatestSessions()
    }

    fun jumpToToday() {
        val today = LocalDate.now(zoneId)
        _uiState.update { it.copy(today = today, selectedDate = today) }
        recomputeWithLatestSessions()
    }

    private var lastSessions: List<SessionEntity> = emptyList()

    private fun observeAllSessionsAndRecompute() {
        viewModelScope.launch {
            sessionDao.observeAll().collect { entities ->
                lastSessions = entities
                recompute(entities)
            }
        }
    }

    private fun recomputeWithLatestSessions() {
        recompute(lastSessions)
    }

    private fun recompute(entities: List<SessionEntity>) {
        try {
            val domainSessions = entities.map { it.toDomain() }

            val nowDate = LocalDate.now(zoneId)
            val selectedDate = uiState.value.selectedDate

            val daily = HistoryCalculator.dailyTotals(
                sessions = domainSessions,
                date = selectedDate,
                zoneId = zoneId,
            )

            val weekly = HistoryCalculator.weeklyTotalsForDate(
                sessions = domainSessions,
                anyDateInWeek = selectedDate,
                zoneId = zoneId,
            )

            val streak = HistoryCalculator.currentStreakDays(
                sessions = domainSessions,
                asOfDate = nowDate,
                zoneId = zoneId,
            )

            // Simple placeholder chart: last 7 days ending on selectedDate.
            val bars = HistoryCalculator.chartBars(
                sessions = domainSessions,
                startDate = selectedDate.minusDays(6),
                endDateInclusive = selectedDate,
                zoneId = zoneId,
            )

            _uiState.update {
                it.copy(
                    isLoading = false,
                    today = nowDate,
                    streakDays = streak,
                    dailyTotals = daily,
                    weeklyTotals = weekly,
                    chartBars = bars,
                    errorMessage = null,
                )
            }
        } catch (t: Throwable) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = t.message ?: "Failed to compute history",
                )
            }
        }
    }
}

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate {
    return Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
}

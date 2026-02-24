package com.focusflow.app.ui.screens.timer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.app.AppServices
import com.focusflow.app.data.db.entities.SessionEntity
import com.focusflow.app.data.prefs.SettingsDataStore
import com.focusflow.app.domain.model.Session
import com.focusflow.app.domain.model.SessionType
import com.focusflow.app.domain.model.TimerPlan
import com.focusflow.app.domain.model.TimerSegment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TimerViewModel(app: Application) : AndroidViewModel(app) {

    data class TaskOption(
        val id: String,
        val title: String,
    )

    private val sessionDao = AppServices.db.sessionDao()
    private val taskDao = AppServices.db.taskDao()
    private val settings: SettingsDataStore = AppServices.settings

    private var tickerJob: Job? = null

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState

    init {
        viewModelScope.launch {
            settings.settingsFlow
                .collect { s ->
                    _uiState.update {
                        it.copy(
                            maxTotalPausedMinutes = s.maxTotalPausedMinutes,
                            segmentEndBehavior = s.segmentEndBehavior,
                        )
                    }
                }
        }

        viewModelScope.launch {
            taskDao.observeAll().collect { entities ->
                val tasks = entities.map { e -> e.toDomain() }
                _uiState.update { it.copy(tasks = tasks) }
            }
        }

        _uiState.update { it.copy(plan = TimerPlan.classicDefault()) }
        applyPlanToUi(_uiState.value.plan)
    }

    fun selectTask(taskId: String?) {
        _uiState.update { it.copy(linkedTaskId = taskId) }
    }

    fun dismissEndOfSegmentPrompt() {
        _uiState.update { it.copy(showEndPrompt = false) }
    }

    fun startNextSegmentFromPrompt() {
        dismissEndOfSegmentPrompt()
        // Minimal behavior: restart the plan from the beginning.
        start()
    }

    fun stopFromPrompt() {
        dismissEndOfSegmentPrompt()
        stop()
    }

    @Suppress("UNUSED_PARAMETER")
    fun openPlanEditor() {
        // Navigation/Editor screen wiring lives elsewhere.
        _uiState.update { it.copy(message = "Custom sequence editor not wired yet") }
    }

    fun setPlanClassicDefaults(
        workMinutes: Int,
        breakMinutes: Int,
        longBreakMinutes: Int,
        longBreakEveryWorkSessions: Int,
    ) {
        val newPlan = TimerPlan.classicDefault(
            name = "Classic",
            id = _uiState.value.plan.id,
            workMinutes = workMinutes,
            breakMinutes = breakMinutes,
            longBreakMinutes = longBreakMinutes,
            longBreakEveryWorkSessions = longBreakEveryWorkSessions,
        )
        _uiState.update { it.copy(plan = newPlan) }
        applyPlanToUi(newPlan)
    }

    fun setCustomSequence(segments: List<TimerSegment>, name: String = "Custom") {
        val newPlan = TimerPlan.customSequence(name = name, segments = segments)
        _uiState.update { it.copy(plan = newPlan) }
        applyPlanToUi(newPlan)
    }

    fun start() {
        // UI-only countdown. Domain TimerEngine integration is handled elsewhere / later.
        val ms = planFirstSegmentMs(_uiState.value.plan)
        _uiState.update { it.copy(isRunning = true, isPaused = false, remainingMs = ms, message = null) }
        ensureTicker()
    }

    fun pause() {
        _uiState.update { it.copy(isPaused = true) }
        stopTickerIfIdle()
    }

    fun resume() {
        _uiState.update { it.copy(isPaused = false) }
        ensureTicker()
    }

    fun stop() {
        _uiState.update { it.copy(isRunning = false, isPaused = false, remainingMs = 0L, showEndPrompt = false) }
        stopTickerIfIdle()
    }

    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(250)

                val s = _uiState.value
                if (s.isRunning && !s.isPaused) {
                    val next = (s.remainingMs - 250L).coerceAtLeast(0L)
                    val reachedEnd = next == 0L && s.remainingMs > 0L

                    _uiState.update {
                        val newShowPrompt = if (reachedEnd) {
                            when (it.segmentEndBehavior) {
                                SettingsDataStore.SegmentEndBehavior.AUTO_ADVANCE -> false
                                SettingsDataStore.SegmentEndBehavior.PROMPT,
                                SettingsDataStore.SegmentEndBehavior.ASK_EACH_TIME -> true
                            }
                        } else it.showEndPrompt

                        it.copy(
                            remainingMs = next,
                            isRunning = if (reachedEnd && it.segmentEndBehavior == SettingsDataStore.SegmentEndBehavior.AUTO_ADVANCE) false else it.isRunning,
                            showEndPrompt = newShowPrompt,
                        )
                    }
                }

                stopTickerIfIdle()
            }
        }
    }

    private fun stopTickerIfIdle() {
        val s = _uiState.value
        if (!s.isRunning || s.isPaused) {
            tickerJob?.cancel()
            tickerJob = null
        }
    }

    private fun applyPlanToUi(plan: TimerPlan) {
        val segments = plan.segments ?: emptyList()
        val first = segments.firstOrNull()
        _uiState.update {
            it.copy(
                currentType = first?.type ?: SessionType.WORK,
                remainingMs = planFirstSegmentMs(plan),
            )
        }
    }

    private fun planFirstSegmentMs(plan: TimerPlan): Long {
        val segments = plan.segments ?: emptyList()
        val first = segments.firstOrNull()
        return (first?.durationMinutes?.toLong() ?: 25L) * 60_000L
    }

    @Suppress("UNUSED_PARAMETER")
    private fun persistCompletedSession(session: Session) {
        viewModelScope.launch {
            sessionDao.insert(SessionEntity.fromDomain(session))
        }
    }
}

data class TimerUiState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentType: SessionType = SessionType.WORK,
    val remainingMs: Long = 0L,

    val plan: TimerPlan = TimerPlan.classicDefault(),
    val linkedTaskId: String? = null,
    val tasks: List<com.focusflow.app.domain.model.Task> = emptyList(),

    val maxTotalPausedMinutes: Int = 0,
    val segmentEndBehavior: SettingsDataStore.SegmentEndBehavior = SettingsDataStore.SegmentEndBehavior.ASK_EACH_TIME,

    val showEndPrompt: Boolean = false,
    val message: String? = null,
) {
    val selectedTaskId: String?
        get() = linkedTaskId

    val availableTasks: List<TimerViewModel.TaskOption>
        get() = tasks.map { TimerViewModel.TaskOption(id = it.id, title = it.title) }

    val dismissAllowedForEndPrompt: Boolean
        get() = segmentEndBehavior != SettingsDataStore.SegmentEndBehavior.ASK_EACH_TIME

    val segmentEndBehaviorLabel: String
        get() = when (segmentEndBehavior) {
            SettingsDataStore.SegmentEndBehavior.AUTO_ADVANCE -> "Auto-advance"
            SettingsDataStore.SegmentEndBehavior.PROMPT -> "Prompt"
            SettingsDataStore.SegmentEndBehavior.ASK_EACH_TIME -> "Ask each time"
        }
}

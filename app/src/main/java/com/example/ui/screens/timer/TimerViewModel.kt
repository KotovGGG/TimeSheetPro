package com.example.ui.screens.timer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AppSettings
import com.example.data.local.entity.Client
import com.example.data.local.entity.TimeEntry
import com.example.data.local.entity.TimeEntryStatus
import com.example.data.repository.ClientRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TimeEntryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TimerViewModel(
    private val clientRepository: ClientRepository,
    private val timeEntryRepository: TimeEntryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // Active Clients list for selecting who to log time for
    val activeClients: StateFlow<List<Client>> = clientRepository.getActiveClients()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current app settings
    val settingsState: StateFlow<AppSettings> = settingsRepository.getSettingsFlow()
        .map { it ?: AppSettings() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    private val currentDay = flow {
        while (true) {
            emit(dayStart(System.currentTimeMillis()))
            delay(1000)
        }
    }.distinctUntilChanged()

    val dailySummary: StateFlow<DailySummary> = combine(
        timeEntryRepository.getAllTimeEntries(), currentDay
    ) { entries, start -> summarizeDay(entries, start) }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailySummary())

    // Observe state from our robust TimerStateManager
    val timerState: StateFlow<TimerState> = TimerStateManager.timerState

    // State of live elapsed seconds for the active time entry
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    // State of live pause/break seconds for the active time entry
    private val _pauseSeconds = MutableStateFlow(0L)
    val pauseSeconds: StateFlow<Long> = _pauseSeconds.asStateFlow()

    private var tickerJob: Job? = null

    init {
        // Automatically start/stop local ticker based on TimerStateManager's status
        viewModelScope.launch {
            timerState.collect { state ->
                if (state.status == TimerStatus.RUNNING || state.status == TimerStatus.PAUSED) {
                    startTicker()
                } else {
                    stopTicker()
                }
            }
        }

        // Seed settings if the database is empty (no clients seeded by default)
        viewModelScope.launch {
            try {
                val settings = settingsRepository.getSettingsFlow().first()
                if (settings == null) {
                    settingsRepository.saveSettings(AppSettings())
                }
            } catch (e: Exception) {
                // Ignore or log error
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                _elapsedSeconds.value = TimerStateManager.getElapsedSeconds()
                _pauseSeconds.value = TimerStateManager.getPauseSeconds()
                delay(1000)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
        _elapsedSeconds.value = TimerStateManager.getElapsedSeconds()
        _pauseSeconds.value = TimerStateManager.getPauseSeconds()
    }

    fun startTimer(context: Context, clientId: Long?) {
        viewModelScope.launch {
            // Start state manager
            TimerStateManager.start(clientId)
            
            // Start Foreground Service
            TimerService.startService(context, clientId)
        }
    }

    fun pauseTimer(context: Context) {
        val intent = android.content.Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeTimer(context: Context) {
        val intent = android.content.Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_RESUME
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun updateActiveComment(comment: String) {
        TimerStateManager.updateComment(comment)
        viewModelScope.launch {
            val state = timerState.value
            val timeEntryId = state.timeEntryId ?: return@launch
            val existingEntry = timeEntryRepository.getTimeEntryById(timeEntryId).firstOrNull() ?: return@launch
            timeEntryRepository.updateTimeEntry(existingEntry.copy(
                comment = comment.ifBlank { null },
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    fun stopTimer(context: Context) {
        val intent = android.content.Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_FINISH
        }
        context.startService(intent)
    }
}

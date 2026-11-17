package com.example.ui.screens.timer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TimerStatus {
    IDLE,
    RUNNING,
    PAUSED
}

data class TimerState(
    val status: TimerStatus = TimerStatus.IDLE,
    val clientId: Long? = null,
    val startTime: Long = 0L,
    val accumulatedSeconds: Long = 0L,
    val comment: String = "",
    val timeEntryId: Long? = null,
    val pendingSaveDuration: Long? = null,
    val pendingSavePauseDuration: Long? = null,
    val sessionStartTime: Long = 0L
)

object TimerStateManager {
    private const val PREFS_NAME = "TimeSheetProTimerPrefs"
    private const val KEY_STATUS = "status"
    private const val KEY_CLIENT_ID = "clientId"
    private const val KEY_START_TIME = "startTime"
    private const val KEY_ACCUMULATED_SECONDS = "accumulatedSeconds"
    private const val KEY_COMMENT = "comment"
    private const val KEY_TIME_ENTRY_ID = "timeEntryId"
    private const val KEY_PENDING_SAVE_DURATION = "pendingSaveDuration"
    private const val KEY_PENDING_SAVE_PAUSE_DURATION = "pendingSavePauseDuration"
    private const val KEY_SESSION_START_TIME = "sessionStartTime"
    private const val KEY_LAST_SELECTED_CLIENT_ID = "lastSelectedClientId"

    private lateinit var prefs: SharedPreferences
    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private val _widgetTick = MutableStateFlow(0L)
    val widgetTick: StateFlow<Long> = _widgetTick.asStateFlow()

    private val _showConfirmDialog = MutableStateFlow(false)
    val showConfirmDialog: StateFlow<Boolean> = _showConfirmDialog.asStateFlow()

    fun setShowConfirmDialog(show: Boolean) {
        _showConfirmDialog.value = show
    }

    fun incrementWidgetTick() {
        _widgetTick.value = _widgetTick.value + 1
    }

    @Synchronized
    fun initialize(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadState()
        }
    }

    private fun loadState() {
        val statusStr = prefs.getString(KEY_STATUS, TimerStatus.IDLE.name) ?: TimerStatus.IDLE.name
        val status = try { TimerStatus.valueOf(statusStr) } catch (e: Exception) { TimerStatus.IDLE }
        
        val clientIdVal = prefs.getLong(KEY_CLIENT_ID, -1L)
        val clientId = if (clientIdVal == -1L) null else clientIdVal
        
        val startTime = prefs.getLong(KEY_START_TIME, 0L)
        val accumulatedSeconds = prefs.getLong(KEY_ACCUMULATED_SECONDS, 0L)
        val comment = prefs.getString(KEY_COMMENT, "") ?: ""
        
        val timeEntryIdVal = prefs.getLong(KEY_TIME_ENTRY_ID, -1L)
        val timeEntryId = if (timeEntryIdVal == -1L) null else timeEntryIdVal

        val pendingSaveDurationVal = prefs.getLong(KEY_PENDING_SAVE_DURATION, -1L)
        val pendingSaveDuration = if (pendingSaveDurationVal == -1L) null else pendingSaveDurationVal

        val pendingSavePauseDurationVal = prefs.getLong(KEY_PENDING_SAVE_PAUSE_DURATION, -1L)
        val pendingSavePauseDuration = if (pendingSavePauseDurationVal == -1L) null else pendingSavePauseDurationVal

        val sessionStartTime = prefs.getLong(KEY_SESSION_START_TIME, 0L)

        _timerState.value = TimerState(
            status = status,
            clientId = clientId,
            startTime = startTime,
            accumulatedSeconds = accumulatedSeconds,
            comment = comment,
            timeEntryId = timeEntryId,
            pendingSaveDuration = pendingSaveDuration,
            pendingSavePauseDuration = pendingSavePauseDuration,
            sessionStartTime = sessionStartTime
        )
    }

    @Synchronized
    fun start(clientId: Long? = null) {
        val now = System.currentTimeMillis()
        val newState = TimerState(
            status = TimerStatus.RUNNING,
            clientId = clientId,
            startTime = now,
            accumulatedSeconds = 0L,
            comment = "",
            timeEntryId = null,
            pendingSaveDuration = null,
            pendingSavePauseDuration = null,
            sessionStartTime = now
        )
        saveState(newState)
    }

    @Synchronized
    fun pause() {
        val currentState = _timerState.value
        if (currentState.status != TimerStatus.RUNNING) return
        
        val now = System.currentTimeMillis()
        val additionalSeconds = maxOf(0L, (now - currentState.startTime) / 1000)
        val newAccumulated = currentState.accumulatedSeconds + additionalSeconds
        
        val newState = currentState.copy(
            status = TimerStatus.PAUSED,
            startTime = 0L,
            accumulatedSeconds = newAccumulated
        )
        saveState(newState)
    }

    @Synchronized
    fun resume() {
        val currentState = _timerState.value
        if (currentState.status != TimerStatus.PAUSED) return
        
        val now = System.currentTimeMillis()
        val newState = currentState.copy(
            status = TimerStatus.RUNNING,
            startTime = now
        )
        saveState(newState)
    }

    @Synchronized
    fun updateComment(comment: String) {
        val currentState = _timerState.value
        if (currentState.status == TimerStatus.IDLE) return
        val newState = currentState.copy(comment = comment)
        saveState(newState)
    }

    @Synchronized
    fun stopAndPending() {
        val currentState = _timerState.value
        if (currentState.status == TimerStatus.IDLE) return
        val elapsed = getElapsedSeconds()
        val pauseSeconds = getPauseSeconds()
        val newState = TimerState(
            status = TimerStatus.IDLE,
            clientId = currentState.clientId,
            startTime = 0L,
            accumulatedSeconds = 0L,
            comment = currentState.comment,
            timeEntryId = null,
            pendingSaveDuration = elapsed,
            pendingSavePauseDuration = pauseSeconds,
            sessionStartTime = 0L
        )
        saveState(newState)
    }

    @Synchronized
    fun clearPending() {
        val currentState = _timerState.value
        val newState = currentState.copy(
            pendingSaveDuration = null,
            pendingSavePauseDuration = null
        )
        saveState(newState)
    }

    @Synchronized
    fun stop() {
        val newState = TimerState(
            status = TimerStatus.IDLE,
            clientId = null,
            startTime = 0L,
            accumulatedSeconds = 0L,
            comment = "",
            timeEntryId = null,
            pendingSaveDuration = null,
            pendingSavePauseDuration = null,
            sessionStartTime = 0L
        )
        saveState(newState)
    }

    private fun saveState(state: TimerState) {
        _timerState.value = state
        prefs.edit().apply {
            putString(KEY_STATUS, state.status.name)
            putLong(KEY_CLIENT_ID, state.clientId ?: -1L)
            putLong(KEY_START_TIME, state.startTime)
            putLong(KEY_ACCUMULATED_SECONDS, state.accumulatedSeconds)
            putString(KEY_COMMENT, state.comment)
            putLong(KEY_TIME_ENTRY_ID, state.timeEntryId ?: -1L)
            putLong(KEY_PENDING_SAVE_DURATION, state.pendingSaveDuration ?: -1L)
            putLong(KEY_PENDING_SAVE_PAUSE_DURATION, state.pendingSavePauseDuration ?: -1L)
            putLong(KEY_SESSION_START_TIME, state.sessionStartTime)
            apply()
        }
    }

    fun getElapsedSeconds(): Long {
        val state = _timerState.value
        return when (state.status) {
            TimerStatus.IDLE -> 0L
            TimerStatus.PAUSED -> state.accumulatedSeconds
            TimerStatus.RUNNING -> {
                val additional = maxOf(0L, (System.currentTimeMillis() - state.startTime) / 1000)
                state.accumulatedSeconds + additional
            }
        }
    }

    fun getPauseSeconds(): Long {
        val state = _timerState.value
        if (state.status == TimerStatus.IDLE || state.sessionStartTime == 0L) {
            return 0L
        }
        val totalElapsedMillis = System.currentTimeMillis() - state.sessionStartTime
        val totalElapsedSeconds = maxOf(0L, totalElapsedMillis / 1000)
        val workedSeconds = getElapsedSeconds()
        return maxOf(0L, totalElapsedSeconds - workedSeconds)
    }

    fun getLastSelectedClientId(): Long? {
        if (!::prefs.isInitialized) return null
        val idVal = prefs.getLong(KEY_LAST_SELECTED_CLIENT_ID, -1L)
        return if (idVal == -1L) null else idVal
    }

    fun setLastSelectedClientId(clientId: Long) {
        if (!::prefs.isInitialized) return
        prefs.edit().putLong(KEY_LAST_SELECTED_CLIENT_ID, clientId).apply()
    }
}

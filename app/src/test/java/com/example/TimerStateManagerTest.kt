package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.timer.TimerState
import com.example.ui.screens.timer.TimerStateManager
import com.example.ui.screens.timer.TimerStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [36])
class TimerStateManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("TimeSheetProTimerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        TimerStateManager.initialize(context)
        TimerStateManager.stop()
    }

    @Test
    fun initialStateIsIdle() {
        val state = TimerStateManager.timerState.value
        assertEquals(TimerStatus.IDLE, state.status)
        assertEquals(0L, state.startTime)
        assertEquals(0L, state.accumulatedSeconds)
        assertNull(state.clientId)
        assertEquals(0L, TimerStateManager.getElapsedSeconds())
    }

    @Test
    fun startTimerTransitionsToRunning() {
        TimerStateManager.start(clientId = 42L)
        val state = TimerStateManager.timerState.value
        assertEquals(TimerStatus.RUNNING, state.status)
        assertEquals(42L, state.clientId)
        assertTrue(state.startTime > 0L)
        assertTrue(state.sessionStartTime > 0L)
        assertEquals(0L, state.accumulatedSeconds)
    }

    @Test
    fun pauseTimerTransitionsToPausedAndAccumulatesTime() {
        TimerStateManager.start(clientId = 10L)
        Thread.sleep(50) // Small delay to guarantee elapsed calculation
        TimerStateManager.pause()

        val state = TimerStateManager.timerState.value
        assertEquals(TimerStatus.PAUSED, state.status)
        assertEquals(0L, state.startTime)
        assertTrue(state.accumulatedSeconds >= 0L)
    }

    @Test
    fun resumeTimerTransitionsToRunning() {
        TimerStateManager.start(clientId = 10L)
        TimerStateManager.pause()
        val pausedAccumulated = TimerStateManager.timerState.value.accumulatedSeconds

        TimerStateManager.resume()
        val resumedState = TimerStateManager.timerState.value
        assertEquals(TimerStatus.RUNNING, resumedState.status)
        assertTrue(resumedState.startTime > 0L)
        assertEquals(pausedAccumulated, resumedState.accumulatedSeconds)
    }

    @Test
    fun stopTimerResetsToIdle() {
        TimerStateManager.start(clientId = 7L)
        TimerStateManager.updateComment("Тестовый комментарий")
        TimerStateManager.stop()

        val state = TimerStateManager.timerState.value
        assertEquals(TimerStatus.IDLE, state.status)
        assertNull(state.clientId)
        assertEquals("", state.comment)
        assertEquals(0L, TimerStateManager.getElapsedSeconds())
    }

    @Test
    fun stopAndPendingSavesPendingDurations() {
        TimerStateManager.start(clientId = 99L)
        TimerStateManager.updateComment("В ожидании сохранения")
        TimerStateManager.stopAndPending()

        val state = TimerStateManager.timerState.value
        assertEquals(TimerStatus.IDLE, state.status)
        assertEquals(99L, state.clientId)
        assertEquals("В ожидании сохранения", state.comment)
        assertNotNull(state.pendingSaveDuration)
        assertNotNull(state.pendingSavePauseDuration)

        TimerStateManager.clearPending()
        val clearedState = TimerStateManager.timerState.value
        assertNull(clearedState.pendingSaveDuration)
        assertNull(clearedState.pendingSavePauseDuration)
    }

    @Test
    fun notificationSaveFreezesTheMatchingSessionAndKeepsItOnFailure() {
        TimerStateManager.start(42L)
        val start = TimerStateManager.timerState.value.sessionStartTime
        assertNull(TimerStateManager.beginNotificationSave(start + 1))
        val pending = checkNotNull(TimerStateManager.beginNotificationSave(start))
        try {
            assertEquals(TimerStatus.PAUSED, pending.status)
            assertEquals(42L, pending.clientId)
            assertNull(TimerStateManager.beginNotificationSave(start))
            TimerStateManager.resume()
            TimerStateManager.stopAndPending()
            assertEquals(pending, TimerStateManager.timerState.value)
        } finally {
            TimerStateManager.endNotificationSave(saved = false)
        }
        assertEquals(pending, TimerStateManager.timerState.value)
        assertNotNull(TimerStateManager.beginNotificationSave(start))
        TimerStateManager.endNotificationSave(saved = true)
        assertEquals(TimerStatus.IDLE, TimerStateManager.timerState.value.status)
        assertNull(TimerStateManager.beginNotificationSave(start))
    }

    @Test
    fun savedNotificationEntryRemainsUntilTheMatchingRecordIsShown() {
        TimerStateManager.showSavedNotificationEntry(27L)
        TimerStateManager.showSavedNotificationEntry(28L)
        TimerStateManager.stop()
        TimerStateManager.acknowledgeSavedNotificationEntry(27L)

        assertEquals(28L, TimerStateManager.savedNotificationEntry.value)

        TimerStateManager.acknowledgeSavedNotificationEntry(28L)
        assertNull(TimerStateManager.savedNotificationEntry.value)
    }

    @Test
    fun confirmDialogAndWidgetTickFlags() {
        TimerStateManager.setShowConfirmDialog(true)
        assertTrue(TimerStateManager.showConfirmDialog.value)

        TimerStateManager.setShowConfirmDialog(false)
        assertFalse(TimerStateManager.showConfirmDialog.value)

        val initialTick = TimerStateManager.widgetTick.value
        TimerStateManager.incrementWidgetTick()
        assertEquals(initialTick + 1, TimerStateManager.widgetTick.value)
    }

    @Test
    fun lastSelectedClientIdPersistence() {
        TimerStateManager.setLastSelectedClientId(555L)
        assertEquals(555L, TimerStateManager.getLastSelectedClientId())
    }
}

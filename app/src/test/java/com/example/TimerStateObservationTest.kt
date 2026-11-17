package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.timer.TimerStateManager
import com.example.ui.screens.timer.TimerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TimerStateObservationTest {
    @Test fun widgetObservationCannotReviveStoppedSession() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        TimerStateManager.initialize(context)
        TimerStateManager.start()
        // Glance initializes while reacting to timer state, before saveState finishes writing preferences.
        val observer = CoroutineScope(Dispatchers.Unconfined).launch {
            TimerStateManager.timerState.collect { TimerStateManager.initialize(context) }
        }
        try {
            TimerStateManager.stop()
            assertEquals(TimerStatus.IDLE, TimerStateManager.timerState.value.status)
        } finally { observer.cancel() }
    }
}

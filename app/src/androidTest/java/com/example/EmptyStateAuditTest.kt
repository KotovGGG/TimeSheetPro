package com.example

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AppSettings
import com.example.data.local.entity.ThemeMode
import com.example.ui.screens.timer.TimerStateManager
import com.example.ui.screens.timer.TimerStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.File

/** Refuses populated databases; Run-EmptyAudit.ps1 uses a separate, newly created AVD. */
@RunWith(AndroidJUnit4::class)
class EmptyStateAuditTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun emptyWindows() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(BuildConfig.DEBUG && context.packageName == "com.example.preview")
        check(InstrumentationRegistry.getArguments().getString("emptyAudit") == "true")
        val database = AppDatabase.getDatabase(context)
        runBlocking {
            check(database.clientDao().getAllClients().first().isEmpty())
            check(database.timeEntryDao().getAllTimeEntries().first().isEmpty())
            check(database.reportDao().getAllReports().first().isEmpty())
        }
        check(TimerStateManager.timerState.value.status == TimerStatus.IDLE)
        fun tag(name: String) {
            compose.waitUntil(10000) { compose.onAllNodesWithTag(name).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag(name).performClick()
        }
        fun text(label: String) = compose.onAllNodesWithText(label).onLast().performClick()
        fun capture(theme: String, name: String) {
            compose.waitForIdle()
            instrumentation.uiAutomation.waitForIdle(500, 5000)
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            val directory = checkNotNull(context.getExternalFilesDir("empty-audit")).apply { mkdirs() }
            File(directory, "$theme-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            runBlocking { database.appSettingsDao().saveSettings(AppSettings(themeMode = theme)) }
            val name = theme.name.lowercase()
            tag("nav_item_clients")
            compose.onNodeWithText("Нет добавленных заказчиков").assertIsDisplayed()
            capture(name, "clients")
            tag("nav_item_records")
            compose.onNodeWithText("Записи не найдены").assertIsDisplayed()
            capture(name, "records")
            compose.onNodeWithContentDescription("История отчётов").performClick()
            compose.onNodeWithText("История отчётов пуста").assertIsDisplayed()
            capture(name, "history")
            compose.onAllNodesWithContentDescription("Закрыть").onLast().performClick()
            tag("nav_item_timer"); capture(name, "timer")
            tag("manual_entry_button")
            compose.onNodeWithText("Сохранить").performScrollTo().assertIsNotEnabled()
            capture(name, "manual-no-client")
            compose.onNodeWithTag("client_selector_field").performScrollTo().performClick()
            compose.onNodeWithText("Пока нет заказчиков. Добавьте заказчика во вкладке «Заказчики».").assertIsDisplayed()
            capture(name, "client-picker-empty")
            compose.onAllNodesWithContentDescription("Закрыть").onLast().performClick()
            text("Отмена")
            tag("control_timer_button"); tag("stop_timer_button"); tag("confirm_finish_timer")
            compose.waitUntil(10000) { compose.onAllNodesWithText("Сохранить запись").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Сохранить").performScrollTo().assertIsNotEnabled()
            capture(name, "session-no-client")
            text("Не сохранять")
            capture(name, "discard-confirm"); text("Не сохранять")
            compose.waitUntil(10000) { TimerStateManager.timerState.value.status == TimerStatus.IDLE }
            runBlocking { check(database.timeEntryDao().getAllTimeEntries().first().isEmpty()) }
            tag("nav_item_settings"); capture(name, "settings")
        }
    }
}

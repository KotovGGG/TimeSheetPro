package com.example

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar

/** Runs on the isolated preview package and captures actual Android windows for manual review. */
@RunWith(AndroidJUnit4::class)
class VisualAuditTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val database get() = AppDatabase.getDatabase(context)
    private var themeName = "light"
    private var demoClientId = 0L
    private var demoEntryId = 0L
    private var demoReportId = 0L

    @Before fun prepareDemonstrationData() = runBlocking {
        check(BuildConfig.DEBUG && context.packageName == "com.example.preview")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val serviceInfo = checkNotNull(automation.serviceInfo)
        serviceInfo.flags = serviceInfo.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = serviceInfo
        val names = listOf("Демо • Студия Север", "Демо • Международная студия архитектуры и проектирования")
        names.forEachIndexed { index, name ->
            val existing = database.clientDao().getAllClients().first().find { it.name == name }
            val id = existing?.id ?: database.clientDao().insertClient(Client(name = name,
                colorHex = if (index == 0) "#D0BCFF" else "#52B788", defaultHourlyRate = 2500.0))
            if (index == 0) demoClientId = id
            val start = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 10 + index); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val comment = "Демо • Проверка интерфейса ${index + 1}"
            val previous = database.timeEntryDao().getAllTimeEntries().first().find { it.comment == comment }
            val entry = TimeEntry(id = previous?.id ?: 0, clientId = id, startTime = start,
                endTime = start + 3600000L, durationSeconds = 3600, rawDurationSeconds = 3600,
                comment = comment, appliedRate = if (index == 0) 2500.0 else 9876543.21, amount = if (index == 0) 2500.0 else 9876543.21,
                status = if (index == 0) TimeEntryStatus.NOT_PAID else TimeEntryStatus.PAID)
            val entryId = database.timeEntryDao().insertTimeEntry(entry)
            if (index == 0) demoEntryId = entryId
        }
        val settings = database.appSettingsDao().getSettingsFlow().first() ?: AppSettings()
        database.appSettingsDao().saveSettings(settings.copy(currency = "RUB"))
        val reportName = "Демо • Визуальная проверка отчёта"
        val previousReport = database.reportDao().getAllReports().first().find { it.clientNames == reportName }
        demoReportId = database.reportDao().insertReport(Report(id = previousReport?.id ?: 0,
            clientNames = reportName, periodLabel = "За весь период", exportFormat = "PDF",
            totalAmount = 2500.0, totalDurationSeconds = 3600, entriesCount = 1,
            entryIdsJson = "[$demoEntryId]", filePath = "", fileName = "demonstration.pdf"))
    }

    private fun themes(modes: List<ThemeMode> = listOf(ThemeMode.LIGHT, ThemeMode.DARK), body: () -> Unit) {
        modes.forEach { theme ->
            themeName = theme.name.lowercase()
            runBlocking {
                val settings = database.appSettingsDao().getSettingsFlow().first() ?: AppSettings()
                database.appSettingsDao().saveSettings(settings.copy(themeMode = theme))
            }
            body()
        }
    }
    private fun tag(name: String) {
        try { compose.waitUntil(10000) { compose.onAllNodesWithTag(name).fetchSemanticsNodes().isNotEmpty() } }
        catch (error: AssertionError) { capture("failure-tag-$name"); throw error }
        catch (error: androidx.compose.ui.test.ComposeTimeoutException) { capture("failure-tag-$name"); throw error }
        compose.onNodeWithTag(name).performClick()
        compose.waitForIdle()
    }
    private fun text(label: String) {
        try { compose.waitUntil(10000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() } }
        catch (error: androidx.compose.ui.test.ComposeTimeoutException) { capture("failure-text-${label.replace('/', '-')}"); throw error }
        compose.onAllNodesWithText(label).onLast().performClick(); compose.waitForIdle()
    }
    private fun back() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(500, 5000)
        val close = compose.onAllNodesWithContentDescription("Закрыть")
        if (close.fetchSemanticsNodes().isNotEmpty()) {
            val button = close.onLast()
            if (!button.isDisplayed()) button.performScrollTo()
            button.performClick()
        }
        else InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        automation.waitForIdle(500, 5000)
    }
    private fun closeKeyboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Back can dismiss the form when the preceding dialog's IME is already closing.
        // Hide IME on the actual window, without sending a navigation action.
        instrumentation.runOnMainSync {
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach { view ->
                view.windowInsetsController?.hide(android.view.WindowInsets.Type.ime())
            }
        }
        compose.waitUntil(5000) {
            var hidden = false
            instrumentation.runOnMainSync {
                hidden = android.view.inspector.WindowInspector.getGlobalWindowViews().none {
                    it.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
                }
            }
            hidden
        }
        compose.waitForIdle()
    }
    private fun capture(name: String): File {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        awaitRenderedFrame()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5000)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = checkNotNull(context.getExternalFilesDir("visual-audit")).apply { mkdirs() }
        val variant = InstrumentationRegistry.getArguments().getString("auditVariant", "").orEmpty()
            .replace(Regex("[^a-zA-Z0-9_-]"), "")
        val file = File(directory, "${if (variant.isEmpty()) "" else "$variant-"}$themeName-$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun awaitRenderedFrame() {
        val committed = java.util.concurrent.CountDownLatch(1)
        var scheduled = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews()
                .lastOrNull { it.hasWindowFocus() && it.isShown && it.isHardwareAccelerated }
            if (view != null) {
                scheduled = true
                view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                view.invalidate()
            }
        }
        // Native system pickers belong to another process and have no inspectable target View.
        if (scheduled) check(committed.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
            "The focused application window did not commit its screenshot frame"
        }
    }

    @Test fun settingsWindows() = themes {
        tag("nav_item_settings"); capture("settings")
        tag("settings_rate"); capture("rate")
        compose.onNodeWithTag("settings_rate_input").performTextReplacement("не число")
        capture("rate-error"); text("Отмена")
        for (key in listOf("currency", "time_rounding", "amount_rounding", "theme")) {
            tag("settings_$key"); capture(key); back()
        }
        compose.onNodeWithTag("settings_backup").performScrollTo(); tag("settings_backup")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Копия готова").fetchSemanticsNodes().isNotEmpty() }
        capture("backup-ready"); back()
    }

    @Test fun clientWindowsAndDraftRestoration() = themes {
        tag("nav_item_clients"); capture("clients")
        tag("add_client_fab"); capture("client-create")
        compose.onNodeWithTag("dialog_client_name_input").performTextInput("Демо черновик")
        tag("client_color_picker"); capture("client-colors"); back()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("dialog_client_name_input").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dialog_client_name_input").assertTextContains("Демо черновик")
        text("Отмена")
        compose.onNodeWithTag("clients_lazy_column").performScrollToNode(hasTestTag("client_card_$demoClientId"))
        tag("client_card_$demoClientId"); capture("client-edit")
        compose.onNodeWithTag("dialog_delete_button").performScrollTo(); tag("dialog_delete_button")
        capture("client-delete-confirm"); text("Отмена"); text("Отмена")
    }

    @Test fun recordWindows() = themes {
        tag("nav_item_records"); capture("records")
        compose.onNodeWithContentDescription("Фильтры").performClick(); capture("filters"); back()
        tag("add_manual_record_fab"); capture("record-create"); text("Отмена")
        compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("entry_$demoEntryId"))
        tag("entry_$demoEntryId"); capture("record-edit")
        compose.onNodeWithTag("edit_comment_input").performTextReplacement("Демо черновик записи")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("edit_comment_input").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("edit_comment_input").assertTextContains("Демо черновик записи")
        text("Отмена")
        compose.onNodeWithTag("entry_$demoEntryId").performTouchInput { longClick() }
        capture("records-selection")
        compose.onNodeWithContentDescription("Отмена").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("add_manual_record_fab").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Экспорт отчётов").performClick(); capture("export-xlsx")
        text("Счёт (PDF)"); capture("export-pdf"); back()
        compose.onNodeWithContentDescription("История отчётов").performClick(); capture("reports-history"); back()
    }

    @Test fun timerWindows() = themes {
        tag("nav_item_timer"); capture("timer")
        tag("manual_entry_button"); capture("timer-manual"); text("Отмена")
    }

    @Test fun timerLifecycleLight() = timerLifecycleWindows(ThemeMode.LIGHT)
    @Test fun timerLifecycleDark() = timerLifecycleWindows(ThemeMode.DARK)

    private fun timerLifecycleWindows(theme: ThemeMode) = themes(listOf(theme)) {
        tag("nav_item_timer"); tag("control_timer_button"); capture("timer-running")
        tag("pause_timer_button"); capture("timer-paused")
        compose.activityRule.scenario.recreate()
        tag("resume_timer_button"); tag("stop_timer_button"); capture("timer-finish")
        val activityBeforeFinish = compose.activity
        tag("confirm_finish_timer")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Сохранить запись").fetchSemanticsNodes().isNotEmpty() }
        capture("timer-save")
        check(compose.activity === activityBeforeFinish) { "Finishing the timer recreated the Activity" }
        compose.onNodeWithText("Не сохранять").performScrollTo(); text("Не сохранять")
        capture("timer-discard-confirm")
        compose.onAllNodesWithText("Не сохранять").onLast().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("control_timer_button").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun reportDetailsAndPaymentWindows() = themes {
        tag("nav_item_records")
        compose.onNodeWithContentDescription("История отчётов").performClick()
        tag("report_$demoReportId"); capture("report-details")
        compose.onNodeWithText("Удалить из истории").performScrollTo(); text("Удалить из истории")
        capture("report-delete-confirm"); text("Отмена"); back()
        compose.onNodeWithContentDescription("История отчётов").performClick()
        tag("report_pay_$demoReportId"); capture("report-payment")
        text("Отмена")
    }

    @Test fun dateAndTimeWindows() = themes {
        tag("nav_item_timer"); tag("manual_entry_button")
        compose.onNodeWithContentDescription("Изменить дату и время").performClick()
        capture("date-time"); back()
        compose.onNodeWithContentDescription("Изменить время работы").performClick()
        capture("duration"); back(); text("Отмена")
    }

    @Test fun reportPaymentAndDeletion() = themes {
        val now = System.currentTimeMillis()
        val entryId = runBlocking { database.timeEntryDao().insertTimeEntry(TimeEntry(
            clientId = demoClientId, startTime = now, endTime = now + 3600000,
            durationSeconds = 3600, rawDurationSeconds = 3600, appliedRate = 100.0, amount = 100.0,
            comment = "Демо • Оплата отчёта $now", status = TimeEntryStatus.INVOICED
        )) }
        val file = File(context.cacheDir, "audit-payment-$now.pdf").apply { writeText("%PDF-1.4\n%%EOF") }
        val reportId = runBlocking { database.reportDao().insertReport(Report(
            clientNames = "Демо • Оплата и удаление $now", periodLabel = "За весь период", exportFormat = "PDF",
            totalAmount = 100.0, totalDurationSeconds = 3600, entriesCount = 1,
            entryIdsJson = "[$entryId]", filePath = file.absolutePath, fileName = file.name
        )) }
        try {
            tag("nav_item_records")
            compose.onNodeWithContentDescription("История отчётов").performClick()
            tag("report_pay_$reportId")
            compose.onNodeWithText("Подтвердить").performScrollTo(); text("Подтвердить")
            compose.waitUntil(10000) { runBlocking {
                val report = database.reportDao().getReportById(reportId)
                report?.status == ReportStatus.PAID && report.paymentConfirmedAt != null &&
                    database.timeEntryDao().getTimeEntryById(entryId).first()?.status == TimeEntryStatus.PAID
            } }
            compose.onNodeWithContentDescription("История отчётов").performClick()
            tag("report_$reportId"); capture("report-paid-details")
            compose.onNodeWithText("Подтвердить оплату").assertDoesNotExist()
            compose.onNodeWithText("Удалить из истории").performScrollTo(); text("Удалить из истории")
            capture("report-paid-delete-confirm"); text("Удалить")
            compose.waitUntil(10000) { runBlocking { database.reportDao().getReportById(reportId) == null } }
            val preserved = runBlocking { database.timeEntryDao().getTimeEntryById(entryId).first() }
            check(preserved?.status == TimeEntryStatus.PAID && preserved.amount == 100.0 && preserved.durationSeconds == 3600L)
            check(!file.exists()) { "Deleting a report must delete only its own saved file" }
        } finally {
            runBlocking {
                database.reportDao().getReportById(reportId)?.let { database.reportDao().deleteReport(it) }
                database.timeEntryDao().deleteEntries(listOf(entryId))
            }
            file.delete()
        }
    }

    @Test fun settingsApplyChoices() = themes {
        val original = runBlocking { checkNotNull(database.appSettingsDao().getSettingsDirect()) }
        fun awaitSetting(predicate: (AppSettings) -> Boolean) {
            compose.waitUntil(10000) { runBlocking { database.appSettingsDao().getSettingsDirect()?.let(predicate) == true } }
            compose.waitForIdle()
        }
        fun choose(row: String, label: String) {
            compose.onNodeWithTag(row).performScrollTo(); tag(row)
            compose.onAllNodesWithText(label).onLast().performScrollTo(); text(label)
        }
        try {
            tag("nav_item_settings")
            tag("settings_rate")
            compose.onNodeWithTag("settings_rate_input").performTextReplacement("1234,50")
            closeKeyboard(); text("Сохранить")
            awaitSetting { it.globalHourlyRate == 1234.5 }
            capture("settings-rate-saved")
            for (currency in listOf("USD", "EUR", "RUB", "GBP", "CHF", "JPY", "KZT", "BYN")) {
                choose("settings_currency", currency)
                awaitSetting { it.currency == currency }
            }
            capture("settings-currency-applied")
            for ((minutes, label) in listOf(0 to "Без округления", 5 to "5 минут", 10 to "10 минут", 15 to "15 минут", 30 to "30 минут")) {
                choose("settings_time_rounding", label)
                awaitSetting { it.timeRoundingMinutes == minutes }
            }
            for ((mode, label) in listOf(
                AmountRoundingMode.NONE to "Без дополнительного округления",
                AmountRoundingMode.ROUND_UP to "В большую сторону",
                AmountRoundingMode.ROUND_DOWN to "В меньшую сторону",
                AmountRoundingMode.ROUND_NEAREST to "До ближайшего значения"
            )) {
                choose("settings_amount_rounding", label)
                awaitSetting { it.amountRoundingMode == mode }
            }
            capture("settings-rounding-applied")
            for ((mode, label) in listOf(ThemeMode.LIGHT to "Светлая", ThemeMode.DARK to "Тёмная", ThemeMode.SYSTEM to "Как на устройстве")) {
                choose("settings_theme", label)
                awaitSetting { it.themeMode == mode }
                compose.onNodeWithText(label).assertIsDisplayed()
            }
            capture("settings-system-theme")
        } finally {
            runBlocking { database.appSettingsDao().saveSettings(original) }
            compose.waitForIdle()
        }
    }

    @Test fun zeroAmountSessionAndManualRecord() = themes {
        val original = runBlocking { checkNotNull(database.appSettingsDao().getSettingsDirect()) }
        val name = "Демо • Нулевая ставка ${System.currentTimeMillis()}"
        val client = Client(name = name, colorHex = "#D0BCFF", defaultHourlyRate = 0.0)
        val clientId = runBlocking {
            database.appSettingsDao().saveSettings(original.copy(globalHourlyRate = 0.0,
                timeRoundingMinutes = 0, amountRoundingMode = AmountRoundingMode.NONE))
            database.clientDao().insertClient(client)
        }
        val lastClient = com.example.ui.screens.timer.TimerStateManager.getLastSelectedClientId()
        var ownsTimer = false
        fun selectClient() {
            compose.onNodeWithTag("client_selector_field").performScrollTo(); tag("client_selector_field")
            compose.onAllNodesWithText(name).onLast().performScrollTo(); text(name)
        }
        fun awaitEntry(comment: String, duration: Long) = compose.waitUntil(10000) { runBlocking {
            database.timeEntryDao().getAllTimeEntries().first().any {
                it.clientId == clientId && it.comment == comment && it.durationSeconds == duration && it.amount == 0.0
            }
        } }
        try {
            check(com.example.ui.screens.timer.TimerStateManager.timerState.value.status == com.example.ui.screens.timer.TimerStatus.IDLE)
            tag("nav_item_timer"); tag("control_timer_button"); ownsTimer = true
            compose.waitUntil(6000) { com.example.ui.screens.timer.TimerStateManager.getElapsedSeconds() >= 1 }
            tag("stop_timer_button"); tag("confirm_finish_timer")
            selectClient()
            compose.onNode(hasSetTextAction() and hasText("Комментарий")).performScrollTo().performTextReplacement("Демо • Сохранённая сессия")
            closeKeyboard()
            compose.onNodeWithText("Сумма записи будет 0. Проверьте ставку и время работы.").performScrollTo()
            capture("session-zero-amount")
            compose.onAllNodesWithText("Сохранить").onLast().performScrollTo(); text("Сохранить")
            awaitEntry("Демо • Сохранённая сессия", 60)
            tag("nav_item_records"); tag("add_manual_record_fab")
            selectClient()
            compose.onNodeWithTag("dialog_manual_comment_input").performScrollTo().performTextReplacement("Демо • Ручная запись списка")
            closeKeyboard()
            compose.onNodeWithText("Сумма записи будет 0. Проверьте ставку и время работы.").performScrollTo()
            capture("record-manual-zero-amount")
            compose.onAllNodesWithText("Сохранить").onLast().performScrollTo(); text("Сохранить")
            awaitEntry("Демо • Ручная запись списка", 3600)
        } finally {
            if (ownsTimer) InstrumentationRegistry.getInstrumentation().runOnMainSync {
                com.example.ui.screens.timer.TimerService.stopService(context)
                com.example.ui.screens.timer.TimerStateManager.stop()
                lastClient?.let { com.example.ui.screens.timer.TimerStateManager.setLastSelectedClientId(it) }
            }
            if (ownsTimer) check(context.getSharedPreferences("TimeSheetProTimerPrefs", 0).edit().commit())
            runBlocking {
                val ids = database.timeEntryDao().getAllTimeEntries().first().filter { it.clientId == clientId }.map { it.id }
                database.timeEntryDao().deleteEntries(ids)
                database.clientDao().deleteClient(client.copy(id = clientId))
                database.appSettingsDao().saveSettings(original)
            }
        }
    }

    @Test fun manualSaveAndClientEditing() = themes {
        val name = "Демо • Проверка сохранения ${System.currentTimeMillis()}"
        tag("nav_item_clients"); tag("add_client_fab")
        compose.onNodeWithTag("dialog_client_name_input").performTextReplacement(name)
        compose.onNodeWithTag("dialog_client_rate_input").performTextReplacement("100")
        closeKeyboard()
        compose.onNodeWithTag("dialog_confirm_button").performScrollTo(); tag("dialog_confirm_button")
        var createdClientId = 0L
        compose.waitUntil(10000) { runBlocking {
            createdClientId = database.clientDao().getAllClients().first().find { it.name == name }?.id ?: 0L
            createdClientId != 0L
        } }
        tag("nav_item_timer"); tag("manual_entry_button")
        compose.onNodeWithTag("client_selector_field").assertIsEnabled().performScrollTo(); tag("client_selector_field")
        capture("client-picker")
        compose.onAllNodesWithText(name).onLast().performScrollTo(); text(name)
        compose.onAllNodesWithText("Сохранить").onLast().performScrollTo(); text("Сохранить")
        var createdEntryId = 0L
        compose.waitUntil(10000) { runBlocking {
            val entry = database.timeEntryDao().getAllTimeEntries().first().find { it.clientId == createdClientId }
            createdEntryId = entry?.id ?: 0L
            entry != null && entry.durationSeconds == 3600L && kotlin.math.abs(entry.amount - 100.0) < 0.01
        } }
        tag("nav_item_records")
        compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("entry_$createdEntryId"))
        tag("entry_$createdEntryId"); capture("saved-record")
        compose.onNodeWithTag("edit_comment_input").performTextReplacement("Демо • Запись сохранена через интерфейс")
        closeKeyboard()
        compose.onAllNodesWithText("Сохранить").onLast().performScrollTo(); text("Сохранить")
        compose.waitUntil(10000) { runBlocking {
            database.timeEntryDao().getAllTimeEntries().first().any { it.id == createdEntryId && it.comment == "Демо • Запись сохранена через интерфейс" }
        } }
        // Only remove the two fixtures created by this test; never touch other preview records.
        tag("entry_$createdEntryId")
        compose.onNodeWithText("Удалить запись").performScrollTo(); text("Удалить запись")
        capture("record-delete-confirm"); text("Удалить")
        compose.waitUntil(10000) { runBlocking { database.timeEntryDao().getAllTimeEntries().first().none { it.id == createdEntryId } } }
        tag("nav_item_clients")
        compose.onNodeWithTag("clients_lazy_column").performScrollToNode(hasTestTag("client_card_$createdClientId"))
        tag("client_card_$createdClientId")
        compose.onNodeWithTag("dialog_client_rate_input").performTextReplacement("120")
        closeKeyboard()
        compose.onNodeWithTag("dialog_confirm_button").performScrollTo(); tag("dialog_confirm_button")
        compose.waitUntil(10000) { runBlocking { database.clientDao().getAllClients().first().any { it.id == createdClientId && it.defaultHourlyRate == 120.0 } } }
        tag("client_card_$createdClientId")
        compose.onNodeWithTag("dialog_delete_button").performScrollTo(); tag("dialog_delete_button")
        text("Удалить")
        compose.waitUntil(10000) { runBlocking { database.clientDao().getAllClients().first().none { it.id == createdClientId && !it.isArchived } } }
    }

    @Test fun clientDeletionPreservesLinkedData() = themes {
        val landscape = InstrumentationRegistry.getArguments().getString("auditVariant") == "landscape"
        compose.activityRule.scenario.onActivity { it.requestedOrientation = if (landscape)
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == if (landscape)
            android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT }
        val now = System.currentTimeMillis()
        val name = "Демо • Заказчик со связанными записями $now"
        val clientId = runBlocking { database.clientDao().insertClient(Client(name = name, colorHex = "#D0BCFF", defaultHourlyRate = 100.0)) }
        val client = runBlocking { database.clientDao().getAllClients().first().single { it.id == clientId } }
        val rate = RateHistoryEntry(clientId = clientId, rate = 100.0, effectiveFrom = now - 86400000L).let {
            it.copy(id = runBlocking { database.rateHistoryDao().insertEntry(it) })
        }
        val entry = TimeEntry(clientId = clientId, startTime = now, endTime = now + 3600000,
            durationSeconds = 3600, rawDurationSeconds = 3600, comment = "Демо • Связанная оплаченная запись",
            appliedRate = 100.0, amount = 100.0, status = TimeEntryStatus.PAID).let {
            it.copy(id = runBlocking { database.timeEntryDao().insertTimeEntry(it) })
        }
        val file = File(context.cacheDir, "audit-linked-client-$now.txt").apply { writeText("Own attachment fixture") }
        val fileBytes = file.readBytes()
        val report = Report(clientNames = name, periodLabel = "Демонстрация", exportFormat = "PDF",
            totalAmount = 100.0, totalDurationSeconds = 3600, entriesCount = 1, entryIdsJson = "[${entry.id}]",
            filePath = file.absolutePath, fileName = file.name, status = ReportStatus.PAID,
            paymentPhotoUri = android.net.Uri.fromFile(file).toString(), paymentConfirmedAt = now,
            paymentNotes = "Демо • Сохранить оплату и вложение").let {
            it.copy(id = runBlocking { database.reportDao().insertReport(it) })
        }
        fun assertLinkedData() = runBlocking {
            check(database.timeEntryDao().getTimeEntryById(entry.id).first() == entry)
            check(database.rateHistoryDao().getHistoryForClient(clientId).first().single() == rate)
            check(database.reportDao().getReportById(report.id) == report)
            check(file.readBytes().contentEquals(fileBytes))
        }
        try {
            tag("nav_item_clients")
            compose.onNodeWithTag("clients_lazy_column").performScrollToNode(hasTestTag("client_card_$clientId"))
            tag("client_card_$clientId")
            compose.onNodeWithTag("dialog_delete_button").performScrollTo(); tag("dialog_delete_button")
            capture("client-linked-delete-confirm")
            text("Отмена")
            check(runBlocking { database.clientDao().getAllClients().first().single { it.id == clientId } } == client)
            assertLinkedData()
            tag("dialog_delete_button"); text("Удалить")
            compose.waitUntil(10000) { runBlocking { database.clientDao().getAllClients().first().none { it.id == clientId } } }
            assertLinkedData()
            tag("nav_item_records")
            compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("entry_${entry.id}"))
            compose.onNodeWithTag("entry_${entry.id}").assertIsDisplayed()
            capture("client-linked-record-retained")
        } finally {
            runBlocking {
                database.reportDao().deleteReport(report)
                database.timeEntryDao().deleteEntries(listOf(entry.id))
                database.rateHistoryDao().deleteEntry(rate)
                database.clientDao().deleteClient(client)
            }
            check(file.delete() || !file.exists())
        }
    }

    @Test fun paymentStatusAndBulkDeletion() = themes {
        val now = System.currentTimeMillis()
        val ids = runBlocking {
            (0..1).map { index -> database.timeEntryDao().insertTimeEntry(TimeEntry(
                clientId = demoClientId, startTime = now - index * 60000, endTime = now + 3600000 - index * 60000,
                durationSeconds = 3600, rawDurationSeconds = 3600, appliedRate = 100.0, amount = 100.0,
                comment = "Демо • Массовые действия $now / $index", status = TimeEntryStatus.NOT_PAID
            )) }
        }
        fun awaitStatuses(status: TimeEntryStatus) = compose.waitUntil(10000) { runBlocking {
            database.timeEntryDao().getAllTimeEntries().first().filter { it.id in ids }
                .let { it.size == 2 && it.all { entry -> entry.status == status } }
        } }
        fun selectBoth() {
            compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("entry_${ids.first()}"))
            compose.onNodeWithTag("entry_${ids.first()}").performTouchInput { longClick() }
            tag("entry_${ids.last()}")
            compose.onNodeWithText("Выбрано: 2").assertIsDisplayed()
        }
        try {
            tag("nav_item_records")
            compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("entry_${ids.first()}"))
            tag("status_badge_${ids.first()}"); capture("record-status-choice")
            text("Счёт выставлен")
            compose.waitUntil(10000) { runBlocking {
                database.timeEntryDao().getTimeEntryById(ids.first()).first()?.status == TimeEntryStatus.INVOICED
            } }
            tag("status_badge_${ids.first()}"); capture("record-status-selected"); back()
            selectBoth()
            compose.onNodeWithContentDescription("Изменить статус").performClick()
            capture("records-bulk-status-choice")
            text("Оплачено"); awaitStatuses(TimeEntryStatus.PAID)
            selectBoth()
            compose.onNodeWithContentDescription("Удалить").performClick()
            capture("records-bulk-delete-confirm"); text("Отмена")
            awaitStatuses(TimeEntryStatus.PAID)
            compose.onNodeWithContentDescription("Удалить").performClick(); text("Удалить")
            compose.waitUntil(10000) { runBlocking {
                database.timeEntryDao().getAllTimeEntries().first().none { it.id in ids }
            } }
        } finally {
            // These IDs belong only to this test, including after a failed UI assertion.
            runBlocking { database.timeEntryDao().deleteEntries(ids) }
        }
    }

    @Test fun deterministicChoiceDialogs() = themes {
        check(context.resources.configuration.fontScale == 1f) { "Golden comparison requires font scale 100%" }
        compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        runBlocking {
            val settings = database.appSettingsDao().getSettingsFlow().first() ?: AppSettings()
            database.appSettingsDao().saveSettings(settings.copy(currency = "RUB", globalHourlyRate = 1500.0, timeRoundingMinutes = 0,
                amountRoundingMode = AmountRoundingMode.NONE))
        }
        tag("nav_item_settings")
        for (choice in listOf("currency", "time_rounding", "amount_rounding", "theme")) {
            compose.onNodeWithTag("settings_scroll").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {
                it(0f, -100000f)
            }
            compose.waitForIdle()
            tag("settings_$choice")
            awaitRenderedFrame()
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5000)
            compose.onNodeWithContentDescription("Закрыть").assertIsDisplayed()
            val screenshot = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            val insets = compose.activity.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.systemBars())
            // Capture the real dialog window; Compose captureToImage can copy the underlying activity.
            // System bars contain the wall clock and are excluded from deterministic comparison.
            val bitmap = Bitmap.createBitmap(screenshot, 0, insets.top, screenshot.width,
                screenshot.height - insets.top - insets.bottom)
            screenshot.recycle()
            val name = "$themeName-dialog-$choice.png"
            val directory = checkNotNull(context.getExternalFilesDir("visual-goldens")).apply { mkdirs() }
            File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (InstrumentationRegistry.getArguments().getString("recordGoldens") != "true") {
                val expected = InstrumentationRegistry.getInstrumentation().context.assets.open("visual-goldens/$name")
                    .use { android.graphics.BitmapFactory.decodeStream(it) }
                // Compare the sheet itself: underlying scroll and pressed/focus state belong to separate screen tests.
                // A modifier on ModalBottomSheet lies outside its anchored offset in alpha28.
                // Its children report the actual on-screen position after that offset.
                val handleBounds = compose.onNodeWithTag("choice_dialog_handle", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
                val contentBounds = compose.onNodeWithTag("choice_dialog_content", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
                val top = (handleBounds.top.toInt() - insets.top).coerceIn(0, bitmap.height - 1)
                val bottom = (contentBounds.bottom.toInt() - insets.top).coerceAtMost(bitmap.height)
                check(bottom > top) { "Invalid sheet comparison bounds: $handleBounds / $contentBounds" }
                val actualDialog = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, bottom - top)
                val expectedDialog = Bitmap.createBitmap(expected, 0, top, expected.width, bottom - top)
                org.junit.Assert.assertTrue("Dialog changed: $name (ROI $top..$bottom; actual image in visual-goldens)", actualDialog.sameAs(expectedDialog))
                actualDialog.recycle()
                expectedDialog.recycle()
                expected.recycle()
            }
            back()
        }
    }

    @Test fun adaptiveWindows() = themes {
        val landscape = InstrumentationRegistry.getArguments().getString("auditVariant", "").orEmpty().startsWith("landscape")
        val expected = if (landscape) android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT
        compose.activityRule.scenario.onActivity { it.requestedOrientation = if (landscape)
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == expected }
        for (route in listOf("timer", "records", "clients", "settings")) {
            tag("nav_item_$route"); capture("adaptive-$route")
        }
        tag("settings_rate"); capture("adaptive-rate"); text("Отмена")
        tag("nav_item_timer")
        compose.onNodeWithTag("manual_entry_button").performScrollTo(); tag("manual_entry_button")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("client_selector_field").fetchSemanticsNodes().isNotEmpty() }
        capture("adaptive-manual")
        compose.onNodeWithContentDescription("Изменить время работы").performScrollTo().performClick()
        capture("adaptive-duration")
        compose.onAllNodesWithText("Отмена").onLast().assertIsDisplayed()
        text("Отмена")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Выбор времени").fetchSemanticsNodes().isEmpty() }
        closeKeyboard()
        compose.onAllNodesWithText("Сохранить").onLast().performScrollTo().assertIsDisplayed()
        capture("adaptive-manual-actions")
        compose.onAllNodesWithText("Отмена").onLast().performScrollTo(); text("Отмена")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("client_selector_field").fetchSemanticsNodes().isEmpty() }
        tag("nav_item_records")
        compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("status_badge_$demoEntryId"))
        tag("entry_$demoEntryId"); capture("adaptive-record-edit")
        compose.onAllNodesWithText("Сохранить").onLast().performScrollTo().assertIsDisplayed()
        capture("adaptive-record-actions")
        compose.onAllNodesWithText("Отмена").onLast().performScrollTo(); text("Отмена")
        tag("status_badge_$demoEntryId"); capture("adaptive-status-choice"); back()
        compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("entry_$demoEntryId"))
        compose.onNodeWithTag("entry_$demoEntryId").performTouchInput { longClick() }
        capture("adaptive-selection")
        compose.onNodeWithContentDescription("Экспортировать отчёт").assertIsDisplayed()
        compose.onNodeWithContentDescription("Изменить статус").assertIsDisplayed()
        compose.onNodeWithContentDescription("Удалить").assertIsDisplayed()
        compose.onNodeWithContentDescription("Изменить статус").performClick()
        capture("adaptive-bulk-status-choice"); back()
        compose.onNodeWithContentDescription("Экспортировать отчёт").performClick()
        compose.onNodeWithText("Excel (XLSX)").performScrollTo(); text("Excel (XLSX)")
        compose.onNodeWithTag("bulk_column_SUM").performScrollTo().assertIsOn()
        capture("adaptive-selected-columns")
        compose.onNodeWithText("Создать отчёт").performScrollTo().assertIsDisplayed()
        capture("adaptive-selected-actions"); back()
        compose.onNodeWithContentDescription("Отмена").performClick()
        compose.onNodeWithContentDescription("Фильтры").performClick(); capture("adaptive-filters")
        compose.onNodeWithText("Применить").performScrollTo(); capture("adaptive-filter-actions")
        compose.onNodeWithTag("filter_client_search").performScrollTo().performTextReplacement("Международная")
        closeKeyboard()
        compose.onNodeWithTag("filter_client_search").assertTextContains("Международная")
        capture("adaptive-filter-search"); back()
        tag("nav_item_clients"); tag("add_client_fab"); capture("adaptive-client")
        compose.onNodeWithTag("dialog_client_name_input").performTouchInput { click() }
        var keyboardTop = 0
        compose.waitUntil(5000) {
            var visible = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val view = android.view.inspector.WindowInspector.getGlobalWindowViews().firstOrNull {
                    it.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
                }
                if (view != null) {
                    visible = true
                    keyboardTop = view.height - checkNotNull(view.rootWindowInsets).getInsets(android.view.WindowInsets.Type.ime()).bottom
                }
            }
            visible
        }
        capture("adaptive-keyboard")
        val fieldBounds = compose.onNodeWithTag("dialog_client_name_input").fetchSemanticsNode().boundsInWindow
        check(fieldBounds.bottom <= keyboardTop) { "Focused name field is covered by the keyboard: $fieldBounds / top=$keyboardTop" }
        closeKeyboard()
        compose.onNodeWithTag("client_color_picker").performScrollTo(); tag("client_color_picker")
        capture("adaptive-client-colors"); back()
        compose.onNodeWithTag("dialog_confirm_button").performScrollTo().assertIsDisplayed()
        capture("adaptive-client-actions")
        compose.onAllNodesWithText("Отмена").onLast().performScrollTo(); text("Отмена")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("dialog_client_name_input").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun receiptDocumentAndRestoration() = themes {
        val now = System.currentTimeMillis()
        val displayName = "receipt-$now.png"
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TimeSheetProAudit")
            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
        }
        val imageUri = checkNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).apply {
            drawColor(android.graphics.Color.WHITE)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK; textSize = 32f
            }
            drawText("TimeSheetPro / test receipt", 36f, 90f, paint)
            drawText("Amount: 100.00 RUB", 36f, 160f, paint)
            drawText("Demonstration data", 36f, 230f, paint)
        }
        checkNotNull(resolver.openOutputStream(imageUri)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        resolver.update(imageUri, android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        val entryId = runBlocking { database.timeEntryDao().insertTimeEntry(TimeEntry(
            clientId = demoClientId, startTime = now, endTime = now + 3600000,
            durationSeconds = 3600, rawDurationSeconds = 3600, appliedRate = 100.0, amount = 100.0,
            comment = "Демо • Чек $now", status = TimeEntryStatus.INVOICED)) }
        val file = File(context.cacheDir, "audit-receipt-$now.pdf").apply { writeText("%PDF-1.4\n%%EOF") }
        val reportId = runBlocking { database.reportDao().insertReport(Report(
            clientNames = "Демо • Чек $now", periodLabel = "За весь период", exportFormat = "PDF",
            totalAmount = 100.0, totalDurationSeconds = 3600, entriesCount = 1,
            entryIdsJson = "[$entryId]", filePath = file.absolutePath, fileName = file.name)) }
        var persistedUri: android.net.Uri? = null
        var imageDeleted = false
        try {
            tag("nav_item_records")
            compose.onNodeWithContentDescription("История отчётов").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("report_pay_$reportId").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("report_pay_$reportId").performScrollTo().assertIsDisplayed()
            tag("report_pay_$reportId")
            try { compose.waitUntil(10000) {
                compose.onAllNodesWithText("Приложить чек").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            } } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
                capture("receipt-payment-open-failure"); throw failure
            }
            compose.onNodeWithText("Приложить чек").performScrollTo(); text("Приложить чек")
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            fun documentNode() = automation.windows.mapNotNull { it.root }
                .filter { it.packageName?.toString()?.endsWith(".documentsui") == true }
                .flatMap { it.findAccessibilityNodeInfosByText(displayName) }
                .firstOrNull()
            try { compose.waitUntil(10000) { documentNode() != null } }
            catch (failure: androidx.compose.ui.test.ComposeTimeoutException) { capture("receipt-picker-failure"); throw failure }
            capture("receipt-system-picker")
            var target = checkNotNull(documentNode())
            var documentBounds = android.graphics.Rect().also { target.getBoundsInScreen(it) }
            // Grid filenames can be hidden children. Tap the first visible document cell,
            // rather than walking up to a clickable recycler whose centre is a different file.
            while (!target.isVisibleToUser || documentBounds.isEmpty) {
                target = checkNotNull(target.parent) { "The receipt document has no visible parent" }
                documentBounds = android.graphics.Rect().also { target.getBoundsInScreen(it) }
            }
            if (!target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                val bounds = documentBounds
                check(target.isVisibleToUser && !bounds.isEmpty) { "The receipt document has no visible click target: $bounds / ${target.className}" }
                val time = android.os.SystemClock.uptimeMillis()
                for ((action, eventTime) in listOf(android.view.MotionEvent.ACTION_DOWN to time, android.view.MotionEvent.ACTION_UP to time + 50)) {
                    val event = android.view.MotionEvent.obtain(time, eventTime, action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                    try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
                }
            }
            try { compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Чек оплаты").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() } }
            catch (failure: androidx.compose.ui.test.ComposeTimeoutException) { capture("receipt-after-click-failure"); throw failure }
            capture("receipt-payment-preview")
            compose.onNodeWithContentDescription("Чек оплаты").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Чек целиком").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Уменьшить чек").assertIsNotEnabled()
            capture("receipt-viewer")
            compose.onNodeWithContentDescription("Увеличить чек").performClick()
            compose.onNodeWithText("Чек оплаты · 150%").assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Чек целиком").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            compose.onNodeWithText("Чек оплаты · 150%").assertIsDisplayed()
            capture("receipt-viewer-restored")
            text("Сбросить масштаб")
            compose.onNodeWithContentDescription("Чек целиком").performTouchInput {
                pinch(start0 = center - androidx.compose.ui.geometry.Offset(60f, 0f),
                    end0 = center - androidx.compose.ui.geometry.Offset(120f, 0f),
                    start1 = center + androidx.compose.ui.geometry.Offset(60f, 0f),
                    end1 = center + androidx.compose.ui.geometry.Offset(120f, 0f), durationMillis = 400)
            }
            compose.onNodeWithContentDescription("Уменьшить чек").assertIsEnabled()
            capture("receipt-viewer-gesture")
            compose.onNodeWithTag("receipt_viewport").performTouchInput {
                swipe(start = center, end = center + androidx.compose.ui.geometry.Offset(200f, 200f))
            }
            capture("receipt-viewer-panned")
            text("Сбросить масштаб")
            repeat(4) { compose.onNodeWithContentDescription("Увеличить чек").performClick() }
            compose.onNodeWithText("Чек оплаты · 500%").assertIsDisplayed()
            compose.onNodeWithContentDescription("Увеличить чек").assertIsNotEnabled()
            repeat(2) { compose.onNodeWithTag("receipt_viewport").performTouchInput {
                swipe(start = center - androidx.compose.ui.geometry.Offset(width * 0.3f, 0f),
                    end = center + androidx.compose.ui.geometry.Offset(width * 0.3f, 0f))
            } }
            val maximum = capture("receipt-viewer-maximum-panned")
            val bounds = compose.onNodeWithTag("receipt_viewport").fetchSemanticsNode().boundsInWindow
            val shot = checkNotNull(android.graphics.BitmapFactory.decodeFile(maximum.absolutePath))
            try {
                // The own fixture is white in its centre. It must remain visible after large pans,
                // including a wide viewport where the fitted portrait image has empty side space.
                val pixel = shot.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt())
                check(android.graphics.Color.red(pixel) > 230 && android.graphics.Color.green(pixel) > 230 && android.graphics.Color.blue(pixel) > 230) {
                    "The receipt disappeared from the viewport after panning"
                }
            } finally { shot.recycle() }
            text("Сбросить масштаб"); compose.onNodeWithText("Чек оплаты · 100%").assertIsDisplayed(); back()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Чек оплаты").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            compose.onNodeWithText("Подтвердить").performScrollTo(); text("Подтвердить")
            compose.waitUntil(10000) { runBlocking { database.reportDao().getReportById(reportId)?.status == ReportStatus.PAID } }
            val report = runBlocking { checkNotNull(database.reportDao().getReportById(reportId)) }
            persistedUri = android.net.Uri.parse(checkNotNull(report.paymentPhotoUri))
            check(resolver.persistedUriPermissions.any { it.uri == persistedUri && it.isReadPermission })
            compose.onNodeWithContentDescription("История отчётов").performClick(); tag("report_$reportId")
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Чек перевода").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Чек перевода").performScrollTo(); capture("receipt-report-details")
            compose.onNodeWithContentDescription("Чек перевода").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Чек целиком").fetchSemanticsNodes().isNotEmpty() }
            capture("receipt-viewer-from-report"); back(); back()
            resolver.delete(imageUri, null, null)
            imageDeleted = true
            compose.activityRule.scenario.recreate(); tag("nav_item_records")
            compose.onNodeWithContentDescription("История отчётов").performClick(); tag("report_$reportId")
            val unavailable = "Чек недоступен: файл перемещён или доступ отозван"
            compose.waitUntil(10000) { compose.onAllNodesWithText(unavailable).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(unavailable).performScrollTo(); capture("receipt-unavailable"); back()
            check(runBlocking { database.timeEntryDao().getTimeEntryById(entryId).first()?.status } == TimeEntryStatus.PAID)
        } finally {
            persistedUri?.takeIf { uri -> resolver.persistedUriPermissions.any { it.uri == uri } }?.let {
                resolver.releasePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (!imageDeleted) resolver.delete(imageUri, null, null)
            runBlocking {
                database.reportDao().getReportById(reportId)?.let { database.reportDao().deleteReport(it) }
                database.timeEntryDao().deleteEntries(listOf(entryId))
            }
            file.delete()
        }
    }

    @Test fun selectedExportAndColumns() = themes {
        val now = System.currentTimeMillis()
        val testClient = Client(name = "Демо • Выбранный экспорт $now", colorHex = "#625DA0", defaultHourlyRate = 100.0)
        val clientId = runBlocking { database.clientDao().insertClient(testClient) }
        val ids = runBlocking { (0..1).map { index -> database.timeEntryDao().insertTimeEntry(TimeEntry(
            clientId = clientId, startTime = now - index * 1000, endTime = now + 3600000 - index * 1000,
            durationSeconds = 3600, rawDurationSeconds = 3600, appliedRate = 100.0, amount = 100.0,
            comment = "audit-omitted-comment-$now-$index")) } }
        val reportIds = mutableListOf<Long>()
        try {
            tag("nav_item_records")
            for (format in listOf("Excel (XLSX)", "Счёт (PDF)")) {
                compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("entry_${ids.first()}"))
                compose.onNodeWithTag("entry_${ids.first()}").performTouchInput { longClick() }
                compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("entry_${ids.last()}"))
                tag("entry_${ids.last()}")
                compose.onNodeWithContentDescription("Экспортировать отчёт").performClick()
                text(format)
                if (format.contains("XLSX")) {
                    for (column in listOf("DATE", "CLIENT", "COMMENT")) {
                        val node = compose.onNodeWithTag("bulk_column_$column").performScrollTo()
                        if (node.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] == androidx.compose.ui.state.ToggleableState.Off) {
                            node.performClick()
                        }
                        node.assertIsOn().performClick().assertIsOff()
                    }
                    val sum = compose.onNodeWithTag("bulk_column_SUM")
                    sum.assertIsOn().performClick(); sum.assertIsOn()
                    capture("selected-export-sum-only")
                    // Every column can be restored; selecting only SUM must still produce a valid file.
                    for (column in listOf("DATE", "CLIENT", "COMMENT")) {
                        compose.onNodeWithTag("bulk_column_$column").performClick().assertIsOn().performClick().assertIsOff()
                    }
                } else capture("selected-export-pdf")
                compose.onNodeWithText("Создать отчёт").performScrollTo()
                val previousId = runBlocking { database.reportDao().getAllReports().first().maxOfOrNull { it.id } ?: 0L }
                val previousAutoAdvance = compose.mainClock.autoAdvance
                compose.mainClock.autoAdvance = false
                val report = try {
                    compose.onNodeWithText("Создать отчёт").performClick(); compose.mainClock.advanceTimeByFrame()
                    compose.waitUntil(20000) { runBlocking { database.reportDao().getAllReports().first().any { it.id > previousId } } }
                    runBlocking { database.reportDao().getAllReports().first().single { it.id > previousId } }
                } finally { compose.mainClock.autoAdvance = previousAutoAdvance }
                reportIds += report.id
                val included = org.json.JSONArray(report.entryIdsJson).let { array ->
                    (0 until array.length()).map { array.getLong(it) }.toSet()
                }
                check(included == ids.toSet() && report.entriesCount == 2 && report.totalDurationSeconds == 7200L && report.totalAmount == 200.0)
                val file = File(report.filePath)
                check(file.isFile && file.length() > 0)
                if (format.contains("XLSX")) {
                    java.util.zip.ZipFile(file).use { zip ->
                        val sheet = zip.getInputStream(checkNotNull(zip.getEntry("xl/worksheets/sheet1.xml")))
                            .bufferedReader().use { it.readText() }
                        check(!sheet.contains("audit-omitted-comment-$now"))
                        check(Regex("<v>100(?:\\.0+)?</v>").findAll(sheet).count() == 2)
                    }
                } else check(file.inputStream().use { stream ->
                    val header = ByteArray(5)
                    stream.read(header) == 5 && String(header, Charsets.US_ASCII) == "%PDF-"
                })
                compose.waitUntil(10000) { compose.onAllNodesWithText("Отчёт готов").fetchSemanticsNodes().isNotEmpty() }
                capture("selected-export-success-${report.exportFormat.lowercase()}"); back()
            }
        } finally {
            runBlocking {
                reportIds.forEach { id -> database.reportDao().getReportById(id)?.let { report ->
                    File(report.filePath).delete(); database.reportDao().deleteReport(report)
                } }
                database.timeEntryDao().deleteEntries(ids)
                database.clientDao().deleteClient(testClient.copy(id = clientId))
            }
        }
    }

    @Test fun componentCatalogWindows() {
        try {
            tag("nav_item_timer")
            for (dark in listOf(false, true)) {
                themeName = if (dark) "dark" else "light"
                compose.activityRule.scenario.onActivity { activity ->
                    androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                        .isAppearanceLightStatusBars = !dark
                    activity.setContent {
                        androidx.compose.runtime.key(dark) {
                            com.example.ui.theme.MyApplicationTheme(darkTheme = dark) {
                                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier
                                    .background(androidx.compose.material3.MaterialTheme.colorScheme.background).systemBarsPadding()) {
                                    com.example.ui.theme.ComponentCatalog()
                                }
                            }
                        }
                    }
                }
                capture("catalog-timer")
                compose.onNodeWithTag("catalog_actions").performScrollTo(); capture("catalog-actions")
                compose.onNodeWithTag("catalog_fields").performScrollTo(); capture("catalog-fields")
                val checkbox = compose.onNodeWithText("Включить комментарии")
                checkbox.performScrollTo().assertIsOff().performClick(); checkbox.assertIsOn()
                compose.onNodeWithTag("catalog_statuses").performScrollTo(); capture("catalog-statuses")
            }
        } finally {
            // The catalogue replaces only this test's Activity composition, never the production navigation.
            compose.activityRule.scenario.recreate()
        }
    }

    @Test fun filtersAndPeriods() = themes {
        val now = System.currentTimeMillis()
        val name = "Демо • Проверка фильтров $now"
        val testClient = Client(name = name, colorHex = "#625DA0", defaultHourlyRate = 100.0)
        val clientId = runBlocking { database.clientDao().insertClient(testClient) }
        val ids = runBlocking {
            (0..3).map { index ->
                val start = if (index == 3) now - 40L * 24 * 3600000 else now - index * 1000
                database.timeEntryDao().insertTimeEntry(TimeEntry(clientId = clientId,
                    startTime = start, endTime = start + 3600000, durationSeconds = 3600,
                    rawDurationSeconds = 3600, comment = "$name / $index", appliedRate = 100.0,
                    amount = 100.0, status = TimeEntryStatus.entries[index % 3]))
            }
        }
        fun openFilters() { compose.onNodeWithContentDescription("Фильтры").performClick() }
        fun applyFilters() { compose.onNodeWithText("Применить").performScrollTo(); text("Применить") }
        fun assertEntries(expected: List<Long>) {
            compose.waitUntil(10000) { expected.any { compose.onAllNodesWithTag("entry_$it").fetchSemanticsNodes().isNotEmpty() } }
            expected.forEach { id ->
                compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("entry_$id"))
                compose.onNodeWithTag("entry_$id").assertIsDisplayed()
            }
            (ids - expected.toSet()).forEach { compose.onNodeWithTag("entry_$it").assertDoesNotExist() }
        }
        try {
            tag("nav_item_records")
            compose.onNodeWithContentDescription("Предыдущий месяц").performClick(); capture("records-previous-month")
            compose.onNodeWithContentDescription("Следующий месяц").performClick()
            openFilters()
            compose.onNodeWithTag("filter_client_search").performScrollTo().performTextReplacement("нет такого заказчика $now")
            closeKeyboard(); compose.onNodeWithText("Заказчики не найдены").assertIsDisplayed(); capture("filter-search-empty")
            compose.onNodeWithContentDescription("Очистить поиск").performClick()
            compose.onNodeWithTag("filter_client_search").performTextReplacement(name)
            closeKeyboard()
            val client = compose.onNodeWithTag("filter_client_$clientId")
            client.performScrollTo().assertIsOff().performClick(); client.assertIsOn()
            // A single merged checkbox target carries both the label and the checked state.
            client.assertTextContains(name)
            capture("filter-client-selected")
            compose.onNodeWithText("Все").performScrollTo(); text("Все")
            compose.onNodeWithText("Старые").performScrollTo(); text("Старые")
            applyFilters()
            compose.onNodeWithTag("records_list").performScrollToIndex(0)
            compose.onNodeWithTag("entry_${ids.last()}").assertIsDisplayed()
            assertEntries(ids); capture("filter-all-oldest")
            openFilters(); text("Новые"); applyFilters()
            compose.onNodeWithTag("records_list").performScrollToIndex(0)
            compose.onNodeWithTag("entry_${ids.first()}").assertIsDisplayed()
            assertEntries(ids)
            for ((period, expected) in listOf("Неделя" to ids.take(3), "Месяц" to ids.take(3), "Свой" to ids.take(3))) {
                openFilters(); text(period)
                if (period == "Свой") {
                    capture("filter-custom-period")
                    compose.onAllNodesWithContentDescription("Выбрать дату").onFirst().performClick()
                    capture("filter-custom-start-date"); text("Отмена")
                    compose.onAllNodesWithContentDescription("Выбрать дату").onLast().performClick()
                    capture("filter-custom-end-date"); text("Отмена")
                }
                applyFilters(); assertEntries(expected)
            }
            var previousStatus: String? = null
            for ((index, label) in listOf("Не оплачен", "Счёт выставлен", "Оплачено").withIndex()) {
                openFilters()
                previousStatus?.let { compose.onAllNodesWithText(it).onLast().performScrollTo(); text(it) }
                compose.onAllNodesWithText(label).onLast().performScrollTo(); text(label)
                applyFilters(); assertEntries(listOf(ids[index])); capture("filter-status-$index")
                previousStatus = label
            }
            openFilters(); compose.onNodeWithText("Сбросить").performScrollTo(); text("Сбросить")
            compose.onNodeWithText("Месяц").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("filter_client_$clientId").performScrollTo().assertIsOff()
            applyFilters()
        } finally {
            runBlocking { database.timeEntryDao().deleteEntries(ids); database.clientDao().deleteClient(testClient.copy(id = clientId)) }
        }
    }

    @Test fun exportFiles() = themes {
        tag("nav_item_records")
        for (format in listOf("Excel (XLSX)", "Счёт (PDF)")) {
            compose.onNodeWithContentDescription("Экспорт отчётов").performClick()
            text(format); compose.onNodeWithText(format).assertIsSelected()
            compose.onNodeWithTag("export_client_$demoClientId").performScrollTo()
            if (!compose.onNodeWithTag("export_client_$demoClientId").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Selected]) {
                tag("export_client_$demoClientId")
            }
            for (status in listOf("Не оплачен", "Счёт выставлен", "Оплачено")) {
                if (compose.onAllNodesWithText(status).onLast().fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Selected] == false) {
                    text(status)
                }
            }
            compose.onNodeWithText("Создать отчёт").performScrollTo()
            val previousReportId = runBlocking { database.reportDao().getAllReports().first().maxOfOrNull { it.id } ?: 0L }
            val previousAutoAdvance = compose.mainClock.autoAdvance
            compose.mainClock.autoAdvance = false
            try {
                compose.onNodeWithText("Создать отчёт").performClick()
                compose.mainClock.advanceTimeByFrame()
                // File generation runs on real IO/Default dispatchers, independently of the test animation clock.
                // Await its persisted result without asking an indeterminate progress animation to become idle.
                compose.waitUntil(20000) { runBlocking {
                    database.reportDao().getAllReports().first().any { it.id > previousReportId }
                } }
            } finally {
                compose.mainClock.autoAdvance = previousAutoAdvance
            }
            compose.waitUntil(20000) { compose.onAllNodesWithText("Отчёт готов").fetchSemanticsNodes().isNotEmpty() }
            capture("export-success-${if (format.contains("PDF")) "pdf" else "xlsx"}")
            back()
        }
    }
}

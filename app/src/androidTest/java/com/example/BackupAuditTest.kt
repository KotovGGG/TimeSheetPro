package com.example

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.ui.screens.settings.BackupPayload
import com.example.ui.screens.timer.TimerStateManager
import com.example.ui.screens.timer.TimerStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Destructive restore is restricted to a separate empty preview AVD by the runner and guards. */
@RunWith(AndroidJUnit4::class)
class BackupAuditTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val database get() = AppDatabase.getDatabase(context)
    private val automation get() = instrumentation.uiAutomation
    private var themeName = "light"

    @Test fun backupWindowsAndRoundTrip() {
        check(BuildConfig.DEBUG && context.packageName == "com.example.preview")
        check(InstrumentationRegistry.getArguments().getString("emptyAudit") == "true")
        val original = runBlocking {
            check(database.clientDao().getAllClients().first().isEmpty())
            check(database.timeEntryDao().getAllTimeEntries().first().isEmpty())
            check(database.reportDao().getAllReports().first().isEmpty())
            database.appSettingsDao().getSettingsDirect() ?: AppSettings()
        }
        check(TimerStateManager.timerState.value.status == TimerStatus.IDLE)
        val landscape = InstrumentationRegistry.getArguments().getString("auditOrientation") == "landscape"
        if (landscape) {
            instrumentation.runOnMainSync { compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        }
        val serviceInfo = checkNotNull(automation.serviceInfo)
        serviceInfo.flags = serviceInfo.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = serviceInfo
        val stamp = System.currentTimeMillis()
        val clientId = runBlocking { database.clientDao().insertClient(Client(
            name = "Демо • Проверка резервной копии $stamp", colorHex = "#52B788", defaultHourlyRate = 1234.5)) }
        val rateId = runBlocking { database.rateHistoryDao().insertEntry(RateHistoryEntry(
            clientId = clientId, rate = 1234.5, effectiveFrom = stamp - 86400000)) }
        val entryIds = runBlocking { listOf(TimeEntryStatus.INVOICED, TimeEntryStatus.PAID).mapIndexed { index, status ->
            database.timeEntryDao().insertTimeEntry(TimeEntry(clientId = clientId, startTime = stamp + index * 7200000,
                endTime = stamp + index * 7200000 + 3600000, durationSeconds = 3600, rawDurationSeconds = 3587,
                appliedRate = 1234.5, amount = 1234.5, comment = "Демо • Строка ${index + 1}\nМногострочный комментарий", status = status))
        } }
        val ownedDocuments = mutableListOf<Uri>()
        val ownedCacheFiles = mutableListOf<File>()
        try {
            for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                themeName = theme.name.lowercase()
                val settings = original.copy(globalHourlyRate = 9876.54, currency = "RUB", timeRoundingMinutes = 10,
                    amountRoundingMode = AmountRoundingMode.ROUND_NEAREST, amountRoundingStep = 0.5,
                    firstDayOfWeek = FirstDayOfWeek.SUNDAY, themeMode = theme)
                runBlocking { database.appSettingsDao().saveSettings(settings) }
                tag("nav_item_settings")
                compose.onNodeWithTag("settings_backup").performScrollTo(); tag("settings_backup")
                waitText("Копия готова"); capture("ready")
                val cacheFile = checkNotNull(context.cacheDir.listFiles()?.filter {
                    it.name.startsWith("TimeSheetPro_Backup_") && it.extension == "json"
                }?.maxByOrNull { it.lastModified() })
                ownedCacheFiles += cacheFile
                val json = cacheFile.readText(Charsets.UTF_8)
                val payload = Json.decodeFromString(BackupPayload.serializer(), json)
                check(payload.schemaVersion == 1)
                check(payload.clients.single().id == clientId)
                check(payload.timeEntries.map { it.id }.toSet() == entryIds.toSet())
                check(payload.rateHistory.any { it.id == rateId })
                check(payload.appSettings == settings)
                text("Поделиться")
                compose.waitUntil(10000) {
                    automation.windows.any { it.root?.packageName?.toString() == "com.android.intentresolver" }
                }
                capture("share-system"); back()
                waitText("Копия готова")
                text("Сохранить файл")
                waitDocuments()
                val filename = "TimeSheetPro_Audit_${themeName}_$stamp.json"
                val edit = checkNotNull(documentNodes().firstOrNull { it.className?.toString() == "android.widget.EditText" })
                check(edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, filename)
                }))
                capture("save-system")
                clickDocumentNode(checkNotNull(documentNodes().firstOrNull { it.text?.toString()?.equals("Save", true) == true }))
                waitText("Резервная копия сохранена"); capture("saved"); text("Закрыть")
                // Restore the file actually saved by CreateDocument, rather than a test copy.
                val validName = filename
                val changedEntry = payload.timeEntries.first { it.id == entryIds.first() }
                    .copy(comment = "Демо • Временное изменение", amount = 0.0)
                val changedSettings = settings.copy(globalHourlyRate = 0.0, currency = "EUR")
                runBlocking {
                    database.timeEntryDao().updateTimeEntry(changedEntry)
                    database.appSettingsDao().saveSettings(changedSettings)
                }
                selectRestore(validName); capture("restore-confirm")
                text("Отмена")
                assertPayload(payload.copy(appSettings = changedSettings,
                    timeEntries = payload.timeEntries.map { if (it.id == changedEntry.id) changedEntry else it }))
                selectRestore(validName); text("Заменить данные")
                waitText("Данные восстановлены"); capture("restored"); text("Закрыть")
                assertPayload(payload)
                for ((kind, invalidJson) in listOf("invalid" to "{invalid json", "schema" to json.replace("\"schemaVersion\":2", "\"schemaVersion\":99"))) {
                    val invalidName = "$kind-${themeName}-$stamp.json"
                    ownedDocuments += createDocument(invalidName, invalidJson)
                    selectRestore(invalidName); text("Заменить данные")
                    waitText("Ошибка копии"); capture("restore-$kind-error"); text("Закрыть")
                    assertPayload(payload)
                }
                compose.onNodeWithTag("settings_restore").performScrollTo(); tag("settings_restore")
                waitDocuments(); capture("open-system")
                // Back first leaves a nested directory; only the next Back cancels GetContent.
                repeat(3) {
                    if (documentNodes().isNotEmpty()) { back(); automation.waitForIdle(500, 5000) }
                }
                compose.waitUntil(10000) { compose.onAllNodesWithTag("settings_restore").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                compose.onNodeWithText("Заменить текущие данные?").assertDoesNotExist()
                assertPayload(payload)
            }
        } catch (failure: Throwable) {
            capture("failure")
            throw failure
        } finally {
            runBlocking {
                database.timeEntryDao().deleteEntries(entryIds)
                database.rateHistoryDao().getAllHistory().first().find { it.id == rateId }?.let { database.rateHistoryDao().deleteEntry(it) }
                database.clientDao().getClientById(clientId).first()?.let { database.clientDao().deleteClient(it) }
                database.appSettingsDao().saveSettings(original)
            }
            ownedDocuments.forEach { context.contentResolver.delete(it, null, null) }
            ownedCacheFiles.distinct().forEach { check(it.delete() || !it.exists()) }
        }
    }

    private fun assertPayload(payload: BackupPayload) = runBlocking {
        check(database.clientDao().getAllClients().first().sortedBy { it.id } == payload.clients.sortedBy { it.id })
        check(database.timeEntryDao().getAllTimeEntries().first().sortedBy { it.id } == payload.timeEntries.sortedBy { it.id })
        check(database.rateHistoryDao().getAllHistory().first().sortedBy { it.id } == payload.rateHistory.sortedBy { it.id })
        check(database.appSettingsDao().getSettingsDirect() == payload.appSettings)
        check(database.reportDao().getAllReports().first().isEmpty())
    }
    private fun createDocument(name: String, json: String): Uri {
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/TimeSheetProAudit")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }))
        checkNotNull(resolver.openOutputStream(uri)).bufferedWriter(Charsets.UTF_8).use { it.write(json) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return uri
    }
    private fun selectRestore(name: String) {
        compose.onNodeWithTag("settings_restore").performScrollTo(); tag("settings_restore")
        waitDocuments()
        automation.waitForIdle(500, 5000)
        documentNodes().firstOrNull { it.contentDescription?.toString() == "List view" }?.let {
            clickDocumentNode(it); automation.waitForIdle(500, 5000)
        }
        clickDocumentNode(documentNodes().first { it.contentDescription?.toString() == "Search" })
        compose.waitUntil(10000) { documentNodes().any { it.className?.toString() == "android.widget.AutoCompleteTextView" } }
        val search = documentNodes().first { it.className?.toString() == "android.widget.AutoCompleteTextView" }
        check(search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, name)
        }))
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        fun item(node: AccessibilityNodeInfo) = !node.isEditable && (node.text?.toString() == name || node.contentDescription?.toString() == name)
        compose.waitUntil(10000) { documentNodes().any { item(it) } }
        if (automation.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }) {
            back(); automation.waitForIdle(500, 5000)
        }
        capture("restore-${name.substringBefore('-')}-system")
        clickDocumentNode(documentNodes().first { item(it) })
        waitText("Заменить текущие данные?")
    }
    private fun documentNodes(): List<AccessibilityNodeInfo> {
        fun descendants(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(node) + (0 until node.childCount).flatMap {
            node.getChild(it)?.let { child -> descendants(child) }.orEmpty()
        }
        return automation.windows.mapNotNull { it.root }.filter { it.packageName?.toString()?.endsWith(".documentsui") == true }.flatMap { descendants(it) }
    }
    private fun waitDocuments() = compose.waitUntil(10000) { documentNodes().isNotEmpty() }
    private fun clickDocumentNode(node: AccessibilityNodeInfo) {
        var target = node
        var bounds = android.graphics.Rect().also { target.getBoundsInScreen(it) }
        while (!target.isVisibleToUser || bounds.isEmpty) {
            target = checkNotNull(target.parent)
            bounds = android.graphics.Rect().also { target.getBoundsInScreen(it) }
        }
        check(target.className?.toString() !in listOf("android.widget.GridView", "androidx.recyclerview.widget.RecyclerView") && bounds.height() >= 12) {
            "Refusing a tap outside the visible document cell: ${node.text} / ${target.className} / $bounds"
        }
        File(checkNotNull(context.getExternalFilesDir("backup-audit")), "clicks.txt").appendText(
            "$themeName ${node.text}: ${target.className}, clickable=${target.isClickable}, bounds=$bounds\n")
        if (target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
        // DocumentsUI grid labels are not clickable accessibility nodes. Use the
        // same native tap that selects this visible label in the manual audit.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
            "input tap ${bounds.centerX()} ${bounds.centerY()}"
        )).use { it.readBytes() }
    }
    private fun tag(name: String) {
        compose.waitUntil(10000) { compose.onAllNodesWithTag(name).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.onNodeWithTag(name).performClick()
    }
    private fun waitText(value: String) = compose.waitUntil(10000) {
        compose.onAllNodesWithText(value).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
    private fun text(value: String) { waitText(value); compose.onAllNodesWithText(value).onLast().performClick() }
    private fun back() { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK) }
    private fun capture(name: String) {
        compose.waitForIdle(); instrumentation.waitForIdleSync()
        val committed = java.util.concurrent.CountDownLatch(1)
        var scheduled = false
        instrumentation.runOnMainSync {
            android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull {
                it.hasWindowFocus() && it.isShown && it.isHardwareAccelerated
            }?.let { view ->
                scheduled = true
                view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                view.invalidate()
            }
        }
        if (scheduled) check(committed.await(5, java.util.concurrent.TimeUnit.SECONDS))
        automation.waitForIdle(500, 5000)
        val directory = checkNotNull(context.getExternalFilesDir("backup-audit")).apply { mkdirs() }
        val bitmap = checkNotNull(automation.takeScreenshot())
        val fontScale = compose.activity.resources.configuration.fontScale
        val orientation = if (InstrumentationRegistry.getArguments().getString("auditOrientation") == "landscape") "landscape-" else ""
        val prefix = orientation + if (fontScale > 1.01f) "font${(fontScale * 100).toInt()}-" else ""
        File(directory, "$prefix$themeName-$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}

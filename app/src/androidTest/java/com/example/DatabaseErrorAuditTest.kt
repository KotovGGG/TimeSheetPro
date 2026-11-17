package com.example

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ThemeMode
import com.example.ui.screens.timer.TimerStateManager
import com.example.ui.screens.timer.TimerStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises MainActivity's real startup failure with a separate, disposable schema-99 database. */
@RunWith(AndroidJUnit4::class)
class DatabaseErrorAuditTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command)
        .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText().trim() } }

    @Test fun unknownSchemaShowsPreservationWindow() {
        check(BuildConfig.DEBUG && context.packageName == "com.example.preview")
        TimerStateManager.initialize(context)
        check(TimerStateManager.timerState.value.status == TimerStatus.IDLE) { "Finish the preview timer first." }
        val app = context.applicationContext as TimeSheetApp
        val originalDatabase = AppDatabase.getDatabase(context)
        // Prime the existing container before the substitution; it must retain its original DAO.
        app.container.settingsRepository
        val originalTheme = app.lastKnownThemeMode
        val originalFont = shell("settings get system font_scale")
        val auditVariant = InstrumentationRegistry.getArguments().getString("auditVariant", "").orEmpty()
        val name = "audit-unknown-schema-${System.nanoTime()}"
        val file = context.getDatabasePath(name)
        check(!file.exists())
        SQLiteDatabase.openOrCreateDatabase(file, null).use {
            it.execSQL("CREATE TABLE audit_marker (id INTEGER PRIMARY KEY, label TEXT NOT NULL)")
            it.execSQL("INSERT INTO audit_marker VALUES (42, 'preserve this fixture')")
            it.version = 99
        }
        val incompatible = AppDatabase.buildDatabase(context, name)
        // Test-only substitution avoids touching or renaming timesheet_pro_database and avoids
        // adding a public injection API solely for this audit. Restore the exact instance in finally.
        val singleton = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        check(singleton.get(null) === originalDatabase)
        try {
            singleton.set(null, incompatible)
            val cases = listOf(
                Triple("normal", "1.0", false), Triple("font150", "1.5", false),
                Triple("font200", "2.0", false), Triple("landscape", "1.0", true),
                Triple("landscape-font200", "2.0", true)
            ).filter { when (auditVariant) {
                "narrow100" -> it.first == "normal"
                "narrow200" -> it.first == "font200"
                else -> true
            } }
            for ((variant, font, landscape) in cases) {
                shell("settings put system font_scale $font")
                for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                    app.lastKnownThemeMode = theme
                    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                        scenario.onActivity { it.requestedOrientation = if (landscape)
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                        compose.waitUntil(10000) {
                            var correct = false
                            scenario.onActivity {
                                correct = it.resources.configuration.orientation == (if (landscape)
                                    Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT) &&
                                    kotlin.math.abs(it.resources.configuration.fontScale - font.toFloat()) < 0.01f
                            }
                            correct
                        }
                        compose.waitUntil(10000) { compose.onAllNodesWithText("Не удалось открыть данные").fetchSemanticsNodes().isNotEmpty() }
                        compose.onNodeWithText("Не удалось открыть данные").assertIsDisplayed()
                        compose.onNodeWithText("База не была пересоздана.", substring = true).assertIsDisplayed()
                        compose.onNodeWithTag("nav_item_timer").assertDoesNotExist()
                        capture("${if (auditVariant.isEmpty()) "" else "$auditVariant-"}$variant-${theme.name.lowercase()}")
                    }
                    SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                        check(database.version == 99)
                        database.rawQuery("SELECT id, label FROM audit_marker", null).use { cursor ->
                            check(cursor.moveToFirst() && cursor.getInt(0) == 42 && cursor.getString(1) == "preserve this fixture")
                            check(!cursor.moveToNext())
                        }
                    }
                }
            }
        } finally {
            singleton.set(null, originalDatabase)
            incompatible.close()
            app.lastKnownThemeMode = originalTheme
            shell("settings put system font_scale $originalFont")
            check(context.deleteDatabase(name))
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle(); instrumentation.waitForIdleSync()
        compose.waitUntil(5000) {
            var focused = false
            instrumentation.runOnMainSync {
                focused = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .any { it.hasWindowFocus() && it.isShown && it.isHardwareAccelerated }
            }
            focused
        }
        val committed = java.util.concurrent.CountDownLatch(1)
        instrumentation.runOnMainSync {
            val view = checkNotNull(android.view.inspector.WindowInspector.getGlobalWindowViews()
                .lastOrNull { it.hasWindowFocus() && it.isShown && it.isHardwareAccelerated })
            view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }; view.invalidate()
        }
        check(committed.await(5, java.util.concurrent.TimeUnit.SECONDS))
        instrumentation.uiAutomation.waitForIdle(500, 5000)
        val directory = checkNotNull(context.getExternalFilesDir("database-error-audit")).apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}

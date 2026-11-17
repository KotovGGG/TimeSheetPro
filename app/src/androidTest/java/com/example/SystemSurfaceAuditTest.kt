package com.example

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ui.screens.timer.TimeSheetWidget
import com.example.ui.screens.timer.TimeSheetWidgetReceiver
import com.example.ui.screens.timer.TimerService
import com.example.ui.screens.timer.TimerStateManager
import com.example.ui.screens.timer.TimerStatus
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the real Glance RemoteViews in an Android host; never binds a production widget. */
@RunWith(AndroidJUnit4::class)
class SystemSurfaceAuditTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertTrue(message, condition())
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        val committed = java.util.concurrent.CountDownLatch(1)
        var scheduled = false
        instrumentation.runOnMainSync {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews()
                .lastOrNull { it.hasWindowFocus() && it.isShown && it.isHardwareAccelerated }
            if (view != null) {
                scheduled = true
                view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                view.invalidate()
            }
        }
        if (scheduled) check(committed.await(5, java.util.concurrent.TimeUnit.SECONDS))
        instrumentation.uiAutomation.waitForIdle(500, 5000)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val theme = if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
        val directory = checkNotNull(context.getExternalFilesDir("visual-audit")).apply { mkdirs() }
        File(directory, "$theme-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun labelledView(root: View, label: String): View? {
        if (root.contentDescription?.toString() == label) return root
        if (root is ViewGroup) for (index in 0 until root.childCount) {
            labelledView(root.getChildAt(index), label)?.let { return it }
        }
        return null
    }

    private fun clickAction(root: View, label: String) {
        var action = checkNotNull(labelledView(root, label))
        while (!action.isClickable && action.parent is View) action = action.parent as View
        assertTrue("Widget action: $label", action.performClick())
    }

    /** Clicks actual SystemUI RemoteViews rather than sending their PendingIntent directly. */
    @Test fun notificationActionsAndReturn() {
        check(BuildConfig.DEBUG && context.packageName == "com.example.preview")
        TimerStateManager.initialize(context)
        check(TimerStateManager.timerState.value.status == TimerStatus.IDLE) {
            "Finish the preview timer first; existing sessions must remain untouched."
        }
        val automation = instrumentation.uiAutomation
        val originalInfo = automation.serviceInfo
        val originalFlags = checkNotNull(originalInfo).flags
        val database = com.example.data.local.AppDatabase.getDatabase(context)
        val originalSettings = checkNotNull(runBlocking { database.appSettingsDao().getSettingsDirect() }) {
            "Open the preview app first so its settings can be preserved."
        }
        automation.serviceInfo = checkNotNull(originalInfo).apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun findLabel(root: android.view.accessibility.AccessibilityNodeInfo?, label: String): android.view.accessibility.AccessibilityNodeInfo? {
            if (root == null) return null
            if (root.isVisibleToUser && (root.text?.toString() == label || root.contentDescription?.toString() == label)) return root
            for (index in 0 until root.childCount) findLabel(root.getChild(index), label)?.let { return it }
            return null
        }
        fun findSystemAction(label: String) = automation.windows.asSequence().mapNotNull { it.root }
            .filter { it.packageName?.toString() == "com.android.systemui" }
            .mapNotNull { findLabel(it, label) }.firstOrNull()
        fun clickSystemAction(label: String) {
            await("SystemUI action $label") { findSystemAction(label) != null }
            var node = checkNotNull(findSystemAction(label))
            while (!node.isClickable && node.parent != null) node = node.parent
            check(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        }
        try {
            runBlocking { database.appSettingsDao().saveSettings(originalSettings
                .copy(themeMode = com.example.data.local.entity.ThemeMode.SYSTEM)) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var originalActivity: MainActivity
                scenario.onActivity { originalActivity = it; TimerService.startService(it, null) }
                await("Running test session") { TimerStateManager.timerState.value.status == TimerStatus.RUNNING }
                val session = TimerStateManager.timerState.value.sessionStartTime
                automation.executeShellCommand("cmd statusbar expand-notifications").close()
                await("Pause accessible label") { findSystemAction("Пауза") != null }
                capture("notification-actions-running")
                clickSystemAction("Пауза")
                await("Pause through notification") { TimerStateManager.timerState.value.status == TimerStatus.PAUSED }
                await("Resume accessible label") { findSystemAction("Продолжить") != null }
                capture("notification-actions-paused")
                clickSystemAction("Продолжить")
                await("Resume through notification") { TimerStateManager.timerState.value.status == TimerStatus.RUNNING }
                check(TimerStateManager.timerState.value.sessionStartTime == session)
                clickSystemAction("Завершить")
                await("Finish confirmation from notification") { TimerStateManager.showConfirmDialog.value }
                automation.executeShellCommand("cmd statusbar collapse").close()
                await("Visible finish confirmation") { findLabel(automation.rootInActiveWindow, "Завершить запись?") != null }
                capture("notification-actions-finish-confirm")
                scenario.onActivity { check(it === originalActivity) { "Notification recreated the existing Activity" } }
                var resume = checkNotNull(findLabel(automation.rootInActiveWindow, "Продолжить"))
                while (!resume.isClickable && resume.parent != null) resume = resume.parent
                check(resume.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                await("Continue cancels finish") { !TimerStateManager.showConfirmDialog.value }
                await("Finish confirmation dismissed") { findLabel(automation.rootInActiveWindow, "Завершить запись?") == null }
                check(TimerStateManager.timerState.value.status == TimerStatus.RUNNING)
                check(TimerStateManager.timerState.value.sessionStartTime == session)
                capture("notification-actions-returned")
            }
        } finally {
            automation.executeShellCommand("cmd statusbar collapse").close()
            instrumentation.runOnMainSync {
                TimerService.stopService(context)
                TimerStateManager.setShowConfirmDialog(false)
                TimerStateManager.stop()
            }
            check(context.getSharedPreferences("TimeSheetProTimerPrefs", 0).edit().commit())
            runBlocking { database.appSettingsDao().saveSettings(originalSettings) }
            originalInfo.flags = originalFlags
            automation.serviceInfo = originalInfo
        }
    }

    @Test fun widgetAndNotificationStates() {
        check(BuildConfig.DEBUG && context.packageName == "com.example.preview")
        TimerStateManager.initialize(context)
        check(TimerStateManager.timerState.value.status == TimerStatus.IDLE) {
            "Finish the preview timer before running this test; existing sessions are never discarded."
        }
        val manager = AppWidgetManager.getInstance(context)
        val host = AppWidgetHost(context, 73091)
        val widgetId = host.allocateAppWidgetId()
        try {
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
            try {
                assertTrue("Preview widget binding", manager.bindAppWidgetIdIfAllowed(widgetId,
                    ComponentName(context, TimeSheetWidgetReceiver::class.java), Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 96)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 96)
                    }))
            } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
            host.startListening()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var root: FrameLayout
                lateinit var widget: View
                scenario.onActivity { activity ->
                    root = FrameLayout(activity).apply {
                        setBackgroundColor(if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                            Configuration.UI_MODE_NIGHT_YES) 0xFF16151D.toInt() else 0xFFF7F5FC.toInt())
                    }
                    widget = host.createView(activity, widgetId, checkNotNull(manager.getAppWidgetInfo(widgetId)))
                    val density = activity.resources.displayMetrics.density
                    root.addView(widget, FrameLayout.LayoutParams((250 * density).toInt(), (96 * density).toInt(), Gravity.CENTER))
                    activity.setContentView(root)
                }
                runBlocking { TimeSheetWidget().updateAll(context) }
                await("Idle widget action") {
                    var found = false
                    scenario.onActivity { found = labelledView(widget, "Запустить") != null }
                    found
                }
                capture("widget-idle-minimum")
                scenario.onActivity { clickAction(widget, "Запустить") }
                await("Start through widget") { TimerStateManager.timerState.value.status == TimerStatus.RUNNING }
                await("Pause widget action") {
                    var found = false
                    scenario.onActivity { found = labelledView(widget, "Пауза") != null }
                    found
                }
                capture("widget-running-minimum")
                await("Foreground notification") {
                    context.getSystemService(android.app.NotificationManager::class.java).activeNotifications
                        .any { it.id == TimerService.NOTIFICATION_ID }
                }
                instrumentation.uiAutomation.executeShellCommand("cmd statusbar expand-notifications").close()
                SystemClock.sleep(700)
                capture("notification-running")
                instrumentation.uiAutomation.executeShellCommand("cmd statusbar collapse").close()
                scenario.onActivity { clickAction(widget, "Пауза") }
                await("Pause through widget") { TimerStateManager.timerState.value.status == TimerStatus.PAUSED }
                await("Resume widget action") {
                    var found = false
                    scenario.onActivity { found = labelledView(widget, "Старт") != null }
                    found
                }
                capture("widget-paused-minimum")
                instrumentation.uiAutomation.executeShellCommand("cmd statusbar expand-notifications").close()
                SystemClock.sleep(700)
                capture("notification-paused")
                instrumentation.uiAutomation.executeShellCommand("cmd statusbar collapse").close()
                scenario.onActivity { clickAction(widget, "Старт") }
                await("Resume through widget") { TimerStateManager.timerState.value.status == TimerStatus.RUNNING }
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            instrumentation.uiAutomation.executeShellCommand("cmd statusbar collapse").close()
            instrumentation.runOnMainSync {
                TimerService.stopService(context)
                TimerStateManager.stop()
            }
            // The runner can kill the process immediately after finally; flush queued apply() writes on this test thread.
            val preferences = context.applicationContext.getSharedPreferences("TimeSheetProTimerPrefs", 0)
            assertTrue("Flush test timer cleanup", preferences.edit().commit())
            assertEquals("IDLE", preferences.getString("status", null))
            host.stopListening()
            host.deleteAppWidgetId(widgetId)
        }
    }
}

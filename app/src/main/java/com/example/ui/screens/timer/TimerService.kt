package com.example.ui.screens.timer

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.room.withTransaction
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.glance.appwidget.updateAll
import com.example.MainActivity
import com.example.R
import com.example.TimeSheetApp
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AppSettings
import com.example.utils.AutoBackupManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.*
import java.time.Duration
import java.time.Instant

class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "TimerServiceChannel"
        const val NOTIFICATION_ID = 8008
        
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_FINISH = "ACTION_FINISH"
        private const val ACTION_SAVE_SESSION = "ACTION_SAVE_SESSION"
        private const val ACTION_OPEN_AND_SAVE = "ACTION_OPEN_AND_SAVE"
        private const val EXTRA_SESSION_START = "session_start"
        const val ACTION_UPDATE = "ACTION_UPDATE"

        private fun saveActivity(context: Context) =
            ComponentName(context.packageName, "${context.packageName}.SaveTimerActivity")

        internal fun requestSaveFromNotification(context: Context, intent: Intent): Boolean {
            if (intent.action != ACTION_OPEN_AND_SAVE || intent.component != saveActivity(context)) return false
            context.startService(Intent(context, TimerService::class.java).apply {
                action = ACTION_SAVE_SESSION
                putExtra(EXTRA_SESSION_START, intent.getLongExtra(EXTRA_SESSION_START, 0L))
            })
            return true
        }
        
        fun startService(context: Context, clientId: Long? = null) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                if (clientId != null) {
                    putExtra("clientId", clientId)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_FINISH
            }
            context.stopService(intent)
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var updateJob: Job? = null
    private var isServiceStarted = false
    private var savingSession = false
    private var saveFailed = false
    private var screenOff = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            screenOff = action == Intent.ACTION_SCREEN_OFF
            val state = TimerStateManager.timerState.value
            if (state.status != TimerStatus.IDLE) updateNotification(state)
            if (action == Intent.ACTION_USER_PRESENT || action == Intent.ACTION_SCREEN_ON) {
                serviceScope.launch {
                    try {
                        TimerStateManager.incrementWidgetTick()
                        TimeSheetWidget().updateAll(applicationContext)
                    } catch (e: Exception) {
                        android.util.Log.w("TimerService", "Widget refresh failed", e)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        screenOff = !getSystemService(PowerManager::class.java).isInteractive
        
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(screenReceiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Listen to state changes to update notification/widgets
        serviceScope.launch {
            TimerStateManager.timerState.collect { state ->
                // Always update the widget on state changes
                try {
                    TimeSheetWidget().updateAll(applicationContext)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                if (state.status == TimerStatus.IDLE) {
                    if (isServiceStarted) {
                        updateJob?.cancel()
                        updateJob = null
                        stopSelf()
                    }
                } else {
                    updateNotification(state)
                    startTicker()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        val current = TimerStateManager.timerState.value
        if (savingSession || intent?.hasExtra(EXTRA_SESSION_START) == true &&
            intent.getLongExtra(EXTRA_SESSION_START, 0) != current.sessionStartTime
        ) return START_STICKY

        // Handle actions first so that state is updated before startForeground
        when (action) {
            ACTION_START -> {
                saveFailed = false
                val state = TimerStateManager.timerState.value
                if (state.status == TimerStatus.IDLE) {
                    val clientIdExtra = intent?.getLongExtra("clientId", -1L) ?: -1L
                    val clientId = if (clientIdExtra == -1L) null else clientIdExtra
                    TimerStateManager.start(clientId)
                }
                startTicker()
            }
            ACTION_PAUSE -> {
                TimerStateManager.pause()
            }
            ACTION_RESUME -> {
                TimerStateManager.resume()
                startTicker()
            }
            ACTION_SAVE_SESSION -> saveSession(intent?.getLongExtra(EXTRA_SESSION_START, 0) ?: 0)
            ACTION_FINISH -> {
                finishTimer()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                // Just update notification
            }
        }
        
        val state = TimerStateManager.timerState.value
        if (state.status != TimerStatus.IDLE) {
            isServiceStarted = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, 
                    buildNotification(state), 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(state))
            }
        } else {
            stopSelf()
        }
        
        return START_STICKY
    }

    private fun startTicker() {
        if (updateJob?.isActive == true) return
        updateJob = serviceScope.launch {
            while (isActive) {
                val state = TimerStateManager.timerState.value
                if (state.status == TimerStatus.RUNNING || state.status == TimerStatus.PAUSED) {
                    // Update Glance widget every second to keep time perfectly synced in real-time
                    TimerStateManager.incrementWidgetTick()
                    try {
                        TimeSheetWidget().updateAll(applicationContext)
                    } catch (e: Exception) {
                        android.util.Log.w("TimerService", "Widget refresh failed", e)
                    }
                }
                delay(1000)
            }
        }
    }

    private fun updateNotification(state: TimerState) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: TimerState): Notification {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openApp = PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val running = state.status == TimerStatus.RUNNING
        val elapsed = TimerStateManager.getElapsedSeconds()
        val pauseSeconds = TimerStateManager.getPauseSeconds()
        val canSave = state.clientId != null && state.sessionStartTime > 0
        val locked = screenOff || getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val pauseLabel = getString(if (running) R.string.timer_pause else R.string.timer_resume)
        val finishLabel = getString(if (canSave) R.string.timer_save_session else R.string.timer_finish)
        val pause = sessionAction(
            if (running) ACTION_PAUSE else ACTION_RESUME, state.sessionStartTime
        )
        val finish = when {
            !canSave -> getFinishPendingIntent()
            locked -> sessionAction(ACTION_SAVE_SESSION, state.sessionStartTime)
            else -> openAndSave(state.sessionStartTime)
        }
        val pauseIcon = if (running) R.drawable.ic_pause else R.drawable.ic_play
        val saveStatus = when {
            savingSession -> getString(R.string.timer_saving)
            saveFailed -> getString(R.string.timer_save_retry)
            else -> null
        }

        if (locked && Build.VERSION.SDK_INT >= 36 &&
            Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1 &&
            getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
        ) {
            fun action(
                icon: Int,
                label: String,
                intent: PendingIntent,
                primary: Boolean,
                requiresUnlock: Boolean = false
            ) =
                Notification.Action.Builder(Icon.createWithResource(this, icon), label, intent)
                    .setAuthenticationRequired(requiresUnlock)
                    .apply {
                        if (Build.VERSION.SDK_INT >= 37) {
                            setStyleHint(Notification.Action.STYLE_ICON_ONLY)
                            setEmphasisHint(if (primary) {
                                Notification.Action.EMPHASIS_PRIMARY
                            } else {
                                Notification.Action.EMPHASIS_SECONDARY
                            })
                        }
                    }
                    .build()

            val live = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer_notification)
                .setColor(getColor(R.color.widget_primary))
                .setContentIntent(openApp)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setRequestPromotedOngoing(true)
                .addAction(action(pauseIcon, pauseLabel, pause, primary = true))
                .addAction(action(R.drawable.ic_save, finishLabel, finish, primary = false, requiresUnlock = !canSave))
            saveStatus?.let(live::setContentTitle)
            if (Build.VERSION.SDK_INT >= 37) {
                live.setStyle(stopwatchStyle(running, elapsed, pauseSeconds))
            } else {
                live.setContentTitle(saveStatus ?: getString(R.string.app_name))
                    .setContentText(if (running) pauseText(pauseSeconds)
                        else "${DateUtils.formatElapsedTime(elapsed)} · ${pauseText(pauseSeconds)}")
                    .setWhen(System.currentTimeMillis() - elapsed * 1000)
                    .setShowWhen(running)
                    .setUsesChronometer(running)
            }
            return live.build()
        }

        val customView = timerViews(running, elapsed, pauseSeconds, saveStatus).apply {
            setImageViewResource(R.id.btn_play_pause, pauseIcon)
            setContentDescription(R.id.btn_play_pause, pauseLabel)
            setContentDescription(R.id.btn_stop, finishLabel)
            setOnClickPendingIntent(R.id.btn_play_pause, pause)
            setOnClickPendingIntent(R.id.btn_stop, finish)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(saveStatus ?: "${formatDuration(elapsed)} · ${pauseText(pauseSeconds)}")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(customView)
            .setCustomBigContentView(customView)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    @RequiresApi(37)
    private fun stopwatchStyle(running: Boolean, elapsed: Long, pauseSeconds: Long): Notification.MetricStyle {
        val format = Notification.Metric.TimeDifference.FORMAT_CHRONOMETER
        val now = Instant.now()
        fun stopwatch(seconds: Long, counting: Boolean, label: Int): Notification.Metric {
            val value = if (counting) {
                Notification.Metric.TimeDifference.forStopwatch(now.minusSeconds(seconds), format)
            } else {
                Notification.Metric.TimeDifference.forPausedStopwatch(Duration.ofSeconds(seconds), format)
            }
            return Notification.Metric(value, getString(label))
        }
        return Notification.MetricStyle()
            .addMetric(stopwatch(elapsed, running, R.string.timer_work_duration))
            .addMetric(stopwatch(pauseSeconds, !running, R.string.timer_break_duration))
            .setCriticalMetric(0)
    }

    private fun pauseText(seconds: Long) =
        getString(R.string.timer_break_clock, DateUtils.formatElapsedTime(seconds))

    private fun timerViews(running: Boolean, elapsed: Long, pauseSeconds: Long, saveStatus: String?) =
        RemoteViews(packageName, R.layout.notification_timer).apply {
            val now = SystemClock.elapsedRealtime()
            val showPauseClock = !running && saveStatus == null
            setViewVisibility(R.id.timer_text, if (running) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.timer_frozen, if (running) View.GONE else View.VISIBLE)
            setChronometer(R.id.timer_text, now - elapsed * 1000, null, running)
            setTextViewText(R.id.timer_frozen, DateUtils.formatElapsedTime(elapsed))
            setViewVisibility(R.id.timer_pause_clock, if (showPauseClock) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.timer_pause_frozen, if (showPauseClock) View.GONE else View.VISIBLE)
            setChronometer(R.id.timer_pause_clock, now - pauseSeconds * 1000, null, showPauseClock)
            setTextViewText(R.id.timer_pause_frozen, saveStatus ?: DateUtils.formatElapsedTime(pauseSeconds))
        }

    private fun sessionAction(action: String, sessionStart: Long): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply {
            this.action = action
            data = "timesheetpro://timer/$sessionStart".toUri()
            putExtra(EXTRA_SESSION_START, sessionStart)
        }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openAndSave(sessionStart: Long): PendingIntent {
        val intent = Intent(ACTION_OPEN_AND_SAVE).apply {
            component = saveActivity(this@TimerService)
            data = "timesheetpro://timer/$sessionStart".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SESSION_START, sessionStart)
        }
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun saveSession(sessionStart: Long) {
        val state = TimerStateManager.beginNotificationSave(sessionStart) ?: return
        val clientId = checkNotNull(state.clientId)
        val finishedAt = System.currentTimeMillis()
        savingSession = true
        saveFailed = false
        serviceScope.launch {
            var saved = false
            try {
                val app = application as TimeSheetApp
                val database = AppDatabase.getDatabase(app)
                val entryId = database.withTransaction {
                    val entries = database.timeEntryDao()
                    // A retry after process death must not insert the same session twice.
                    entries.findSavedTimerSessionId(clientId, sessionStart) ?: run {
                        val client = checkNotNull(app.container.clientRepository.getClientById(clientId).first())
                        val settings = app.container.settingsRepository.getSettingsDirect() ?: AppSettings()
                        entries.insertTimeEntry(trackedSessionEntry(
                            clientId, client, state.comment, null, state.accumulatedSeconds,
                            settings, sessionStart, finishedAt, createdAt = sessionStart
                        ))
                    }
                }
                TimerStateManager.showSavedNotificationEntry(entryId)
                saved = true
                AutoBackupManager.triggerBackup(app)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Keep the paused session available for retry if reading or writing Room fails.
                saveFailed = true
                Log.e("TimerService", "Could not save timer session", error)
            } finally {
                TimerStateManager.endNotificationSave(saved)
                savingSession = false
                if (!saved && serviceJob.isActive) updateNotification(TimerStateManager.timerState.value)
            }
        }
    }

    private fun getFinishPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_WIDGET_FINISH"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun finishTimer() {
        val state = TimerStateManager.timerState.value
        if (state.status == TimerStatus.IDLE) return
        
        serviceScope.launch(Dispatchers.Main) {
            // Clean up state and set pending save duration
            TimerStateManager.stopAndPending()
            
            // Update widgets
            try {
                TimeSheetWidget().updateAll(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Bring MainActivity to the foreground to prompt saving the session
            val activityIntent = Intent(applicationContext, MainActivity::class.java).apply {
                action = "ACTION_WIDGET_FINISH"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            try {
                applicationContext.startActivity(activityIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.timer_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        updateJob?.cancel()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

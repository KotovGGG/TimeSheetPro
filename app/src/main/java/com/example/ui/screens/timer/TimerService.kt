package com.example.ui.screens.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.updateAll
import com.example.MainActivity
import com.example.R
import com.example.TimeSheetApp
import com.example.data.local.entity.AppSettings
import com.example.data.local.entity.AmountRoundingMode
import com.example.data.local.entity.TimeEntry
import com.example.data.local.entity.TimeEntryStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull

class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "TimerServiceChannel"
        const val NOTIFICATION_ID = 8008
        
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_FINISH = "ACTION_FINISH"
        const val ACTION_UPDATE = "ACTION_UPDATE"
        
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

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == Intent.ACTION_USER_PRESENT || action == Intent.ACTION_SCREEN_ON) {
                serviceScope.launch {
                    try {
                        TimerStateManager.incrementWidgetTick()
                        TimeSheetWidget().updateAll(applicationContext)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
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
        
        // Handle actions first so that state is updated before startForeground
        when (action) {
            ACTION_START -> {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, 
                    buildNotification(state), 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
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
            var tickCount = 0
            while (isActive) {
                val state = TimerStateManager.timerState.value
                if (state.status == TimerStatus.RUNNING || state.status == TimerStatus.PAUSED) {
                    // Update notification every second to keep both main time and pause time completely synchronized
                    updateNotification(state)
                    
                    // Update Glance widget every second to keep time perfectly synced in real-time
                    TimerStateManager.incrementWidgetTick()
                    try {
                        TimeSheetWidget().updateAll(applicationContext)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                tickCount++
                delay(1000)
            }
        }
    }

    private fun updateNotification(state: TimerState) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: TimerState): Notification {
        // Tap intent to open MainActivity
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val elapsed = TimerStateManager.getElapsedSeconds()
        
        val pauseSecs = TimerStateManager.getPauseSeconds()
        val pauseBase = android.os.SystemClock.elapsedRealtime() - (pauseSecs * 1000)
        val isPaused = state.status == TimerStatus.PAUSED

        val clientVisibility = android.view.View.GONE
        val pauseVisibility = if (state.status != TimerStatus.IDLE && pauseSecs > 0) android.view.View.VISIBLE else android.view.View.GONE

        val baseTime = android.os.SystemClock.elapsedRealtime() - (elapsed * 1000)
        val isStarted = state.status == TimerStatus.RUNNING

        val playPauseIcon = if (state.status == TimerStatus.RUNNING) {
            com.example.R.drawable.ic_pause
        } else {
            com.example.R.drawable.ic_play
        }
        val playPauseBg = if (state.status == TimerStatus.RUNNING) {
            com.example.R.drawable.bg_notification_button_secondary
        } else {
            com.example.R.drawable.bg_notification_button_primary
        }
        val playPauseActionTitle = if (state.status == TimerStatus.RUNNING) "Пауза" else "Продолжить"

        // Collapsed view
        val collapsedView = RemoteViews(packageName, R.layout.notification_timer).apply {
            setTextViewText(R.id.client_name, "")
            setViewVisibility(R.id.client_name, clientVisibility)
            
            // Set up Chronometer
            setChronometer(R.id.timer_text, baseTime, null, isStarted)
            
            setChronometer(R.id.pause_text, pauseBase, null, isPaused)
            setViewVisibility(R.id.pause_text, pauseVisibility)
            
            // Set up pending intents for actions
            setOnClickPendingIntent(R.id.btn_play_pause, getActionPendingIntent(state.status))
            setContentDescription(R.id.btn_play_pause, playPauseActionTitle)
            setOnClickPendingIntent(R.id.btn_stop, getFinishPendingIntent())
            
            // Icons & backgrounds
            setImageViewResource(R.id.btn_play_pause, playPauseIcon)
            setInt(R.id.btn_play_pause, "setBackgroundResource", playPauseBg)
        }

        // Expanded view
        val expandedView = RemoteViews(packageName, R.layout.notification_timer_big).apply {
            setTextViewText(R.id.client_name, "")
            setViewVisibility(R.id.client_name, clientVisibility)
            
            // Set up Chronometer
            setChronometer(R.id.timer_text, baseTime, null, isStarted)
            
            setChronometer(R.id.pause_text, pauseBase, null, isPaused)
            setViewVisibility(R.id.pause_text, pauseVisibility)
            
            setOnClickPendingIntent(R.id.btn_play_pause, getActionPendingIntent(state.status))
            setContentDescription(R.id.btn_play_pause, playPauseActionTitle)
            setOnClickPendingIntent(R.id.btn_stop, getFinishPendingIntent())
            
            // Icons & backgrounds
            setImageViewResource(R.id.btn_play_pause, playPauseIcon)
            setInt(R.id.btn_play_pause, "setBackgroundResource", playPauseBg)
        }

        // Format duration for lock screen fallback text
        val hours = elapsed / 3600
        val minutes = (elapsed % 3600) / 60
        val seconds = elapsed % 60
        val durationText = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        val statusText = if (isPaused) "Запись приостановлена" else "Идет запись времени"

        // Build a highly interactive standard fallback notification for lock screen / secure contexts
        val publicNotificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_play)
            .setContentTitle("TimeSheetPro")
            .setContentIntent(tapPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (isStarted) {
            publicNotificationBuilder.setUsesChronometer(true)
            publicNotificationBuilder.setWhen(baseTime)
            publicNotificationBuilder.setContentText(statusText)
        } else {
            publicNotificationBuilder.setUsesChronometer(false)
            publicNotificationBuilder.setContentText("$statusText: $durationText")
        }

        // Add standard interactive actions to the lock screen notification
        val playPauseActionIcon = if (state.status == TimerStatus.RUNNING) {
            com.example.R.drawable.ic_pause
        } else {
            com.example.R.drawable.ic_play
        }
        val playPauseAction = NotificationCompat.Action.Builder(
            playPauseActionIcon,
            playPauseActionTitle,
            getActionPendingIntent(state.status)
        ).build()

        val stopAction = NotificationCompat.Action.Builder(
            com.example.R.drawable.ic_save,
            "Завершить",
            getFinishPendingIntent()
        ).build()

        publicNotificationBuilder.addAction(playPauseAction)
        publicNotificationBuilder.addAction(stopAction)

        val publicNotification = publicNotificationBuilder.build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_play) // Use local icon
            .setContentTitle("TimeSheetPro")
            .setContentText("$statusText: $durationText")
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(tapPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setPublicVersion(publicNotification)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun getActionPendingIntent(status: TimerStatus): PendingIntent {
        val action = if (status == TimerStatus.RUNNING) ACTION_PAUSE else ACTION_RESUME
        val intent = Intent(this, TimerService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, if (status == TimerStatus.RUNNING) 1 else 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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

    private fun roundAmount(amount: Double, mode: AmountRoundingMode, step: Double): Double {
        if (mode == AmountRoundingMode.NONE || step <= 0.0) {
            return kotlin.math.round(amount * 100.0) / 100.0
        }
        val factor = 1.0 / step
        return when (mode) {
            AmountRoundingMode.ROUND_UP -> kotlin.math.ceil(amount * factor) / factor
            AmountRoundingMode.ROUND_DOWN -> kotlin.math.floor(amount * factor) / factor
            AmountRoundingMode.ROUND_NEAREST -> kotlin.math.round(amount * factor) / factor
            else -> amount
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Time Tracking Service",
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

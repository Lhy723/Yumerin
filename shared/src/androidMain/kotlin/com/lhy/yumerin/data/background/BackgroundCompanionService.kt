package com.lhy.yumerin.data.background

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lhy.yumerin.data.room.createAndroidAppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackgroundCompanionService : Service() {
    private var paused = false
    private val preferences by lazy { getSharedPreferences("background_companion", MODE_PRIVATE) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        paused = preferences.getBoolean(AndroidBackgroundCompanionController.KEY_PAUSED, false)
        NotificationChannels.ensure(this)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                preferences.edit()
                    .putBoolean(AndroidBackgroundCompanionController.KEY_ENABLED, false)
                    .putBoolean(AndroidBackgroundCompanionController.KEY_PAUSED, false)
                    .apply()
                BackgroundWorkScheduler.schedule(this)
                serviceScope.launch {
                    runCatching {
                        val graph = createAndroidAppGraph(applicationContext)
                        val settings = graph.settings.observeSettings().first()
                        graph.settings.saveSettings(settings.copy(backgroundCompanionEnabled = false))
                    }
                    withContext(Dispatchers.Main) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            ACTION_PAUSE -> {
                paused = !paused
                preferences.edit().putBoolean(AndroidBackgroundCompanionController.KEY_PAUSED, paused).apply()
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification() = NotificationCompat.Builder(this, NotificationChannels.BACKGROUND_STATUS)
        .setSmallIcon(android.R.drawable.ic_popup_sync)
        .setContentTitle(if (paused) "陪伴已暂停" else "Yumerin 正在陪伴")
        .setContentText(if (paused) "点击暂停按钮可继续" else "后台任务已启用，消息仍会保存在本地")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .addAction(
            0,
            if (paused) "继续" else "暂停",
            PendingIntentFactory.service(this, ACTION_PAUSE)
        )
        .addAction(0, "停止", PendingIntentFactory.service(this, ACTION_STOP))
        .build()

    companion object {
        const val ACTION_START = "com.lhy.yumerin.action.START_COMPANION"
        const val ACTION_PAUSE = "com.lhy.yumerin.action.PAUSE_COMPANION"
        const val ACTION_STOP = "com.lhy.yumerin.action.STOP_COMPANION"
        const val NOTIFICATION_ID = 1001
    }
}

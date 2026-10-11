package com.lhy.yumerin.data.background

import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.lhy.yumerin.domain.background.BackgroundCompanionController

class AndroidBackgroundCompanionController(
    context: Context
) : BackgroundCompanionController {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override suspend fun setEnabled(enabled: Boolean) {
        if (
            enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            preferences.edit().putBoolean(KEY_ENABLED, false).apply()
            BackgroundWorkScheduler.cancel(appContext)
            return
        }
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            preferences.edit().putBoolean(KEY_PAUSED, false).apply()
            NotificationChannels.ensure(appContext)
            BackgroundWorkScheduler.schedule(appContext)
            val intent = Intent(appContext, BackgroundCompanionService::class.java)
                .setAction(BackgroundCompanionService.ACTION_START)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(appContext, intent)
                else appContext.startService(intent)
            } catch (_: SecurityException) {
                preferences.edit().putBoolean(KEY_ENABLED, false).apply()
                BackgroundWorkScheduler.cancel(appContext)
            }
        } else {
            // Foreground mode is disabled, but scheduled low-frequency tasks remain
            // available when the user has enabled proactive messages.
            BackgroundWorkScheduler.schedule(appContext)
            appContext.startService(
                Intent(appContext, BackgroundCompanionService::class.java)
                    .setAction(BackgroundCompanionService.ACTION_STOP)
            )
        }
    }

    override suspend fun stopAll() {
        preferences.edit()
            .putBoolean(KEY_ENABLED, false)
            .putBoolean(KEY_PAUSED, false)
            .apply()
        BackgroundWorkScheduler.cancel(appContext)
        appContext.stopService(Intent(appContext, BackgroundCompanionService::class.java))
    }

    override fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    companion object {
        private const val PREFERENCES = "background_companion"
        const val KEY_ENABLED = "enabled"
        const val KEY_PAUSED = "paused"
    }
}

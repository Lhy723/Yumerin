package com.lhy.yumerin.data.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val preferences = context.getSharedPreferences("background_companion", Context.MODE_PRIVATE)
        BackgroundWorkScheduler.schedule(context)
        if (!preferences.getBoolean(AndroidBackgroundCompanionController.KEY_ENABLED, false)) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            preferences.edit().putBoolean(AndroidBackgroundCompanionController.KEY_ENABLED, false).apply()
            return
        }
        NotificationChannels.ensure(context)
        ContextCompat.startForegroundService(
            context,
            Intent(context, BackgroundCompanionService::class.java)
                .setAction(BackgroundCompanionService.ACTION_START)
        )
    }
}

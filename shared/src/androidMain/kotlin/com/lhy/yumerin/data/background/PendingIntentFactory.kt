package com.lhy.yumerin.data.background

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

internal object PendingIntentFactory {
    fun service(context: Context, action: String): PendingIntent {
        val intent = Intent(context, BackgroundCompanionService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

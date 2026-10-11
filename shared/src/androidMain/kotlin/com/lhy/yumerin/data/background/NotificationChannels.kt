package com.lhy.yumerin.data.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val CHAT = "chat"
    const val COMPANIONSHIP = "companionship_reminders"
    const val BACKGROUND_STATUS = "background_status"
    const val LOCAL_MODEL_DOWNLOAD = "local_model_download"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHAT, "聊天消息", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(COMPANIONSHIP, "陪伴提醒", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(BACKGROUND_STATUS, "后台陪伴状态", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(LOCAL_MODEL_DOWNLOAD, "本地模型下载", NotificationManager.IMPORTANCE_LOW)
            )
        )
    }
}

package com.lhy.yumerin.data.background

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lhy.yumerin.core.model.ChatMessage

object CompanionNotifier {
    fun show(context: Context, message: ChatMessage, muted: Boolean) {
        if (muted) return
        if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationChannels.ensure(context)
        val intent = Intent().apply {
            setClassName(context, "com.lhy.yumerin.MainActivity")
            putExtra(EXTRA_CHAT_ID, message.chatId)
        }
        val pending = android.app.PendingIntent.getActivity(
            context,
            message.chatId.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompat.from(context).notify(
            message.chatId.hashCode(),
            NotificationCompat.Builder(context, NotificationChannels.COMPANIONSHIP)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(message.senderName)
                .setContentText(message.text.take(120))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build()
        )
    }

    const val EXTRA_CHAT_ID = "chat_id"
}

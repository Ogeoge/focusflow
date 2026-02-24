package com.focusflow.app.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.focusflow.app.R

/**
 * Small helper to post timer transition notifications from both in-app code and BroadcastReceiver.
 * Kept minimal for compile/runtime stability.
 */
object TimerNotificationHelper {

    fun postTransitionNotification(
        context: Context,
        segmentTypeContractValue: String?,
        planLabel: String?,
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val segmentLabel = when (segmentTypeContractValue) {
            "work" -> "Work"
            "break" -> "Break"
            "long_break" -> "Long break"
            null -> "Next segment"
            else -> "Next segment"
        }

        val title = "FocusFlow"
        val text = buildString {
            append("$segmentLabel finished")
            if (!planLabel.isNullOrBlank()) append(" • $planLabel")
        }

        val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_TIMER_TRANSITIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID_TRANSITION, notification)
    }

    private const val NOTIFICATION_ID_TRANSITION = 1001
}

package com.focusflow.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Central place to define notification channel IDs and ensure they exist.
 */
object NotificationChannels {

    /**
     * Backward-compatible alias used by older call sites.
     */
    fun createAll(appContext: android.content.Context) = ensureCreated(appContext)


    const val CHANNEL_TIMER_TRANSITIONS = "timer_transitions"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val existing = nm.getNotificationChannel(CHANNEL_TIMER_TRANSITIONS)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_TIMER_TRANSITIONS,
            "Timer transitions",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notifications when a timer segment ends."
        }

        nm.createNotificationChannel(channel)
    }
}

package com.focusflow.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives AlarmManager timer transition events.
 *
 * Minimal v1 behavior:
 * - Posts a transition notification (sound/vibration/DND behavior handled by NotificationChannels + settings).
 * - No persisted "running timer" state is stored yet; the TimerViewModel remains the source of truth
 *   while the app is in memory.
 */
class TimerBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        if (action != TimerAlarmScheduler.ACTION_TIMER_TRANSITION) return

        val segmentType = intent.getStringExtra(TimerAlarmScheduler.EXTRA_SEGMENT_TYPE)
        val planLabel = intent.getStringExtra(TimerAlarmScheduler.EXTRA_PLAN_LABEL)

        // Keep the receiver minimal and resilient: no crashes on malformed extras.
        // Post the transition notification.
        NotificationChannels.ensureCreated(context)
        TimerNotificationHelper.postTransitionNotification(
            context = context,
            segmentTypeContractValue = segmentType,
            planLabel = planLabel,
        )
    }
}

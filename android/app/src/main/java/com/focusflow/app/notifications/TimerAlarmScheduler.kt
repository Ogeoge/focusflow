package com.focusflow.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules timer transition events.
 *
 * First version strategy:
 * - Use AlarmManager to schedule an exact-ish broadcast that is received by [TimerBroadcastReceiver].
 * - UI/ViewModel is responsible for persisting any timer state needed to reconstruct what to do
 *   at trigger time (this file only schedules/cancels alarms).
 *
 * Notes:
 * - Exact alarms may require user permission on some OEMs / Android versions.
 * - If exact scheduling is not allowed, we fall back to inexact alarms.
 */
object TimerAlarmScheduler {

    const val ACTION_TIMER_TRANSITION = "com.focusflow.app.notifications.ACTION_TIMER_TRANSITION"

    /**
     * The receiver uses this to know what kind of transition happened.
     * Values are contract-aligned session types: work|break|long_break.
     */
    const val EXTRA_SEGMENT_TYPE = "extra_segment_type"

    /** Epoch millis when the segment was expected to end (informational). */
    const val EXTRA_TARGET_END_EPOCH_MS = "extra_target_end_epoch_ms"

    /** Optional task id to display in the notification (uuid string). */
    const val EXTRA_LINKED_TASK_ID = "extra_linked_task_id"

    /** Optional plan label for analytics/export/display. */
    const val EXTRA_PLAN_LABEL = "extra_plan_label"

    private const val REQUEST_CODE_TRANSITION = 1001

    fun scheduleSegmentEnd(
        context: Context,
        triggerAtEpochMs: Long,
        segmentType: String,
        linkedTaskId: String? = null,
        planLabel: String? = null,
    ) {
        require(triggerAtEpochMs > 0L) { "triggerAtEpochMs must be > 0" }
        require(segmentType in setOf("work", "break", "long_break")) {
            "segmentType must be one of work|break|long_break"
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val pi = buildPendingIntent(
            context = context,
            segmentType = segmentType,
            targetEndEpochMs = triggerAtEpochMs,
            linkedTaskId = linkedTaskId,
            planLabel = planLabel,
        )

        // Cancel any existing scheduled transition to keep a single active alarm.
        alarmManager.cancel(pi)

        val canExact = canScheduleExactAlarmsCompat(alarmManager)

        when {
            canExact -> {
                // Best effort exact.
                // Use setExactAndAllowWhileIdle when possible to reduce drift.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtEpochMs,
                        pi,
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtEpochMs,
                        pi,
                    )
                }
            }

            else -> {
                // Fallback: inexact. The timer UI should still function when the app is in foreground;
                // this is mainly to get a notification when backgrounded.
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtEpochMs,
                    pi,
                )
            }
        }
    }

    fun cancelScheduledSegmentEnd(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(
            context = context,
            segmentType = "work",
            targetEndEpochMs = 0L,
            linkedTaskId = null,
            planLabel = null,
        )
        alarmManager.cancel(pi)
    }

    private fun buildPendingIntent(
        context: Context,
        segmentType: String,
        targetEndEpochMs: Long,
        linkedTaskId: String?,
        planLabel: String?,
    ): PendingIntent {
        val intent = Intent(context, TimerBroadcastReceiver::class.java).apply {
            action = ACTION_TIMER_TRANSITION
            putExtra(EXTRA_SEGMENT_TYPE, segmentType)
            putExtra(EXTRA_TARGET_END_EPOCH_MS, targetEndEpochMs)
            if (linkedTaskId != null) putExtra(EXTRA_LINKED_TASK_ID, linkedTaskId)
            if (planLabel != null) putExtra(EXTRA_PLAN_LABEL, planLabel)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_TRANSITION,
            intent,
            flags,
        )
    }

    private fun canScheduleExactAlarmsCompat(alarmManager: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}

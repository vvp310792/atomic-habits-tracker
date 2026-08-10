package com.atomichabits.tracker.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.atomichabits.tracker.data.Habit
import java.util.Calendar

/**
 * Schedules one exact, repeating (self-rescheduling) alarm per habit that has
 * reminders enabled. AlarmManager alarms do not survive reboot, so [BootReceiver]
 * re-registers everything on BOOT_COMPLETED.
 */
object ReminderScheduler {

    fun schedule(context: Context, habit: Habit) {
        if (!habit.reminderEnabled) {
            cancel(context, habit.id)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val triggerAt = nextTriggerMillis(habit.reminderHour, habit.reminderMinute)
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SHOW_REMINDER
            putExtra(NotificationHelper.EXTRA_HABIT_ID, habit.id)
            putExtra(EXTRA_HABIT_NAME, habit.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, habit.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // No permission yet (user must grant "Alarms & reminders" in system settings).
            // Fall back to an inexact alarm so something still fires.
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancel(context: Context, habitId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SHOW_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, habitId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (trigger.before(now)) {
            trigger.add(Calendar.DAY_OF_YEAR, 1)
        }
        return trigger.timeInMillis
    }

    const val EXTRA_HABIT_NAME = "extra_habit_name"
}

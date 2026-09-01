package com.atomichabits.tracker.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.data.isHabitPausedOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(NotificationHelper.EXTRA_HABIT_ID, -1L)
        if (habitId == -1L) return

        val app = context.applicationContext as HabitTrackerApp
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_SHOW_REMINDER -> {
                        val fresh = app.database.habitDao().getHabit(habitId)
                        // Belt-and-suspenders: ReminderScheduler already only schedules
                        // for the habit's active days, but if activeDays changed after
                        // this specific alarm was already queued (edit happened between
                        // scheduling and firing), don't show a notification for a day
                        // that's no longer scheduled - just quietly reschedule instead.
                        // Also silenced during an active travel pause covering this habit
                        // (see PausePeriod.kt) - no point buzzing about a habit that's
                        // deliberately excused while travelling.
                        val pausePeriods = app.database.pausePeriodDao().getAllOnce()
                        val isPaused = fresh != null && isHabitPausedOn(fresh.syncId, LocalDate.now(), pausePeriods)
                        val todayIsActive = fresh != null &&
                            ReminderScheduler.isActiveOn(fresh.activeDays, java.util.Calendar.getInstance())
                        if (fresh != null && todayIsActive && !isPaused) {
                            val name = intent.getStringExtra(ReminderScheduler.EXTRA_HABIT_NAME) ?: ""
                            NotificationHelper.showReminder(context, habitId, name)
                        }
                        // Reschedule tomorrow's (or next active day's) alarm for this habit.
                        fresh?.let { ReminderScheduler.schedule(context, it) }
                    }
                    ACTION_MARK_DONE -> {
                        app.repository.toggleCompletion(habitId, LocalDate.now())
                        NotificationManagerCompat.from(context).cancel(habitId.toInt())
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SHOW_REMINDER = "com.atomichabits.tracker.ACTION_SHOW_REMINDER"
        const val ACTION_MARK_DONE = "com.atomichabits.tracker.ACTION_MARK_DONE"
    }
}

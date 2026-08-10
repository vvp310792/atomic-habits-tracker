package com.atomichabits.tracker.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.atomichabits.tracker.HabitTrackerApp
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
                        val name = intent.getStringExtra(ReminderScheduler.EXTRA_HABIT_NAME) ?: ""
                        NotificationHelper.showReminder(context, habitId, name)
                        // Reschedule tomorrow's alarm for this habit.
                        app.database.habitDao().getHabit(habitId)?.let { fresh ->
                            ReminderScheduler.schedule(context, fresh)
                        }
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

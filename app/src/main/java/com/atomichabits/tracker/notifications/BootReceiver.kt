package com.atomichabits.tracker.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.atomichabits.tracker.HabitTrackerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as HabitTrackerApp
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.database.habitDao().getAllActiveOnce()
                    .filter { it.reminderEnabled }
                    .forEach { habit -> ReminderScheduler.schedule(context, habit) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

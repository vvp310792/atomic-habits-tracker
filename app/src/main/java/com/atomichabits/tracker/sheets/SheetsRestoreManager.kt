package com.atomichabits.tracker.sheets

import android.content.Context
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.data.Habit
import com.atomichabits.tracker.data.HabitLog
import com.atomichabits.tracker.notifications.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class RestoreOutcome {
    data class Success(val habitsRestored: Int, val logsRestored: Int) : RestoreOutcome()
    data object NothingFound : RestoreOutcome()
    data object Failed : RestoreOutcome()
}

object SheetsRestoreManager {

    /**
     * Pulls everything currently in the linked Google Sheet and merges it into the
     * local database, matching by [Habit.syncId]. Existing local habits are updated
     * in place (by syncId); habits present remotely but not locally are recreated.
     * Nothing local is deleted, so this is safe to run even if some local data
     * already exists - it's meant primarily for a fresh install with an empty DB.
     */
    suspend fun restore(context: Context, webAppUrl: String): RestoreOutcome = withContext(Dispatchers.IO) {
        val app = context.applicationContext as HabitTrackerApp
        val exporter = SheetsExporter(webAppUrl)
        val remote = exporter.pull() ?: return@withContext RestoreOutcome.Failed

        if (remote.habits.isEmpty() && remote.logs.isEmpty()) {
            return@withContext RestoreOutcome.NothingFound
        }

        val habitDao = app.database.habitDao()
        val logDao = app.database.habitLogDao()

        val localIdBySyncId = mutableMapOf<String, Long>()

        remote.habits.forEach { r ->
            val existing = habitDao.getBySyncId(r.syncId)
            val habit = Habit(
                id = existing?.id ?: 0,
                syncId = r.syncId,
                name = r.name,
                emoji = r.emoji,
                colorHex = r.colorHex,
                activeDays = r.activeDays,
                timeOfDay = r.timeOfDay,
                reminderEnabled = r.reminderEnabled,
                reminderHour = r.reminderHour,
                reminderMinute = r.reminderMinute,
                lawObvious = r.lawObvious,
                lawAttractive = r.lawAttractive,
                lawEasy = r.lawEasy,
                lawSatisfying = r.lawSatisfying,
                createdAtEpochDay = r.createdAtEpochDay,
                archived = r.archived,
                sortOrder = existing?.sortOrder ?: 0
            )
            val localId = habitDao.upsert(habit)
            localIdBySyncId[r.syncId] = if (existing != null) existing.id else localId
        }

        var logsRestored = 0
        remote.logs.forEach { r ->
            val localHabitId = localIdBySyncId[r.syncId] ?: return@forEach
            logDao.upsert(
                HabitLog(
                    habitId = localHabitId,
                    dateEpochDay = r.dateEpochDay,
                    completed = r.completed,
                    completedAtMillis = r.completedAtMillis,
                    synced = true // it just came from the sheet, no need to push it right back
                )
            )
            logsRestored++
        }

        // Re-arm reminders for every restored habit that wants one.
        remote.habits.filter { it.reminderEnabled && !it.archived }.forEach { r ->
            localIdBySyncId[r.syncId]?.let { localId ->
                habitDao.getHabit(localId)?.let { habit -> ReminderScheduler.schedule(context, habit) }
            }
        }

        RestoreOutcome.Success(habitsRestored = remote.habits.size, logsRestored = logsRestored)
    }
}

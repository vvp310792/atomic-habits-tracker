package com.atomichabits.tracker.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.atomichabits.tracker.data.HabitDao
import com.atomichabits.tracker.data.HabitJournalEntryDao
import com.atomichabits.tracker.data.HabitLogDao
import com.atomichabits.tracker.data.IdentityDao
import com.atomichabits.tracker.data.ImpulseLogDao
import com.atomichabits.tracker.data.computeJournalDaysWithout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val WEEKDAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")

/**
 * Dumps everything useful for analysing the person's habit system - habits (with
 * their Atomic Habits fields: the 4 Laws, stacking, identity link), every
 * completion date, identities, and "Позыв" (urge) events - into one JSON file,
 * meant to be shared/pasted into a chat with an AI for review and
 * recommendations, not as a machine-restore backup format.
 */
object DataExporter {

    suspend fun export(
        context: Context,
        habitDao: HabitDao,
        habitLogDao: HabitLogDao,
        identityDao: IdentityDao,
        impulseLogDao: ImpulseLogDao,
        journalDao: HabitJournalEntryDao
    ): File {
        val habits = habitDao.getAllOnce()
        val logs = habitLogDao.getAllOnce()
        val identities = identityDao.getAllOnce()
        val impulseLogs = impulseLogDao.getAllOnce()
        val journalEntries = journalDao.getAllOnce()
        val completedDatesByHabitId = logs.filter { it.completed }.groupBy { it.habitId }

        val root = JSONObject().apply {
            put("exportedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
            put("app", "Atomic Habits Tracker")

            put("identities", JSONArray().apply {
                identities.forEach { identity ->
                    put(JSONObject().apply {
                        put("statement", identity.statement)
                        put("archived", identity.archived)
                    })
                }
            })

            put("habits", JSONArray().apply {
                habits.forEach { habit ->
                    val dates = completedDatesByHabitId[habit.id].orEmpty()
                        .map { LocalDate.ofEpochDay(it.dateEpochDay).toString() }
                        .sorted()
                    put(JSONObject().apply {
                        put("name", habit.name)
                        put("category", habit.category)
                        // What the Habit Scorecard exercise honestly classifies this as,
                        // independent of whether it's actively tracked.
                        put("qualityType", habit.qualityType)
                        put("isTracked", habit.isTracked)
                        if (habit.manuallyMastered) put("mastery", "manually declared mastered")
                        put("timeOfDay", habit.timeOfDay)
                        put("activeDays", JSONArray(activeDayNames(habit.activeDays)))
                        if (habit.reminderEnabled) {
                            put("reminder", "%02d:%02d".format(habit.reminderHour, habit.reminderMinute))
                        }
                        if (listOf(habit.lawObvious, habit.lawAttractive, habit.lawEasy, habit.lawSatisfying)
                                .any { it.isNotBlank() }
                        ) {
                            put("fourLaws", JSONObject().apply {
                                if (habit.lawObvious.isNotBlank()) put("obvious", habit.lawObvious)
                                if (habit.lawAttractive.isNotBlank()) put("attractive", habit.lawAttractive)
                                if (habit.lawEasy.isNotBlank()) put("easy", habit.lawEasy)
                                if (habit.lawSatisfying.isNotBlank()) put("satisfying", habit.lawSatisfying)
                            })
                        }
                        if (habit.qualityType == "HARMFUL") {
                            if (habit.alternativeSuggestion.isNotBlank()) {
                                put("alternativeSuggestion", habit.alternativeSuggestion)
                            }
                            if (habit.whyItMatters.isNotBlank()) put("whyItMatters", habit.whyItMatters)
                            if (habit.selfBindingAction.isNotBlank()) put("selfBindingAction", habit.selfBindingAction)
                            if (habit.isTracked) {
                                val daysWithout = computeJournalDaysWithout(habit.syncId, habit.createdAtEpochDay, journalEntries)
                                put("daysWithoutSlip", daysWithout.currentDays)
                                put("bestDaysWithoutSlip", daysWithout.bestDays)
                                if (habit.journalCycleStartEpochDay > 0) {
                                    put("journalCycleStarted", LocalDate.ofEpochDay(habit.journalCycleStartEpochDay).toString())
                                }
                            }
                        }
                        if (habit.stackAnchorLabel.isNotBlank()) put("stackedAfter", habit.stackAnchorLabel)
                        if (habit.identityLabel.isNotBlank()) put("linkedIdentity", habit.identityLabel)
                        if (habit.temptationBundle.isNotBlank()) put("temptationBundle", habit.temptationBundle)
                        if (habit.difficultyNote.isNotBlank()) {
                            put("difficultyLevel", habit.difficultyNote)
                            if (habit.difficultyBumpedAtEpochDay > 0) {
                                put("difficultyLastBumped", LocalDate.ofEpochDay(habit.difficultyBumpedAtEpochDay).toString())
                            }
                        }
                        put("archived", habit.archived)
                        put("totalCompletions", dates.size)
                        put("completionDates", JSONArray(dates))
                    })
                }
            })

            put("impulseEvents", JSONArray().apply {
                impulseLogs.sortedBy { it.timestampMillis }.forEach { log ->
                    put(JSONObject().apply {
                        put("date", LocalDate.ofEpochDay(log.dateEpochDay).toString())
                        // "CHECK" = held out against the urge, "CROSS" = gave in.
                        put("outcome", log.outcome)
                        if (log.triggerTags.isNotBlank()) {
                            put("triggers", JSONArray(log.triggerTags.split(",").map { it.trim() }))
                        }
                        if (log.note.isNotBlank()) put("note", log.note)
                        if (log.linkedHarmfulAnchorLabel.isNotBlank()) {
                            put("linkedHabit", log.linkedHarmfulAnchorLabel)
                        }
                    })
                }
            })

            // The current daily-diary model (Misuzu Nakashima's CBT method), which
            // replaced the check/cross "Позыв" screen above - this is now the
            // primary source of truth for harmful-habit data going forward.
            put("journalEntries", JSONArray().apply {
                val habitNameBySyncId = habits.associate { it.syncId to it.name }
                journalEntries.sortedBy { it.dateEpochDay }.forEach { entry ->
                    put(JSONObject().apply {
                        put("habit", habitNameBySyncId[entry.habitSyncId] ?: entry.habitSyncId)
                        put("date", LocalDate.ofEpochDay(entry.dateEpochDay).toString())
                        if (entry.todaysEvents.isNotBlank()) put("todaysEvents", entry.todaysEvents)
                        put("hadIncident", entry.hadIncident)
                        put("amount", entry.amount)
                        if (entry.whatIWanted.isNotBlank()) put("whatIWanted", entry.whatIWanted)
                        if (entry.substituteBehavior.isNotBlank()) {
                            put("substituteBehavior", entry.substituteBehavior)
                            put("substituteSucceeded", entry.substituteSucceeded)
                        }
                    })
                }
            })
        }

        val exportDir = File(context.cacheDir, "export").apply { mkdirs() }
        // Only ever keep the latest export around - nothing reads old ones, and
        // letting them pile up in cache would just be silent, purposeless growth.
        exportDir.listFiles()?.forEach { it.delete() }
        val stamp = FILE_STAMP.format(LocalDateTime.now())
        val file = File(exportDir, "atomic-habits-export_$stamp.json")
        file.writeText(root.toString(2))
        return file
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Экспорт привычек")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Поделиться экспортом привычек")
    }

    private fun activeDayNames(mask: Int): List<String> =
        WEEKDAY_NAMES.filterIndexed { index, _ -> (mask shr index) and 1 == 1 }
}

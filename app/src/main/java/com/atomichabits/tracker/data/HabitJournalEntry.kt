package com.atomichabits.tracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One day's diary entry for one harmful habit, modelled directly on the
 * 4-column table from Misuzu Nakashima's CBT-based method (function analysis
 * of behaviour): not a binary "held/gave in" like the old Impulse screen, but
 * an honest daily log of how MUCH happened, what was actually going on, what
 * need was really being chased, and what was tried instead - because per that
 * method a bad habit persists because it solves something real, and reducing
 * it is a gradual substitution process, not an instant switch.
 *
 * [todaysEvents] / [hadIncident] - "Сегодняшние события": free-text context
 * for the day, with [hadIncident] marking whether something habit-related
 * happened at all (the book's "○" checkbox).
 * [amount] - "Объём нежелательной привычки": free text on purpose (e.g. "5
 * банок", "не было") since the real quantity unit differs per habit and isn't
 * always numeric - descriptive detail only, filled in when [hadSlip] is true.
 * [hadSlip] is the explicit source of truth for whether today counts as a
 * slip - NOT whether [amount] happens to be blank. Early on this was inferred
 * from a blank amount, but that broke the moment someone honestly typed "0"
 * into the amount field as instructed by its own hint text: "0" is a
 * non-blank string, so every single filled-in day was silently counted as a
 * slip. An explicit checkbox has no such ambiguity.
 * [whatIWanted] - "Чего я хотел(а) на самом деле": re-examined fresh every
 * single day (not set once at habit creation like whyItMatters/alternativeSuggestion) -
 * the whole point of the method is that this answer changes as understanding deepens.
 * [substituteBehavior] / [substituteSucceeded] - "Замещающее поведение (если
 * удалось, поставьте кружок)": what was tried instead that day, and whether it worked.
 *
 * One entry per (habit, day) - see the unique index - so filling today's form
 * again just replaces today's row.
 */
@Entity(
    tableName = "habit_journal_entries",
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["habitId", "dateEpochDay"], unique = true)
    ]
)
data class HabitJournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "",
    val habitId: Long = 0,
    val habitSyncId: String = "",
    val dateEpochDay: Long = 0,
    val todaysEvents: String = "",
    val hadIncident: Boolean = false,
    val hadSlip: Boolean = false,
    val amount: String = "",
    val whatIWanted: String = "",
    val substituteBehavior: String = "",
    val substituteSucceeded: Boolean = false,
    /** Which 30-day cycle this entry belongs to - see Habit.journalCycleStartEpochDay. */
    val cycleStartEpochDay: Long = 0
)

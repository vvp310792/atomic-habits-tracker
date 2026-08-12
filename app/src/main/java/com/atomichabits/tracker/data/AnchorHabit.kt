package com.atomichabits.tracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An already-established routine (not itself tracked with logs/streaks/reminders)
 * that new [Habit]s can be "stacked" onto, per James Clear's habit-stacking
 * formula: "After [ANCHOR], I will [NEW HABIT]." [type] is "USEFUL", "HARMFUL",
 * "NEUTRAL", or "DESIRED". USEFUL/HARMFUL/NEUTRAL together are also what the
 * Habit Scorecard flow (an audit of your current routines, judgment-free) writes
 * into - it's the same table, just populated via a guided flow instead of one at
 * a time. Typically only USEFUL/HARMFUL make good stacking anchors.
 *
 * [alternativeSuggestion] is only meaningful for HARMFUL entries: a concrete "do
 * this instead" action (e.g. "Выпить стакан воды"), shown as a suggestion on the
 * Impulse screen when this habit is linked to an urge.
 */
@Entity(tableName = "anchor_habits", indices = [Index(value = ["syncId"], unique = true)])
data class AnchorHabit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "",
    val name: String,
    val type: String = "USEFUL", // "USEFUL" | "HARMFUL" | "NEUTRAL" | "DESIRED"
    val createdAtEpochDay: Long = 0,
    val archived: Boolean = false,
    val alternativeSuggestion: String = ""
)

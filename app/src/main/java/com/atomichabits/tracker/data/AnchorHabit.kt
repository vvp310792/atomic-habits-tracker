package com.atomichabits.tracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An already-established routine (not itself tracked with logs/streaks/reminders)
 * that new [Habit]s can be "stacked" onto, per James Clear's habit-stacking
 * formula: "After [ANCHOR], I will [NEW HABIT]." [type] is "USEFUL" or "HARMFUL" -
 * both are worth cataloguing (this doubles as a lightweight version of the
 * book's "Habit Scorecard" exercise), but typically only useful ones make good
 * stacking anchors.
 */
@Entity(tableName = "anchor_habits", indices = [Index(value = ["syncId"], unique = true)])
data class AnchorHabit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "",
    val name: String,
    val type: String = "USEFUL", // "USEFUL" | "HARMFUL"
    val createdAtEpochDay: Long = 0,
    val archived: Boolean = false
)

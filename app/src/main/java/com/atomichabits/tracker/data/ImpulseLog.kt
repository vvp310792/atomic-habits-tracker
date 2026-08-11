package com.atomichabits.tracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One "Позыв" (urge) event. [outcome] is "CHECK" (held out) or "CROSS" (gave in).
 * The daily tally of CHECK vs CROSS is the whole point - not streaks, so a single
 * CROSS doesn't feel like "the day is ruined" (the "what the hell effect" from
 * relapse-prevention research). [triggerTags]/[note] are only filled in for CROSS,
 * as a short, low-friction reflection rather than a shame-inducing confession.
 */
@Entity(tableName = "impulse_logs", indices = [Index(value = ["syncId"], unique = true)])
data class ImpulseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "",
    val dateEpochDay: Long = 0,
    val timestampMillis: Long = System.currentTimeMillis(),
    val outcome: String = "CHECK", // "CHECK" | "CROSS"
    val triggerTags: String = "", // comma-separated, e.g. "Тревога,Скука"
    val note: String = ""
)

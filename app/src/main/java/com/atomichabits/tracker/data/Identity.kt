package com.atomichabits.tracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An identity statement (James Clear's "identity-based habits": every habit
 * completion is a vote for the type of person you're becoming), e.g.
 * "Я — человек, который заботится о своём теле". One or more [Habit]s can be
 * linked to an Identity (see [Habit.identityId]); each completion of a linked
 * habit counts as a "vote" toward that identity.
 */
@Entity(tableName = "identities", indices = [Index(value = ["syncId"], unique = true)])
data class Identity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "",
    val statement: String,
    val createdAtEpochDay: Long = 0,
    val archived: Boolean = false
)

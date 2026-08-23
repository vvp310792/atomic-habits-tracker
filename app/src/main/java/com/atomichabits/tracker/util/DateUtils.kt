package com.atomichabits.tracker.util

import java.time.LocalDate

/** Monday-first short weekday labels, index 0 = Monday .. 6 = Sunday, matching Habit.activeDays bit order. */
val WEEKDAY_LABELS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

const val ALL_DAYS_MASK = 0b1111111

fun isDayActive(activeDays: Int, bitIndex: Int): Boolean = (activeDays shr bitIndex) and 1 == 1

fun toggleDay(activeDays: Int, bitIndex: Int): Int = activeDays xor (1 shl bitIndex)

fun isHabitScheduledOn(activeDays: Int, date: LocalDate): Boolean {
    val bit = date.dayOfWeek.value - 1
    return isDayActive(activeDays, bit)
}

fun isHabitScheduledToday(activeDays: Int): Boolean = isHabitScheduledOn(activeDays, LocalDate.now())

/** Storage values for Habit.timeOfDay, in display order. */
val TIME_OF_DAY_VALUES = listOf("MORNING", "DAY", "EVENING", "ALL_DAY")

fun timeOfDayLabel(value: String): String = when (value) {
    "DAY" -> "День"
    "EVENING" -> "Вечер"
    "ALL_DAY" -> "Весь день"
    else -> "Утро"
}

/** Correct Russian declension of "день/дня/дней" for a count. */
fun declineDays(n: Int): String = when {
    n % 100 in 11..14 -> "дней"
    n % 10 == 1 -> "день"
    n % 10 in 2..4 -> "дня"
    else -> "дней"
}

/** Correct Russian declension of "секунда/секунды/секунд" for a count. */
fun declineSeconds(n: Int): String = when {
    n % 100 in 11..14 -> "секунд"
    n % 10 == 1 -> "секунда"
    n % 10 in 2..4 -> "секунды"
    else -> "секунд"
}

package com.atomichabits.tracker.util

import java.time.LocalDate

/** Monday-first short weekday labels, index 0 = Monday .. 6 = Sunday, matching Habit.activeDays bit order. */
val WEEKDAY_LABELS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

const val ALL_DAYS_MASK = 0b1111111

fun isDayActive(activeDays: Int, bitIndex: Int): Boolean = (activeDays shr bitIndex) and 1 == 1

fun toggleDay(activeDays: Int, bitIndex: Int): Int = activeDays xor (1 shl bitIndex)

fun isHabitScheduledToday(activeDays: Int): Boolean {
    val bit = LocalDate.now().dayOfWeek.value - 1
    return isDayActive(activeDays, bit)
}

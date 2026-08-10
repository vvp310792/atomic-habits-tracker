package com.atomichabits.tracker.util

/** Storage values for Habit.category, in display order. */
val CATEGORY_VALUES = listOf(
    "CAREER", "INTELLECT", "SELF_DEVELOPMENT", "FINANCE", "SOCIETY", "HEALTH", "FAMILY"
)

fun categoryLabel(value: String): String = when (value) {
    "CAREER" -> "Карьера"
    "INTELLECT" -> "Интеллект"
    "FINANCE" -> "Финансы"
    "SOCIETY" -> "Общество"
    "HEALTH" -> "Здоровье"
    "FAMILY" -> "Семья"
    else -> "Саморазвитие" // SELF_DEVELOPMENT + fallback for unknown/legacy values
}

/** Fixed accent color (hex) per category - not user-choosable, tied to the tag itself. */
fun categoryColorHex(value: String): String = when (value) {
    "CAREER" -> "#8B5CF6"           // фиолетовый
    "INTELLECT" -> "#3B82F6"        // синий
    "FINANCE" -> "#22C55E"          // зелёный
    "SOCIETY" -> "#F97316"          // оранжевый
    "HEALTH" -> "#EF4444"           // красный
    "FAMILY" -> "#EC4899"           // розовый
    else -> "#38BDF8"               // SELF_DEVELOPMENT -> голубой + fallback
}

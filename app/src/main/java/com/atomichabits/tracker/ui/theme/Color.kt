package com.atomichabits.tracker.ui.theme

import androidx.compose.ui.graphics.Color

// Light scheme
val PrimaryLight = Color(0xFF6750E4)
val OnPrimaryLight = Color(0xFFFFFFFF)
val BackgroundLight = Color(0xFFFAF9FF)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFEDEBFA)
val OnSurfaceLight = Color(0xFF1B1B23)

// Dark scheme
val PrimaryDark = Color(0xFFC4BAFF)
val OnPrimaryDark = Color(0xFF2E1D8C)
val BackgroundDark = Color(0xFF131218)
val SurfaceDark = Color(0xFF1D1C24)
val SurfaceVariantDark = Color(0xFF2B2A35)
val OnSurfaceDark = Color(0xFFE7E5F0)

// Habit accent presets offered in the color picker
val HabitColorPresets = listOf(
    0xFF7C6CF0, // violet
    0xFF3DBE8B, // green
    0xFFF2A93B, // amber
    0xFFEF6461, // coral
    0xFF3AA6D9, // blue
    0xFFE0609D, // pink
)

// Heatmap intensity (empty -> completed)
val HeatmapEmpty = Color(0xFFE7E5F0)
val HeatmapFilled = Color(0xFF6750E4)

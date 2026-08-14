package com.atomichabits.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.ui.theme.HeatmapEmpty
import com.atomichabits.tracker.ui.theme.HeatmapFilled
import kotlinx.coroutines.flow.filter
import java.time.LocalDate

/**
 * Renders the last [weeksToShow] weeks as a Monday-first grid, one column per week,
 * similar to a GitHub contributions graph: weeks run left (oldest) to right (most
 * recent), each column top (Monday) to bottom (Sunday). The row auto-scrolls to its
 * right edge on open, so today's week is what's actually visible without the person
 * needing to know to scroll - reading then goes right-to-left, from today backward
 * into history, which is what "the calendar should count properly" means in practice
 * for a habit-history view.
 */
@Composable
fun HabitHeatmap(completedDates: Set<LocalDate>, weeksToShow: Int = 13) {
    val today = LocalDate.now()
    val currentWeekMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val startMonday = currentWeekMonday.minusWeeks((weeksToShow - 1).toLong())
    val scrollState = rememberScrollState()

    LaunchedEffect(weeksToShow) {
        snapshotFlow { scrollState.maxValue }
            .filter { it > 0 }
            .collect { max -> scrollState.scrollTo(max) }
    }

    Row(
        modifier = Modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (w in 0 until weeksToShow) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val weekMonday = startMonday.plusWeeks(w.toLong())
                for (d in 0 until 7) {
                    val date = weekMonday.plusDays(d.toLong())
                    val isFuture = date.isAfter(today)
                    val filled = completedDates.contains(date)
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .background(
                                color = if (isFuture) androidx.compose.ui.graphics.Color.Transparent
                                else if (filled) HeatmapFilled else HeatmapEmpty,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.ui.theme.HeatmapEmpty
import com.atomichabits.tracker.ui.theme.HeatmapFilled
import java.time.LocalDate

/**
 * Renders the last [weeksToShow] weeks as a Monday-first grid, one column per week,
 * similar to a GitHub contributions graph.
 */
@Composable
fun HabitHeatmap(completedDates: Set<LocalDate>, weeksToShow: Int = 13) {
    val today = LocalDate.now()
    val currentWeekMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val startMonday = currentWeekMonday.minusWeeks((weeksToShow - 1).toLong())

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
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

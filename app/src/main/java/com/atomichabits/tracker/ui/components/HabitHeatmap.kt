package com.atomichabits.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

private val MONTH_NAMES = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
)
private val WEEKDAY_HEADERS = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")

/**
 * A classic month-view calendar for one habit's history: a Пн..Вс weekday header,
 * weeks stacking downward as rows, and the day-of-month number in every cell -
 * filled solid when [completedDates] contains that date. Opens on the current
 * month; the arrows page to earlier/later months (paging forward is capped at the
 * current month, since there's nothing to show beyond today).
 */
@Composable
fun HabitHeatmap(completedDates: Set<LocalDate>) {
    val today = remember { LocalDate.now() }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(today)) }

    val firstOfMonth = visibleMonth.atDay(1)
    val leadingBlanks = firstOfMonth.dayOfWeek.value - 1 // Monday = 0
    val daysInMonth = visibleMonth.lengthOfMonth()
    val monthLabel = "${MONTH_NAMES[visibleMonth.monthValue - 1]} ${visibleMonth.year}"

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = null)
            }
            Text(monthLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { visibleMonth = visibleMonth.plusMonths(1) },
                enabled = visibleMonth.isBefore(YearMonth.from(today))
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
        }
        Spacer(Modifier.size(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            WEEKDAY_HEADERS.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.size(4.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.height((((leadingBlanks + daysInMonth + 6) / 7) * 40).dp),
            userScrollEnabled = false
        ) {
            items(leadingBlanks) { Box(Modifier.size(32.dp)) }
            items(daysInMonth) { dayIndex ->
                val date = visibleMonth.atDay(dayIndex + 1)
                val isToday = date == today
                val isFuture = date.isAfter(today)
                val completed = completedDates.contains(date)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                when {
                                    completed -> MaterialTheme.colorScheme.primary
                                    isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else -> Color.Transparent
                                },
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (dayIndex + 1).toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                completed -> Color.White
                                isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}

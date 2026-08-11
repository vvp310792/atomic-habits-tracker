package com.atomichabits.tracker.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.ui.components.DateProgressRing
import com.atomichabits.tracker.util.isHabitScheduledOn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val HISTORY_DAYS = 30L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(app: HabitTrackerApp, onOpenHabit: (Long) -> Unit) {
    val habits by app.repository.observeActiveHabits().collectAsState(initial = emptyList())
    val today = remember { LocalDate.now() }
    val windowStart = remember { today.minusDays(HISTORY_DAYS - 1) }
    val logs by app.repository.observeLogsBetween(windowStart, today).collectAsState(initial = emptyList())

    val days = remember { (0 until HISTORY_DAYS).map { today.minusDays(it) } } // newest first

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(days, key = { it.toEpochDay() }) { date ->
                val scheduledIds = habits.filter { isHabitScheduledOn(it.activeDays, date) }.map { it.id }.toSet()
                val completedCount = logs.count {
                    it.dateEpochDay == date.toEpochDay() && it.completed && it.habitId in scheduledIds
                }
                if (scheduledIds.isNotEmpty()) {
                    HistoryDayRow(date, completedCount, scheduledIds.size, today)
                }
            }
        }
    }
}

@Composable
private fun HistoryDayRow(date: LocalDate, completed: Int, scheduled: Int, today: LocalDate) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp)) {
                    DateProgressRing(
                        date = date,
                        completed = completed,
                        scheduled = scheduled,
                        selected = date == today,
                        onClick = {}
                    )
                }
                Text(
                    dayLabel(date, today),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                "$completed/$scheduled",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String {
    if (date == today) return "Сегодня"
    if (date == today.minusDays(1)) return "Вчера"
    return date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
}

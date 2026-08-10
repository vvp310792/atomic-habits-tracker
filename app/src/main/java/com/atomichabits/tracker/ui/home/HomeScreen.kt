package com.atomichabits.tracker.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.data.Habit
import com.atomichabits.tracker.ui.components.DateProgressRing
import com.atomichabits.tracker.ui.components.HabitCard
import com.atomichabits.tracker.ui.components.SectionHeader
import com.atomichabits.tracker.util.isHabitScheduledOn
import com.atomichabits.tracker.util.timeOfDayLabel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val DATE_WINDOW_DAYS = 14L // shows the last 14 days, today included, in the date strip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: HabitTrackerApp,
    onAddHabit: () -> Unit,
    onOpenHabit: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val habits by app.repository.observeActiveHabits().collectAsState(initial = emptyList())
    val today = remember { LocalDate.now() }
    val windowStart = remember { today.minusDays(DATE_WINDOW_DAYS - 1) }
    val days = remember { (0 until DATE_WINDOW_DAYS).map { windowStart.plusDays(it) } }

    val windowLogs by app.repository.observeLogsBetween(windowStart, today).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var selectedDate by remember { mutableStateOf(today) }

    // completed/scheduled counts per date, driving how full each ring in the strip is
    val progressByDate = remember(habits, windowLogs) {
        days.associateWith { date ->
            val scheduledIds = habits.filter { isHabitScheduledOn(it.activeDays, date) }.map { it.id }.toSet()
            val completedCount = windowLogs.count {
                it.dateEpochDay == date.toEpochDay() && it.completed && it.habitId in scheduledIds
            }
            completedCount to scheduledIds.size
        }
    }

    val habitsForSelectedDate = remember(habits, selectedDate) {
        habits.filter { isHabitScheduledOn(it.activeDays, selectedDate) }
    }
    val completedIdsForSelectedDate = remember(windowLogs, selectedDate) {
        windowLogs.filter { it.dateEpochDay == selectedDate.toEpochDay() && it.completed }
            .map { it.habitId }.toSet()
    }

    val morning = habitsForSelectedDate.filter { it.timeOfDay == "MORNING" }
    val daySection = habitsForSelectedDate.filter { it.timeOfDay == "DAY" }
    val evening = habitsForSelectedDate.filter { it.timeOfDay == "EVENING" }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = maxOf(0, days.size - 6)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(headerTitle(selectedDate, today)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHabit) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    items(days) { date ->
                        val (completed, scheduled) = progressByDate[date] ?: (0 to 0)
                        DateProgressRing(
                            date = date,
                            completed = completed,
                            scheduled = scheduled,
                            selected = date == selectedDate,
                            onClick = { selectedDate = date }
                        )
                    }
                }
            }

            fun habitToggle(habit: Habit): () -> Unit = {
                scope.launch {
                    app.repository.toggleCompletion(habit.id, selectedDate)
                }
            }

            if (morning.isNotEmpty()) {
                item { SectionHeader(timeOfDayLabel("MORNING")) }
                items(morning, key = { "m${it.id}" }) { habit ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        HabitCard(
                            habit = habit,
                            completedToday = habit.id in completedIdsForSelectedDate,
                            repository = app.repository,
                            refreshKey = habit.id in completedIdsForSelectedDate,
                            onToggle = habitToggle(habit),
                            onClick = { onOpenHabit(habit.id) }
                        )
                    }
                }
            }
            if (daySection.isNotEmpty()) {
                item { SectionHeader(timeOfDayLabel("DAY")) }
                items(daySection, key = { "d${it.id}" }) { habit ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        HabitCard(
                            habit = habit,
                            completedToday = habit.id in completedIdsForSelectedDate,
                            repository = app.repository,
                            refreshKey = habit.id in completedIdsForSelectedDate,
                            onToggle = habitToggle(habit),
                            onClick = { onOpenHabit(habit.id) }
                        )
                    }
                }
            }
            if (evening.isNotEmpty()) {
                item { SectionHeader(timeOfDayLabel("EVENING")) }
                items(evening, key = { "e${it.id}" }) { habit ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        HabitCard(
                            habit = habit,
                            completedToday = habit.id in completedIdsForSelectedDate,
                            repository = app.repository,
                            refreshKey = habit.id in completedIdsForSelectedDate,
                            onToggle = habitToggle(habit),
                            onClick = { onOpenHabit(habit.id) }
                        )
                    }
                }
            }

            if (habitsForSelectedDate.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.home_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

private fun headerTitle(selectedDate: LocalDate, today: LocalDate): String {
    if (selectedDate == today) return "Сегодня"
    if (selectedDate == today.minusDays(1)) return "Вчера"
    val formatter = DateTimeFormatter.ofPattern("d MMMM")
    val monthGenitive = selectedDate.format(formatter)
    val weekday = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ru"))
    return "$monthGenitive, $weekday"
}

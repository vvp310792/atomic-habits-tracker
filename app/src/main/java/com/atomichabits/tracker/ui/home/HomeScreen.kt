package com.atomichabits.tracker.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.data.Habit
import com.atomichabits.tracker.data.computeDaysWithout
import com.atomichabits.tracker.ui.components.CrossGroupDraggableSections
import com.atomichabits.tracker.ui.components.DateProgressRing
import com.atomichabits.tracker.ui.components.DragGroup
import com.atomichabits.tracker.ui.components.HabitCard
import com.atomichabits.tracker.util.TIME_OF_DAY_VALUES
import com.atomichabits.tracker.util.declineDays
import com.atomichabits.tracker.util.isHabitScheduledOn
import com.atomichabits.tracker.util.timeOfDayLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val FILTERS = listOf("ALL", "MORNING", "DAY", "EVENING", "ALL_DAY")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: HabitTrackerApp,
    onAddHabit: () -> Unit,
    onOpenHabit: (Long) -> Unit,
    onOpenImpulse: (String) -> Unit
) {
    val habits by app.repository.observeActiveHabits().collectAsState(initial = emptyList())
    // "Сделал" (green) is for habits you're actively building (USEFUL/NEUTRAL/
    // DESIRED) - HARMFUL ones live in the "Устоял" (red) section below instead,
    // since checking off "did the bad thing" isn't a meaningful daily checkbox.
    val trackedHabits = remember(habits) { habits.filter { it.isTracked && it.qualityType != "HARMFUL" } }
    val trackedHarmful = remember(habits) { habits.filter { it.isTracked && it.qualityType == "HARMFUL" } }
    val impulseLogs by app.impulseRepository.observeAll().collectAsState(initial = emptyList())
    val today = remember { LocalDate.now() }
    // Full current week Monday..Sunday, so "today" sits wherever its weekday
    // falls rather than always being the last/rightmost item.
    val weekMonday = remember(today) { today.minusDays((today.dayOfWeek.value - 1).toLong()) }
    val weekSunday = remember(weekMonday) { weekMonday.plusDays(6) }
    val days = remember(weekMonday) { (0..6).map { weekMonday.plusDays(it.toLong()) } }

    val windowLogs by app.repository.observeLogsBetween(weekMonday, weekSunday).collectAsState(initial = emptyList())

    var selectedDate by remember { mutableStateOf(today) }
    var filter by remember { mutableStateOf("ALL") }

    val progressByDate = remember(trackedHabits, windowLogs) {
        days.associateWith { date ->
            val scheduledIds = trackedHabits.filter { isHabitScheduledOn(it.activeDays, date) }.map { it.id }.toSet()
            val completedCount = windowLogs.count {
                it.dateEpochDay == date.toEpochDay() && it.completed && it.habitId in scheduledIds
            }
            completedCount to scheduledIds.size
        }
    }

    val habitsForSelectedDate = remember(trackedHabits, selectedDate) {
        trackedHabits.filter { isHabitScheduledOn(it.activeDays, selectedDate) }
    }
    val completedIdsForSelectedDate = remember(windowLogs, selectedDate) {
        windowLogs.filter { it.dateEpochDay == selectedDate.toEpochDay() && it.completed }
            .map { it.habitId }.toSet()
    }

    val timeGroups = TIME_OF_DAY_VALUES.map { tod ->
        DragGroup(tod, timeOfDayLabel(tod), habitsForSelectedDate.filter { it.timeOfDay == tod })
    }
    val visibleTimeGroups = timeGroups.filter { filter == "ALL" || filter == it.key }

    // "Perfect days" so far this week (Monday..today, not the whole displayed
    // week - future days can't be "perfect" yet), for the weekly counter card.
    val weekDaysElapsed = remember(today, weekMonday) {
        (0..ChronoUnit.DAYS.between(weekMonday, today)).map { weekMonday.plusDays(it) }
    }
    val perfectDaysThisWeek = remember(trackedHabits, windowLogs, weekDaysElapsed) {
        weekDaysElapsed.count { date ->
            val scheduled = trackedHabits.filter { isHabitScheduledOn(it.activeDays, date) }.map { it.id }.toSet()
            if (scheduled.isEmpty()) return@count false
            val completed = windowLogs.filter { it.dateEpochDay == date.toEpochDay() && it.completed }.map { it.habitId }.toSet()
            scheduled.all { it in completed }
        }
    }

    fun habitToggle(habit: Habit): () -> Unit = {
        app.launchPersistent { app.repository.toggleCompletion(habit.id, selectedDate) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(headerTitle(selectedDate, today)) },
                actions = {
                    IconButton(onClick = onAddHabit) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                }
            )
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

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FILTERS.forEach { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(filterLabel(f)) }
                        )
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.home_section_done),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item {
                CrossGroupDraggableSections(
                    groups = visibleTimeGroups,
                    itemKey = { it.id },
                    onMove = { habit, _, toGroupKey ->
                        app.launchPersistent { app.repository.saveHabit(habit.copy(timeOfDay = toGroupKey)) }
                    },
                    onReorder = { _, orderedItems ->
                        app.launchPersistent { app.repository.reorder(orderedItems.map { it.id }) }
                    },
                    emptyGroupHint = stringResource(R.string.home_group_empty_hint)
                ) { habit, isDragging ->
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

            val visibleCount = habitsForSelectedDate.size
            if (visibleCount == 0) {
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

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        stringResource(R.string.home_week_progress, perfectDaysThisWeek, weekDaysElapsed.size),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            if (selectedDate == today && trackedHarmful.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.home_section_held),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                    )
                }
                items(trackedHarmful, key = { "held_${it.id}" }) { habit ->
                    val daysWithout = remember(habit, impulseLogs) {
                        computeDaysWithout(habit.syncId, habit.createdAtEpochDay, impulseLogs)
                    }
                    val hadSlipToday = remember(habit, impulseLogs, today) {
                        impulseLogs.any {
                            it.linkedHarmfulAnchorId == habit.syncId &&
                                it.outcome == "CROSS" &&
                                it.dateEpochDay == today.toEpochDay()
                        }
                    }
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        HeldTodayCard(
                            habit = habit,
                            hadSlipToday = hadSlipToday,
                            daysWithout = daysWithout.currentDays,
                            onClick = { onOpenImpulse(habit.syncId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeldTodayCard(habit: Habit, hadSlipToday: Boolean, daysWithout: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("${habit.emoji} ${habit.name}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (hadSlipToday) stringResource(R.string.home_held_slip_today)
                    else stringResource(R.string.home_held_clean_today),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            if (daysWithout > 0) {
                Text(
                    "\uD83D\uDEE1 $daysWithout ${declineDays(daysWithout)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

private fun filterLabel(f: String): String = when (f) {
    "MORNING" -> "Утром"
    "DAY" -> "Днём"
    "EVENING" -> "Вечером"
    "ALL_DAY" -> "Весь день"
    else -> "Все"
}

private fun headerTitle(selectedDate: LocalDate, today: LocalDate): String {
    if (selectedDate == today) return "Сегодня"
    if (selectedDate == today.minusDays(1)) return "Вчера"
    val formatter = DateTimeFormatter.ofPattern("d MMMM")
    val monthGenitive = selectedDate.format(formatter)
    val weekday = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ru"))
    return "$monthGenitive, $weekday"
}

package com.atomichabits.tracker.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.data.Habit
import com.atomichabits.tracker.data.HabitLog
import com.atomichabits.tracker.data.ImpulseLog
import com.atomichabits.tracker.util.isHabitScheduledOn
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(app: HabitTrackerApp, onOpenHabit: (Long) -> Unit) {
    val habits by app.repository.observeActiveHabits().collectAsState(initial = emptyList())
    val trackedHabits = remember(habits) { habits.filter { it.isTracked } }
    val allLogs by app.repository.observeAllLogs().collectAsState(initial = emptyList())
    val impulseLogs by app.impulseRepository.observeAll().collectAsState(initial = emptyList())
    val today = remember { LocalDate.now() }

    var tabIndex by remember { mutableIntStateOf(0) }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(today)) }

    val stats = remember(trackedHabits, allLogs) { computeHistoryStats(trackedHabits, allLogs, today) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(stringResource(R.string.history_title)) })
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text(stringResource(R.string.history_tab_calendar)) })
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text(stringResource(R.string.history_tab_habits)) })
                    Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text(stringResource(R.string.history_tab_achievements)) })
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tabIndex) {
                0 -> CalendarTab(
                    habits = trackedHabits,
                    allLogs = allLogs,
                    impulseLogs = impulseLogs,
                    stats = stats,
                    today = today,
                    visibleMonth = visibleMonth,
                    onMonthChange = { visibleMonth = it }
                )
                1 -> HabitsStatsTab(trackedHabits, allLogs)
                else -> AchievementsTab(habits, impulseLogs, stats)
            }
        }
    }
}

// region ---- stats computation ----

private data class HistoryStats(
    val perfectDaysAllTime: Int,
    val perfectDaysThisWeek: Int,
    val completionPercent: Int,
    val totalScheduled: Int,
    val totalCompleted: Int,
    val bestCurrentStreak: Int,
    val bestStreakEver: Int,
    val totalCompletionsEver: Int,
    val completionsThisWeek: Int
)

private fun computeHistoryStats(habits: List<Habit>, allLogs: List<HabitLog>, today: LocalDate): HistoryStats {
    val completedByDate = allLogs.filter { it.completed }.groupBy { it.dateEpochDay }
    val earliestFromHabits = habits.minOfOrNull { it.createdAtEpochDay }
    val earliestFromLogs = allLogs.minOfOrNull { it.dateEpochDay }
    val earliestEpoch = listOfNotNull(earliestFromHabits, earliestFromLogs).minOrNull() ?: today.toEpochDay()
    val earliestDate = LocalDate.ofEpochDay(earliestEpoch)
    val allDates = if (earliestDate.isAfter(today)) emptyList() else
        (0..ChronoUnit.DAYS.between(earliestDate, today)).map { earliestDate.plusDays(it) }

    var totalScheduled = 0
    var totalCompleted = 0
    var perfectDaysAllTime = 0
    val weekMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    var perfectDaysThisWeek = 0

    for (date in allDates) {
        val scheduledHabits = habits.filter { isHabitScheduledOn(it.activeDays, date) && it.createdAtEpochDay <= date.toEpochDay() }
        if (scheduledHabits.isEmpty()) continue
        val completedIds = completedByDate[date.toEpochDay()]?.map { it.habitId }?.toSet() ?: emptySet()
        val completedCount = scheduledHabits.count { it.id in completedIds }
        totalScheduled += scheduledHabits.size
        totalCompleted += completedCount
        val isPerfect = completedCount >= scheduledHabits.size
        if (isPerfect) perfectDaysAllTime++
        if (isPerfect && !date.isBefore(weekMonday) && !date.isAfter(today)) perfectDaysThisWeek++
    }

    var bestCurrentStreak = 0
    var bestStreakEver = 0
    val logsByHabit = allLogs.filter { it.completed }.groupBy { it.habitId }
    for (habit in habits) {
        val completedDays = logsByHabit[habit.id]?.map { it.dateEpochDay }?.toSet() ?: emptySet()
        var current = 0
        var cursor = today
        var guard = 0
        while (guard < 3660) {
            guard++
            if (!isHabitScheduledOn(habit.activeDays, cursor)) {
                cursor = cursor.minusDays(1)
                continue
            }
            if (completedDays.contains(cursor.toEpochDay())) {
                current++
                cursor = cursor.minusDays(1)
            } else break
        }
        if (current > bestCurrentStreak) bestCurrentStreak = current

        var best = 0
        var running = 0
        val sorted = completedDays.sorted()
        for (i in sorted.indices) {
            running = if (i == 0 || sorted[i] == sorted[i - 1] + 1) running + 1 else 1
            if (running > best) best = running
        }
        if (best > bestStreakEver) bestStreakEver = best
    }

    val totalCompletionsEver = allLogs.count { it.completed }
    val completionsThisWeek = allLogs.count {
        it.completed && it.dateEpochDay >= weekMonday.toEpochDay() && it.dateEpochDay <= today.toEpochDay()
    }
    val completionPercent = if (totalScheduled == 0) 0 else (totalCompleted * 100 / totalScheduled)

    return HistoryStats(
        perfectDaysAllTime = perfectDaysAllTime,
        perfectDaysThisWeek = perfectDaysThisWeek,
        completionPercent = completionPercent,
        totalScheduled = totalScheduled,
        totalCompleted = totalCompleted,
        bestCurrentStreak = bestCurrentStreak,
        bestStreakEver = bestStreakEver,
        totalCompletionsEver = totalCompletionsEver,
        completionsThisWeek = completionsThisWeek
    )
}

// endregion

// region ---- Calendar tab ----

@Composable
private fun CalendarTab(
    habits: List<Habit>,
    allLogs: List<HabitLog>,
    impulseLogs: List<ImpulseLog>,
    stats: HistoryStats,
    today: LocalDate,
    visibleMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                item {
                    StatCard(
                        title = stringResource(R.string.history_stat_streak),
                        value = stats.bestCurrentStreak.toString(),
                        subtitle = stringResource(R.string.history_stat_best_streak, stats.bestStreakEver),
                        color = Color(0xFF3AA6D9)
                    )
                }
                item {
                    StatCard(
                        title = stringResource(R.string.history_stat_total_completions),
                        value = stats.totalCompletionsEver.toString(),
                        subtitle = stringResource(R.string.history_stat_this_week, stats.completionsThisWeek),
                        color = Color(0xFFEF6461)
                    )
                }
                item {
                    StatCard(
                        title = stringResource(R.string.history_stat_completion_rate),
                        value = "${stats.completionPercent}%",
                        subtitle = stringResource(R.string.history_stat_habits_fraction, stats.totalCompleted, stats.totalScheduled),
                        color = Color(0xFFF2A93B)
                    )
                }
                item {
                    StatCard(
                        title = stringResource(R.string.history_stat_perfect_days),
                        value = stats.perfectDaysAllTime.toString(),
                        subtitle = stringResource(R.string.history_stat_this_week, stats.perfectDaysThisWeek),
                        color = Color(0xFF3DBE8B)
                    )
                }
            }
        }

        item {
            MonthCalendar(
                allLogs = allLogs,
                today = today,
                visibleMonth = visibleMonth,
                onMonthChange = onMonthChange
            )
            Spacer(Modifier.size(20.dp))
        }

        item {
            ImpulseTrendChart(logs = impulseLogs, today = today)
            Spacer(Modifier.size(20.dp))
        }

        item {
            Text(stringResource(R.string.history_statistics_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(10.dp))
        }

        items(weeklyBuckets(habits, allLogs, today)) { week ->
            WeekStatCard(week)
            Spacer(Modifier.size(10.dp))
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, subtitle: String, color: Color) {
    Card(
        modifier = Modifier.width(150.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(Modifier.size(6.dp))
            Text(value, style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(4.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun MonthCalendar(
    allLogs: List<HabitLog>,
    today: LocalDate,
    visibleMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit
) {
    val completedByDate = remember(allLogs) { allLogs.filter { it.completed }.groupBy { it.dateEpochDay } }
    val firstOfMonth = visibleMonth.atDay(1)
    val leadingBlanks = firstOfMonth.dayOfWeek.value - 1 // Monday = 0
    val daysInMonth = visibleMonth.lengthOfMonth()
    val monthLabel = "${monthGenitive(visibleMonth.monthValue)} ${visibleMonth.year}"

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onMonthChange(visibleMonth.minusMonths(1)) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = null)
                }
                Text(monthLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onMonthChange(visibleMonth.plusMonths(1)) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС").forEach {
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
                modifier = Modifier.height((((leadingBlanks + daysInMonth + 6) / 7) * 42).dp)
            ) {
                items(leadingBlanks) { Box(Modifier.size(32.dp)) }
                items(daysInMonth) { dayIndex ->
                    val date = visibleMonth.atDay(dayIndex + 1)
                    val isToday = date == today
                    val completedCount = completedByDate[date.toEpochDay()]?.size ?: 0
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    when {
                                        isToday -> MaterialTheme.colorScheme.primary
                                        completedCount > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else -> Color.Transparent
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (dayIndex + 1).toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region ---- Impulse ("Позыв") trend chart ----

/**
 * Stacked daily bar chart of the last [days] days of "Позыв" activity: the
 * green portion is CHECK (urge held), stacked with a red portion for CROSS
 * (gave in) on top - both counted per day, same drawing approach as
 * [com.atomichabits.tracker.ui.components.CompletionBarChart]. Days with no
 * logged urges at all show a small neutral baseline mark rather than nothing,
 * so an empty day still reads as a data point, not a rendering gap.
 */
@Composable
private fun ImpulseTrendChart(logs: List<ImpulseLog>, today: LocalDate, days: Int = 30) {
    val checkColor = MaterialTheme.colorScheme.primary
    val crossColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    val since = today.minusDays((days - 1).toLong())
    val byDate = remember(logs) { logs.groupBy { it.dateEpochDay } }
    val totalChecks = remember(logs) { logs.count { it.outcome == "CHECK" } }
    val totalCrosses = remember(logs) { logs.count { it.outcome == "CROSS" } }
    val total = totalChecks + totalCrosses
    val successPercent = if (total == 0) 0 else (totalChecks * 100 / total)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.history_impulse_chart_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (total > 0) {
                    Text("$successPercent%", style = MaterialTheme.typography.titleMedium, color = checkColor)
                }
            }
            Text(
                if (total == 0) {
                    stringResource(R.string.history_impulse_chart_empty)
                } else {
                    stringResource(R.string.history_impulse_chart_subtitle, totalChecks, totalCrosses)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            if (total > 0) {
                Spacer(Modifier.size(12.dp))
                val maxPerDay = remember(byDate, since, days) {
                    (0 until days).maxOf { offset ->
                        byDate[since.plusDays(offset.toLong()).toEpochDay()]?.size ?: 0
                    }.coerceAtLeast(1)
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    val gap = 3.dp.toPx()
                    val barWidth = (size.width - gap * (days - 1)) / days
                    val cornerRadius = CornerRadius(barWidth / 2.5f, barWidth / 2.5f)
                    for (i in 0 until days) {
                        val date = since.plusDays(i.toLong())
                        val dayLogs = byDate[date.toEpochDay()].orEmpty()
                        val checks = dayLogs.count { it.outcome == "CHECK" }
                        val crosses = dayLogs.count { it.outcome == "CROSS" }
                        val x = i * (barWidth + gap)
                        if (checks == 0 && crosses == 0) {
                            drawRoundRect(
                                color = trackColor,
                                topLeft = Offset(x, size.height - size.height * 0.06f),
                                size = Size(barWidth, size.height * 0.06f),
                                cornerRadius = cornerRadius
                            )
                        } else {
                            val checkHeight = size.height * (checks.toFloat() / maxPerDay)
                            val crossHeight = size.height * (crosses.toFloat() / maxPerDay)
                            if (checkHeight > 0) {
                                drawRoundRect(
                                    color = checkColor,
                                    topLeft = Offset(x, size.height - checkHeight),
                                    size = Size(barWidth, checkHeight),
                                    cornerRadius = cornerRadius
                                )
                            }
                            if (crossHeight > 0) {
                                drawRoundRect(
                                    color = crossColor,
                                    topLeft = Offset(x, size.height - checkHeight - crossHeight),
                                    size = Size(barWidth, crossHeight),
                                    cornerRadius = cornerRadius
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.size(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ImpulseLegendDot(checkColor, stringResource(R.string.history_impulse_legend_check))
                    ImpulseLegendDot(crossColor, stringResource(R.string.history_impulse_legend_cross))
                }
            }
        }
    }
}

@Composable
private fun ImpulseLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

// endregion

// region ---- Weekly stats ----

private data class WeekBucket(val start: LocalDate, val end: LocalDate, val percent: Int, val dailyPercent: List<Int>)

private fun weeklyBuckets(habits: List<Habit>, allLogs: List<HabitLog>, today: LocalDate, maxWeeks: Int = 12): List<WeekBucket> {
    val completedByDate = allLogs.filter { it.completed }.groupBy { it.dateEpochDay }
    val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val result = mutableListOf<WeekBucket>()
    for (w in 0 until maxWeeks) {
        val start = thisMonday.minusWeeks(w.toLong())
        val end = start.plusDays(6)
        val dailyPercent = (0..6).map { offset ->
            val date = start.plusDays(offset.toLong())
            if (date.isAfter(today)) return@map -1
            val scheduled = habits.count { isHabitScheduledOn(it.activeDays, date) && it.createdAtEpochDay <= date.toEpochDay() }
            if (scheduled == 0) return@map -1
            val completed = (completedByDate[date.toEpochDay()]?.size ?: 0).coerceAtMost(scheduled)
            (completed * 100 / scheduled)
        }
        val validDays = dailyPercent.filter { it >= 0 }
        if (validDays.isEmpty()) continue
        val avgPercent = validDays.sum() / validDays.size
        result.add(WeekBucket(start, end, avgPercent, dailyPercent))
    }
    return result
}

@Composable
private fun WeekStatCard(week: WeekBucket) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${week.start.dayOfMonth} ${monthShort(week.start.monthValue)} - ${week.end.dayOfMonth} ${monthShort(week.end.monthValue)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("${week.percent}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                stringResource(R.string.history_avg_completion),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                listOf("П", "В", "С", "Ч", "П", "С", "В").forEachIndexed { i, label ->
                    val pct = week.dailyPercent[i]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height((if (pct <= 0) 8.0 else 8.0 + pct * 0.5).dp)
                                .background(
                                    if (pct > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

// endregion

// region ---- Habits tab ----

@Composable
private fun HabitsStatsTab(habits: List<Habit>, allLogs: List<HabitLog>) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(habits, key = { it.id }) { habit ->
            val logs = allLogs.filter { it.habitId == habit.id && it.completed }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("${habit.emoji} ${habit.name}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.history_habit_total, logs.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
        if (habits.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.home_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// endregion

// region ---- Achievements tab ----

private data class Achievement(val title: String, val description: String, val icon: ImageVector, val unlocked: Boolean)

@Composable
private fun AchievementsTab(
    habits: List<Habit>,
    impulseLogs: List<ImpulseLog>,
    stats: HistoryStats
) {
    val impulseChecks = impulseLogs.count { it.outcome == "CHECK" }
    val libraryEntries = habits.count { !it.isTracked }
    val achievements = remember(stats, impulseChecks, libraryEntries, habits) {
        listOf(
            Achievement("Первый шаг", "Выполните первую привычку", Icons.Filled.EmojiEvents, stats.totalCompletionsEver >= 1),
            Achievement("Неделя силы", "Серия из 7 дней по одной привычке", Icons.Filled.EmojiEvents, stats.bestStreakEver >= 7),
            Achievement("Месяц дисциплины", "Серия из 30 дней по одной привычке", Icons.Filled.EmojiEvents, stats.bestStreakEver >= 30),
            Achievement("100 побед", "100 отметок о выполнении всего", Icons.Filled.EmojiEvents, stats.totalCompletionsEver >= 100),
            Achievement("500 побед", "500 отметок о выполнении всего", Icons.Filled.EmojiEvents, stats.totalCompletionsEver >= 500),
            Achievement("Идеальная неделя", "Все привычки выполнены все 7 дней подряд", Icons.Filled.EmojiEvents, stats.perfectDaysThisWeek >= 7),
            Achievement("Мастер позыва", "10 удержанных позывов", Icons.Filled.EmojiEvents, impulseChecks >= 10),
            Achievement("Библиотекарь", "5 привычек в библиотеке опор", Icons.Filled.EmojiEvents, libraryEntries >= 5)
        )
    }

    LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp)) {
        items(achievements) { achievement ->
            Card(
                modifier = Modifier
                    .padding(6.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (achievement.unlocked) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        if (achievement.unlocked) achievement.icon else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (achievement.unlocked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        achievement.title,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        color = if (achievement.unlocked) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        achievement.description,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// endregion

private fun monthGenitive(month: Int): String = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря"
)[month - 1]

private fun monthShort(month: Int): String = listOf(
    "янв.", "февр.", "мар.", "апр.", "мая", "июн.",
    "июл.", "авг.", "сент.", "окт.", "нояб.", "дек."
)[month - 1]

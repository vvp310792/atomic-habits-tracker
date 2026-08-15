package com.atomichabits.tracker.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.data.Habit
import com.atomichabits.tracker.data.HabitStats
import com.atomichabits.tracker.ui.components.CompletionBarChart
import com.atomichabits.tracker.ui.components.HabitHeatmap
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    app: HabitTrackerApp,
    habitId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    var habit by remember { mutableStateOf<Habit?>(null) }
    var stats by remember { mutableStateOf(HabitStats(0, 0, 0, 0, 0, false)) }
    var completedDates by remember { mutableStateOf(setOf<LocalDate>()) }
    var barValues by remember { mutableStateOf(List(30) { 0 }) }
    var barDaySpan by remember { mutableStateOf(30) }

    LaunchedEffect(habitId) {
        val h = app.database.habitDao().getHabit(habitId) ?: return@LaunchedEffect
        habit = h
        stats = app.repository.computeStats(h)

        val logs = app.database.habitLogDao().getAllForHabitOnce(habitId)
        val doneDates = logs.filter { it.completed }.map { LocalDate.ofEpochDay(it.dateEpochDay) }.toSet()
        completedDates = doneDates

        val today = LocalDate.now()
        val windowStart = today.minusDays(29)
        // For a habit younger than 30 days, starting the window at a fixed
        // "today - 29" pads the chart with days from before the habit even
        // existed - real data ends up compressed into just the last few slots
        // instead of spanning the chart. Clamp the start to the habit's own
        // creation date when that's more recent, so the chart always reads as
        // "the whole life of this habit so far", growing wider over time
        // instead of looking stuck at the right edge.
        val createdDate = if (h.createdAtEpochDay > 0) LocalDate.ofEpochDay(h.createdAtEpochDay) else windowStart
        val since = if (createdDate.isAfter(windowStart)) createdDate else windowStart
        val span = (ChronoUnit.DAYS.between(since, today).toInt() + 1).coerceIn(1, 30)
        barDaySpan = span
        barValues = (0 until span).map { offset ->
            if (doneDates.contains(since.plusDays(offset.toLong()))) 1 else 0
        }
    }

    val h = habit ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(h.emoji + "  " + h.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    label = stringResource(R.string.detail_current_streak),
                    value = "${stats.currentStreak}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.detail_best_streak),
                    value = "${stats.bestStreak}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.detail_completion_rate),
                    value = "${stats.completionRatePercent}%",
                    modifier = Modifier.weight(1f)
                )
            }

            MasteryProgressSection(stats)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.detail_heatmap_title), style = MaterialTheme.typography.titleMedium)
                HabitHeatmap(completedDates = completedDates)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    pluralDaysTitle(barDaySpan),
                    style = MaterialTheme.typography.titleMedium
                )
                CompletionBarChart(values = barValues)
            }

            if (h.lawObvious.isNotBlank() || h.lawAttractive.isNotBlank() ||
                h.lawEasy.isNotBlank() || h.lawSatisfying.isNotBlank()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.laws_title), style = MaterialTheme.typography.titleMedium)
                    LawSummaryRow(stringResource(R.string.law_obvious_title), h.lawObvious)
                    LawSummaryRow(stringResource(R.string.law_attractive_title), h.lawAttractive)
                    LawSummaryRow(stringResource(R.string.law_easy_title), h.lawEasy)
                    LawSummaryRow(stringResource(R.string.law_satisfying_title), h.lawSatisfying)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Progress toward "mastery" (habit-formation automaticity, per Lally et al. 2010:
 * what predicts automaticity is the overall completion RATE over a long window,
 * not an unbroken streak - see [HabitStats.masteryProgressPercent]). Below the
 * 80%-over-90-days threshold this reads as a plain progress bar toward the goal;
 * once mastered it flips to a settled "освоено" state instead of a bar that
 * would otherwise look permanently maxed-out.
 */
@Composable
private fun MasteryProgressSection(stats: HabitStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.detail_mastery_title), style = MaterialTheme.typography.titleMedium)
            Text(
                if (stats.isMastered) {
                    stringResource(R.string.detail_mastery_done)
                } else {
                    stringResource(R.string.detail_mastery_percent, stats.masteryProgressPercent)
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (stats.isMastered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        LinearProgressIndicator(
            progress = { (stats.masteryProgressPercent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            if (stats.masteryScheduledDays < 14) {
                stringResource(R.string.detail_mastery_hint_not_enough_data)
            } else {
                stringResource(R.string.detail_mastery_hint, stats.masteryScheduledDays)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun LawSummaryRow(title: String, value: String) {
    if (value.isBlank()) return
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * "Последние 30 дней" once the habit has enough history to fill that window,
 * otherwise a correctly-declined "Последние N дней/дня/день" matching however
 * long the habit has actually existed - see [HabitDetailScreen]'s [barDaySpan].
 */
@Composable
private fun pluralDaysTitle(span: Int): String {
    if (span >= 30) return stringResource(R.string.detail_chart_title)
    val word = when {
        span % 100 in 11..14 -> "дней"
        span % 10 == 1 -> "день"
        span % 10 in 2..4 -> "дня"
        else -> "дней"
    }
    return "Последние $span $word"
}

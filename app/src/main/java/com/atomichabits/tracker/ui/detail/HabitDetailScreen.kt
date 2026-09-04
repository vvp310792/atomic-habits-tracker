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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.atomichabits.tracker.data.MASTERY_MIN_DISPLAY_DAYS
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
        // The bar chart's window: grows with the habit's actual age (so a
        // 5-day-old habit doesn't get padded with 25 days of "before it
        // existed" empty bars crammed at the right edge), but never shrinks
        // below a full week - a 2-3 day window reads as broken/empty rather
        // than as "a young habit doing well", and a week is the smallest span
        // that's actually legible as a trend. Capped at 30 for older habits.
        val createdDate = if (h.createdAtEpochDay > 0) LocalDate.ofEpochDay(h.createdAtEpochDay) else today.minusDays(29)
        val naturalSpan = (ChronoUnit.DAYS.between(createdDate, today).toInt() + 1)
        val span = naturalSpan.coerceIn(7, 30)
        val since = today.minusDays((span - 1).toLong())
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
            if (h.temptationBundle.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        "\uD83D\uDD12 " + h.temptationBundle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

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

            MasteryProgressSection(stats, h.manuallyMastered)

            if (stats.isMastered) {
                GoldilocksSection(
                    difficultyNote = h.difficultyNote,
                    onBump = { newNote ->
                        app.launchPersistent {
                            val updated = h.copy(
                                difficultyNote = newNote,
                                difficultyBumpedAtEpochDay = LocalDate.now().toEpochDay()
                            )
                            app.repository.saveHabit(updated)
                            val fresh = app.database.habitDao().getHabit(updated.id)
                            if (fresh != null) {
                                habit = fresh
                                stats = app.repository.computeStats(fresh)
                            }
                        }
                    }
                )
            }

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
private fun MasteryProgressSection(stats: HabitStats, manuallyMastered: Boolean) {
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
            when {
                manuallyMastered -> stringResource(R.string.detail_mastery_hint_manual)
                stats.masteryScheduledDays < MASTERY_MIN_DISPLAY_DAYS -> stringResource(R.string.detail_mastery_hint_not_enough_data)
                else -> stringResource(R.string.detail_mastery_hint, stats.masteryScheduledDays)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * The Goldilocks Rule (motivation peaks when a task is a bit past your current
 * skill level, neither trivial nor overwhelming): shown once a habit is
 * actually mastered (see [HabitStats.isMastered]) as a nudge to describe and
 * bump its difficulty, rather than letting an easy habit stay easy forever.
 * Bumping restarts the mastery clock for the new, harder version - see
 * [Habit.difficultyBumpedAtEpochDay].
 */
@Composable
private fun GoldilocksSection(difficultyNote: String, onBump: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var input by remember(difficultyNote) { mutableStateOf(difficultyNote) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.goldilocks_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                stringResource(R.string.goldilocks_mastered_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
            )
            Text(
                if (difficultyNote.isNotBlank()) {
                    stringResource(R.string.goldilocks_current_level, difficultyNote)
                } else {
                    stringResource(R.string.goldilocks_no_level)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            OutlinedButton(onClick = { showDialog = true }) {
                Text(stringResource(R.string.goldilocks_bump_button))
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBump(input.trim())
                        showDialog = false
                    },
                    enabled = input.isNotBlank()
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.goldilocks_dialog_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(stringResource(R.string.goldilocks_dialog_hint)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.goldilocks_bumped_hint, java.time.LocalDate.now().toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
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

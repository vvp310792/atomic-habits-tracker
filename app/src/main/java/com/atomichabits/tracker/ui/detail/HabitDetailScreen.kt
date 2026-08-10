package com.atomichabits.tracker.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.data.Habit
import com.atomichabits.tracker.data.HabitStats
import com.atomichabits.tracker.ui.components.CompletionBarChart
import com.atomichabits.tracker.ui.components.HabitHeatmap
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    app: HabitTrackerApp,
    habitId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    var habit by remember { mutableStateOf<Habit?>(null) }
    var stats by remember { mutableStateOf(HabitStats(0, 0, 0)) }
    var completedDates by remember { mutableStateOf(setOf<LocalDate>()) }
    var last30 by remember { mutableStateOf(List(30) { 0 }) }

    LaunchedEffect(habitId) {
        val h = app.database.habitDao().getHabit(habitId) ?: return@LaunchedEffect
        habit = h
        stats = app.repository.computeStats(h)

        val logs = app.database.habitLogDao().getAllForHabitOnce(habitId)
        val doneDates = logs.filter { it.completed }.map { LocalDate.ofEpochDay(it.dateEpochDay) }.toSet()
        completedDates = doneDates

        val since = LocalDate.now().minusDays(29)
        last30 = (0..29).map { offset ->
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.detail_heatmap_title), style = MaterialTheme.typography.titleMedium)
                HabitHeatmap(completedDates = completedDates)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.detail_chart_title), style = MaterialTheme.typography.titleMedium)
                CompletionBarChart(values = last30)
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

@Composable
private fun LawSummaryRow(title: String, value: String) {
    if (value.isBlank()) return
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

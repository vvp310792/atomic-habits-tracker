package com.atomichabits.tracker.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.data.Habit
import com.atomichabits.tracker.ui.components.CategoryTag
import com.atomichabits.tracker.ui.components.CrossGroupDraggableSections
import com.atomichabits.tracker.ui.components.DragGroup
import com.atomichabits.tracker.util.TIME_OF_DAY_VALUES
import com.atomichabits.tracker.util.timeOfDayLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsListScreen(
    app: HabitTrackerApp,
    onOpenHabit: (Long) -> Unit,
    onEditUntracked: (Long) -> Unit,
    onAddHabit: (initialQualityType: String, initialTracked: Boolean) -> Unit
) {
    val habits by app.repository.observeActiveHabits().collectAsState(initial = emptyList())
    val impulseLogs by app.impulseRepository.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val greenHabits = habits.filter { it.qualityType == "USEFUL" }
    val yellowHabits = habits.filter { it.qualityType == "NEUTRAL" || it.qualityType == "DESIRED" }
    val redHabits = habits.filter { it.qualityType == "HARMFUL" }

    val impulseScoreByHabit = remember(impulseLogs) {
        impulseLogs
            .filter { it.linkedHarmfulAnchorId.isNotBlank() }
            .groupBy { it.linkedHarmfulAnchorId }
            .mapValues { (_, logs) ->
                logs.count { it.outcome == "CHECK" } to logs.count { it.outcome == "CROSS" }
            }
    }

    fun rowClick(habit: Habit) {
        if (habit.isTracked) onOpenHabit(habit.id) else onEditUntracked(habit.id)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.habits_title)) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                ColoredQualitySection(
                    title = stringResource(R.string.habits_section_green),
                    tint = Color(0xFF3DBE8B),
                    habits = greenHabits,
                    impulseScoreByHabit = impulseScoreByHabit,
                    onAdd = { onAddHabit("USEFUL", true) },
                    onMove = { habit, toTime -> scope.launch { app.repository.saveHabit(habit.copy(timeOfDay = toTime)) } },
                    onReorder = { orderedItems -> scope.launch { app.repository.reorder(orderedItems.map { it.id }) } },
                    onClick = ::rowClick
                )
                Spacer(Modifier.size(20.dp))
            }
            item {
                ColoredQualitySection(
                    title = stringResource(R.string.habits_section_yellow),
                    tint = Color(0xFFF2A93B),
                    habits = yellowHabits,
                    impulseScoreByHabit = impulseScoreByHabit,
                    onAdd = { onAddHabit("DESIRED", false) },
                    onMove = { habit, toTime -> scope.launch { app.repository.saveHabit(habit.copy(timeOfDay = toTime)) } },
                    onReorder = { orderedItems -> scope.launch { app.repository.reorder(orderedItems.map { it.id }) } },
                    onClick = ::rowClick
                )
                Spacer(Modifier.size(20.dp))
            }
            item {
                ColoredQualitySection(
                    title = stringResource(R.string.habits_section_red),
                    tint = Color(0xFFEF6461),
                    habits = redHabits,
                    impulseScoreByHabit = impulseScoreByHabit,
                    onAdd = { onAddHabit("HARMFUL", false) },
                    onMove = { habit, toTime -> scope.launch { app.repository.saveHabit(habit.copy(timeOfDay = toTime)) } },
                    onReorder = { orderedItems -> scope.launch { app.repository.reorder(orderedItems.map { it.id }) } },
                    onClick = ::rowClick
                )
            }
        }
    }
}

@Composable
private fun ColoredQualitySection(
    title: String,
    tint: Color,
    habits: List<Habit>,
    impulseScoreByHabit: Map<String, Pair<Int, Int>>,
    onAdd: () -> Unit,
    onMove: (Habit, String) -> Unit,
    onReorder: (List<Habit>) -> Unit,
    onClick: (Habit) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.10f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = tint)
                IconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = tint)
                }
            }
            if (habits.isEmpty()) {
                Text(
                    stringResource(R.string.anchors_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                val timeGroups = TIME_OF_DAY_VALUES.map { tod ->
                    DragGroup(tod, timeOfDayLabel(tod), habits.filter { it.timeOfDay == tod })
                }
                CrossGroupDraggableSections(
                    groups = timeGroups,
                    itemKey = { it.id },
                    onMove = { habit, _, toGroupKey -> onMove(habit, toGroupKey) },
                    onReorder = { _, orderedItems -> onReorder(orderedItems) },
                    emptyGroupHint = stringResource(R.string.home_group_empty_hint)
                ) { habit, _ ->
                    UniversalHabitRow(habit, impulseScoreByHabit[habit.syncId]) { onClick(habit) }
                }
            }
        }
    }
}

@Composable
private fun UniversalHabitRow(habit: Habit, impulseScore: Pair<Int, Int>?, onClick: () -> Unit) {
    val accent = remember(habit.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(habit.colorHex)) }
            .getOrDefault(Color(0xFF7C6CF0))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accent.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(habit.emoji, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    habit.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (!habit.isTracked) {
                    Text(
                        stringResource(R.string.habits_not_tracked_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                if (habit.isTracked) {
                    CategoryTag(value = habit.category)
                }
                if (habit.identityLabel.isNotBlank()) {
                    Text(
                        "\uD83E\uDDE9 " + habit.identityLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (habit.alternativeSuggestion.isNotBlank()) {
                    Text(
                        "\uD83D\uDCA1 " + habit.alternativeSuggestion,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (habit.whyItMatters.isNotBlank()) {
                    Text(
                        "\uD83C\uDFAF " + habit.whyItMatters,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                if (impulseScore != null) {
                    Text(
                        "\u26A1 ${impulseScore.first}:${impulseScore.second}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

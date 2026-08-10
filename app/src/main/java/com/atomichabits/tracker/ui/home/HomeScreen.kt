package com.atomichabits.tracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.sheets.SheetsSyncWorker
import com.atomichabits.tracker.ui.components.HabitCard
import com.atomichabits.tracker.util.isHabitScheduledToday
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: HabitTrackerApp,
    onAddHabit: () -> Unit,
    onOpenHabit: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val habits by app.repository.observeActiveHabits().collectAsState(initial = emptyList())
    val today = rememberToday()
    val todaysLogs by app.repository.observeLogsForDate(today).collectAsState(initial = emptyList())
    val autoSync by app.settingsStore.autoSyncEnabled.collectAsState(initial = false)
    val completedIds = todaysLogs.filter { it.completed }.map { it.habitId }.toSet()
    val scope = rememberCoroutineScope()

    val scheduledHabits = habits.filter { isHabitScheduledToday(it.activeDays) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
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
        if (scheduledHabits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.home_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(scheduledHabits, key = { it.id }) { habit ->
                    val completed = habit.id in completedIds
                    HabitCard(
                        habit = habit,
                        completedToday = completed,
                        repository = app.repository,
                        refreshKey = completed,
                        onToggle = {
                            scope.launch {
                                app.repository.toggleCompletion(habit.id, today)
                                if (autoSync) SheetsSyncWorker.syncNow(app)
                            }
                        },
                        onClick = { onOpenHabit(habit.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberToday(): LocalDate = remember { LocalDate.now() }

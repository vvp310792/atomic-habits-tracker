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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.data.AnchorHabit
import com.atomichabits.tracker.data.Habit
import com.atomichabits.tracker.ui.components.CategoryTag
import com.atomichabits.tracker.util.timeOfDayLabel
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsListScreen(
    app: HabitTrackerApp,
    onAddHabit: () -> Unit,
    onOpenHabit: (Long) -> Unit,
    onStartDesired: (String) -> Unit
) {
    val habits by app.repository.observeActiveHabits().collectAsState(initial = emptyList())
    val anchors by app.anchorRepository.observeActive().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val useful = anchors.filter { it.type == "USEFUL" }
    val desired = anchors.filter { it.type == "DESIRED" }
    val harmful = anchors.filter { it.type == "HARMFUL" }

    var addDialogType by remember { mutableStateOf<String?>(null) } // null = closed, else the type being added

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.habits_title)) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item { SectionTitle(stringResource(R.string.habits_section_tracked), onAdd = onAddHabit) }
            if (habits.isEmpty()) {
                item { EmptyHint(stringResource(R.string.home_empty)) }
            } else {
                items(habits, key = { "h${it.id}" }) { habit ->
                    TrackedHabitRow(habit) { onOpenHabit(habit.id) }
                    Spacer(Modifier.size(8.dp))
                }
            }

            item {
                Spacer(Modifier.size(16.dp))
                SectionTitle(stringResource(R.string.anchors_useful), onAdd = { addDialogType = "USEFUL" })
            }
            if (useful.isEmpty()) {
                item { EmptyHint(stringResource(R.string.anchors_empty)) }
            } else {
                items(useful, key = { "u${it.id}" }) { anchor ->
                    AnchorRow(anchor, onDelete = { scope.launch { app.anchorRepository.archive(anchor.id) } })
                    Spacer(Modifier.size(8.dp))
                }
            }

            item {
                Spacer(Modifier.size(16.dp))
                SectionTitle(stringResource(R.string.anchors_desired), onAdd = { addDialogType = "DESIRED" })
            }
            if (desired.isEmpty()) {
                item { EmptyHint(stringResource(R.string.anchors_empty)) }
            } else {
                items(desired, key = { "d${it.id}" }) { anchor ->
                    AnchorRow(
                        anchor,
                        onDelete = { scope.launch { app.anchorRepository.archive(anchor.id) } },
                        onStart = { onStartDesired(anchor.name) }
                    )
                    Spacer(Modifier.size(8.dp))
                }
            }

            item {
                Spacer(Modifier.size(16.dp))
                SectionTitle(stringResource(R.string.anchors_harmful), onAdd = { addDialogType = "HARMFUL" })
            }
            if (harmful.isEmpty()) {
                item { EmptyHint(stringResource(R.string.anchors_empty)) }
            } else {
                items(harmful, key = { "hm${it.id}" }) { anchor ->
                    AnchorRow(anchor, onDelete = { scope.launch { app.anchorRepository.archive(anchor.id) } })
                    Spacer(Modifier.size(8.dp))
                }
            }
        }
    }

    val dialogType = addDialogType
    if (dialogType != null) {
        AddAnchorDialog(
            onDismiss = { addDialogType = null },
            onSave = { name ->
                scope.launch {
                    app.anchorRepository.save(
                        AnchorHabit(name = name, type = dialogType, createdAtEpochDay = LocalDate.now().toEpochDay())
                    )
                }
                addDialogType = null
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun TrackedHabitRow(habit: Habit, onClick: () -> Unit) {
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
                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                Row {
                    CategoryTag(value = habit.category)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        timeOfDayLabel(habit.timeOfDay),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnchorRow(anchor: AnchorHabit, onDelete: () -> Unit, onStart: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(anchor.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (onStart != null) {
                IconButton(onClick = onStart) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAnchorDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        title = { Text(stringResource(R.string.anchors_add_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.anchors_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

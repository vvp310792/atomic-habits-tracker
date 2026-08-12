package com.atomichabits.tracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.data.Habit

/** A resolved stack target - the syncId of the habit this one is chained after. */
data class StackTarget(val id: String, val type: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StackAnchorPickerDialog(
    candidates: List<Habit>,
    onDismiss: () -> Unit,
    onPick: (StackTarget?) -> Unit
) {
    val tracked = candidates.filter { it.isTracked }
    val useful = candidates.filter { !it.isTracked && it.qualityType == "USEFUL" }
    val harmful = candidates.filter { !it.isTracked && it.qualityType == "HARMFUL" }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        title = { Text(stringResource(R.string.stack_picker_title)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item {
                    Text(
                        stringResource(R.string.stack_picker_none),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(null) }
                            .padding(vertical = 10.dp)
                    )
                }
                if (tracked.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.stack_picker_habits),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(tracked, key = { "t${it.id}" }) { habit ->
                        StackOptionRow("${habit.emoji} ${habit.name}") {
                            onPick(StackTarget(habit.syncId, "HABIT", habit.name))
                        }
                    }
                }
                if (useful.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.anchors_useful),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(useful, key = { "u${it.id}" }) { habit ->
                        StackOptionRow(habit.name) {
                            onPick(StackTarget(habit.syncId, "HABIT", habit.name))
                        }
                    }
                }
                if (harmful.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.anchors_harmful),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(harmful, key = { "hm${it.id}" }) { habit ->
                        StackOptionRow(habit.name) {
                            onPick(StackTarget(habit.syncId, "HABIT", habit.name))
                        }
                    }
                }
                if (candidates.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.stack_picker_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun StackOptionRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    )
}

package com.atomichabits.tracker.ui.anchors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.data.AnchorHabit
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnchorLibraryScreen(app: HabitTrackerApp, onBack: () -> Unit) {
    val anchors by app.anchorRepository.observeActive().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }

    val useful = anchors.filter { it.type == "USEFUL" }
    val harmful = anchors.filter { it.type == "HARMFUL" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.anchors_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.anchors_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (useful.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.anchors_useful), style = MaterialTheme.typography.titleMedium)
                }
                items(useful, key = { "u${it.id}" }) { anchor ->
                    AnchorRow(anchor) { scope.launch { app.anchorRepository.archive(anchor.id) } }
                }
            }
            if (harmful.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.anchors_harmful),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                items(harmful, key = { "h${it.id}" }) { anchor ->
                    AnchorRow(anchor) { scope.launch { app.anchorRepository.archive(anchor.id) } }
                }
            }
            if (anchors.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.anchors_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddAnchorDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, type ->
                scope.launch {
                    app.anchorRepository.save(
                        AnchorHabit(
                            syncId = java.util.UUID.randomUUID().toString(),
                            name = name,
                            type = type,
                            createdAtEpochDay = LocalDate.now().toEpochDay()
                        )
                    )
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AnchorRow(anchor: AnchorHabit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(anchor.name, style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAnchorDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("USEFUL") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), type) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        title = { Text(stringResource(R.string.anchors_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.anchors_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "USEFUL",
                        onClick = { type = "USEFUL" },
                        label = { Text(stringResource(R.string.anchors_useful)) }
                    )
                    FilterChip(
                        selected = type == "HARMFUL",
                        onClick = { type = "HARMFUL" },
                        label = { Text(stringResource(R.string.anchors_harmful)) }
                    )
                }
            }
        }
    )
}

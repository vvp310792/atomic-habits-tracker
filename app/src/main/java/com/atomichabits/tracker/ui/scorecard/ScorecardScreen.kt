package com.atomichabits.tracker.ui.scorecard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class ScoredEntry(val name: String, val type: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScorecardScreen(app: HabitTrackerApp, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("NEUTRAL") }
    val addedThisSession = remember { mutableStateListOf<ScoredEntry>() }

    fun addEntry() {
        if (name.isBlank()) return
        val entryName = name.trim()
        scope.launch {
            app.anchorRepository.save(
                AnchorHabit(name = entryName, type = selectedType, createdAtEpochDay = LocalDate.now().toEpochDay())
            )
        }
        addedThisSession.add(0, ScoredEntry(entryName, selectedType))
        name = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scorecard_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.scorecard_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.scorecard_entry_label)) },
                placeholder = { Text(stringResource(R.string.scorecard_entry_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedType == "USEFUL",
                    onClick = { selectedType = "USEFUL" },
                    label = { Text(stringResource(R.string.scorecard_positive)) }
                )
                FilterChip(
                    selected = selectedType == "NEUTRAL",
                    onClick = { selectedType = "NEUTRAL" },
                    label = { Text(stringResource(R.string.scorecard_neutral)) }
                )
                FilterChip(
                    selected = selectedType == "HARMFUL",
                    onClick = { selectedType = "HARMFUL" },
                    label = { Text(stringResource(R.string.scorecard_negative)) }
                )
            }

            Button(onClick = { addEntry() }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scorecard_add))
            }

            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.scorecard_session_title, addedThisSession.size),
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(addedThisSession) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(scoreColor(entry.type), CircleShape)
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Button(
                onClick = onBack,
                enabled = addedThisSession.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.scorecard_done))
            }
        }
    }
}

private fun scoreColor(type: String): Color = when (type) {
    "USEFUL" -> Color(0xFF3DBE8B)
    "HARMFUL" -> Color(0xFFEF6461)
    else -> Color(0xFFB0B0B0)
}

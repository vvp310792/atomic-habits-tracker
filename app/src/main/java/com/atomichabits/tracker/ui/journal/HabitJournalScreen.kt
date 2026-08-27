package com.atomichabits.tracker.ui.journal

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.data.Habit
import com.atomichabits.tracker.data.HabitJournalEntry
import com.atomichabits.tracker.data.computeJournalDaysWithout
import com.atomichabits.tracker.util.declineDays
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val CYCLE_LENGTH_DAYS = 30
private val ENTRY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM")

/**
 * Daily diary of harmful habits, modelled on Misuzu Nakashima's CBT method
 * (functional analysis of behaviour) - replaces the old check/cross "Позыв"
 * screen. Per habit: a 30-day cycle of daily entries (today's events, how
 * much happened, what was actually wanted, what substitute was tried), not a
 * binary held/gave-in tally - see HabitJournalEntry for the full rationale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitJournalScreen(app: HabitTrackerApp, onBack: (() -> Unit)? = null, initialHabitId: Long? = null) {
    val habits by app.repository.observeActiveHabits().collectAsState(initial = emptyList())
    val harmful = habits.filter { it.qualityType == "HARMFUL" && it.isTracked }

    var selectedHabitId by remember { mutableStateOf(initialHabitId) }
    LaunchedEffect(harmful) {
        if ((selectedHabitId == null || harmful.none { it.id == selectedHabitId }) && harmful.isNotEmpty()) {
            selectedHabitId = harmful.first().id
        }
    }
    val selectedHabit = harmful.find { it.id == selectedHabitId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.journal_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (harmful.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.journal_no_habits),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                harmful.forEach { h ->
                    FilterChip(
                        selected = h.id == selectedHabitId,
                        onClick = { selectedHabitId = h.id },
                        label = { Text("${h.emoji} ${h.name}") }
                    )
                }
            }

            selectedHabit?.let { habit ->
                JournalContent(app, habit)
            }
        }
    }
}

@Composable
private fun JournalContent(app: HabitTrackerApp, habit: Habit) {
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val entries by app.journalRepository.observeForHabit(habit.id).collectAsState(initial = emptyList())

    val daysWithout = remember(habit, entries) {
        computeJournalDaysWithout(habit.syncId, habit.createdAtEpochDay, entries)
    }

    val cycleStart = habit.journalCycleStartEpochDay
    val cycleDayNumber = if (cycleStart > 0) (today.toEpochDay() - cycleStart + 1).toInt() else 0

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (daysWithout.currentDays > 0) {
            item {
                Text(
                    "\uD83D\uDEE1 ${daysWithout.currentDays} ${declineDays(daysWithout.currentDays)} без «${habit.name}»",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (habit.whyItMatters.isNotBlank()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        "\uD83C\uDFAF " + habit.whyItMatters,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        if (habit.alternativeSuggestion.isNotBlank()) {
            item {
                Card {
                    Text(
                        "\uD83D\uDCA1 " + habit.alternativeSuggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        if (habit.selfBindingAction.isNotBlank()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Text(
                        "\uD83D\uDD10 " + habit.selfBindingAction,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        when {
            cycleStart == 0L -> item {
                StartCycleCard {
                    app.launchPersistent {
                        app.repository.saveHabit(habit.copy(journalCycleStartEpochDay = today.toEpochDay()))
                    }
                }
            }
            cycleDayNumber > CYCLE_LENGTH_DAYS -> {
                val cycleEntries = entries.filter { it.cycleStartEpochDay == cycleStart }
                item {
                    CycleSummaryCard(
                        entries = cycleEntries,
                        onRestart = {
                            app.launchPersistent {
                                app.repository.saveHabit(habit.copy(journalCycleStartEpochDay = today.toEpochDay()))
                            }
                        }
                    )
                }
            }
            else -> {
                item {
                    Text(
                        stringResource(R.string.journal_cycle_progress, cycleDayNumber, CYCLE_LENGTH_DAYS),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.size(8.dp))
                    LinearProgressIndicator(
                        progress = { (cycleDayNumber.toFloat() / CYCLE_LENGTH_DAYS).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val todayEntry = entries.find { it.dateEpochDay == today.toEpochDay() }
                item {
                    TodayEntryForm(
                        existing = todayEntry,
                        onSave = { form ->
                            scope.launch {
                                app.journalRepository.save(
                                    (todayEntry ?: HabitJournalEntry()).copy(
                                        habitId = habit.id,
                                        habitSyncId = habit.syncId,
                                        dateEpochDay = today.toEpochDay(),
                                        todaysEvents = form.todaysEvents,
                                        hadIncident = form.hadIncident,
                                        hadSlip = form.hadSlip,
                                        amount = form.amount,
                                        whatIWanted = form.whatIWanted,
                                        substituteBehavior = form.substituteBehavior,
                                        substituteSucceeded = form.substituteSucceeded,
                                        cycleStartEpochDay = cycleStart
                                    )
                                )
                            }
                        }
                    )
                }

                val history = entries
                    .filter { it.cycleStartEpochDay == cycleStart && it.dateEpochDay < today.toEpochDay() }
                    .sortedByDescending { it.dateEpochDay }
                if (history.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.journal_history_title), style = MaterialTheme.typography.titleMedium)
                    }
                    items(history, key = { it.id }) { entry ->
                        JournalEntryHistoryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun StartCycleCard(onStart: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.journal_start_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                stringResource(R.string.journal_start_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
            )
            OutlinedButton(onClick = onStart) {
                Text(stringResource(R.string.journal_start_button))
            }
        }
    }
}

private data class EntryFormState(
    val todaysEvents: String,
    val hadIncident: Boolean,
    val hadSlip: Boolean,
    val amount: String,
    val whatIWanted: String,
    val substituteBehavior: String,
    val substituteSucceeded: Boolean
)

@Composable
private fun TodayEntryForm(existing: HabitJournalEntry?, onSave: (EntryFormState) -> Unit) {
    var todaysEvents by remember(existing) { mutableStateOf(existing?.todaysEvents ?: "") }
    var hadIncident by remember(existing) { mutableStateOf(existing?.hadIncident ?: false) }
    var hadSlip by remember(existing) { mutableStateOf(existing?.hadSlip ?: false) }
    var amount by remember(existing) { mutableStateOf(existing?.amount ?: "") }
    var whatIWanted by remember(existing) { mutableStateOf(existing?.whatIWanted ?: "") }
    var substituteBehavior by remember(existing) { mutableStateOf(existing?.substituteBehavior ?: "") }
    var substituteSucceeded by remember(existing) { mutableStateOf(existing?.substituteSucceeded ?: false) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.journal_today_title), style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = todaysEvents,
                onValueChange = { todaysEvents = it },
                label = { Text(stringResource(R.string.journal_field_events)) },
                placeholder = { Text(stringResource(R.string.journal_field_events_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hadIncident, onCheckedChange = { hadIncident = it })
                Text(stringResource(R.string.journal_field_had_incident), style = MaterialTheme.typography.bodyMedium)
            }

            // The explicit source of truth for "days without" - not whether
            // Amount happens to be blank, which broke the moment someone
            // honestly typed "0" there (see HabitJournalEntry.kt).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = hadSlip,
                    onCheckedChange = {
                        hadSlip = it
                        if (!it) amount = ""
                    }
                )
                Text(stringResource(R.string.journal_field_had_slip), style = MaterialTheme.typography.bodyMedium)
            }

            if (hadSlip) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.journal_field_amount)) },
                    placeholder = { Text(stringResource(R.string.journal_field_amount_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = whatIWanted,
                onValueChange = { whatIWanted = it },
                label = { Text(stringResource(R.string.journal_field_wanted)) },
                placeholder = { Text(stringResource(R.string.journal_field_wanted_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = substituteBehavior,
                onValueChange = { substituteBehavior = it },
                label = { Text(stringResource(R.string.journal_field_substitute)) },
                placeholder = { Text(stringResource(R.string.journal_field_substitute_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = substituteSucceeded, onCheckedChange = { substituteSucceeded = it })
                Text(stringResource(R.string.journal_field_substitute_succeeded), style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedButton(
                onClick = {
                    onSave(
                        EntryFormState(
                            todaysEvents = todaysEvents.trim(),
                            hadIncident = hadIncident,
                            hadSlip = hadSlip,
                            amount = if (hadSlip) amount.trim() else "",
                            whatIWanted = whatIWanted.trim(),
                            substituteBehavior = substituteBehavior.trim(),
                            substituteSucceeded = substituteSucceeded
                        )
                    )
                }
            ) {
                Text(stringResource(if (existing != null) R.string.journal_update_button else R.string.save))
            }
        }
    }
}

@Composable
private fun JournalEntryHistoryCard(entry: HabitJournalEntry) {
    val date = remember(entry.dateEpochDay) { LocalDate.ofEpochDay(entry.dateEpochDay).format(ENTRY_DATE_FORMAT) }
    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (entry.todaysEvents.isNotBlank()) {
                Text(entry.todaysEvents, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                if (!entry.hadSlip) {
                    stringResource(R.string.journal_history_clean)
                } else if (entry.amount.isNotBlank()) {
                    stringResource(R.string.journal_history_amount, entry.amount)
                } else {
                    stringResource(R.string.journal_history_slip)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (!entry.hadSlip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            if (entry.whatIWanted.isNotBlank()) {
                Text(
                    stringResource(R.string.journal_history_wanted, entry.whatIWanted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            if (entry.substituteBehavior.isNotBlank()) {
                Text(
                    (if (entry.substituteSucceeded) "\u2705 " else "") + entry.substituteBehavior,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun CycleSummaryCard(entries: List<HabitJournalEntry>, onRestart: () -> Unit) {
    val cleanDays = entries.count { !it.hadSlip }
    val slipDays = entries.count { it.hadSlip }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.journal_cycle_done_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                stringResource(R.string.journal_cycle_done_summary, cleanDays, slipDays),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            TextButton(onClick = onRestart) {
                Text(stringResource(R.string.journal_restart_button))
            }
        }
    }
}

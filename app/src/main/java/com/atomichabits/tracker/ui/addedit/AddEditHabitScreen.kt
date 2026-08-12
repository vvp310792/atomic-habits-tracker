package com.atomichabits.tracker.ui.addedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.data.Habit
import com.atomichabits.tracker.notifications.ReminderScheduler
import com.atomichabits.tracker.ui.components.CategoryPicker
import com.atomichabits.tracker.ui.components.EmojiPicker
import com.atomichabits.tracker.ui.components.LawSection
import com.atomichabits.tracker.ui.components.StackAnchorPickerDialog
import com.atomichabits.tracker.ui.components.StackTarget
import com.atomichabits.tracker.ui.components.WeekdayPicker
import com.atomichabits.tracker.util.ALL_DAYS_MASK
import com.atomichabits.tracker.util.categoryColorHex
import com.atomichabits.tracker.util.TIME_OF_DAY_VALUES
import com.atomichabits.tracker.util.timeOfDayLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditHabitScreen(
    app: HabitTrackerApp,
    habitId: Long?,
    initialName: String? = null,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var id by remember { mutableStateOf(0L) }
    var syncId by remember { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var name by remember { mutableStateOf(initialName ?: "") }
    var emoji by remember { mutableStateOf("\u2705") }
    var category by remember { mutableStateOf("SELF_DEVELOPMENT") }
    var colorHex by remember { mutableStateOf(categoryColorHex("SELF_DEVELOPMENT")) }
    var activeDays by remember { mutableStateOf(ALL_DAYS_MASK) }
    var timeOfDay by remember { mutableStateOf("MORNING") }
    var reminderEnabled by remember { mutableStateOf(false) }
    var reminderHour by remember { mutableStateOf(9) }
    var reminderMinute by remember { mutableStateOf(0) }
    var lawObvious by remember { mutableStateOf("") }
    var lawAttractive by remember { mutableStateOf("") }
    var lawEasy by remember { mutableStateOf("") }
    var lawSatisfying by remember { mutableStateOf("") }
    var createdAtEpochDay by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    var stackAnchorId by remember { mutableStateOf("") }
    var stackAnchorType by remember { mutableStateOf("") }
    var stackAnchorLabel by remember { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showStackPicker by remember { mutableStateOf(false) }
    var identityId by remember { mutableStateOf("") }
    var identityLabel by remember { mutableStateOf("") }
    var showIdentityPicker by remember { mutableStateOf(false) }

    val allHabits by app.repository.observeActiveHabits().collectAsState(initial = emptyList())
    val allAnchors by app.anchorRepository.observeActive().collectAsState(initial = emptyList())
    val allIdentities by app.identityRepository.observeActive().collectAsState(initial = emptyList())
    val stackableAnchors = allAnchors.filter { it.type != "DESIRED" }
    val stackableHabits = allHabits.filter { it.id != id }

    var initialLoadDone by remember { mutableStateOf(habitId == null) }

    LaunchedEffect(habitId) {
        if (habitId != null) {
            app.database.habitDao().getHabit(habitId)?.let { h ->
                id = h.id
                syncId = h.syncId
                name = h.name
                emoji = h.emoji
                category = h.category
                colorHex = h.colorHex
                activeDays = h.activeDays
                timeOfDay = h.timeOfDay
                reminderEnabled = h.reminderEnabled
                reminderHour = h.reminderHour
                reminderMinute = h.reminderMinute
                lawObvious = h.lawObvious
                lawAttractive = h.lawAttractive
                lawEasy = h.lawEasy
                lawSatisfying = h.lawSatisfying
                createdAtEpochDay = h.createdAtEpochDay
                stackAnchorId = h.stackAnchorId
                stackAnchorType = h.stackAnchorType
                stackAnchorLabel = h.stackAnchorLabel
                identityId = h.identityId
                identityLabel = h.identityLabel
            }
            initialLoadDone = true
        }
    }

    fun buildHabit() = Habit(
        id = id,
        syncId = syncId,
        name = name.trim(),
        emoji = emoji,
        colorHex = colorHex,
        category = category,
        activeDays = activeDays,
        timeOfDay = timeOfDay,
        reminderEnabled = reminderEnabled,
        reminderHour = reminderHour,
        reminderMinute = reminderMinute,
        lawObvious = lawObvious,
        lawAttractive = lawAttractive,
        lawEasy = lawEasy,
        lawSatisfying = lawSatisfying,
        createdAtEpochDay = createdAtEpochDay,
        stackAnchorId = stackAnchorId,
        stackAnchorType = stackAnchorType,
        stackAnchorLabel = stackAnchorLabel,
        identityId = identityId,
        identityLabel = identityLabel
    )

    // Autosave while editing an existing habit (not while creating a new one -
    // a half-filled new habit with no name yet shouldn't be silently created).
    // Debounced: buildHabit() returns a data class, so LaunchedEffect keyed on
    // it structurally restarts (cancelling the delay) on every field change,
    // and only actually saves once 600ms pass without a further change.
    if (initialLoadDone && id != 0L) {
        val autosaveSnapshot = buildHabit()
        LaunchedEffect(autosaveSnapshot) {
            if (autosaveSnapshot.name.isNotBlank()) {
                delay(600)
                app.repository.saveHabit(autosaveSnapshot)
                ReminderScheduler.schedule(context, autosaveSnapshot)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (habitId == null) R.string.add_habit_title else R.string.edit_habit_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (habitId != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                        }
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_name)) },
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.field_icon), style = MaterialTheme.typography.labelLarge)
                EmojiPicker(selected = emoji, onSelect = { emoji = it })
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.field_category), style = MaterialTheme.typography.labelLarge)
                CategoryPicker(
                    selected = category,
                    onSelect = { value ->
                        category = value
                        colorHex = categoryColorHex(value)
                    }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.field_frequency), style = MaterialTheme.typography.labelLarge)
                WeekdayPicker(activeDays = activeDays, onChange = { activeDays = it })
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.field_time_of_day), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TIME_OF_DAY_VALUES.forEach { value ->
                        FilterChip(
                            selected = timeOfDay == value,
                            onClick = { timeOfDay = value },
                            label = { Text(timeOfDayLabel(value)) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.field_reminder), style = MaterialTheme.typography.labelLarge)
                    Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                }
                if (reminderEnabled) {
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(
                            stringResource(
                                R.string.field_reminder_time
                            ) + ": " + "%02d:%02d".format(reminderHour, reminderMinute)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.field_stack), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { showStackPicker = true }) {
                    Text(
                        if (stackAnchorId.isBlank()) stringResource(R.string.stack_picker_none)
                        else stringResource(R.string.field_stack_selected, stackAnchorLabel)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.field_identity), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { showIdentityPicker = true }) {
                    Text(
                        if (identityId.isBlank()) stringResource(R.string.field_identity_none)
                        else identityLabel
                    )
                }
            }

            Text(stringResource(R.string.laws_title), style = MaterialTheme.typography.titleLarge)

            LawSection(
                title = stringResource(R.string.law_obvious_title),
                hint = stringResource(R.string.law_obvious_hint),
                value = lawObvious,
                onValueChange = { lawObvious = it }
            )
            LawSection(
                title = stringResource(R.string.law_attractive_title),
                hint = stringResource(R.string.law_attractive_hint),
                value = lawAttractive,
                onValueChange = { lawAttractive = it }
            )
            LawSection(
                title = stringResource(R.string.law_easy_title),
                hint = stringResource(R.string.law_easy_hint),
                value = lawEasy,
                onValueChange = { lawEasy = it }
            )
            LawSection(
                title = stringResource(R.string.law_satisfying_title),
                hint = stringResource(R.string.law_satisfying_hint),
                value = lawSatisfying,
                onValueChange = { lawSatisfying = it }
            )

            Button(
                onClick = {
                    scope.launch {
                        val savedId = app.repository.saveHabit(buildHabit())
                        val finalHabit = buildHabit().copy(id = if (id == 0L) savedId else id)
                        ReminderScheduler.schedule(context, finalHabit)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = reminderHour,
            initialMinute = reminderMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    reminderHour = timeState.hour
                    reminderMinute = timeState.minute
                    showTimePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = { TimePicker(state = timeState) }
        )
    }

    if (showStackPicker) {
        StackAnchorPickerDialog(
            trackedHabits = stackableHabits,
            anchors = stackableAnchors,
            onDismiss = { showStackPicker = false },
            onPick = { target: StackTarget? ->
                if (target == null) {
                    stackAnchorId = ""
                    stackAnchorType = ""
                    stackAnchorLabel = ""
                } else {
                    stackAnchorId = target.id
                    stackAnchorType = target.type
                    stackAnchorLabel = target.label
                    if (lawObvious.isBlank()) {
                        lawObvious = "После «${target.label}» я: ${name.ifBlank { "..." }}"
                    }
                }
                showStackPicker = false
            }
        )
    }

    if (showIdentityPicker) {
        var newStatement by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showIdentityPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newStatement.isNotBlank()) {
                            scope.launch {
                                val newId = app.identityRepository.save(
                                    com.atomichabits.tracker.data.Identity(
                                        statement = newStatement.trim(),
                                        createdAtEpochDay = LocalDate.now().toEpochDay()
                                    )
                                )
                                app.database.identityDao().getAllOnce().find { it.id == newId }?.let {
                                    identityId = it.syncId
                                    identityLabel = it.statement
                                }
                            }
                            showIdentityPicker = false
                        }
                    },
                    enabled = newStatement.isNotBlank()
                ) { Text(stringResource(R.string.identity_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showIdentityPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.field_identity)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.field_identity_none),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { identityId = ""; identityLabel = ""; showIdentityPicker = false }
                            .padding(vertical = 10.dp)
                    )
                    allIdentities.forEach { identity ->
                        Text(
                            "\uD83E\uDDE9 " + identity.statement,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    identityId = identity.syncId
                                    identityLabel = identity.statement
                                    showIdentityPicker = false
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedTextField(
                        value = newStatement,
                        onValueChange = { newStatement = it },
                        label = { Text(stringResource(R.string.identity_statement_label)) },
                        placeholder = { Text(stringResource(R.string.identity_statement_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        habitId?.let {
                            app.repository.archiveHabit(it)
                            ReminderScheduler.cancel(context, it)
                        }
                        showDeleteConfirm = false
                        onDone()
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = { Text(stringResource(R.string.delete) + "?") }
        )
    }
}

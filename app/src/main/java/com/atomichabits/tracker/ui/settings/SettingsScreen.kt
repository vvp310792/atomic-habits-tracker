package com.atomichabits.tracker.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.atomichabits.tracker.BuildConfig
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.data.Habit
import com.atomichabits.tracker.data.Identity
import com.atomichabits.tracker.data.PausePeriod
import com.atomichabits.tracker.export.DataExporter
import com.atomichabits.tracker.update.ApkInstaller
import com.atomichabits.tracker.update.UpdateCheckResult
import com.atomichabits.tracker.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: HabitTrackerApp, onBack: (() -> Unit)? = null, onOpenScorecard: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val identities by app.identityRepository.observeActive().collectAsState(initial = emptyList())
    var showAddIdentity by remember { mutableStateOf(false) }
    var editingIdentity by remember { mutableStateOf<Identity?>(null) }
    var editIdentityText by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }

    var currentUserEmail by remember { mutableStateOf(app.authManager.currentUser?.email) }
    var authBusy by remember { mutableStateOf(false) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        app.authManager.authStateFlow().collectLatest { user ->
            currentUserEmail = user?.email
        }
    }

    var checkingUpdate by remember { mutableStateOf(false) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    fun runAuth(action: suspend () -> Result<*>) {
        authBusy = true
        authMessage = null
        scope.launch {
            val result = action()
            authBusy = false
            authMessage = result.exceptionOrNull()?.let {
                it.message ?: context.getString(R.string.settings_account_error)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.identity_section), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.identity_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                identities.forEach { identity ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                "\uD83E\uDDE9 " + identity.statement,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                editingIdentity = identity
                                editIdentityText = identity.statement
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.identity_rename))
                            }
                            IconButton(onClick = { scope.launch { app.identityRepository.archive(identity.id) } }) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { showAddIdentity = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.identity_add))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.scorecard_section), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.scorecard_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Button(onClick = onOpenScorecard, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.ChecklistRtl, contentDescription = null)
                    Text(" " + stringResource(R.string.scorecard_open), modifier = Modifier.padding(start = 6.dp))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_account_section), style = MaterialTheme.typography.titleMedium)

                if (currentUserEmail != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            "\u2705 " + stringResource(R.string.settings_account_signed_in, currentUserEmail!!),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = { app.authManager.signOut() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_account_sign_out))
                    }
                } else {
                    Text(
                        stringResource(R.string.settings_account_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text(stringResource(R.string.settings_account_email_label)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.settings_account_password_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                runAuth { app.authManager.signInWithEmail(emailInput.trim(), passwordInput) }
                            },
                            enabled = !authBusy && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.settings_account_email_sign_in))
                        }
                        OutlinedButton(
                            onClick = {
                                runAuth { app.authManager.signUpWithEmail(emailInput.trim(), passwordInput) }
                            },
                            enabled = !authBusy && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.settings_account_email_sign_up))
                        }
                    }

                    TextButton(
                        onClick = {
                            if (emailInput.isBlank()) return@TextButton
                            authBusy = true
                            authMessage = null
                            scope.launch {
                                val result = app.authManager.sendPasswordReset(emailInput.trim())
                                authBusy = false
                                authMessage = if (result.isSuccess) {
                                    context.getString(R.string.settings_account_reset_sent)
                                } else {
                                    result.exceptionOrNull()?.message ?: context.getString(R.string.settings_account_error)
                                }
                            }
                        },
                        enabled = !authBusy && emailInput.isNotBlank()
                    ) {
                        Text(stringResource(R.string.settings_account_forgot_password))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.settings_account_or),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    Button(
                        onClick = { runAuth { app.authManager.signInWithGoogle(context) } },
                        enabled = !authBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_account_sign_in))
                    }

                    authMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            PausePeriodsSection(app)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_notifications_section), style = MaterialTheme.typography.titleMedium)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(
                        text = if (notificationsGranted)
                            "\u2705 " + stringResource(R.string.settings_notifications_permission)
                        else "\u26A0\uFE0F " + stringResource(R.string.settings_notifications_permission),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_export_section), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_export_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                OutlinedButton(
                    onClick = {
                        exporting = true
                        exportError = null
                        scope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    DataExporter.export(
                                        context = context,
                                        habitDao = app.database.habitDao(),
                                        habitLogDao = app.database.habitLogDao(),
                                        identityDao = app.database.identityDao(),
                                        impulseLogDao = app.database.impulseLogDao(),
                                        journalDao = app.database.habitJournalEntryDao()
                                    )
                                }
                                context.startActivity(DataExporter.shareIntent(context, file))
                            } catch (e: Exception) {
                                exportError = e.message ?: context.getString(R.string.settings_export_error)
                            } finally {
                                exporting = false
                            }
                        }
                    },
                    enabled = !exporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (exporting) stringResource(R.string.settings_export_in_progress)
                        else stringResource(R.string.settings_export_button)
                    )
                }
                exportError?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_updates_section), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_updates_current, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Button(
                    onClick = {
                        checkingUpdate = true
                        updateMessage = null
                        updateResult = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                UpdateChecker.check(BuildConfig.VERSION_CODE)
                            }
                            updateResult = result
                            updateMessage = when (result) {
                                is UpdateCheckResult.UpToDate -> context.getString(R.string.settings_updates_uptodate)
                                is UpdateCheckResult.Failed -> result.reason
                                is UpdateCheckResult.UpdateAvailable -> context.getString(
                                    R.string.settings_updates_available,
                                    result.release.releaseName
                                )
                            }
                            checkingUpdate = false
                        }
                    },
                    enabled = !checkingUpdate && !downloadingUpdate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_updates_check))
                }

                updateMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }

                val available = (updateResult as? UpdateCheckResult.UpdateAvailable)?.release
                if (available != null) {
                    Button(
                        onClick = {
                            if (!ApkInstaller.canRequestInstalls(context)) {
                                updateMessage = context.getString(R.string.settings_updates_grant_permission)
                                ApkInstaller.requestInstallPermission(context)
                                return@Button
                            }
                            downloadingUpdate = true
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    ApkInstaller.download(context, available.downloadUrl)
                                }
                                downloadingUpdate = false
                                if (file != null) {
                                    ApkInstaller.install(context, file)
                                } else {
                                    updateMessage = context.getString(R.string.settings_updates_error)
                                }
                            }
                        },
                        enabled = !downloadingUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                if (downloadingUpdate) R.string.settings_updates_downloading
                                else R.string.settings_updates_install
                            )
                        )
                    }
                }
            }
        }
    }

    if (showAddIdentity) {
        var statement by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddIdentity = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (statement.isNotBlank()) {
                            scope.launch {
                                app.identityRepository.save(
                                    Identity(statement = statement.trim(), createdAtEpochDay = LocalDate.now().toEpochDay())
                                )
                            }
                            showAddIdentity = false
                        }
                    },
                    enabled = statement.isNotBlank()
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddIdentity = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.identity_add)) },
            text = {
                OutlinedTextField(
                    value = statement,
                    onValueChange = { statement = it },
                    label = { Text(stringResource(R.string.identity_statement_label)) },
                    placeholder = { Text(stringResource(R.string.identity_statement_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    editingIdentity?.let { target ->
        AlertDialog(
            onDismissRequest = { editingIdentity = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newStatement = editIdentityText.trim()
                        if (newStatement.isNotBlank()) {
                            scope.launch {
                                app.identityRepository.save(target.copy(statement = newStatement))
                                app.repository.renameIdentityLabel(target.syncId, newStatement)
                            }
                            editingIdentity = null
                        }
                    },
                    enabled = editIdentityText.isNotBlank()
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingIdentity = null }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.identity_rename)) },
            text = {
                OutlinedTextField(
                    value = editIdentityText,
                    onValueChange = { editIdentityText = it },
                    label = { Text(stringResource(R.string.identity_statement_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

/**
 * Travel/business-trip pause management (see PausePeriod.kt): a per-trip list
 * of habits explicitly excused from scheduling for statistics purposes over a
 * fixed date range, so a business trip doesn't dent a streak, completion
 * rate, or mastery progress that took months to build.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PausePeriodsSection(app: HabitTrackerApp) {
    val scope = rememberCoroutineScope()
    val periods by app.pausePeriodRepository.observeAll().collectAsState(initial = emptyList())
    val habits by app.repository.observeActiveHabits().collectAsState(initial = emptyList())
    val trackedHabits = remember(habits) { habits.filter { it.isTracked } }
    val today = remember { LocalDate.now() }
    var showAdd by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.settings_pause_section), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_pause_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        periods.forEach { period ->
            val isActive = today.toEpochDay() in period.startEpochDay..period.endEpochDay
            val pausedNames = remember(period, trackedHabits) {
                val ids = period.pausedHabitSyncIds.split(",").map { it.trim() }.toSet()
                trackedHabits.filter { it.syncId in ids }.joinToString(", ") { it.name }
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            (period.label.ifBlank { stringResource(R.string.settings_pause_default_label) }) +
                                if (isActive) " " + stringResource(R.string.settings_pause_active_badge) else "",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "${LocalDate.ofEpochDay(period.startEpochDay)} \u2014 ${LocalDate.ofEpochDay(period.endEpochDay)}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (pausedNames.isNotBlank()) {
                            Text(
                                pausedNames,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    IconButton(onClick = { scope.launch { app.pausePeriodRepository.delete(period) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                    }
                }
            }
        }

        OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_pause_add_button))
        }
    }

    if (showAdd) {
        AddPausePeriodDialog(
            habits = trackedHabits,
            onDismiss = { showAdd = false },
            onSave = { period ->
                scope.launch { app.pausePeriodRepository.save(period) }
                showAdd = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPausePeriodDialog(
    habits: List<Habit>,
    onDismiss: () -> Unit,
    onSave: (PausePeriod) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedHabitIds by remember { mutableStateOf(setOf<String>()) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val canSave = startDate != null && endDate != null && !endDate!!.isBefore(startDate!!) && selectedHabitIds.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val s = startDate
                    val e = endDate
                    if (s != null && e != null && canSave) {
                        onSave(
                            PausePeriod(
                                startEpochDay = s.toEpochDay(),
                                endEpochDay = e.toEpochDay(),
                                label = label.trim(),
                                pausedHabitSyncIds = selectedHabitIds.joinToString(",")
                            )
                        )
                    }
                },
                enabled = canSave
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.settings_pause_add_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.settings_pause_label_field)) },
                    placeholder = { Text(stringResource(R.string.settings_pause_label_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(startDate?.toString() ?: stringResource(R.string.settings_pause_pick_start))
                }
                OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(endDate?.toString() ?: stringResource(R.string.settings_pause_pick_end))
                }

                Text(stringResource(R.string.settings_pause_pick_habits), style = MaterialTheme.typography.labelLarge)
                if (habits.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_pause_no_tracked_habits),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                habits.forEach { h ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = h.syncId in selectedHabitIds,
                            onCheckedChange = { checked ->
                                selectedHabitIds = if (checked) selectedHabitIds + h.syncId else selectedHabitIds - h.syncId
                            }
                        )
                        Text("${h.emoji} ${h.name}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    )

    if (showStartPicker) {
        SimpleDatePickerDialog(
            initial = startDate ?: LocalDate.now(),
            onDismiss = { showStartPicker = false },
            onConfirm = {
                startDate = it
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        SimpleDatePickerDialog(
            initial = endDate ?: startDate ?: LocalDate.now(),
            onDismiss = { showEndPicker = false },
            onConfirm = {
                endDate = it
                showEndPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDatePickerDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                } else {
                    onDismiss()
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    ) {
        DatePicker(state = state)
    }
}

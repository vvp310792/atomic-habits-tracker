package com.atomichabits.tracker.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.atomichabits.tracker.BuildConfig
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.sheets.RestoreOutcome
import com.atomichabits.tracker.sheets.SheetsExporter
import com.atomichabits.tracker.sheets.SheetsRestoreManager
import com.atomichabits.tracker.sheets.SheetsSyncWorker
import com.atomichabits.tracker.update.ApkInstaller
import com.atomichabits.tracker.update.UpdateCheckResult
import com.atomichabits.tracker.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: HabitTrackerApp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sheetsUrl by remember { mutableStateOf("") }
    var autoSync by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    var checkingUpdate by remember { mutableStateOf(false) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        app.settingsStore.sheetsUrl.collect { sheetsUrl = it }
    }
    LaunchedEffect(Unit) {
        app.settingsStore.autoSyncEnabled.collect { autoSync = it }
    }

    val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_sheets_section), style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = sheetsUrl,
                    onValueChange = {
                        sheetsUrl = it
                        scope.launch { app.settingsStore.setSheetsUrl(it) }
                    },
                    label = { Text(stringResource(R.string.settings_sheets_url_label)) },
                    placeholder = { Text(stringResource(R.string.settings_sheets_url_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.settings_sheets_autosync))
                    Switch(
                        checked = autoSync,
                        onCheckedChange = {
                            autoSync = it
                            scope.launch { app.settingsStore.setAutoSync(it) }
                        }
                    )
                }

                Button(
                    onClick = {
                        testing = true
                        statusMessage = null
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                SheetsExporter(sheetsUrl).ping()
                            }
                            statusMessage = if (ok) context.getString(R.string.settings_sheets_success)
                            else context.getString(R.string.settings_sheets_error)
                            testing = false
                            if (ok) SheetsSyncWorker.syncNow(context)
                        }
                    },
                    enabled = sheetsUrl.isNotBlank() && !testing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_sheets_export_now))
                }

                OutlinedButton(
                    onClick = { showRestoreConfirm = true },
                    enabled = sheetsUrl.isNotBlank() && !restoring,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_sheets_restore))
                }
                Text(
                    stringResource(R.string.settings_sheets_restore_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                statusMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }

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

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    restoring = true
                    statusMessage = null
                    scope.launch {
                        val outcome = SheetsRestoreManager.restore(context, sheetsUrl)
                        statusMessage = when (outcome) {
                            is RestoreOutcome.Success -> context.getString(
                                R.string.settings_sheets_restore_success,
                                outcome.habitsRestored,
                                outcome.logsRestored
                            )
                            is RestoreOutcome.NothingFound -> context.getString(R.string.settings_sheets_restore_empty)
                            is RestoreOutcome.Failed -> context.getString(R.string.settings_sheets_error)
                        }
                        restoring = false
                    }
                }) { Text(stringResource(R.string.settings_sheets_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.settings_sheets_restore)) },
            text = { Text(stringResource(R.string.settings_sheets_restore_confirm)) }
        )
    }
}

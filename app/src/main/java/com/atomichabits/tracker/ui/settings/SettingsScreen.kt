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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.sheets.SheetsExporter
import com.atomichabits.tracker.sheets.SheetsSyncWorker
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
        }
    }
}

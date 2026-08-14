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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.atomichabits.tracker.BuildConfig
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.data.Identity
import com.atomichabits.tracker.update.ApkInstaller
import com.atomichabits.tracker.update.UpdateCheckResult
import com.atomichabits.tracker.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: HabitTrackerApp, onBack: (() -> Unit)? = null, onOpenScorecard: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val identities by app.identityRepository.observeActive().collectAsState(initial = emptyList())
    var showAddIdentity by remember { mutableStateOf(false) }
    var editingIdentity by remember { mutableStateOf<Identity?>(null) }
    var editIdentityText by remember { mutableStateOf("") }

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

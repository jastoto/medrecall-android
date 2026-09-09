package com.asok.medrecall.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.backup.BackupResult
import com.asok.medrecall.data.backup.BackupSection
import com.asok.medrecall.ui.components.MedRecallTopBar

private enum class BackupDestination(val icon: ImageVector, val label: String) {
    GOOGLE_DRIVE(Icons.Default.CloudQueue, "Google Drive"),
    ONE_DRIVE(Icons.Default.CloudQueue, "OneDrive"),
    THIS_PHONE(Icons.Default.PhoneAndroid, "Files on This Phone")
}

/**
 * Settings > Backup & Restore. Pick a destination, then Back Up or Restore.
 *
 * Google Drive is the one real destination: Back Up Now either exports
 * every section (Full) or just the ones checked (Individual -- mutually
 * exclusive with Full, per Asok's spec) as separate JSON files under
 * My Drive > MedRecall > Android Backup (see DriveBackupManager /
 * BackupExporter). OneDrive, "Files on This Phone", and Restore (all
 * destinations) are still UI shells -- tapping them explains what's coming
 * rather than doing anything real yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(onGoHome: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val viewModel: BackupViewModel = viewModel(factory = BackupViewModel.factory(context))

    var selected by remember { mutableStateOf(BackupDestination.THIS_PHONE) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    val googleAccount by viewModel.googleAccount.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val selectedSections by viewModel.selectedSections.collectAsState()
    val runState by viewModel.runState.collectAsState()
    val pendingResolution by viewModel.pendingResolution.collectAsState()

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        activity?.let { viewModel.onDriveConsentResult(it, result.resultCode, result.data) }
    }

    LaunchedEffect(pendingResolution) {
        pendingResolution?.let { intentSender ->
            consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = "Backup & Restore",
                onGoHome = onGoHome
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SettingsGroup {
                    BackupDestination.entries.forEachIndexed { index, destination ->
                        DestinationRow(
                            destination = destination,
                            selected = destination == selected,
                            onSelect = { selected = destination }
                        )
                        if (index != BackupDestination.entries.lastIndex) SettingsDivider()
                    }
                }
            }

            if (selected == BackupDestination.GOOGLE_DRIVE) {
                item { BackupScopeSection(mode, selectedSections, onModeChange = viewModel::setMode, onToggleSection = viewModel::toggleSection) }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val isBusy = runState is BackupRunState.AuthorizingDrive || runState is BackupRunState.Uploading
                    Button(
                        onClick = {
                            when (selected) {
                                BackupDestination.GOOGLE_DRIVE -> {
                                    if (googleAccount?.driveConnected != true) {
                                        dialogMessage = "Connect your Google account first in Settings > Account, then come back here to back up to Google Drive."
                                    } else {
                                        activity?.let { viewModel.backUpToGoogleDrive(it) }
                                    }
                                }
                                else -> dialogMessage = "Backing up to ${selected.label} is coming in a future update."
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(text = "  Backing Up...")
                        } else {
                            Icon(Icons.Default.Backup, contentDescription = null)
                            Text(text = "  Back Up Now")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            dialogMessage = "Restoring from ${selected.label} is coming in a future update."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Text(text = "  Restore")
                    }
                }
            }
        }
    }

    dialogMessage?.let { message ->
        ComingSoonDialog(title = "Backup & Restore", message = message, onDismiss = { dialogMessage = null })
    }

    val currentRunState = runState
    if (currentRunState is BackupRunState.Done) {
        BackupResultDialog(results = currentRunState.results, onDismiss = { viewModel.dismissResult() })
    }
    if (currentRunState is BackupRunState.Error) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissResult() },
            title = { Text("Backup & Restore") },
            text = { Text(currentRunState.message) },
            confirmButton = { TextButton(onClick = { viewModel.dismissResult() }) { Text("OK") } }
        )
    }
}

@Composable
private fun BackupScopeSection(
    mode: BackupMode,
    selectedSections: Set<BackupSection>,
    onModeChange: (BackupMode) -> Unit,
    onToggleSection: (BackupSection) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Backup Scope", style = MaterialTheme.typography.labelLarge)
        SettingsGroup {
            ModeRow(
                label = "Full Backup (every section)",
                selected = mode == BackupMode.FULL,
                onSelect = { onModeChange(BackupMode.FULL) }
            )
            SettingsDivider()
            ModeRow(
                label = "Individual Sections",
                selected = mode == BackupMode.INDIVIDUAL,
                onSelect = { onModeChange(BackupMode.INDIVIDUAL) }
            )
            if (mode == BackupMode.INDIVIDUAL) {
                SettingsDivider()
                Column(modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 8.dp)) {
                    BackupSection.entries.forEach { section ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSection(section) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = section in selectedSections, onCheckedChange = { onToggleSection(section) })
                            Text(text = section.label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun BackupResultDialog(results: List<BackupResult>, onDismiss: () -> Unit) {
    val successes = results.filterIsInstance<BackupResult.Success>()
    val failures = results.filterIsInstance<BackupResult.Failure>()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup & Restore") },
        text = {
            Column {
                if (successes.isNotEmpty()) {
                    Text("Backed up to Google Drive (My Drive > MedRecall > Android Backup):")
                    successes.forEach { Text("• ${it.fileName}", style = MaterialTheme.typography.bodySmall) }
                }
                if (failures.isNotEmpty()) {
                    if (successes.isNotEmpty()) Text(" ")
                    Text("Failed:")
                    failures.forEach { Text("• ${it.section.label}: ${it.message}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

@Composable
private fun DestinationRow(destination: BackupDestination, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Icon(destination.icon, contentDescription = null, modifier = Modifier.padding(start = 8.dp, end = 12.dp))
        Text(text = destination.label)
    }
}

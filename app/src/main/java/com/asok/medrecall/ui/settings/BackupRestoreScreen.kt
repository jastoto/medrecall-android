package com.asok.medrecall.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.CheckCircle
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
import com.asok.medrecall.data.backup.BackupDestination
import com.asok.medrecall.data.backup.BackupSection
import com.asok.medrecall.data.backup.CloudBackupFile
import com.asok.medrecall.ui.components.BackIconButton
import com.asok.medrecall.ui.components.MedRecallTopBar
import java.text.DateFormat
import java.util.Date

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun BackupDestination.icon(): ImageVector = when (this) {
    BackupDestination.GOOGLE_DRIVE -> Icons.Default.CloudQueue
    BackupDestination.ONE_DRIVE -> Icons.Default.CloudQueue
    BackupDestination.THIS_PHONE -> Icons.Default.PhoneAndroid
}

/**
 * Settings > Backup & Restore. Google Drive and OneDrive are real
 * connectors now (sign in, pick which sections to include, back up,
 * and restore with a "this replaces everything currently in the app"
 * confirmation) -- see [BackupViewModel] and data/backup/. Files on
 * This Phone is still a UI shell pending its own punch-list item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onGoHome: () -> Unit,
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(factory = BackupViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showBackupList by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.onGoogleSignInResult(result.data) }

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = "Backup & Restore",
                onGoHome = onGoHome,
                actions = { BackIconButton(onClick = onBack) }
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
                            selected = destination == uiState.selectedDestination,
                            onSelect = {
                                viewModel.selectDestination(destination)
                                showBackupList = false
                            }
                        )
                        if (index != BackupDestination.entries.lastIndex) SettingsDivider()
                    }
                }
            }

            item {
                when (uiState.selectedDestination) {
                    BackupDestination.GOOGLE_DRIVE -> SignInRow(
                        label = "Google Drive",
                        signedInAs = uiState.googleSignedInLabel,
                        onSignIn = { googleSignInLauncher.launch(viewModel.googleSignInClient.signInIntent) }
                    )
                    BackupDestination.ONE_DRIVE -> SignInRow(
                        label = "OneDrive",
                        signedInAs = uiState.oneDriveSignedInLabel,
                        onSignIn = {
                            context.findActivity()?.let { viewModel.signInToOneDrive(it) }
                        }
                    )
                    BackupDestination.THIS_PHONE -> {}
                }
            }

            item {
                SettingsCaption("What to include")
                SettingsGroup {
                    BackupSection.entries.forEachIndexed { index, section ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleSection(section) }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = section in uiState.selectedSections,
                                onCheckedChange = { viewModel.toggleSection(section) }
                            )
                            Text(section.label)
                        }
                        if (index != BackupSection.entries.lastIndex) SettingsDivider()
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.backUpNow() },
                        enabled = !uiState.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Text(text = "  Back Up Now")
                    }
                    OutlinedButton(
                        onClick = {
                            showBackupList = true
                            viewModel.refreshBackupList()
                        },
                        enabled = !uiState.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Text(text = "  Restore")
                    }
                    if (uiState.isBusy) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                    uiState.statusMessage?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.primary)
                    }
                    uiState.errorMessage?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (showBackupList) {
                item { SettingsCaption("Choose a backup to restore") }
                if (uiState.availableBackups.isEmpty() && !uiState.isBusy) {
                    item {
                        Text(
                            "No backups found in ${uiState.selectedDestination.label}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    SettingsGroup {
                        uiState.availableBackups.forEachIndexed { index, file ->
                            BackupFileRow(file = file, onClick = { viewModel.requestRestore(file) })
                            if (index != uiState.availableBackups.lastIndex) SettingsDivider()
                        }
                    }
                }
            }
        }
    }

    uiState.pendingRestoreFile?.let { file ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Restore from ${uiState.selectedDestination.label}?") },
            text = {
                Text(
                    "This replaces everything currently in MedRecall+ for the sections in " +
                        "\"${file.name}\" with what's in that backup. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) { Text("Replace Everything") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRestore) { Text("Cancel") }
            }
        )
    }
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
        Icon(destination.icon(), contentDescription = null, modifier = Modifier.padding(start = 8.dp, end = 12.dp))
        Text(text = destination.label)
    }
}

@Composable
private fun SignInRow(label: String, signedInAs: String?, onSignIn: () -> Unit) {
    SettingsGroup {
        if (signedInAs != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text("Signed in to $label")
                    Text(
                        signedInAs,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            SettingsRow(
                icon = Icons.Default.CloudQueue,
                title = "Sign in to $label",
                showChevron = false,
                onClick = onSignIn
            )
        }
    }
}

@Composable
private fun BackupFileRow(file: CloudBackupFile, onClick: () -> Unit) {
    val subtitle = buildString {
        file.modifiedAtMillis?.let { append(DateFormat.getDateTimeInstance().format(Date(it))) }
        file.sizeBytes?.let { bytes ->
            if (isNotEmpty()) append(" · ")
            append("${bytes / 1024} KB")
        }
    }
    SettingsRow(
        icon = Icons.Default.Restore,
        title = file.name,
        subtitle = subtitle.ifBlank { null },
        onClick = onClick
    )
}

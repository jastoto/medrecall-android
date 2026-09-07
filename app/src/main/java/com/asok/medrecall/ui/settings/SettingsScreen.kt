package com.asok.medrecall.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.BuildConfig
import com.asok.medrecall.ui.components.MedRecallTopBar

/**
 * The main Settings screen: Account, Backup & Restore, Send Feedback,
 * Security (biometric lock + PIN), Danger Zone, and the app version.
 * This is the top-level screen reached from the bottom nav's Settings tab
 * -- Account and Backup & Restore each drill into their own sub-screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onGoHome: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenPinSetup: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(LocalContext.current))
) {
    val context = LocalContext.current
    val biometricLockEnabled by viewModel.biometricLockEnabled.collectAsState()
    val pinEnabled by viewModel.pinEnabled.collectAsState()

    var showNoBiometricsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = "Settings",
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsGroup {
                        SettingsRow(
                            icon = Icons.Default.AccountCircle,
                            title = "Account",
                            onClick = onOpenAccount
                        )
                    }
                    SettingsCaption("Manage your Google or Microsoft account, connect health system portals, and share access with family.")
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsGroup {
                        SettingsRow(
                            icon = Icons.Default.Backup,
                            title = "Backup & Restore",
                            onClick = onOpenBackupRestore
                        )
                    }
                    SettingsCaption("Choose where backups go (Google Drive, OneDrive, or files on this phone), and back up or restore your data.")
                }
            }

            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.Mail,
                        title = "Send Feedback",
                        showChevron = false,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_SUBJECT, "MedRecall+ Feedback")
                            }
                            runCatching { context.startActivity(intent) }
                        }
                    )
                }
            }

            item {
                Text(text = "Security", style = MaterialTheme.typography.labelLarge)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsGroup {
                        SettingsSwitchRow(
                            icon = Icons.Default.Fingerprint,
                            title = "Biometric Lock",
                            checked = biometricLockEnabled,
                            onCheckedChange = { turnOn ->
                                if (!turnOn) {
                                    viewModel.setBiometricLockEnabled(false)
                                } else {
                                    val canUseBiometrics = BiometricManager.from(context)
                                        .canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
                                    if (canUseBiometrics) {
                                        viewModel.setBiometricLockEnabled(true)
                                    } else {
                                        showNoBiometricsDialog = true
                                    }
                                }
                            }
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Default.Lock,
                            title = "Set Backup PIN",
                            subtitle = if (pinEnabled) "PIN set" else "Not set",
                            onClick = onOpenPinSetup
                        )
                    }
                    if (biometricLockEnabled && !pinEnabled) {
                        SettingsCaption("Consider also setting a Backup PIN as a fallback in case biometrics don't work.")
                    }
                }
            }

            item {
                Text(text = "Danger Zone", style = MaterialTheme.typography.labelLarge)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsGroup {
                        SettingsDangerRow(
                            icon = Icons.Default.DeleteForever,
                            title = "Delete All Data...",
                            onClick = { showDeleteConfirm = true }
                        )
                    }
                    SettingsCaption("MedRecall+ stores your records only on this device (plus any backups you've turned on). Deleting all data removes everything permanently -- back up first if you want to keep it.")
                }
            }

            item {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }

    if (showNoBiometricsDialog) {
        AlertDialog(
            onDismissRequest = { showNoBiometricsDialog = false },
            title = { Text("No Biometrics Set Up") },
            text = { Text("Set up a fingerprint or face unlock on this device first, then turn this back on.") },
            confirmButton = {
                TextButton(onClick = {
                    showNoBiometricsDialog = false
                    val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        AndroidSettings.ACTION_BIOMETRIC_ENROLL
                    } else {
                        AndroidSettings.ACTION_SECURITY_SETTINGS
                    }
                    runCatching { context.startActivity(Intent(action)) }
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showNoBiometricsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete All Data?") },
            text = { Text("This permanently deletes every doctor, medication, appointment, condition, vital, and report stored in MedRecall+. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteAllData(onDone = onGoHome)
                }) { Text("Delete Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

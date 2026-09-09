package com.asok.medrecall.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CalendarMonth
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
import com.asok.medrecall.data.calendar.DeviceCalendarInfo
import com.asok.medrecall.data.calendar.DeviceCalendarManager
import com.asok.medrecall.ui.components.MedRecallTopBar

/**
 * The main Settings screen: Account, Backup & Restore, Send Feedback,
 * Calendar Sync (which device calendar -- Google, Office/Exchange, or
 * local -- appointments sync to), Security (biometric lock + PIN), Danger
 * Zone, and the app version. This is the top-level screen reached from the
 * bottom nav's Settings tab -- Account and Backup & Restore each drill into
 * their own sub-screen.
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
    val syncCalendar by viewModel.syncCalendar.collectAsState()
    val availableCalendars by viewModel.availableCalendars.collectAsState()

    var showNoBiometricsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCalendarPicker by remember { mutableStateOf(false) }
    var showNoCalendarPermission by remember { mutableStateOf(false) }
    var pendingCalendarSwitch by remember { mutableStateOf<DeviceCalendarInfo?>(null) }
    var pendingTurnOff by remember { mutableStateOf(false) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val allGranted = granted[Manifest.permission.READ_CALENDAR] == true &&
            granted[Manifest.permission.WRITE_CALENDAR] == true
        if (allGranted) {
            viewModel.loadAvailableCalendars(context)
            showCalendarPicker = true
        } else {
            showNoCalendarPermission = true
        }
    }

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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsGroup {
                        SettingsRow(
                            icon = Icons.Default.CalendarMonth,
                            title = "Calendar Sync",
                            subtitle = syncCalendar?.let { "${it.displayName} (${it.providerLabel})" } ?: "Off",
                            onClick = {
                                if (DeviceCalendarManager.hasPermission(context)) {
                                    viewModel.loadAvailableCalendars(context)
                                    showCalendarPicker = true
                                } else {
                                    calendarPermissionLauncher.launch(
                                        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                                    )
                                }
                            }
                        )
                    }
                    SettingsCaption("Pick one calendar already on this phone (Google, Office/Outlook, or a local calendar) to keep your appointments synced to.")
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

    if (showNoCalendarPermission) {
        AlertDialog(
            onDismissRequest = { showNoCalendarPermission = false },
            title = { Text("Calendar Permission Needed") },
            text = { Text("MedRecall+ needs calendar permission to sync appointments to a calendar on this device. You can grant it from this phone's app settings.") },
            confirmButton = {
                TextButton(onClick = {
                    showNoCalendarPermission = false
                    val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    runCatching { context.startActivity(intent) }
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showNoCalendarPermission = false }) { Text("Cancel") }
            }
        )
    }

    if (showCalendarPicker) {
        CalendarPickerDialog(
            calendars = availableCalendars,
            current = syncCalendar,
            onSelect = { chosen ->
                showCalendarPicker = false
                val current = syncCalendar
                if (chosen == null) {
                    if (current != null) pendingTurnOff = true // else already off, nothing to do
                } else if (current != null && current.calendarId != chosen.id) {
                    pendingCalendarSwitch = chosen
                } else {
                    viewModel.applySyncCalendar(chosen, detachExisting = false)
                }
            },
            onDismiss = { showCalendarPicker = false }
        )
    }

    pendingCalendarSwitch?.let { candidate ->
        AlertDialog(
            onDismissRequest = { pendingCalendarSwitch = null },
            title = { Text("Switch Calendars?") },
            text = {
                Text(
                    "Appointments already synced to ${syncCalendar?.displayName ?: "your current calendar"} will not move to " +
                        "${candidate.displayName} and will no longer be kept in sync. New and edited appointments will sync to " +
                        "${candidate.displayName} going forward."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.applySyncCalendar(candidate, detachExisting = true)
                    pendingCalendarSwitch = null
                }) { Text("Switch") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCalendarSwitch = null }) { Text("Cancel") }
            }
        )
    }

    if (pendingTurnOff) {
        AlertDialog(
            onDismissRequest = { pendingTurnOff = false },
            title = { Text("Turn Off Calendar Sync?") },
            text = {
                Text(
                    "Appointments already added to ${syncCalendar?.displayName ?: "your calendar"} will stay there, but " +
                        "MedRecall+ will stop keeping them in sync."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.turnOffSyncCalendar(detachExisting = true)
                    pendingTurnOff = false
                }) { Text("Turn Off") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTurnOff = false }) { Text("Cancel") }
            }
        )
    }
}

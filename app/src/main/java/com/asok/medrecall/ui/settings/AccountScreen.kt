package com.asok.medrecall.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.ui.components.MedRecallTopBar

private data class AccountLinkRow(val icon: ImageVector, val title: String, val comingSoonMessage: String)

private val healthSystemRows = listOf(
    AccountLinkRow(Icons.Default.LocalHospital, "Epic Sandbox", "Connecting to Epic's patient-portal sandbox is coming in a future update. Once connected, MedRecall+ will be able to pull records directly from your health system.")
)

private val familySharingRows = listOf(
    AccountLinkRow(Icons.Default.FamilyRestroom, "Share Reports with Family", "Family sharing is coming in a future update. Once available, you'll be able to give family members view access to your reports.")
)

/**
 * Settings > Account. Three groups: cloud storage accounts, health system
 * connections (Epic Sandbox), and family sharing.
 *
 * Google Drive is the one real connector here -- tapping it runs an actual
 * Credential Manager sign-in followed by a Drive-scope authorization request
 * (see AccountViewModel / GoogleAccountManager). OneDrive, Epic Sandbox, and
 * Family Sharing are still UI shells -- tapping them explains what's coming
 * rather than starting a real flow, since their API credentials (Azure,
 * Epic App Orchard) haven't been set up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onGoHome: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val viewModel: AccountViewModel = viewModel(factory = AccountViewModel.factory(context))

    val googleAccount by viewModel.googleAccount.collectAsState()
    val connectState by viewModel.connectState.collectAsState()
    val pendingResolution by viewModel.pendingResolution.collectAsState()

    var dialogFor by remember { mutableStateOf<AccountLinkRow?>(null) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var showOneDriveComingSoon by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        activity?.let { viewModel.onDriveConsentResult(it, result.resultCode, result.data) }
    }

    // When the ViewModel gets a "Play Services needs to show its own consent
    // screen" outcome, launch it here -- ActivityResultLauncher can only be
    // used from a composable, not from inside the ViewModel.
    LaunchedEffect(pendingResolution) {
        pendingResolution?.let { intentSender ->
            consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = "Account",
                onGoHome = onGoHome
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { AccountSectionHeader("Cloud Storage Accounts") }
            item {
                SettingsGroup {
                    if (connectState is GoogleConnectState.SigningIn || connectState is GoogleConnectState.RequestingDriveAccess) {
                        GoogleDriveConnectingRow(connectState)
                    } else if (googleAccount?.driveConnected == true) {
                        SettingsRow(
                            icon = Icons.Default.CloudQueue,
                            title = "Google Drive",
                            subtitle = "Connected as ${googleAccount?.displayName ?: googleAccount?.email}",
                            onClick = { showDisconnectConfirm = true }
                        )
                    } else {
                        SettingsRow(
                            icon = Icons.Default.CloudQueue,
                            title = "Google Drive",
                            subtitle = "Not connected -- tap to sign in",
                            onClick = { activity?.let { viewModel.connectGoogleDrive(it) } }
                        )
                    }
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.CloudQueue,
                        title = "OneDrive",
                        subtitle = "Not connected",
                        onClick = { showOneDriveComingSoon = true }
                    )
                }
            }

            item { AccountSectionHeader("Health System Connections") }
            item {
                SettingsGroup {
                    healthSystemRows.forEachIndexed { index, row ->
                        SettingsRow(
                            icon = row.icon,
                            title = row.title,
                            subtitle = "Not connected",
                            onClick = { dialogFor = row }
                        )
                        if (index != healthSystemRows.lastIndex) SettingsDivider()
                    }
                }
            }

            item { AccountSectionHeader("Family Sharing") }
            item {
                SettingsGroup {
                    familySharingRows.forEachIndexed { index, row ->
                        SettingsRow(
                            icon = row.icon,
                            title = row.title,
                            subtitle = "Off",
                            onClick = { dialogFor = row }
                        )
                        if (index != familySharingRows.lastIndex) SettingsDivider()
                    }
                }
            }
        }
    }

    dialogFor?.let { row ->
        ComingSoonDialog(title = row.title, message = row.comingSoonMessage, onDismiss = { dialogFor = null })
    }

    if (showOneDriveComingSoon) {
        ComingSoonDialog(
            title = "OneDrive",
            message = "Signing in to OneDrive is coming in a future update. Once connected, MedRecall+ will use it for backups and syncing across devices.",
            onDismiss = { showOneDriveComingSoon = false }
        )
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Disconnect Google Drive?") },
            text = { Text("MedRecall+ will no longer have access to your Google Drive. You can reconnect at any time.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.disconnectGoogleDrive(context)
                    showDisconnectConfirm = false
                }) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val errorState = connectState
    if (errorState is GoogleConnectState.Error) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Google Drive") },
            text = { Text(errorState.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun GoogleDriveConnectingRow(state: GoogleConnectState) {
    val label = when (state) {
        is GoogleConnectState.SigningIn -> "Signing in..."
        is GoogleConnectState.RequestingDriveAccess -> "Requesting Drive access..."
        else -> "Working..."
    }
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text = label, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AccountSectionHeader(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge)
}

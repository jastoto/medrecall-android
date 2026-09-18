package com.asok.medrecall.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.calendar.GoogleCalendarInfo
import com.asok.medrecall.ui.components.BackIconButton
import com.asok.medrecall.ui.components.MedRecallTopBar

private data class AccountLinkRow(val icon: ImageVector, val title: String, val comingSoonMessage: String)

private val healthSystemRows = listOf(
    AccountLinkRow(Icons.Default.LocalHospital, "Epic Sandbox", "Connecting to Epic's patient-portal sandbox is coming in a future update. Once connected, MedRecall+ will be able to pull records directly from your health system.")
)

/**
 * Settings > Account. The Calendar section connects a Google account and
 * lets Asok pick which of his calendars MedRecall+ should push Appointments
 * to (see AccountViewModel / data/calendar/) -- replaces the old "Cloud
 * Storage Accounts" placeholder rows, since real Google Drive/OneDrive
 * sign-in now lives under Backup & Restore instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onGoHome: () -> Unit,
    onBack: () -> Unit,
    viewModel: AccountViewModel = viewModel(factory = AccountViewModel.factory(LocalContext.current))
) {
    var dialogFor by remember { mutableStateOf<AccountLinkRow?>(null) }
    val uiState by viewModel.uiState.collectAsState()

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.onGoogleSignInResult(result.data) }

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = "Account",
                onGoHome = onGoHome,
                actions = { BackIconButton(onClick = onBack) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { AccountSectionHeader("Calendar") }
            item {
                CalendarSection(
                    uiState = uiState,
                    onSignIn = { googleSignInLauncher.launch(viewModel.googleSignInClient.signInIntent) },
                    onSelectCalendar = { viewModel.selectCalendar(it) },
                    onChangeCalendar = { viewModel.refreshCalendars() },
                    onSignOut = { viewModel.signOut() }
                )
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
        }
    }

    dialogFor?.let { row ->
        ComingSoonDialog(title = row.title, message = row.comingSoonMessage, onDismiss = { dialogFor = null })
    }
}

@Composable
private fun CalendarSection(
    uiState: AccountUiState,
    onSignIn: () -> Unit,
    onSelectCalendar: (GoogleCalendarInfo) -> Unit,
    onChangeCalendar: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            uiState.signedInLabel == null -> {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.CalendarMonth,
                        title = "Sign in to Google Calendar",
                        showChevron = false,
                        onClick = onSignIn
                    )
                }
            }
            uiState.selectedCalendarId == null -> {
                SettingsGroup {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("Signed in as ${uiState.signedInLabel}. Choose a calendar to sync appointments to:")
                    }
                    SettingsDivider()
                    uiState.availableCalendars.forEachIndexed { index, calendar ->
                        SettingsRow(
                            icon = Icons.Default.CalendarMonth,
                            title = calendar.name,
                            subtitle = if (calendar.isPrimary) "Primary" else null,
                            showChevron = false,
                            onClick = { onSelectCalendar(calendar) }
                        )
                        if (index != uiState.availableCalendars.lastIndex) SettingsDivider()
                    }
                }
            }
            else -> {
                SettingsGroup {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text("Syncing to ${uiState.selectedCalendarName}")
                            Text(
                                uiState.signedInLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    SettingsDivider()
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        TextButton(onClick = onChangeCalendar) { Text("Change Calendar") }
                        TextButton(onClick = onSignOut) { Text("Sign Out") }
                    }
                }
            }
        }

        if (uiState.isBusy) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        uiState.statusMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        uiState.errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AccountSectionHeader(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge)
}

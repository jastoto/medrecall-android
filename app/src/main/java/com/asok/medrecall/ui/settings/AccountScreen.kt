package com.asok.medrecall.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.asok.medrecall.ui.components.MedRecallTopBar

private data class AccountLinkRow(val icon: ImageVector, val title: String, val comingSoonMessage: String)

private val cloudStorageRows = listOf(
    AccountLinkRow(Icons.Default.CloudQueue, "Google Drive", "Signing in to Google Drive is coming in a future update. Once connected, MedRecall+ will use it for backups and syncing across devices."),
    AccountLinkRow(Icons.Default.CloudQueue, "OneDrive", "Signing in to OneDrive is coming in a future update. Once connected, MedRecall+ will use it for backups and syncing across devices.")
)

private val healthSystemRows = listOf(
    AccountLinkRow(Icons.Default.LocalHospital, "Epic Sandbox", "Connecting to Epic's patient-portal sandbox is coming in a future update. Once connected, MedRecall+ will be able to pull records directly from your health system.")
)

private val familySharingRows = listOf(
    AccountLinkRow(Icons.Default.FamilyRestroom, "Share Reports with Family", "Family sharing is coming in a future update. Once available, you'll be able to give family members view access to your reports.")
)

/**
 * Settings > Account. Three groups: cloud storage accounts (Google
 * Drive/OneDrive), health system connections (Epic Sandbox), and family
 * sharing. Every row here is a UI shell for now -- tapping one explains
 * what it will do rather than starting a real sign-in flow, since real
 * OAuth needs API credentials (Google Cloud, Azure, Epic App Orchard)
 * Asok hasn't set up yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onGoHome: () -> Unit) {
    var dialogFor by remember { mutableStateOf<AccountLinkRow?>(null) }

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
                    cloudStorageRows.forEachIndexed { index, row ->
                        SettingsRow(
                            icon = row.icon,
                            title = row.title,
                            subtitle = "Not connected",
                            onClick = { dialogFor = row }
                        )
                        if (index != cloudStorageRows.lastIndex) SettingsDivider()
                    }
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
}

@Composable
private fun AccountSectionHeader(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge)
}

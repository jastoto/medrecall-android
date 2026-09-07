package com.asok.medrecall.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.asok.medrecall.ui.components.MedRecallTopBar

private enum class BackupDestination(val icon: ImageVector, val label: String) {
    GOOGLE_DRIVE(Icons.Default.CloudQueue, "Google Drive"),
    ONE_DRIVE(Icons.Default.CloudQueue, "OneDrive"),
    THIS_PHONE(Icons.Default.PhoneAndroid, "Files on This Phone")
}

/**
 * Settings > Backup & Restore. Lets Asok pick a destination, then Back Up
 * or Restore. Every destination is a UI shell today (see AccountScreen's
 * doc comment for why) -- both buttons show a "coming soon" explanation
 * instead of actually moving data, since a real local-file backup would
 * still need a matching restore/import path built and tested end to end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(onGoHome: () -> Unit) {
    var selected by remember { mutableStateOf(BackupDestination.THIS_PHONE) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

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
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            dialogMessage = "Backing up to ${selected.label} is coming in a future update."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Text(text = "  Back Up Now")
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

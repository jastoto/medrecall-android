package com.asok.medrecall.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asok.medrecall.data.calendar.DeviceCalendarInfo
import com.asok.medrecall.data.settings.SyncCalendarSelection

/**
 * Lists every calendar already set up on the phone (Google, Office/Exchange,
 * or a local-only calendar), grouped by account, plus an "Off" option.
 * MedRecall syncs appointments to exactly one at a time -- picking a
 * different one than [current] is handled by the caller (SettingsScreen),
 * which shows a confirmation before actually switching.
 */
@Composable
fun CalendarPickerDialog(
    calendars: List<DeviceCalendarInfo>,
    current: SyncCalendarSelection?,
    onSelect: (DeviceCalendarInfo?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync Appointments To") },
        text = {
            if (calendars.isEmpty()) {
                Text("No calendars found on this device. Add a Google or Office/Exchange account in your phone's Settings > Accounts, or add a local calendar in your Calendar app, then try again.")
            } else {
                LazyColumn {
                    item {
                        CalendarOptionRow(
                            label = "Off",
                            subtitle = "Don't sync appointments to any calendar",
                            selected = current == null,
                            onClick = { onSelect(null) }
                        )
                    }
                    items(calendars.groupBy { it.providerLabel }.entries.toList()) { entry ->
                        val provider = entry.key
                        val group = entry.value
                        Column {
                            Text(
                                text = provider,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                            group.forEach { calendar ->
                                CalendarOptionRow(
                                    label = calendar.displayName,
                                    subtitle = calendar.accountName,
                                    selected = current?.calendarId == calendar.id,
                                    onClick = { onSelect(calendar) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun CalendarOptionRow(label: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

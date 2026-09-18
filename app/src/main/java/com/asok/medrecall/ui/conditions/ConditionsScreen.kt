package com.asok.medrecall.ui.conditions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.ui.components.MedRecallTopBar
import com.asok.medrecall.ui.components.SwipeToDeleteRow
import kotlinx.coroutines.launch

private val tileColors = listOf(
    Color(0xFFE0435A), // red
    Color(0xFF6C63E8), // indigo/purple
    Color(0xFFFF9F43), // orange
    Color(0xFF2BB3A3), // teal
    Color(0xFF4CAF50), // green
    Color(0xFFAB47BC)  // purple
)

private fun colorForCondition(conditionId: Int): Color =
    tileColors[Math.floorMod(conditionId, tileColors.size)]

// Anything with an unrecognized/old status string falls back to "Active"
// so it never silently disappears from the list.
private val statusOrder = listOf("Active", "Monitoring", "Resolved")

private fun effectiveStatus(condition: Condition): String =
    if (condition.status in statusOrder) condition.status else "Active"

private data class ConditionGroupData(
    val header: String,
    val conditions: List<Condition>,
    val rowSubtitle: (Condition) -> String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionsScreen(
    onAddCondition: () -> Unit,
    onEditCondition: (Int) -> Unit,
    onGoHome: () -> Unit,
    viewModel: ConditionsViewModel = viewModel(factory = ConditionsViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Ids swiped-away but not yet actually deleted from the database --
    // filtered out of the visible list immediately so the swipe feels
    // instant, then either restored (Undo tapped) or committed to the
    // repository once the Undo snackbar times out.
    var pendingDeleteIds by remember { mutableStateOf(setOf<Int>()) }

    fun deleteWithUndo(condition: Condition) {
        pendingDeleteIds = pendingDeleteIds + condition.id
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Deleted ${condition.name}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                pendingDeleteIds = pendingDeleteIds - condition.id
            } else {
                viewModel.deleteCondition(condition)
                pendingDeleteIds = pendingDeleteIds - condition.id
            }
        }
    }

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = "Conditions",
                onGoHome = onGoHome,
                actions = {
                    IconButton(onClick = onAddCondition) {
                        Icon(Icons.Default.Add, contentDescription = "Add condition")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val visibleConditions = uiState.conditions.filter { it.id !in pendingDeleteIds }

        if (visibleConditions.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No conditions yet. Tap + to add one.")
            }
        } else {
            val doctorsById = uiState.doctors.associateBy { it.id }

            fun doctorSubtitle(condition: Condition): String? =
                condition.doctorId?.let { doctorsById[it]?.name }

            // Back to plain status headers for every status, including
            // Active -- doctor is shown per-row as a subtitle instead of
            // being the grouping key, so a doctor's conditions don't get
            // scattered depending on status but conditions also don't get
            // buried inside a doctor (or "No Doctor Assigned") bucket.
            val allGroups = statusOrder.mapNotNull { status ->
                val conditionsForStatus = visibleConditions
                    .filter { effectiveStatus(it) == status }
                    .sortedBy { it.name }
                if (conditionsForStatus.isEmpty()) {
                    null
                } else {
                    ConditionGroupData(status, conditionsForStatus, ::doctorSubtitle)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                allGroups.forEachIndexed { index, group ->
                    item(key = "group_${group.header}_$index") {
                        ConditionGroup(
                            group = group,
                            onEditCondition = onEditCondition,
                            onDeleteCondition = ::deleteWithUndo
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionGroup(
    group: ConditionGroupData,
    onEditCondition: (Int) -> Unit,
    onDeleteCondition: (Condition) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section heading (Active / Monitoring / Resolved) is a plain
        // heading above the card, not part of the bordered box.
        Text(
            text = group.header,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            val rowBackgroundColor = CardDefaults.cardColors().containerColor
            Column {
                group.conditions.forEachIndexed { index, condition ->
                    SwipeToDeleteRow(contentBackgroundColor = rowBackgroundColor,
                        onDelete = { onDeleteCondition(condition) }) {
                        ConditionRow(
                            condition = condition,
                            subtitle = group.rowSubtitle(condition),
                            onClick = { onEditCondition(condition.id) }
                        )
                    }
                    if (index < group.conditions.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionRow(condition: Condition, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colorForCondition(condition.id), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                iconForKey(condition.iconKey),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = condition.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

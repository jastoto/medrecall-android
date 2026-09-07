package com.asok.medrecall.ui.conditions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.Condition

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

// Section display order. Anything with an unrecognized/old status string
// falls back into "Active" so it never silently disappears from the list.
private val statusOrder = listOf("Active", "Monitoring", "Resolved")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionsScreen(
    onAddCondition: () -> Unit,
    onEditCondition: (Int) -> Unit,
    onGoHome: () -> Unit,
    viewModel: ConditionsViewModel = viewModel(factory = ConditionsViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conditions") },
                navigationIcon = {
                    IconButton(onClick = onGoHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                },
                actions = {
                    IconButton(onClick = onAddCondition) {
                        Icon(Icons.Default.Add, contentDescription = "Add condition")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.conditions.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No conditions yet. Tap + to add one.")
            }
        } else {
            val grouped = uiState.conditions.groupBy { condition ->
                if (condition.status in statusOrder) condition.status else "Active"
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statusOrder.forEach { status ->
                    val conditionsForStatus = grouped[status]?.sortedBy { it.name } ?: emptyList()
                    if (conditionsForStatus.isNotEmpty()) {
                        item(key = "header_$status") {
                            Text(
                                status,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(
                            conditionsForStatus.chunked(4),
                            key = { row -> status + "_" + row.first().id }
                        ) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { condition ->
                                    ConditionTile(
                                        condition = condition,
                                        onClick = { onEditCondition(condition.id) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(4 - row.size) {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionTile(condition: Condition, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(colorForCondition(condition.id), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                iconForKey(condition.iconKey),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            condition.name,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

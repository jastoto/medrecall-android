package com.asok.medrecall.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asok.medrecall.ui.components.RaisedIconTile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onSelectReport: (ReportType) -> Unit, onGoHome: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = {
                    IconButton(onClick = onGoHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(reportsGrid) { reportType ->
                RaisedIconTile(
                    icon = reportType.icon,
                    label = reportType.title,
                    gradientColors = reportType.tileColors,
                    onClick = { onSelectReport(reportType) }
                )
            }
        }
    }
}

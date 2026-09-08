package com.asok.medrecall.ui.ask

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.search.SearchCategory
import com.asok.medrecall.data.search.SearchResult
import com.asok.medrecall.navigation.Destination
import com.asok.medrecall.ui.components.MedRecallTopBar

/**
 * Friendly copy shown (via Snackbar) whenever a submitted search comes back
 * with nothing -- reworded from Asok's spec into the app's normal tone.
 */
private const val NO_RESULTS_MESSAGE =
    "Your search did not return any results. Try adjusting your search terms and search again."

// Display order + the home-grid Destination each category borrows its icon
// and color from, so a result visually matches the tile it came from.
private val categoryOrder = listOf(
    SearchCategory.DOCTOR to Destination.Doctors,
    SearchCategory.MEDICATION to Destination.Medications,
    SearchCategory.CONDITION to Destination.Conditions,
    SearchCategory.VITAL to Destination.Vitals,
    SearchCategory.APPOINTMENT to Destination.Calendar,
    SearchCategory.NOTE to Destination.RecordVisit,
    SearchCategory.MEDICAL_ID to Destination.MedicalId
)

/**
 * "Look up one or more words across the app" -- searches doctors,
 * medications, conditions, vitals, appointments, visit notes, and Medical
 * ID all from one search box. Tapping a result jumps straight to that
 * record's existing screen ([onNavigateToRoute]); Visit Notes have no
 * dedicated screen of their own yet, so those open a read-only dialog here
 * instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskMedRecallScreen(
    onGoHome: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    viewModel: AskMedRecallViewModel = viewModel(factory = AskMedRecallViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    var noteBeingViewed by remember { mutableStateOf<SearchResult?>(null) }

    LaunchedEffect(uiState.hasSearched, uiState.isSearching, uiState.results.size) {
        if (uiState.hasSearched && !uiState.isSearching && uiState.results.isEmpty()) {
            snackbarHostState.showSnackbar(NO_RESULTS_MESSAGE)
        }
    }

    Scaffold(
        topBar = { MedRecallTopBar(title = "Ask MedRecall", onGoHome = onGoHome) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search doctors, meds, conditions, vitals…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                    viewModel.runSearch()
                })
            )

            when {
                uiState.isSearching -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                !uiState.hasSearched -> AskMedRecallIntro()

                uiState.results.isEmpty() -> AskMedRecallEmptyResults()

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    categoryOrder.forEach { (category, destination) ->
                        val matches = uiState.results.filter { it.category == category }
                        if (matches.isNotEmpty()) {
                            item(key = "header_${category.name}") {
                                Text(
                                    category.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(matches, key = { it.id }) { result ->
                                SearchResultRow(
                                    result = result,
                                    icon = destination.icon,
                                    color = destination.tileColors.first(),
                                    onClick = {
                                        if (result.route != null) {
                                            onNavigateToRoute(result.route)
                                        } else {
                                            noteBeingViewed = result
                                        }
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    noteBeingViewed?.let { note ->
        AlertDialog(
            onDismissRequest = { noteBeingViewed = null },
            confirmButton = {
                TextButton(onClick = { noteBeingViewed = null }) { Text("Close") }
            },
            title = { Text(note.title) },
            text = { Text(note.detailBody ?: "") }
        )
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResult,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(result.title, fontWeight = FontWeight.SemiBold, color = Color.Black)
            if (result.subtitle.isNotBlank()) {
                Text(
                    result.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AskMedRecallIntro() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = Destination.AskMedRecall.tileColors.first(),
            modifier = Modifier.size(56.dp)
        )
        Text(
            "Ask MedRecall",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            "Search across your doctors, medications, conditions, vitals, appointments, visit notes, and Medical ID.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun AskMedRecallEmptyResults() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Text(
            NO_RESULTS_MESSAGE,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

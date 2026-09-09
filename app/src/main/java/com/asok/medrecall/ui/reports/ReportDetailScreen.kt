package com.asok.medrecall.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.asok.medrecall.ui.components.MedRecallTopBar

/**
 * Generic detail screen reused for every report type: a preview tile +
 * description, a "Generate Report" button, then -- once a PDF exists in
 * this screen's state -- "View / Print" and "Share" actions plus a
 * Regenerate link. See ReportDetailViewModel for the generate() flow and
 * ReportActions.kt for what View/Print and Share actually do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    reportType: ReportType,
    onGoHome: () -> Unit,
    onGoBack: () -> Unit,
    viewModel: ReportDetailViewModel = viewModel(
        factory = ReportDetailViewModel.factory(LocalContext.current, reportType)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentFile = uiState.generatedFile

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = reportType.title,
                onGoHome = onGoHome,
                onGoBack = onGoBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(reportType.tileColors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = reportType.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = reportType.description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (currentFile == null) {
                Button(
                    onClick = { viewModel.generate() },
                    enabled = !uiState.isGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Description, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (uiState.isGenerating) "Generating…" else "Generate Report")
                }
            } else {
                val generatedLabel = uiState.generatedAt?.let {
                    "Ready — generated " + SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(it))
                } ?: "Report ready"
                Text(
                    text = generatedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { openPrintPreview(context, currentFile, "${reportType.title} - MedRecall+") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View / Print")
                    }
                    Button(
                        onClick = { sharePdf(context, currentFile, "${reportType.title} - MedRecall+") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { viewModel.regenerate() }) {
                    Text("Regenerate")
                }
            }

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

package com.asok.medrecall.ui.help

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asok.medrecall.ui.components.MedRecallTopBar
import com.asok.medrecall.ui.components.BackIconButton

/**
 * The step-by-step page for one [HelpTopic]: a colored icon badge, the
 * summary line, an amber "Heads Up" callout for anything not fully wired
 * up yet, then numbered "Setting It Up" and "How to Use It" sections
 * (either is skipped when the topic has no steps for it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpDetailScreen(topic: HelpTopic, onGoHome: () -> Unit, onBackToList: () -> Unit) {
    Scaffold(
        topBar = {
            MedRecallTopBar(title = topic.title, onGoHome = onGoHome) {
                BackIconButton(onClick = onBackToList)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(topic.tint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(topic.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Text(
                        text = topic.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
            topic.comingSoonNote?.let { note ->
                item { HeadsUpCallout(note) }
            }
            if (topic.setupSteps.isNotEmpty()) {
                item { SectionHeader("Setting It Up") }
                itemsIndexed(topic.setupSteps) { index, step ->
                    StepRow(number = index + 1, text = step)
                }
            }
            if (topic.useSteps.isNotEmpty()) {
                item { SectionHeader("How to Use It") }
                itemsIndexed(topic.useSteps) { index, step ->
                    StepRow(number = index + 1, text = step)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HeadsUpCallout(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFF8A6D3B),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B5424),
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

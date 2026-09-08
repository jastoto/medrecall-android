package com.asok.medrecall.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asok.medrecall.ui.components.MedRecallTopBar
import com.asok.medrecall.ui.settings.SettingsDivider
import com.asok.medrecall.ui.settings.SettingsGroup
import com.asok.medrecall.ui.settings.SettingsRow

/**
 * Help & How-To: one scrolling list, one entry per task MedRecall+ can
 * do (see [HelpTopic] / [helpTopics]). Each row shows a one-line summary
 * and a chevron into [HelpDetailScreen] for the full setup/use steps.
 * Reuses Settings' own grouped-row components (SettingsGroup/SettingsRow/
 * SettingsDivider) instead of inventing a new list style, so this screen
 * matches the rest of the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onGoHome: () -> Unit, onSelectTopic: (HelpTopic) -> Unit) {
    Scaffold(
        topBar = { MedRecallTopBar(title = "Help", onGoHome = onGoHome) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                SettingsGroup {
                    helpTopics.forEachIndexed { index, topic ->
                        SettingsRow(
                            icon = topic.icon,
                            title = topic.title,
                            subtitle = topic.summary,
                            tint = topic.tint,
                            onClick = { onSelectTopic(topic) }
                        )
                        if (index != helpTopics.lastIndex) SettingsDivider()
                    }
                }
            }
        }
    }
}

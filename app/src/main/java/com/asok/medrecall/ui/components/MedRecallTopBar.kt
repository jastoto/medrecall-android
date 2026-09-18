package com.asok.medrecall.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * The standard top bar for every non-Home screen: a centered, bold
 * title, and -- when [onGoHome] is given -- the same raised 3D Home
 * button used everywhere else in the app (see [HomeIconButton]). Pass
 * [actions] exactly as you would to a plain TopAppBar for a screen's
 * own trailing icons (Add, Edit, etc.).
 *
 * Title color is intentionally left to inherit CenterAlignedTopAppBar's
 * own content color rather than being hardcoded -- a hardcoded
 * Color.Black here was invisible against the app bar's background in
 * dark mode (same class of bug as the home-screen tile labels).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedRecallTopBar(
    title: String,
    onGoHome: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (onGoHome != null) {
                HomeIconButton(onClick = onGoHome)
            }
        },
        actions = actions
    )
}

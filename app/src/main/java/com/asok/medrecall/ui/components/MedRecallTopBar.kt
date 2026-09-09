package com.asok.medrecall.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * The standard top bar for every non-Home screen: a centered, bold,
 * black title, and -- when [onGoHome] is given -- the same raised 3D
 * Home button used everywhere else in the app (see [HomeIconButton]).
 * Pass [onGoBack] too (punch item #10, repositioned punch item #24) to
 * also show the orange BackIconButton at the FAR RIGHT of the top bar
 * (in the trailing/actions area, after whatever the screen passes via
 * [actions]) -- for a screen reached by pushing onto the nav stack
 * (e.g. a report's detail screen reached from the Reports list) where
 * a one-step-back option is wanted alongside Home. Home always stays
 * in its usual leftmost spot, consistent with every other screen.
 * Pass [actions] exactly as you would to a plain TopAppBar for a
 * screen's own trailing icons (Add, Edit, etc.) -- Back renders after
 * them, furthest right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedRecallTopBar(
    title: String,
    onGoHome: (() -> Unit)? = null,
    onGoBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (onGoHome != null) {
                HomeIconButton(onClick = onGoHome)
            }
        },
        actions = {
            actions()
            if (onGoBack != null) {
                BackIconButton(onClick = onGoBack)
            }
        }
    )
}

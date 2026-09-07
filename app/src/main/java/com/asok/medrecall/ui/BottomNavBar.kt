package com.asok.medrecall.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.asok.medrecall.navigation.bottomNavDestinations
import com.asok.medrecall.ui.components.RaisedIconTile

private val TILE_SIZE = 52.dp
private val ICON_SIZE = 22.dp
private val CORNER_RADIUS = 14.dp
private val ELEVATION = 8.dp

/**
 * The app's bottom row -- Ask MedRecall, Help & How-To, Recurring
 * Reminders, Settings -- each rendered as the same raised 3D icon button
 * used everywhere else in the app (see RaisedIconTile) rather than a
 * plain Material NavigationBar, evenly spaced across a single row.
 */
@Composable
fun MedRecallBottomBar(navController: NavHostController) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            bottomNavDestinations.forEach { destination ->
                RaisedIconTile(
                    icon = destination.icon,
                    label = destination.label,
                    gradientColors = destination.tileColors,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    iconSize = ICON_SIZE,
                    cornerRadius = CORNER_RADIUS,
                    tileSize = TILE_SIZE,
                    elevation = ELEVATION,
                    labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                    labelMaxLines = 2,
                    labelTopPadding = 6.dp
                )
            }
        }
    }
}

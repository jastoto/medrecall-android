package com.asok.medrecall.ui.vitals

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.asok.medrecall.data.health.HealthConnectManager
import com.asok.medrecall.ui.components.RaisedIconTile
import kotlinx.coroutines.launch

/**
 * Matches Asok's iOS Vitals screenshot: a compact grid of the 9 vitals we
 * track (see VitalType.kt) followed by a bordered "FOR USE WITH A WATCH"
 * section for the 2 that aren't standard Health Connect data types (ECG,
 * Falls). Uses the app's standard RaisedIconTile 3D look, just sized down
 * (tileSize/elevation/labelStyle) so all of it fits on screen without
 * scrolling -- this screen packs more tiles than anywhere else in the app.
 * A slim banner offers to install/connect Health Connect; manual entry
 * keeps working either way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VitalsScreen(onSelectVital: (VitalType) -> Unit, onGoHome: () -> Unit) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var healthConnectStatus by remember { mutableStateOf(HealthConnectStatus.CHECKING) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        healthConnectStatus = if (granted.containsAll(HealthConnectManager.allReadPermissions)) {
            HealthConnectStatus.CONNECTED
        } else {
            HealthConnectStatus.NEEDS_PERMISSION
        }
    }

    LaunchedEffect(Unit) {
        healthConnectStatus = when {
            !HealthConnectManager.isAvailable(context) -> HealthConnectStatus.NOT_INSTALLED
            HealthConnectManager.hasAllPermissions(HealthConnectManager.getClient(context)) -> HealthConnectStatus.CONNECTED
            else -> HealthConnectStatus.NEEDS_PERMISSION
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vitals") },
                navigationIcon = {
                    IconButton(onClick = onGoHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            HealthConnectBanner(
                status = healthConnectStatus,
                onConnect = { permissionLauncher.launch(HealthConnectManager.allReadPermissions) },
                onInstall = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
                        )
                    } catch (e: Exception) {
                        coroutineScope.launch { snackbarHostState.showSnackbar("Couldn't open the Play Store.") }
                    }
                }
            )

            trackedVitals.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { vital ->
                        RaisedIconTile(
                            icon = vital.icon,
                            label = vital.title,
                            gradientColors = vital.tileColors,
                            onClick = { onSelectVital(vital) },
                            modifier = Modifier.weight(1f),
                            iconSize = COMPACT_ICON_SIZE,
                            cornerRadius = COMPACT_CORNER_RADIUS,
                            tileSize = COMPACT_TILE_SIZE,
                            elevation = COMPACT_ELEVATION,
                            labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                            labelMaxLines = 2,
                            labelTopPadding = 6.dp
                        )
                    }
                    repeat(3 - row.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }

            Text(
                "FOR USE WITH A WATCH",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                watchOnlyVitals.forEach { watchVital ->
                    RaisedIconTile(
                        icon = watchVital.icon,
                        label = watchVital.title,
                        gradientColors = watchTileColors,
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "${watchVital.title} isn't a standard Health Connect data type — nothing to connect yet"
                                )
                            }
                        },
                        modifier = Modifier.weight(1f).alpha(0.7f),
                        iconSize = COMPACT_ICON_SIZE,
                        cornerRadius = COMPACT_CORNER_RADIUS,
                        tileSize = COMPACT_TILE_SIZE,
                        elevation = COMPACT_ELEVATION,
                        labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                        labelMaxLines = 2,
                        labelTopPadding = 6.dp
                    )
                }
                repeat(3 - watchOnlyVitals.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HealthConnectBanner(status: HealthConnectStatus, onConnect: () -> Unit, onInstall: () -> Unit) {
    if (status == HealthConnectStatus.CONNECTED || status == HealthConnectStatus.CHECKING) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            if (status == HealthConnectStatus.NOT_INSTALLED) "Health Connect isn't installed"
            else "Connect Health Connect for automatic readings",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        TextButton(onClick = if (status == HealthConnectStatus.NOT_INSTALLED) onInstall else onConnect) {
            Text(if (status == HealthConnectStatus.NOT_INSTALLED) "Install" else "Connect")
        }
    }
}

// Compact sizing for RaisedIconTile on this screen only -- see the tileSize
// doc note on RaisedIconTile itself for why. Home screen and everywhere
// else keeps the default (larger, width-driven) sizing.
private val COMPACT_TILE_SIZE = 52.dp
private val COMPACT_ICON_SIZE = 22.dp
private val COMPACT_CORNER_RADIUS = 14.dp
private val COMPACT_ELEVATION = 8.dp

private val watchTileColors = listOf(Color(0xFFBDBDBD), Color(0xFF8A8A8A))

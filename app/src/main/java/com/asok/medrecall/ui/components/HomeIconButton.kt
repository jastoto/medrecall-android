package com.asok.medrecall.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asok.medrecall.navigation.Destination

/**
 * The small, round version of [RaisedIconSurface] used as every screen's
 * top-bar Home button, so Home always has the exact same domed, glossy,
 * embossed 3D look as every other icon in the app -- just scaled down to
 * fit a TopAppBar's navigationIcon slot.
 */
@Composable
fun HomeIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    RaisedIconSurface(
        icon = Icons.Default.Home,
        contentDescription = "Home",
        gradientColors = Destination.Home.tileColors,
        onClick = onClick,
        modifier = modifier.padding(start = 12.dp),
        iconSize = 18.dp,
        cornerRadius = 17.dp,
        tileSize = 34.dp,
        elevation = 6.dp
    )
}

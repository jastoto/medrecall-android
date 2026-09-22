package com.asok.medrecall.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import com.asok.medrecall.navigation.Destination
import com.asok.medrecall.navigation.homeGridDestinations
import com.asok.medrecall.ui.components.RaisedIconTile

@Composable
fun HomeScreen(onDestinationClick: (Destination) -> Unit) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Portrait is untouched: still exactly 3 fixed columns with tiles
    // stretching to fill the column width (tileSize = null), same as before
    // this fix -- nothing about portrait's look changes.
    //
    // Landscape only: instead of letting those same 3 columns stretch across
    // the much wider landscape width (that's what made icons look huge),
    // compute the tile size the phone's OWN portrait width would produce --
    // screenWidthDp/screenHeightDp swap when you rotate, so the smaller of
    // the two is always the portrait-orientation width -- and use that fixed
    // size with more columns (via Adaptive) to fill the landscape width.
    // Net effect: landscape tiles are the same physical size as this same
    // phone's portrait tiles, just more of them per row.
    val landscapeTileSize = if (isLandscape) {
        val portraitWidthDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
        val portraitTileSize = (portraitWidthDp - 64) / 3
        // Asok's call: landscape tiles ~15% smaller than portrait, then another
        // 15% off that (0.85 * 0.85 = ~0.7225) -- roughly 28% smaller than
        // portrait overall. Also nets more columns per row via the Adaptive
        // grid below, since smaller tiles mean more fit across the width.
        (portraitTileSize * 0.7225f).dp
    } else {
        null
    }

    LazyVerticalGrid(
        columns = if (isLandscape) {
            GridCells.Adaptive(minSize = (landscapeTileSize ?: 104.dp) + 16.dp)
        } else {
            GridCells.Fixed(3)
        },
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(homeGridDestinations) { destination ->
            RaisedIconTile(
                icon = destination.icon,
                label = destination.label,
                gradientColors = destination.tileColors,
                onClick = { onDestinationClick(destination) },
                tileSize = landscapeTileSize
            )
        }
    }
}

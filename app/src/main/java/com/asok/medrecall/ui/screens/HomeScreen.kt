package com.asok.medrecall.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asok.medrecall.navigation.Destination
import com.asok.medrecall.navigation.homeGridDestinations
import com.asok.medrecall.ui.components.RaisedIconTile

@Composable
fun HomeScreen(onDestinationClick: (Destination) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(homeGridDestinations) { destination ->
            RaisedIconTile(
                icon = destination.icon,
                label = destination.label,
                gradientColors = destination.tileColors,
                onClick = { onDestinationClick(destination) }
            )
        }
    }
}

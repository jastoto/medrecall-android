package com.asok.medrecall.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The small, round version of [RaisedIconSurface] used for a screen's
 * top-bar "back" action -- the exact same domed/glossy/embossed 3D look
 * as [HomeIconButton], just in orange instead of Home's purple, so it
 * reads as its own distinct action rather than a second Home button.
 */
@Composable
fun BackIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    RaisedIconSurface(
        icon = Icons.Default.ArrowBack,
        contentDescription = "Back",
        gradientColors = listOf(Color(0xFFFF9D5C), Color(0xFFE87F2E)),
        onClick = onClick,
        modifier = modifier.padding(end = 12.dp),
        iconSize = 18.dp,
        cornerRadius = 17.dp,
        tileSize = 34.dp,
        elevation = 6.dp
    )
}

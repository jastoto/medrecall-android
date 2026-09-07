package com.asok.medrecall.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * MedRecall's shared "3D raised button" style: [RaisedIconSurface] (the
 * domed/shadowed/embossed icon look) plus a bold-black label centered
 * below it. This is the standard button look for the app: reuse this
 * composable for any new icon-tile button instead of a flat Card/Button.
 *
 * By default the tile is a square sized off the available width (the
 * Home-screen grid look). Pass `tileSize` for a small fixed-size tile
 * instead -- e.g. the Vitals grid, which packs far more tiles on one
 * screen and needs the same 3D look at a much smaller footprint; pass a
 * smaller `elevation` alongside it so the shadow stays proportionate
 * (an 18dp shadow under a 52dp tile looks like a blur, not a lift).
 */
@Composable
fun RaisedIconTile(
    icon: ImageVector,
    label: String,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 38.dp,
    cornerRadius: Dp = 28.dp,
    tileSize: Dp? = null,
    elevation: Dp = 18.dp,
    labelStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    labelColor: Color = Color.Black,
    labelMaxLines: Int = 1,
    labelTopPadding: Dp = 8.dp
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RaisedIconSurface(
            icon = icon,
            contentDescription = label,
            gradientColors = gradientColors,
            onClick = onClick,
            iconSize = iconSize,
            cornerRadius = cornerRadius,
            tileSize = tileSize,
            elevation = elevation
        )
        Text(
            text = label,
            style = labelStyle,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = labelMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = labelTopPadding)
        )
    }
}

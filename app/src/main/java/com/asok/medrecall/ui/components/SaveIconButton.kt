package com.asok.medrecall.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The small, round version of [RaisedIconSurface] used for a form's
 * top-bar "save" action -- the same domed/glossy/embossed 3D look as
 * [BackIconButton] and [HomeIconButton], in green so it reads as a
 * distinct, positive action rather than a second Back/Cancel button.
 *
 * [enabled] mirrors the old Save button's disabled-until-valid behavior
 * (e.g. a blank required name field) -- the icon dims and stops
 * responding to taps rather than disappearing, so its position on
 * screen never shifts.
 */
@Composable
fun SaveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    RaisedIconSurface(
        icon = Icons.Default.Check,
        contentDescription = "Save",
        gradientColors = listOf(Color(0xFF6FDB84), Color(0xFF2E9E4F)),
        onClick = onClick,
        modifier = modifier.padding(end = 12.dp),
        iconSize = 18.dp,
        cornerRadius = 17.dp,
        tileSize = 34.dp,
        elevation = 6.dp,
        enabled = enabled
    )
}

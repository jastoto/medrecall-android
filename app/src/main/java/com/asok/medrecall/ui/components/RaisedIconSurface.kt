package com.asok.medrecall.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The visual core of MedRecall's shared "3D raised button" look -- a
 * domed, glossy, embossed icon surface with no label. [RaisedIconTile]
 * wraps this with a label below for grids; [HomeIconButton] wraps it at
 * a small fixed size for every screen's top-bar Home button. Keeping the
 * layered drawing in one place means both stay pixel-for-pixel the same
 * raised-button look. Layers, back to front:
 *  1. A soft drop shadow (Modifier.shadow) so it visibly lifts off the page.
 *  2. An off-center radial gradient fill (light source top-left) instead
 *     of a flat/linear tint, so it looks domed rather than flat.
 *  3. A subtle light-to-dark bevel border tracing the edge, like glass
 *     catching a rim light.
 *  4. A dark gradient hugging the bottom edge, to fake the shadowed
 *     underside of the dome.
 *  5. A soft white gloss ellipse near the top, like a highlight reflection.
 *  6. The icon itself drawn twice -- a dark, offset "engraved" copy behind
 *     a white copy on top -- so it reads as embossed, not flat.
 */
@Composable
fun RaisedIconSurface(
    icon: ImageVector,
    contentDescription: String?,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 38.dp,
    cornerRadius: Dp = 28.dp,
    tileSize: Dp? = null,
    elevation: Dp = 18.dp
) {
    val topColor = gradientColors.first()
    val bottomColor = gradientColors.last()
    val highlightColor = lerp(topColor, Color.White, 0.35f)

    Box(
        modifier = modifier
            .then(
                if (tileSize != null) Modifier.size(tileSize)
                else Modifier.fillMaxWidth().aspectRatio(1f)
            )
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = Color.Black.copy(alpha = 0.40f),
                spotColor = Color.Black.copy(alpha = 0.55f)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .drawWithCache {
                val domeBrush = Brush.radialGradient(
                    colors = listOf(highlightColor, topColor, bottomColor),
                    center = Offset(size.width * 0.30f, size.height * 0.22f),
                    radius = size.maxDimension * 0.95f
                )
                onDrawBehind { drawRect(domeBrush) }
            }
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.65f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.30f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Shadowed underside of the dome, anchored to the bottom edge.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f))
                    )
                )
        )
        // Glossy highlight reflection near the top, like light on glass.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .fillMaxHeight(0.38f)
                .align(Alignment.TopCenter)
                .offset(y = 4.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        // Engraved/embossed icon: a dark offset copy behind a white copy.
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.28f),
            modifier = Modifier
                .size(iconSize)
                .offset(x = 1.2.dp, y = 1.8.dp)
        )
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

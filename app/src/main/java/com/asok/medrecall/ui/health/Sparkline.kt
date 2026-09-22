package com.asok.medrecall.ui.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * A tiny inline line chart for the Health page's trend summary box (punch
 * item #13, "arrow indicator plus a mini sparkline" per Asok's choice).
 * [values] must already be oldest-first, left-to-right -- HealthScreen
 * reverses the DESC-from-the-database reading lists before calling this.
 * Draws nothing (rather than a flat/misleading line) when there are fewer
 * than 2 points.
 */
@Composable
fun Sparkline(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier.width(64.dp).height(28.dp)
) {
    if (values.size < 2) return
    val minValue = values.min()
    val maxValue = values.max()
    val range = (maxValue - minValue).let { if (it > 0.0) it else 1.0 }

    Canvas(modifier = modifier) {
        val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
        val points = values.mapIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - minValue) / range).toFloat() * size.height
            Offset(x, y)
        }
        for (i in 0 until points.size - 1) {
            drawLine(
                color = color,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}

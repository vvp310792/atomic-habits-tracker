package com.atomichabits.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * Simple 0/1 daily-completion bar chart, drawn directly with Canvas.
 * [values] should be ordered oldest -> newest (1 = done, 0 = missed/not scheduled).
 *
 * Deliberately not using a third-party charting library here: those libraries
 * (e.g. Vico) have churned their public API across versions and their AARs
 * have required newer compileSdk levels than this project targets, which made
 * the build fragile. A handful of rectangles is simple enough to draw directly
 * with core Compose APIs, which are stable.
 */
@Composable
fun CompletionBarChart(values: List<Int>) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        if (values.isEmpty()) return@Canvas

        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (values.size - 1)) / values.size
        val cornerRadius = CornerRadius(barWidth / 2.5f, barWidth / 2.5f)

        values.forEachIndexed { index, value ->
            val x = index * (barWidth + gap)
            val filled = value > 0
            val barHeight = if (filled) size.height else size.height * 0.06f
            val top = size.height - barHeight

            drawBar(
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius,
                color = if (filled) barColor else trackColor
            )
        }
    }
}

private fun DrawScope.drawBar(
    topLeft: Offset,
    size: Size,
    cornerRadius: CornerRadius,
    color: androidx.compose.ui.graphics.Color
) {
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = cornerRadius
    )
}

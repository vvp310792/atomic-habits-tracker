package com.atomichabits.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.util.WEEKDAY_LABELS
import java.time.LocalDate

/**
 * One date in the Home screen's date strip: a ring that fills up as the
 * scheduled habits for that day get completed, with the day number in the
 * middle and a short weekday label underneath.
 */
@Composable
fun DateProgressRing(
    date: LocalDate,
    completed: Int,
    scheduled: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val progress = if (scheduled == 0) 0f else (completed.toFloat() / scheduled).coerceIn(0f, 1f)
    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val weekdayLabel = WEEKDAY_LABELS[date.dayOfWeek.value - 1]

    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(40.dp)) {
                val stroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )
                if (progress > 0f) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = stroke
                    )
                }
            }
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge)
        }
        Text(
            weekdayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) ringColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

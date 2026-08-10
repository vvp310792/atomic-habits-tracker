package com.atomichabits.tracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.util.WEEKDAY_LABELS
import com.atomichabits.tracker.util.isDayActive
import com.atomichabits.tracker.util.toggleDay

@Composable
fun WeekdayPicker(
    activeDays: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WEEKDAY_LABELS.forEachIndexed { index, label ->
            val selected = isDayActive(activeDays, index)
            FilterChip(
                selected = selected,
                onClick = { onChange(toggleDay(activeDays, index)) },
                label = { Text(label) }
            )
        }
    }
}
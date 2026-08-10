package com.atomichabits.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.util.CATEGORY_VALUES
import com.atomichabits.tracker.util.categoryColorHex
import com.atomichabits.tracker.util.categoryLabel

@Composable
fun CategoryPicker(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CATEGORY_VALUES.forEach { value ->
            CategoryTag(
                value = value,
                isSelected = value == selected,
                onClick = { onSelect(value) }
            )
        }
    }
}

/** Small colored pill showing a category label - used both in the picker and as a badge on habit cards. */
@Composable
fun CategoryTag(value: String, isSelected: Boolean = true, onClick: (() -> Unit)? = null) {
    val color = remember(value) {
        runCatching { Color(android.graphics.Color.parseColor(categoryColorHex(value))) }
            .getOrDefault(Color(0xFF38BDF8))
    }
    val bg = if (isSelected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSelected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    var modifier = Modifier
        .wrapContentWidth()
        .clip(RoundedCornerShape(50))
        .background(bg)
    if (onClick != null) {
        modifier = modifier.clickable { onClick() }
    }

    Text(
        text = categoryLabel(value),
        color = textColor,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

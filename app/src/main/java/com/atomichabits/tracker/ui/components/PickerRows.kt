package com.atomichabits.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.ui.theme.HabitColorPresets

private val COMMON_EMOJIS = listOf(
    "\u2705", "\uD83D\uDCAA", "\uD83D\uDCDA", "\uD83E\uDDD8", "\uD83D\uDCA7",
    "\uD83C\uDFC3", "\uD83E\uDE7A", "\uD83C\uDF6E", "\uD83D\uDCA4", "\uD83D\uDCDD",
    "\uD83E\uDDF9", "\uD83C\uDFA8", "\uD83D\uDCB0", "\uD83E\uDDD0", "\uD83C\uDFB5"
)

@Composable
fun EmojiPicker(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        COMMON_EMOJIS.forEach { emoji ->
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (emoji == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
                    .clickable { onSelect(emoji) },
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun ColorPicker(selectedHex: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HabitColorPresets.forEach { colorLong ->
            val hex = "#" + colorLong.toString(16).uppercase().takeLast(6)
            val color = Color(colorLong)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color, CircleShape)
                    .border(
                        width = if (hex.equals(selectedHex, ignoreCase = true)) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape
                    )
                    .clickable { onSelect(hex) }
            )
        }
    }
}

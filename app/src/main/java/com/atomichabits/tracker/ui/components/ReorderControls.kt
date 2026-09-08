package com.atomichabits.tracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.R

/**
 * Swaps the item at [index] with its neighbour [delta] positions away (must
 * be +1 or -1) within [order]; returns [order] unchanged if that neighbour is
 * out of range (e.g. moving the first item further up). Shared by both the
 * Habits screen (reordering links within a stacking chain, see
 * HabitsListScreen.kt) and the Today screen (reordering plain habits within
 * a time-of-day group, see HomeScreen.kt) - same swap-with-neighbour math,
 * just applied to a different list.
 */
fun <T> swappedOrder(order: List<T>, index: Int, delta: Int): List<T> {
    val target = index + delta
    if (target !in order.indices) return order
    return order.toMutableList().apply {
        val tmp = this[index]
        this[index] = this[target]
        this[target] = tmp
    }
}

/**
 * Small up/down arrow pair for reordering one row against its neighbours -
 * an alternative to long-press drag for people who find drag gestures
 * fiddly to land precisely on a touchscreen. Used both for chain links
 * (HabitsListScreen.kt) and plain habit rows (HomeScreen.kt).
 *
 * IconButton keeps its default ~48dp touch target here (only the glyph is
 * shrunk) - a smaller tap area would be an easy mis-tap sitting right next
 * to the row's own larger click target, exactly the kind of small touch
 * target that's hard to hit reliably on a phone.
 */
@Composable
fun ReorderArrowControls(canMoveUp: Boolean, canMoveDown: Boolean, onMoveUp: () -> Unit, onMoveDown: () -> Unit) {
    Column {
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.chain_move_up),
                modifier = Modifier.size(18.dp),
                tint = if (canMoveUp) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.chain_move_down),
                modifier = Modifier.size(18.dp),
                tint = if (canMoveDown) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )
        }
    }
}

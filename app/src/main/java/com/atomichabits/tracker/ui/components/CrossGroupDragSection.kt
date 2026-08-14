package com.atomichabits.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** One section of a cross-group draggable list. */
data class DragGroup<T>(val key: String, val label: String, val items: List<T>)

/**
 * Renders [groups] as labelled sections, each item long-press-draggable both
 * within its section (reorders, calling [onReorder]) and across sections
 * (drop on a different section's area to reclassify, calling [onMove]).
 *
 * Hit-testing which section the finger is over compares the dragged item's
 * last known window position (updated continuously while NOT being dragged,
 * via onGloballyPositioned) plus the accumulated drag delta against each
 * section's captured on-screen bounds. This doesn't re-measure bounds mid-
 * drag, so scrolling the list *during* a drag can throw detection off
 * slightly - an accepted trade-off for a personal habit list that's rarely
 * long enough to need mid-drag scrolling.
 */
@Composable
fun <T> CrossGroupDraggableSections(
    groups: List<DragGroup<T>>,
    itemKey: (T) -> Any,
    onMove: (item: T, fromGroupKey: String, toGroupKey: String) -> Unit,
    onReorder: (groupKey: String, orderedItems: List<T>) -> Unit,
    emptyGroupHint: String,
    itemContent: @Composable (T, isDragging: Boolean) -> Unit
) {
    val density = LocalDensity.current
    val fallbackRowHeightPx = with(density) { 68.dp.toPx() }
    val spacerPx = with(density) { 8.dp.toPx() }

    val groupBounds = remember { mutableStateMapOf<String, Rect>() }
    val itemOrigins = remember { mutableStateMapOf<Any, Offset>() }
    val itemHeights = remember { mutableStateMapOf<Any, Float>() }
    var draggedKey by remember { mutableStateOf<Any?>(null) }
    var draggedFromGroup by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var hoverGroupKey by remember { mutableStateOf<String?>(null) }

    // Local, live-reorderable copy per group so within-section dragging feels immediate.
    var localGroups by remember(groups.map { g -> g.key to g.items.map(itemKey) }) { mutableStateOf(groups) }

    Column {
        localGroups.forEach { group ->
            val isHovered = hoverGroupKey == group.key && draggedKey != null && draggedFromGroup != group.key
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords -> groupBounds[group.key] = coords.boundsInWindow() }
                    .then(
                        if (isHovered) {
                            Modifier.background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                RoundedCornerShape(12.dp)
                            )
                        } else Modifier
                    )
                    .padding(vertical = 4.dp)
            ) {
                Text(group.label, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.size(4.dp))
                if (group.items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                        Text(
                            emptyGroupHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    group.items.forEach { item ->
                        val key = itemKey(item)
                        val isDragging = draggedKey == key
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .offset { if (isDragging) IntOffset(0, dragOffsetY.roundToInt()) else IntOffset.Zero }
                                .onGloballyPositioned { coords ->
                                    if (draggedKey != key) {
                                        itemOrigins[key] = coords.positionInWindow()
                                        itemHeights[key] = coords.size.height.toFloat()
                                    }
                                }
                                .pointerInput(key) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedKey = key
                                            draggedFromGroup = group.key
                                            hoverGroupKey = group.key
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            val from = draggedFromGroup
                                            val to = hoverGroupKey
                                            if (from != null && to != null) {
                                                if (from != to) {
                                                    onMove(item, from, to)
                                                } else {
                                                    localGroups.find { it.key == from }?.let { g ->
                                                        onReorder(from, g.items)
                                                    }
                                                }
                                            }
                                            draggedKey = null
                                            draggedFromGroup = null
                                            hoverGroupKey = null
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggedKey = null
                                            draggedFromGroup = null
                                            hoverGroupKey = null
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y

                                            val origin = itemOrigins[key]
                                            if (origin != null) {
                                                val approxWindowY = origin.y + dragOffsetY
                                                val approxWindowX = origin.x
                                                val newHoverKey = groupBounds.entries.firstOrNull { (_, rect) ->
                                                    approxWindowY in rect.top..rect.bottom &&
                                                        approxWindowX >= rect.left && approxWindowX <= rect.right
                                                }?.key
                                                if (newHoverKey != null) hoverGroupKey = newHoverKey
                                            }

                                            // Within-group live reorder (only while hovering original group).
                                            // Target index is found by a real position hit-test against each
                                            // item's own measured height (cumulative from the first item's slot),
                                            // rather than dividing by the dragged item's height alone - items can
                                            // be very different heights (e.g. a 1-habit vs a 3-habit stacked
                                            // chain), and a single-height division under- or over-counts shifts
                                            // in that case.
                                            if (hoverGroupKey == draggedFromGroup && origin != null) {
                                                val g = localGroups.find { it.key == draggedFromGroup }
                                                val currentIndex = g?.items?.indexOfFirst { itemKey(it) == key } ?: -1
                                                if (g != null && currentIndex >= 0 && g.items.isNotEmpty()) {
                                                    val draggedHeight = itemHeights[key] ?: fallbackRowHeightPx
                                                    val draggedCenterY = origin.y + dragOffsetY + draggedHeight / 2f
                                                    val firstKey = itemKey(g.items.first())
                                                    var cumulativeTop = itemOrigins[firstKey]?.y
                                                        ?: groupBounds[g.key]?.top
                                                        ?: origin.y
                                                    var targetIndex = g.items.lastIndex
                                                    for ((idx, listItem) in g.items.withIndex()) {
                                                        val itemH = itemHeights[itemKey(listItem)] ?: fallbackRowHeightPx
                                                        val itemBottom = cumulativeTop + itemH
                                                        if (draggedCenterY < itemBottom) {
                                                            targetIndex = idx
                                                            break
                                                        }
                                                        cumulativeTop = itemBottom + spacerPx
                                                    }
                                                    if (targetIndex != currentIndex) {
                                                        val newItems = g.items.toMutableList().apply {
                                                            add(targetIndex, removeAt(currentIndex))
                                                        }
                                                        localGroups = localGroups.map {
                                                            if (it.key == g.key) it.copy(items = newItems) else it
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            itemContent(item, isDragging)
                        }
                        Spacer(Modifier.size(8.dp))
                    }
                }
            }
        }
    }
}

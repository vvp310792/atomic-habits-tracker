package com.atomichabits.tracker.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import com.atomichabits.tracker.data.DaysWithoutInfo
import com.atomichabits.tracker.data.Habit
import com.atomichabits.tracker.data.MASTERY_MIN_DISPLAY_DAYS
import com.atomichabits.tracker.data.MasteryInfo
import com.atomichabits.tracker.data.computeJournalDaysWithout
import com.atomichabits.tracker.ui.components.CategoryTag
import com.atomichabits.tracker.ui.components.CrossGroupDraggableSections
import com.atomichabits.tracker.ui.components.DragGroup
import com.atomichabits.tracker.util.TIME_OF_DAY_VALUES
import com.atomichabits.tracker.util.declineDays
import com.atomichabits.tracker.util.timeOfDayLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsListScreen(
    app: HabitTrackerApp,
    onOpenHabit: (Long) -> Unit,
    onEditUntracked: (Long) -> Unit,
    onAddHabit: (initialQualityType: String, initialTracked: Boolean) -> Unit
) {
    val habits by app.repository.observeActiveHabits().collectAsState(initial = emptyList())
    val journalEntries by app.journalRepository.observeAll().collectAsState(initial = emptyList())
    val allLogs by app.repository.observeAllLogs().collectAsState(initial = emptyList())
    val pausePeriods by app.pausePeriodRepository.observeAll().collectAsState(initial = emptyList())
    val today = remember { LocalDate.now() }

    val greenHabits = habits.filter { it.qualityType == "USEFUL" }
    val yellowHabits = habits.filter { it.qualityType == "NEUTRAL" || it.qualityType == "DESIRED" }
    val redHabits = habits.filter { it.qualityType == "HARMFUL" }

    // Currently-paused habits (see PausePeriod.kt) - shown as a small badge so
    // "why isn't my streak growing" has an obvious answer while travelling.
    val pausedHabitSyncIds = remember(pausePeriods, today) {
        pausePeriods.filter { today.toEpochDay() in it.startEpochDay..it.endEpochDay }
            .flatMap { it.pausedHabitSyncIds.split(",").map { id -> id.trim() } }
            .toSet()
    }

    // Habit-formation mastery progress per habit: either computed from log
    // history (HabitRepository.computeMastery, tracked habits only) or a flat
    // "mastered" reading for a self-declared one (Habit.manuallyMastered) -
    // which doesn't need any tracking data at all, so it uses a sentinel
    // scheduledDays that clears the same >=14 "enough evidence" gate the
    // computed path uses, letting the row-rendering logic stay identical for
    // both cases.
    val masteryByHabit = remember(habits, allLogs, pausePeriods) {
        val doneEpochDaysByHabitId = allLogs
            .filter { it.completed }
            .groupBy({ it.habitId }, { it.dateEpochDay })
            .mapValues { it.value.toSet() }
        habits.mapNotNull { h ->
            val mastery = when {
                h.manuallyMastered -> MasteryInfo(progressPercent = 100, scheduledDays = MASTERY_MIN_DISPLAY_DAYS, isMastered = true)
                h.isTracked -> app.repository.computeMastery(h, doneEpochDaysByHabitId[h.id].orEmpty(), pausePeriods)
                else -> null
            }
            mastery?.let { h.syncId to it }
        }.toMap()
    }

    // "Days without" for HARMFUL habits (Anna Lembke's dopamine-balance framing,
    // now computed from the daily diary - see data/HabitJournalRepository.kt).
    // Only tracked habits get a count - an untracked one is a library reference,
    // not something actively being worked on in the diary.
    val daysWithoutByHabit = remember(redHabits, journalEntries) {
        redHabits.filter { it.isTracked }
            .associate { h -> h.syncId to computeJournalDaysWithout(h.syncId, h.createdAtEpochDay, journalEntries) }
    }

    fun rowClick(habit: Habit) {
        if (habit.isTracked) onOpenHabit(habit.id) else onEditUntracked(habit.id)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.habits_title)) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                ColoredQualitySection(
                    title = stringResource(R.string.habits_section_green),
                    tint = Color(0xFF3DBE8B),
                    habits = greenHabits,
                    masteryByHabit = masteryByHabit,
                    pausedHabitSyncIds = pausedHabitSyncIds,
                    onAdd = { onAddHabit("USEFUL", true) },
                    onMove = { habit, toTime -> app.launchPersistent { app.repository.saveHabit(habit.copy(timeOfDay = toTime)) } },
                    onReorder = { orderedItems -> app.launchPersistent { app.repository.reorder(orderedItems.map { it.id }) } },
                    onClick = ::rowClick
                )
                Spacer(Modifier.size(20.dp))
            }
            item {
                ColoredQualitySection(
                    title = stringResource(R.string.habits_section_yellow),
                    tint = Color(0xFFF2A93B),
                    habits = yellowHabits,
                    masteryByHabit = masteryByHabit,
                    pausedHabitSyncIds = pausedHabitSyncIds,
                    onAdd = { onAddHabit("DESIRED", false) },
                    onMove = { habit, toTime -> app.launchPersistent { app.repository.saveHabit(habit.copy(timeOfDay = toTime)) } },
                    onReorder = { orderedItems -> app.launchPersistent { app.repository.reorder(orderedItems.map { it.id }) } },
                    onClick = ::rowClick
                )
                Spacer(Modifier.size(20.dp))
            }
            item {
                ColoredQualitySection(
                    title = stringResource(R.string.habits_section_red),
                    tint = Color(0xFFEF6461),
                    habits = redHabits,
                    masteryByHabit = masteryByHabit,
                    daysWithoutByHabit = daysWithoutByHabit,
                    pausedHabitSyncIds = pausedHabitSyncIds,
                    onAdd = { onAddHabit("HARMFUL", false) },
                    onMove = { habit, toTime -> app.launchPersistent { app.repository.saveHabit(habit.copy(timeOfDay = toTime)) } },
                    onReorder = { orderedItems -> app.launchPersistent { app.repository.reorder(orderedItems.map { it.id }) } },
                    onClick = ::rowClick
                )
            }
        }
    }
}

/**
 * A "conveyor" of habits chained via habit stacking (Clear's "after X, I will Y"):
 * [habits] is ordered root-first, each subsequent entry's [Habit.stackAnchorId] points
 * at the syncId of the entry right before it. A chain of length 1 is just a normal,
 * unstacked habit. Chains never cross a [Habit.timeOfDay] boundary - if the next link
 * lives in a different time-of-day group it starts a new chain there instead.
 */
private data class HabitChain(val habits: List<Habit>) {
    val rootId: Long get() = habits.first().id
}

/**
 * Groups [habitsInGroup] (habits already filtered to one time-of-day bucket) into
 * [HabitChain]s, preserving each chain's existing relative order (driven by sortOrder
 * via the incoming list order). A habit only continues a chain if its anchor is another
 * habit within this same group; an anchor outside the group is ignored here (the linked
 * habit still renders, just as the root of its own chain) per the "chains don't cross
 * time-of-day" rule.
 *
 * Defensive against bad/legacy data: at most one child is taken per parent (extra
 * claimants become roots of their own chains), and a visited-set guards against cycles.
 */
private fun buildHabitChains(habitsInGroup: List<Habit>): List<HabitChain> {
    val groupSyncIds = habitsInGroup.map { it.syncId }.toSet()
    val childByParentSyncId = LinkedHashMap<String, Habit>()
    habitsInGroup.forEach { h ->
        if (h.stackAnchorId.isNotBlank() && h.stackAnchorId in groupSyncIds) {
            childByParentSyncId.putIfAbsent(h.stackAnchorId, h)
        }
    }
    val isChainedChild: (Habit) -> Boolean = { h ->
        h.stackAnchorId.isNotBlank() && h.stackAnchorId in groupSyncIds &&
            childByParentSyncId[h.stackAnchorId]?.id == h.id
    }
    val roots = habitsInGroup.filter { !isChainedChild(it) }
    return roots.map { root ->
        val chain = mutableListOf(root)
        val visited = mutableSetOf(root.syncId)
        var current = root
        while (true) {
            val next = childByParentSyncId[current.syncId] ?: break
            if (!visited.add(next.syncId)) break
            chain.add(next)
            current = next
        }
        HabitChain(chain)
    }
}

@Composable
private fun ColoredQualitySection(
    title: String,
    tint: Color,
    habits: List<Habit>,
    masteryByHabit: Map<String, MasteryInfo>,
    daysWithoutByHabit: Map<String, DaysWithoutInfo> = emptyMap(),
    pausedHabitSyncIds: Set<String> = emptySet(),
    onAdd: () -> Unit,
    onMove: (Habit, String) -> Unit,
    onReorder: (List<Habit>) -> Unit,
    onClick: (Habit) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.10f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = tint)
                IconButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = tint)
                }
            }
            if (habits.isEmpty()) {
                Text(
                    stringResource(R.string.anchors_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                val timeGroups = TIME_OF_DAY_VALUES.map { tod ->
                    val chains = buildHabitChains(habits.filter { it.timeOfDay == tod })
                    DragGroup(tod, timeOfDayLabel(tod), chains)
                }
                CrossGroupDraggableSections(
                    groups = timeGroups,
                    itemKey = { it.rootId },
                    onMove = { chain, _, toGroupKey ->
                        // Dragging a chain across time-of-day moves every habit in it
                        // together, so the conveyor stays intact in its new group.
                        chain.habits.forEach { habit -> onMove(habit, toGroupKey) }
                    },
                    onReorder = { _, orderedChains -> onReorder(orderedChains.flatMap { it.habits }) },
                    emptyGroupHint = stringResource(R.string.home_group_empty_hint)
                ) { chain, isDragging ->
                    HabitChainBlock(chain, masteryByHabit, daysWithoutByHabit, pausedHabitSyncIds, isDragging, onClick)
                }
            }
        }
    }
}

/**
 * Renders one [HabitChain] as a single visual block: each habit after the first is
 * nested slightly and preceded by a small "↳" connector, so a stacked sequence reads
 * as one conveyor at a glance instead of disconnected rows. A chain of length 1 looks
 * exactly like a plain habit row (no connector, no indent).
 */
@Composable
private fun HabitChainBlock(
    chain: HabitChain,
    masteryByHabit: Map<String, MasteryInfo>,
    daysWithoutByHabit: Map<String, DaysWithoutInfo>,
    pausedHabitSyncIds: Set<String>,
    isDragging: Boolean,
    onClick: (Habit) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        chain.habits.forEachIndexed { index, habit ->
            if (index == 0) {
                UniversalHabitRow(habit, masteryByHabit[habit.syncId], daysWithoutByHabit[habit.syncId], habit.syncId in pausedHabitSyncIds) { onClick(habit) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(20.dp))
                    Text(
                        "\u21B3",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        UniversalHabitRow(habit, masteryByHabit[habit.syncId], daysWithoutByHabit[habit.syncId], habit.syncId in pausedHabitSyncIds) { onClick(habit) }
                    }
                }
            }
        }
    }
}

@Composable
private fun UniversalHabitRow(habit: Habit, mastery: MasteryInfo?, daysWithout: DaysWithoutInfo?, isPaused: Boolean, onClick: () -> Unit) {
    val accent = remember(habit.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(habit.colorHex)) }
            .getOrDefault(Color(0xFF7C6CF0))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accent.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(habit.emoji, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    habit.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (!habit.isTracked) {
                    Text(
                        stringResource(R.string.habits_not_tracked_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                if (habit.isTracked) {
                    CategoryTag(value = habit.category)
                }
                if (habit.identityLabel.isNotBlank()) {
                    Text(
                        "\uD83E\uDDE9 " + habit.identityLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (habit.temptationBundle.isNotBlank()) {
                    Text(
                        "\uD83D\uDD12 " + habit.temptationBundle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (habit.alternativeSuggestion.isNotBlank()) {
                    Text(
                        "\uD83D\uDCA1 " + habit.alternativeSuggestion,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (habit.whyItMatters.isNotBlank()) {
                    Text(
                        "\uD83C\uDFAF " + habit.whyItMatters,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                if (isPaused) {
                    Text(
                        stringResource(R.string.habits_paused_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (daysWithout != null && daysWithout.currentDays > 0) {
                    Text(
                        "\uD83D\uDEE1 ${daysWithout.currentDays} ${declineDays(daysWithout.currentDays)} без",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (mastery != null && mastery.scheduledDays >= MASTERY_MIN_DISPLAY_DAYS) {
                    if (mastery.isMastered) {
                        Text(
                            stringResource(R.string.habits_mastery_done_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            stringResource(R.string.habits_mastery_progress_badge, mastery.progressPercent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

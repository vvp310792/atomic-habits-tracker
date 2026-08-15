package com.atomichabits.tracker.ui.impulse

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atomichabits.tracker.HabitTrackerApp
import com.atomichabits.tracker.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private val TRIGGER_TAGS = listOf("Тревога", "Скука", "Социальное давление", "Усталость")
private const val WAIT_SECONDS = 120

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpulseScreen(app: HabitTrackerApp, onBack: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val todaysLogs by app.impulseRepository.observeForDate(today).collectAsState(initial = emptyList())
    val checks = todaysLogs.count { it.outcome == "CHECK" }
    val crosses = todaysLogs.count { it.outcome == "CROSS" }

    val anchors by app.repository.observeActiveHabits().collectAsState(initial = emptyList())
    val harmful = anchors.filter { it.qualityType == "HARMFUL" }

    var showReflection by remember { mutableStateOf(false) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var note by remember { mutableStateOf("") }
    var justLogged by remember { mutableStateOf<String?>(null) }
    var sessionKey by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(WAIT_SECONDS) }
    var linkedAnchorId by remember { mutableStateOf<String?>(null) } // null = not yet chosen
    var linkedAnchorLabel by remember { mutableStateOf("") }

    LaunchedEffect(sessionKey) {
        secondsLeft = WAIT_SECONDS
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    val canCheck = secondsLeft <= 0 && linkedAnchorId != null
    val linkedAlternative = harmful.find { it.syncId == linkedAnchorId }?.alternativeSuggestion.orEmpty()
    val linkedWhyItMatters = harmful.find { it.syncId == linkedAnchorId }?.whyItMatters.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.impulse_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.impulse_score, checks, crosses),
                style = MaterialTheme.typography.titleLarge
            )

            if (justLogged == null) {
                if (!showReflection) {
                    Text(
                        if (linkedAnchorId == null) stringResource(R.string.impulse_link_hint_required)
                        else stringResource(R.string.impulse_link_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (linkedAnchorId == null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(harmful) { anchor ->
                            FilterChip(
                                selected = linkedAnchorId == anchor.syncId,
                                onClick = { linkedAnchorId = anchor.syncId; linkedAnchorLabel = anchor.name },
                                label = { Text(anchor.name) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = linkedAnchorId == "",
                                onClick = { linkedAnchorId = ""; linkedAnchorLabel = "" },
                                label = { Text(stringResource(R.string.impulse_no_link)) }
                            )
                        }
                    }

                    if (linkedWhyItMatters.isNotBlank()) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Text(
                                "\uD83C\uDFAF " + linkedWhyItMatters,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (linkedAlternative.isNotBlank()) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Text(
                                "\uD83D\uDCA1 " + linkedAlternative,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Text(
                        if (canCheck) stringResource(R.string.impulse_breathing_hint)
                        else stringResource(R.string.impulse_wait_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    BreathingCircle(secondsLeft = secondsLeft, canCheck = canCheck)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    app.impulseRepository.logCheck(linkedAnchorId.orEmpty(), linkedAnchorLabel)
                                    justLogged = "CHECK"
                                }
                            },
                            enabled = canCheck,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3DBE8B),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF3DBE8B).copy(alpha = 0.35f),
                                disabledContentColor = Color.White.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Text(
                                " " + stringResource(R.string.impulse_check),
                                maxLines = 1,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        Button(
                            onClick = { showReflection = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF6461),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                            Text(
                                " " + stringResource(R.string.impulse_cross),
                                maxLines = 1,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                } else {
                    if (linkedWhyItMatters.isNotBlank()) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Text(
                                "\uD83C\uDFAF " + linkedWhyItMatters,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    if (linkedAlternative.isNotBlank()) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Text(
                                "\uD83D\uDCA1 " + linkedAlternative,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    Text(stringResource(R.string.impulse_reflection_title), style = MaterialTheme.typography.titleMedium)

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(TRIGGER_TAGS) { tag ->
                            FilterChip(
                                selected = tag in selectedTags,
                                onClick = {
                                    selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
                                },
                                label = { Text(tag) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.impulse_note_label)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                app.impulseRepository.logCross(
                                    selectedTags.toList(),
                                    note.trim(),
                                    linkedAnchorId.orEmpty(),
                                    linkedAnchorLabel
                                )
                                showReflection = false
                                selectedTags = emptySet()
                                note = ""
                                justLogged = "CROSS"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            } else {
                Text(
                    stringResource(R.string.impulse_logged),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedButton(
                    onClick = {
                        justLogged = null
                        linkedAnchorId = null
                        linkedAnchorLabel = ""
                        sessionKey++
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.impulse_again))
                }
            }
        }
    }
}

/**
 * Box/square breathing (4-4-4-4): inhale, hold, exhale, hold, each held for
 * [PHASE_SECONDS]. The circle grows during inhale, stays put during both holds,
 * shrinks during exhale - and the phase name is shown so the rhythm is legible,
 * not just a shrinking/growing shape.
 */
private const val PHASE_SECONDS = 3
private const val MIN_SCALE = 0.55f
private const val MAX_SCALE = 1f

private enum class BreathPhase(val label: String) {
    INHALE("Вдох"),
    HOLD_FULL("Задержка"),
    EXHALE("Выдох"),
    HOLD_EMPTY("Задержка")
}

@Composable
private fun BreathingCircle(secondsLeft: Int, canCheck: Boolean) {
    val scale = remember { Animatable(MIN_SCALE) }
    var phase by remember { mutableStateOf(BreathPhase.INHALE) }

    LaunchedEffect(Unit) {
        while (true) {
            phase = BreathPhase.INHALE
            scale.animateTo(MAX_SCALE, tween(PHASE_SECONDS * 1000, easing = LinearEasing))
            phase = BreathPhase.HOLD_FULL
            delay(PHASE_SECONDS * 1000L)
            phase = BreathPhase.EXHALE
            scale.animateTo(MIN_SCALE, tween(PHASE_SECONDS * 1000, easing = LinearEasing))
            phase = BreathPhase.HOLD_EMPTY
            delay(PHASE_SECONDS * 1000L)
        }
    }

    Box(
        modifier = Modifier
            .size(220.dp)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size((200 * scale.value).dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                phase.label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!canCheck) {
                Text(
                    formatTime(secondsLeft),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

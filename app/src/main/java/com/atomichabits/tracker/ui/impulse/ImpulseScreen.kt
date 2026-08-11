package com.atomichabits.tracker.ui.impulse

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

    var showReflection by remember { mutableStateOf(false) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var note by remember { mutableStateOf("") }
    var justLogged by remember { mutableStateOf<String?>(null) }
    var sessionKey by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(WAIT_SECONDS) }

    LaunchedEffect(sessionKey) {
        secondsLeft = WAIT_SECONDS
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    val canCheck = secondsLeft <= 0

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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                stringResource(R.string.impulse_score, checks, crosses),
                style = MaterialTheme.typography.titleLarge
            )

            if (justLogged == null) {
                if (!showReflection) {
                    Text(
                        if (canCheck) stringResource(R.string.impulse_breathing_hint)
                        else stringResource(R.string.impulse_wait_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    BreathingCircle()

                    if (!canCheck) {
                        Text(
                            formatTime(secondsLeft),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    app.impulseRepository.logCheck()
                                    justLogged = "CHECK"
                                }
                            },
                            enabled = canCheck,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3DBE8B)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Text(" " + stringResource(R.string.impulse_check), modifier = Modifier.padding(start = 4.dp))
                        }
                        Button(
                            onClick = { showReflection = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6461)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                            Text(" " + stringResource(R.string.impulse_cross), modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                } else {
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
                                app.impulseRepository.logCross(selectedTags.toList(), note.trim())
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

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

@Composable
private fun BreathingCircle() {
    val transition = rememberInfiniteTransition(label = "breathing")
    val scale by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size((180 * scale).dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
        )
    }
}

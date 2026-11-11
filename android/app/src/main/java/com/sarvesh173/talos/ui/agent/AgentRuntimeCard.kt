package com.sarvesh173.talos.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.applePressable
import com.sarvesh173.talos.ui.orb.OrbState
import com.sarvesh173.talos.ui.orb.ThinkingOrb
import kotlinx.coroutines.delay

/**
 * A running agent card: main or delegate.
 *
 * The time readout is the point of this component. It ticks every second while
 * the card is on screen — that is what the user asked for and what a chat app
 * cannot give them — and it keeps counting in hours, days and months without
 * switching to a coarser fake.
 *
 * Configuration is inline rather than behind a sheet: model, reasoning effort
 * and task are the three things a delegate is *for*, so making the user open a
 * modal to see them would hide the whole point.
 */
@Composable
fun AgentRuntimeCard(
    agent: AgentRuntime,
    isDark: Boolean,
    accent: Color,
    models: List<String>,
    onModelChange: (String) -> Unit,
    onReasoningChange: (ReasoningEffort) -> Unit,
    onTaskChange: (String) -> Unit,
    onStop: () -> Unit,
    onSendTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    // One shared tick for every card. A per-card LaunchedEffect would wake the
    // CPU once per card per second; this way a list of delegates costs one timer.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val elapsed = agent.elapsedSeconds(now)
    val remaining = agent.remainingSeconds(now)
    var expanded by remember(agent.id) { mutableStateOf(false) }

    val shape = RoundedCornerShape(20.dp)
    val surface = if (isDark) Color(0xFF16161A) else Color.White
    val strong = if (isDark) Color.White else Color(0xFF1D1D1F)
    val muted = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.68f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surface)
            .border(BorderStroke(0.8.dp, accent.copy(alpha = 0.28f)), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThinkingOrb(
                state = if (agent.isExpired(now)) OrbState.SOLVING else OrbState.WORKING,
                size = 24.dp,
                ink = if (agent.isExpired(now)) muted else accent,
                label = null,
                announce = false
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = agent.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = strong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    KindBadge(agent.kind, accent, isDark)
                }
                Text(
                    text = buildString {
                        append(AgentClock.compact(elapsed))
                        if (remaining != null) {
                            append(" · ")
                            append(if (remaining == 0L) "expired" else "${AgentClock.compact(remaining)} left")
                        }
                    },
                    fontSize = 11.sp,
                    color = muted
                )
            }

            // Elapsed as a live monospace readout — the per-second tick is lost
            // in a proportional face as the digits change width.
            Text(
                text = AgentClock.format(elapsed),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = strong
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .applePressable(0.94f, pressHaptic = true) { expanded = !expanded },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (expanded) "−" else "+",
                    fontSize = 18.sp,
                    color = muted
                )
            }
        }

        // Delegate lifetime bar. Only shown when a TTL exists.
        if (agent.ttlSeconds != null && agent.ttlSeconds > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            val fraction = (elapsed.toFloat() / agent.ttlSeconds.toFloat()).coerceIn(0f, 1f)
            val barColor = when {
                fraction > 0.85f -> Color(0xFFFF453A)
                fraction > 0.65f -> Color(0xFFFF9F0A)
                else -> accent
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(CircleShape)
                        .background(barColor)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))

                // ---- model ----
                FieldLabel("Model", muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(models.distinct()) { m ->
                        val selected = agent.model == m
                        Chip(
                            label = m.substringAfterLast("/"),
                            selected = selected,
                            accent = accent,
                            isDark = isDark
                        ) { onModelChange(m) }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- reasoning ----
                FieldLabel("Reasoning", muted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ReasoningEffort.entries.toList()) { effort ->
                        Chip(
                            label = effort.label,
                            selected = agent.reasoning == effort,
                            accent = accent,
                            isDark = isDark
                        ) { onReasoningChange(effort) }
                    }
                }
                Text(
                    text = agent.reasoning.blurb,
                    fontSize = 11.sp,
                    color = muted,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ---- task ----
                FieldLabel("Task", muted)
                androidx.compose.foundation.text.BasicTextField(
                    value = agent.task,
                    onValueChange = onTaskChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        color = strong
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                    decorationBox = { inner ->
                        if (agent.task.isBlank()) {
                            Text(
                                text = "What should this agent do?",
                                fontSize = 13.sp,
                                color = muted.copy(alpha = 0.7f)
                            )
                        }
                        inner()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .border(BorderStroke(0.8.dp, accent.copy(alpha = 0.40f)), RoundedCornerShape(12.dp))
                            .applePressable(0.96f, pressHaptic = true) { onSendTask() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Send task", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent)
                    }
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f))
                            .applePressable(0.96f, pressHaptic = true) { onStop() }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (agent.kind == AgentKind.Delegate) "Expire now" else "Stop",
                            fontSize = 12.sp,
                            color = if (isDark) Color.White.copy(alpha = 0.80f) else Color(0xFF1D1D1F)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KindBadge(kind: AgentKind, accent: Color, isDark: Boolean) {
    val label = if (kind == AgentKind.Main) "MAIN" else "DELEGATE"
    val bg = if (kind == AgentKind.Main) {
        accent.copy(alpha = 0.16f)
    } else if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = if (kind == AgentKind.Main) accent else (if (isDark) Color.White.copy(alpha = 0.60f) else Color.Black.copy(alpha = 0.55f))
        )
    }
}

@Composable
private fun FieldLabel(text: String, muted: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = muted,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    accent: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(
                if (selected) accent.copy(alpha = 0.18f)
                else if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
            )
            .border(
                BorderStroke(0.7.dp, if (selected) accent.copy(alpha = 0.45f) else Color.Transparent),
                shape
            )
            .applePressable(0.95f, pressHaptic = true) { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else (if (isDark) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.65f)),
            maxLines = 1
        )
    }
}

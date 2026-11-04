package com.sarvesh173.talos.ui.agent

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.applePressable
import com.sarvesh173.talos.ui.orb.OrbState
import com.sarvesh173.talos.ui.orb.ThinkingOrb
import kotlinx.coroutines.delay

/**
 * An agent asking the human something.
 *
 * Two shapes share one surface because they are the same event from the user's
 * side — "the agent is stopped and needs me":
 *
 *  - **permission** — may I run this? Answering grants or denies a capability,
 *    and the grant has a *scope* (once / this session / full control).
 *  - **question** — which of these do you want? The agent is not asking for
 *    authority, it is asking for a decision, so the answers are the agent's own
 *    options plus free text.
 *
 * Both carry an optional deadline. A permission that expires must fail closed
 * (deny); a question that expires must not invent an answer. Encoding the
 * default per prompt rather than globally is what keeps that honest.
 */
data class AgentQuestion(
    val id: String,
    /** One line the user reads first. */
    val prompt: String,
    /** Optional context: the file, the command, the trade-off. */
    val detail: String = "",
    /** Exact command or payload, shown monospace so it can be judged. */
    val command: String = "",
    val agentName: String = "",
    val kind: QuestionKind = QuestionKind.Permission,
    /** Agent-supplied choices. Empty means free-text only. */
    val options: List<QuestionOption> = emptyList(),
    /** Whether the user may type something the agent did not offer. */
    val allowCustom: Boolean = true,
    /** Seconds until the prompt expires. Null means it waits indefinitely. */
    val timeoutSeconds: Int? = null,
    /** What an expiry means. Permissions default to deny. */
    val onTimeout: TimeoutBehaviour = TimeoutBehaviour.Deny,
    val brandColor: Color = Color(0xFF007AFF)
)

enum class QuestionKind { Permission, Question }

/**
 * How long a grant lasts when the user says yes.
 *
 * "Always allow" as a single switch is how users end up with an agent that can
 * push to main. Naming the three scopes separately makes the trade-off the
 * choice, and [Session] is the one most people actually want.
 */
enum class GrantScope(val label: String, val blurb: String) {
    Once("Allow once", "Just this call"),
    Session("This session", "Until you close the app"),
    Always("Full control", "Every session, until you revoke")
}

/** What happens when a prompt times out. */
enum class TimeoutBehaviour { Deny, AskAgain, Skip }

/** One agent-offered answer. */
data class QuestionOption(
    val label: String,
    val value: String = label,
    val kind: OptionKind = OptionKind.Neutral
)

enum class OptionKind { Affirmative, Negative, Neutral }

/**
 * Full-screen prompt. Deliberately not a bottom sheet: a pending permission is
 * the most important thing on the device while it lasts, and a sheet can be
 * swiped past, which is exactly the wrong outcome for a blocked agent.
 *
 * Layout order is the reading order of a decision: who is asking → what it
 * wants → the exact command → how long you have → the choices.
 */
@Composable
fun AgentQuestionOverlay(
    question: AgentQuestion,
    isDark: Boolean,
    onAnswer: (Answer) -> Unit,
    onDismiss: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val surface = if (isDark) Color(0xFF141418) else Color.White
    val strong = if (isDark) Color.White else Color(0xFF1D1D1F)
    val muted = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.48f)
    val accent = question.brandColor

    var customText by remember(question.id) { mutableStateOf("") }
    var customOpen by remember(question.id) { mutableStateOf(false) }
    var selectedScope by remember(question.id) { mutableStateOf(GrantScope.Once) }

    // Countdown. The remaining time is state the user can see, not a silent
    // background expiry — being denied by a timer you cannot see is the worst
    // version of this feature.
    var remaining by remember(question.id) { mutableStateOf(question.timeoutSeconds ?: -1) }
    LaunchedEffect(question.id) {
        val ttl = question.timeoutSeconds ?: return@LaunchedEffect
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        if (remaining == 0) {
            when (question.onTimeout) {
                TimeoutBehaviour.Deny ->
                    onAnswer(Answer.Expired(question.id))
                TimeoutBehaviour.Skip ->
                    onAnswer(Answer.Skipped(question.id))
                TimeoutBehaviour.AskAgain ->
                    onAnswer(Answer.NeedsAttention(question.id))
            }
        }
    }

    val urgent = question.timeoutSeconds != null && remaining in 0..15
    val progress by animateFloatAsState(
        targetValue = if (question.timeoutSeconds != null && question.timeoutSeconds > 0) {
            (remaining.toFloat() / question.timeoutSeconds).coerceIn(0f, 1f)
        } else 0f,
        animationSpec = tween(900, easing = LinearEasing),
        label = "question_countdown"
    )

    // Scrim eats every tap that is not on a control.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .applePressable(1f) { /* absorb */ },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp)
                .clip(shape)
                .background(surface)
                .border(
                    BorderStroke(
                        0.9.dp,
                        if (urgent) Color(0xFFFF453A).copy(alpha = 0.55f) else accent.copy(alpha = 0.28f)
                    ),
                    shape
                )
                .padding(18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ---- who ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThinkingOrb(
                    state = OrbState.SOLVING,
                    size = 22.dp,
                    ink = if (urgent) Color(0xFFFF453A) else accent,
                    label = null,
                    announce = false
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = question.agentName.ifBlank { "Agent" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = strong,
                    modifier = Modifier.weight(1f)
                )
                if (question.timeoutSeconds != null) {
                    CountdownPill(
                        remaining = remaining,
                        progress = progress,
                        urgent = urgent,
                        isDark = isDark
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Waiting", fontSize = 9.sp, color = muted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- what ----
            Text(
                text = question.prompt,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 23.sp,
                letterSpacing = (-0.2).sp,
                color = strong
            )

            if (question.detail.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = question.detail,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = muted
                )
            }

            if (question.command.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = question.command,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) Color(0xFF0D0D10) else Color(0xFFF2F2F7))
                        .padding(12.dp),
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- answers ----
            if (question.options.isNotEmpty()) {
                question.options.forEach { option ->
                    QuestionOptionRow(
                        option = option,
                        isDark = isDark,
                        accent = accent,
                        onClick = {
                            onAnswer(
                                Answer.Choice(
                                    id = question.id,
                                    value = option.value,
                                    label = option.label
                                )
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (question.kind == QuestionKind.Permission) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "If you allow it",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    color = muted
                )
                Spacer(modifier = Modifier.height(8.dp))

                GrantScope.entries.forEach { scope ->
                    ScopeRow(
                        scope = scope,
                        selected = selectedScope == scope,
                        isDark = isDark,
                        accent = accent
                    ) { selectedScope = scope }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // ---- custom answer ----
            if (question.allowCustom) {
                if (!customOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)),
                                RoundedCornerShape(12.dp)
                            )
                            .applePressable(0.97f, pressHaptic = true) { customOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Answer in my own words", fontSize = 12.sp, color = muted)
                    }
                } else {
                    Column {
                        BasicTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            textStyle = TextStyle(fontSize = 14.sp, color = strong),
                            cursorBrush = SolidColor(accent),
                            decorationBox = { inner ->
                                if (customText.isEmpty()) {
                                    Text(
                                        text = "Type the answer the agent should use",
                                        fontSize = 14.sp,
                                        color = muted.copy(alpha = 0.75f)
                                    )
                                }
                                inner()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
                                .padding(12.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (customText.isBlank()) {
                                        if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)
                                    } else accent
                                )
                                .applePressable(0.96f, pressHaptic = true) {
                                    if (customText.isNotBlank()) {
                                        onAnswer(
                                            Answer.Custom(
                                                id = question.id,
                                                text = customText.trim(),
                                                scope = if (question.kind == QuestionKind.Permission) selectedScope else null
                                            )
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Send answer",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (customText.isBlank()) muted else Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- escape hatches, always last ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (question.kind == QuestionKind.Permission) {
                    ActionPill(
                        label = "Deny",
                        tint = Color(0xFFFF453A),
                        filled = false,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    ) {
                        onAnswer(Answer.Denied(question.id, remember = false))
                    }
                    ActionPill(
                        label = "Allow",
                        tint = accent,
                        filled = true,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    ) {
                        onAnswer(Answer.Granted(question.id, selectedScope))
                    }
                } else {
                    // Yes / no first — the most common agent question is a
                    // yes/no, and it must be one tap, not a typed answer.
                    // Custom free text stays below and the countdown above
                    // stays visible throughout.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionPill(
                            label = "No",
                            tint = Color(0xFFFF453A),
                            filled = false,
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        ) {
                            onAnswer(Answer.Choice(id = question.id, value = "no", label = "No"))
                        }
                        ActionPill(
                            label = "Yes",
                            tint = Color(0xFF34C759),
                            filled = true,
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        ) {
                            onAnswer(Answer.Choice(id = question.id, value = "yes", label = "Yes"))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionPill(
                            label = "Let the agent decide",
                            tint = null,
                            filled = false,
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        ) {
                            onAnswer(Answer.Skipped(question.id))
                        }
                        ActionPill(
                            label = "Close",
                            tint = null,
                            filled = false,
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        ) { onDismiss() }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/** The answer a user gave. Typed so every path is handled, including expiry. */
sealed interface Answer {
    val id: String

    data class Granted(override val id: String, val scope: GrantScope) : Answer
    data class Denied(override val id: String, val remember: Boolean) : Answer
    data class Choice(override val id: String, val value: String, val label: String) : Answer
    data class Custom(
        override val id: String,
        val text: String,
        val scope: GrantScope? = null
    ) : Answer

    data class Skipped(override val id: String) : Answer
    data class Expired(override val id: String) : Answer
    data class NeedsAttention(override val id: String) : Answer
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun CountdownPill(remaining: Int, progress: Float, urgent: Boolean, isDark: Boolean) {
    val tint = if (urgent) Color(0xFFFF453A) else if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.55f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A thin bar reads as "time draining" without needing a ring gauge.
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.22f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(CircleShape)
                        .background(tint)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when {
                    remaining <= 0 -> "expired"
                    remaining < 60 -> "${remaining}s"
                    else -> "${remaining / 60}m ${remaining % 60}s"
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = tint
            )
        }
    }
}

@Composable
private fun QuestionOptionRow(
    option: QuestionOption,
    isDark: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(13.dp)
    val tint = when (option.kind) {
        OptionKind.Affirmative -> Color(0xFF34C759)
        OptionKind.Negative -> Color(0xFFFF453A)
        OptionKind.Neutral -> accent
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(tint.copy(alpha = 0.10f))
            .border(BorderStroke(0.8.dp, tint.copy(alpha = 0.32f)), shape)
            .applePressable(0.97f, pressHaptic = true) { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = option.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDark) Color.White else Color(0xFF1D1D1F)
        )
    }
}

@Composable
private fun ScopeRow(
    scope: GrantScope,
    selected: Boolean,
    isDark: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(
                if (selected) accent.copy(alpha = 0.14f)
                else if (isDark) Color.White.copy(alpha = 0.04f)
                else Color.Black.copy(alpha = 0.03f)
            )
            .border(
                BorderStroke(0.8.dp, if (selected) accent.copy(alpha = 0.45f) else Color.Transparent),
                shape
            )
            .applePressable(0.97f, pressHaptic = true) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scope.label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) accent else (if (isDark) Color.White else Color(0xFF1D1D1F))
            )
            Text(
                text = scope.blurb,
                fontSize = 10.sp,
                color = if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.42f)
            )
        }
        // Radio, drawn rather than borrowed so it matches the 18dp metric.
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(
                    BorderStroke(1.5.dp, if (selected) accent else (if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.20f))),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    tint: Color?,
    filled: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(13.dp)
    val base = tint ?: (if (isDark) Color.White.copy(alpha = 0.90f) else Color(0xFF1D1D1F))
    Box(
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(shape)
            .then(
                if (filled) Modifier.background(tint ?: base)
                else Modifier
                    .background(
                        if (tint != null) tint.copy(alpha = 0.12f)
                        else if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f)
                    )
                    .border(BorderStroke(0.9.dp, base.copy(alpha = 0.35f)), shape)
            )
            .applePressable(0.96f, pressHaptic = true) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) Color.White else base
        )
    }
}

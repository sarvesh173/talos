package com.sarvesh173.talos.ui.chat

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.sarvesh173.talos.ui.agent.CardsDirective
import com.sarvesh173.talos.ui.agent.ChartDirective
import com.sarvesh173.talos.ui.agent.ConfirmDirective
import com.sarvesh173.talos.ui.agent.EmojiDirective
import com.sarvesh173.talos.ui.agent.GameDirective
import com.sarvesh173.talos.ui.agent.PermissionDirective
import com.sarvesh173.talos.ui.agent.SelectDirective
import com.sarvesh173.talos.ui.agent.TalosDirective
import com.sarvesh173.talos.ui.applePressable
import kotlinx.coroutines.delay
import kotlin.math.ceil

/**
 * Reply path for SYSTEM.md `:::talos` directive blocks.
 *
 * [DirectiveHost] queues question blocks (select / confirm / permission /
 * game / emoji) one at a time in order and sends the answer back through
 * `onReply` as the next user turn. Chart and cards blocks are display-only
 * and are rendered by TalosDirectiveRenderers, so they are not queued here.
 */

private val CanvasColor = Color(0xFF000000)
private val SheetColor = Color(0xFF1C1C1E)
private val AccentColor = Color(0xFF0A84FF)
private val DestructiveColor = Color(0xFFFF453A)
private val StrongColor = Color.White
private val MutedColor = Color.White.copy(alpha = 0.60f)
private val FieldColor = Color(0xFF2C2C2E)

/** A typed answer to a directive question, before wire shaping. */
sealed interface DirectiveAnswer {
    /** Permission grant: "once", "session", "full", or "denied". */
    data class Granted(val scope: String) : DirectiveAnswer

    /** Select: chosen option ids. */
    data class Selected(val ids: List<String>) : DirectiveAnswer

    /** Confirm: true = confirm label, false = deny label. */
    data class Confirmed(val value: Boolean) : DirectiveAnswer

    /** Game move, as text; numeric moves are emitted unquoted. */
    data class Move(val move: String) : DirectiveAnswer

    /** Emoji reaction. */
    data class Emoji(val emoji: String) : DirectiveAnswer

    /** Custom free-text answer, sent verbatim (trimmed). */
    data class FreeText(val text: String) : DirectiveAnswer
}

private fun quoteWire(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/**
 * The one pure reply-shaping function: maps a typed [DirectiveAnswer] for a
 * directive to the exact user-turn wire string SYSTEM.md defines.
 *
 * Enforces the permission scope contract here, not just in the UI: a "full"
 * grant is downgraded to denied when the directive's scopes do not include
 * "full". Select ids are filtered to known option ids and emitted in option
 * order. Emoji picks outside the offered grid fall back to plain text.
 */
fun shapeDirectiveReply(directive: TalosDirective, answer: DirectiveAnswer): String {
    return when (answer) {
        is DirectiveAnswer.Granted -> {
            val scopes = (directive as? PermissionDirective)?.scopes.orEmpty()
            val safe = when {
                answer.scope == "denied" -> "denied"
                answer.scope == "full" && "full" !in scopes -> "denied"
                answer.scope in setOf("once", "session", "full") -> answer.scope
                else -> "denied"
            }
            "granted: ${quoteWire(safe)}"
        }
        is DirectiveAnswer.Selected -> {
            val known = (directive as? SelectDirective)?.options?.map { it.id }
            val ids = if (known != null) known.filter { it in answer.ids } else answer.ids
            val body = ids.joinToString(", ") { quoteWire(it) }
            "selected: [$body]"
        }
        is DirectiveAnswer.Confirmed -> if (answer.value) "confirm" else "deny"
        is DirectiveAnswer.Move -> {
            val move = answer.move.trim()
            if (move.matches(Regex("-?\\d+"))) "move: $move" else "move: ${quoteWire(move)}"
        }
        is DirectiveAnswer.Emoji -> {
            val options = (directive as? EmojiDirective)?.options.orEmpty()
            if (options.isEmpty() || answer.emoji in options) {
                "emoji: ${quoteWire(answer.emoji)}"
            } else {
                answer.emoji.trim()
            }
        }
        is DirectiveAnswer.FreeText -> answer.text.trim()
    }
}

private fun isQuestion(directive: TalosDirective): Boolean = directive is SelectDirective ||
    directive is ConfirmDirective ||
    directive is PermissionDirective ||
    directive is GameDirective ||
    directive is EmojiDirective

/**
 * Hosts the active directive question, one at a time, queued in order.
 *
 * When a confirm deadline expires while on screen, the countdown applies
 * `default_on_timeout` and reports it via `onReply` with a
 * "(timed out — default applied)" suffix rather than silently closing.
 */
@Composable
fun DirectiveHost(
    directives: List<TalosDirective>,
    onReply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val questions = directives.filter { isQuestion(it) }
    if (questions.isEmpty()) return
    var index by remember(questions) { mutableIntStateOf(0) }
    if (index >= questions.size) return
    val current = questions[index]

    fun answer(answer: DirectiveAnswer) {
        onReply(shapeDirectiveReply(current, answer))
        index += 1
    }
    fun timeoutAnswer(defaultOnTimeout: String) {
        val shaped = shapeDirectiveReply(
            current,
            DirectiveAnswer.Confirmed(defaultOnTimeout == "confirm")
        )
        onReply("$shaped (timed out — default applied)")
        index += 1
    }

    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CanvasColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(SheetColor)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Question ${index + 1} of ${questions.size}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MutedColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = current.type,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentColor
                )
            }
            when (current) {
                is PermissionDirective -> PermissionQuestion(current) { scope ->
                    answer(DirectiveAnswer.Granted(scope))
                }
                is ConfirmDirective -> ConfirmQuestion(current) { confirmed ->
                    answer(DirectiveAnswer.Confirmed(confirmed))
                }
                is SelectDirective -> SelectQuestion(current) { ids ->
                    answer(DirectiveAnswer.Selected(ids))
                }
                is GameDirective -> GameQuestion(current) { move ->
                    answer(DirectiveAnswer.Move(move))
                }
                is EmojiDirective -> EmojiQuestion(current) { emoji ->
                    answer(DirectiveAnswer.Emoji(emoji))
                }
                is ChartDirective, is CardsDirective -> Unit
            }
            DirectiveReplyBar(
                onSend = { text -> answer(DirectiveAnswer.FreeText(text)) },
                placeholder = "Type a custom answer"
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Permission: Allow / Deny / Always allow (session) / Full control.
// Full control is only offered when scopes include "full" (SYSTEM.md §2.4).
// ---------------------------------------------------------------------------

@Composable
private fun PermissionQuestion(
    directive: PermissionDirective,
    onAnswer: (String) -> Unit
) {
    Text(
        text = "Permission",
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = MutedColor
    )
    Text(
        text = directive.tool,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = StrongColor
    )
    if (directive.reason.isNotBlank()) {
        Text(text = directive.reason, fontSize = 13.sp, lineHeight = 18.sp, color = MutedColor)
    }
    if (directive.command.isNotBlank()) {
        Text(
            text = directive.command,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF7DD3FC),
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
    if ("once" in directive.scopes) {
        DirectiveButton(label = "Allow", tint = AccentColor, filled = true) {
            onAnswer("once")
        }
    }
    DirectiveButton(label = "Deny", tint = DestructiveColor, filled = false) {
        onAnswer("denied")
    }
    if ("session" in directive.scopes) {
        DirectiveButton(label = "Always allow for this session", tint = AccentColor, filled = false) {
            onAnswer("session")
        }
    }
    if ("full" in directive.scopes) {
        DirectiveButton(label = "Full control", tint = AccentColor, filled = false) {
            onAnswer("full")
        }
    }
}

// ---------------------------------------------------------------------------
// Confirm: yes/no with a frame-paced countdown ring.
// ---------------------------------------------------------------------------

@Composable
private fun ConfirmQuestion(
    directive: ConfirmDirective,
    onAnswer: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = directive.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = StrongColor
            )
            if (directive.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = directive.body, fontSize = 13.sp, lineHeight = 18.sp, color = MutedColor)
            }
            if (directive.deadlineSeconds != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "If you don't answer in time, this " +
                        if (directive.defaultOnTimeout == "confirm") "confirms" else "denies" +
                        " automatically.",
                    fontSize = 11.sp,
                    color = MutedColor
                )
            }
        }
        if (directive.deadlineSeconds != null) {
            Spacer(modifier = Modifier.width(12.dp))
            CountdownRing(
                deadlineSeconds = directive.deadlineSeconds,
                onTimeout = { onAnswer(directive.defaultOnTimeout == "confirm") }
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DirectiveButton(label = directive.denyLabel, tint = DestructiveColor, filled = false, modifier = Modifier.weight(1f)) {
            onAnswer(false)
        }
        DirectiveButton(label = directive.confirmLabel, tint = AccentColor, filled = true, modifier = Modifier.weight(1f)) {
            onAnswer(true)
        }
    }
}

private fun findLifecycleOwner(context: Context): LifecycleOwner? {
    var current: Context? = context
    while (current != null) {
        if (current is LifecycleOwner) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}

/**
 * Smooth countdown ring. Progress improves at most once per display frame
 * (withFrameNanos), only the arc sweep changes (never width/height), and the
 * accumulated elapsed time freezes while the screen is not resumed.
 */
@Composable
private fun CountdownRing(deadlineSeconds: Int, onTimeout: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { findLifecycleOwner(context) }
    var isResumed by remember { mutableIntStateOf(1) }
    DisposableEffect(lifecycleOwner) {
        val owner = lifecycleOwner
        if (owner == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed = 1
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isResumed = 0
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var progress by remember(deadlineSeconds) { mutableFloatStateOf(1f) }
    var remainingSeconds by remember(deadlineSeconds) { mutableFloatStateOf(deadlineSeconds.toFloat()) }
    LaunchedEffect(deadlineSeconds) {
        val totalMs = deadlineSeconds * 1000L
        var accumulatedMs = 0L
        var lastResumeMs = SystemClock.elapsedRealtime()
        var wasResumed = isResumed == 1
        var fired = false
        while (!fired) {
            val resumed = isResumed == 1
            if (resumed != wasResumed) {
                val now = SystemClock.elapsedRealtime()
                if (!resumed) {
                    accumulatedMs += now - lastResumeMs
                } else {
                    lastResumeMs = now
                }
                wasResumed = resumed
            }
            if (!resumed) {
                delay(100)
                continue
            }
            withFrameNanos { }
            val now = SystemClock.elapsedRealtime()
            val elapsed = accumulatedMs + (now - lastResumeMs)
            val remainingMs = totalMs - elapsed
            if (remainingMs <= 0L) {
                progress = 0f
                remainingSeconds = 0f
                fired = true
            } else {
                progress = remainingMs.toFloat() / totalMs.toFloat()
                remainingSeconds = remainingMs / 1000f
            }
        }
        onTimeout()
    }
    val urgent = remainingSeconds <= 15f
    val ringTint = if (urgent) DestructiveColor else AccentColor
    val secondsLabel = ceil(remainingSeconds).toInt().coerceAtLeast(0)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
        Canvas(modifier = Modifier.size(52.dp)) {
            val strokePx = 3.dp.toPx()
            drawArc(
                color = MutedColor.copy(alpha = 0.25f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(strokePx)
            )
            drawArc(
                color = ringTint,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(strokePx, cap = StrokeCap.Round)
            )
        }
        Text(
            text = if (secondsLabel < 60) "${secondsLabel}s" else "${secondsLabel / 60}m",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = ringTint
        )
    }
}

// ---------------------------------------------------------------------------
// Select
// ---------------------------------------------------------------------------

@Composable
private fun SelectQuestion(
    directive: SelectDirective,
    onSubmit: (List<String>) -> Unit
) {
    if (directive.title.isNotBlank()) {
        Text(
            text = directive.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = StrongColor
        )
    }
    var selected by remember(directive) {
        mutableStateOf(
            directive.options.filter { it.checked }.map { it.id }.toSet()
        )
    }
    Text(
        text = if (directive.multi) "Pick any" else "Pick one",
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = MutedColor
    )
    directive.options.forEach { option ->
        val isSelected = option.id in selected
        val rowShape = RoundedCornerShape(12.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(rowShape)
                .background(
                    if (isSelected) AccentColor.copy(alpha = 0.14f)
                    else FieldColor.copy(alpha = 0.55f)
                )
                .border(
                    BorderStroke(
                        0.8.dp,
                        if (isSelected) AccentColor.copy(alpha = 0.45f) else Color.Transparent
                    ),
                    rowShape
                )
                .applePressable(0.97f, pressHaptic = true) {
                    selected = if (directive.multi) {
                        if (isSelected) selected - option.id else selected + option.id
                    } else {
                        setOf(option.id)
                    }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = StrongColor
                )
                if (option.hint.isNotBlank()) {
                    Text(text = option.hint, fontSize = 11.sp, color = MutedColor)
                }
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(if (directive.multi) RoundedCornerShape(4.dp) else CircleShape)
                    .border(
                        BorderStroke(
                            1.5.dp,
                            if (isSelected) AccentColor else MutedColor.copy(alpha = 0.5f)
                        ),
                        if (directive.multi) RoundedCornerShape(4.dp) else CircleShape
                    )
                    .background(if (isSelected) AccentColor else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected && directive.multi) {
                    Text(text = "✓", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                if (isSelected && !directive.multi) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
    }
    val canSubmit = selected.isNotEmpty()
    DirectiveButton(
        label = directive.submitLabel,
        tint = AccentColor,
        filled = canSubmit
    ) {
        if (canSubmit) onSubmit(directive.options.map { it.id }.filter { it in selected })
    }
}

// ---------------------------------------------------------------------------
// Game
// ---------------------------------------------------------------------------

@Composable
private fun GameQuestion(
    directive: GameDirective,
    onMove: (String) -> Unit
) {
    if (directive.title.isNotBlank()) {
        Text(
            text = directive.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = StrongColor
        )
    }
    if (directive.prompt.isNotBlank()) {
        Text(text = directive.prompt, fontSize = 13.sp, color = MutedColor)
    }
    when (directive.game) {
        "rock-paper-scissors" -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("rock", "paper", "scissors").forEach { move ->
                    DirectiveButton(label = move.replaceFirstChar { it.uppercase() }, tint = AccentColor, filled = false, modifier = Modifier.weight(1f)) {
                        onMove(move)
                    }
                }
            }
        }
        "tic-tac-toe" -> {
            val boardRaw = (directive.state["board"] as? List<*>) ?: (directive.state["cells"] as? List<*>)
            val board = boardRaw?.map { it?.toString().orEmpty() } ?: List(9) { "" }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until 3) {
                            val cellIndex = row * 3 + col
                            val mark = board.getOrNull(cellIndex).orEmpty()
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(FieldColor.copy(alpha = 0.55f))
                                    .applePressable(0.97f, pressHaptic = true) {
                                        if (mark.isBlank()) onMove((cellIndex + 1).toString())
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (mark.isNotBlank()) {
                                    Text(text = mark, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = StrongColor)
                                }
                            }
                        }
                    }
                }
            }
        }
        "guess-number" -> {
            val min = (directive.state["min"] as? Number)?.toInt() ?: 1
            val max = (directive.state["max"] as? Number)?.toInt() ?: 100
            var guess by remember(directive) { mutableIntStateOf(min) }
            Text(text = "Number between $min and $max", fontSize = 12.sp, color = MutedColor)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DirectiveButton(label = "-", tint = AccentColor, filled = false, modifier = Modifier.weight(1f)) {
                    guess = maxOf(min, guess - 1)
                }
                Text(
                    text = "$guess",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = StrongColor,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                DirectiveButton(label = "+", tint = AccentColor, filled = false, modifier = Modifier.weight(1f)) {
                    guess = minOf(max, guess + 1)
                }
            }
            DirectiveButton(label = "Guess $guess", tint = AccentColor, filled = true) {
                onMove(guess.toString())
            }
        }
        else -> Unit
    }
}

// ---------------------------------------------------------------------------
// Emoji
// ---------------------------------------------------------------------------

@Composable
private fun EmojiQuestion(
    directive: EmojiDirective,
    onPick: (String) -> Unit
) {
    if (directive.title.isNotBlank()) {
        Text(
            text = directive.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = StrongColor
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        directive.options.chunked(4).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(FieldColor.copy(alpha = 0.55f))
                            .applePressable(0.97f, pressHaptic = true) { onPick(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 22.sp)
                    }
                }
                repeat(4 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared button: 48dp, 12dp radius, 0.97 spring press.
// ---------------------------------------------------------------------------

@Composable
private fun DirectiveButton(
    label: String,
    tint: Color,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val buttonShape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(buttonShape)
            .then(
                if (filled) {
                    Modifier.background(tint)
                } else {
                    Modifier
                        .background(tint.copy(alpha = 0.12f))
                        .border(BorderStroke(0.8.dp, tint.copy(alpha = 0.35f)), buttonShape)
                }
            )
            .applePressable(0.97f, pressHaptic = true) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) Color.White else tint
        )
    }
}

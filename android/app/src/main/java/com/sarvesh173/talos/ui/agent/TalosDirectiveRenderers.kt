package com.sarvesh173.talos.ui.agent

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.appleLiquidGlassOptics
import com.sarvesh173.talos.ui.applePressable
import com.sarvesh173.talos.ui.rememberReducedMotion
import com.sarvesh173.talos.ui.spatialSpring
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Compose renderers for the SYSTEM.md `:::talos` directive blocks.
 *
 * Every block is a Liquid Glass card with generous whitespace and spring
 * motion (Apple HIG discipline, no Material chrome). Interactive blocks
 * return their answer as the exact wire strings SYSTEM.md defines —
 * `selected: ["id", ...]`, `granted: "once"`, `move: "rock"`, `emoji: "🔥"` —
 * through [onReply], which the chat screen sends as the next user turn.
 */

private val SeriesPalette = listOf(
    Color(0xFF007AFF), Color(0xFF34C759), Color(0xFFFF9F0A), Color(0xFFAF52DE)
)

private fun accentColor(name: String): Color = when (name) {
    "blue" -> Color(0xFF007AFF)
    "green" -> Color(0xFF34C759)
    "orange" -> Color(0xFFFF9F0A)
    "red" -> Color(0xFFFF453A)
    "purple" -> Color(0xFFAF52DE)
    else -> Color(0xFF8E8E93)
}

@Composable
fun TalosDirectiveHost(
    directive: TalosDirective,
    isDark: Boolean,
    brandColor: Color,
    onReply: (String) -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val surface = if (isDark) Color(0xFF161822).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.94f)
    val strong = if (isDark) Color.White else Color(0xFF1D1D1F)
    val muted = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.48f)
    var answered by remember(directive) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surface)
            .appleLiquidGlassOptics(isDark = isDark, chromaticGlow = brandColor, shape = shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (directive) {
            is ChartDirective -> ChartBlock(directive, strong, muted, brandColor)
            is SelectDirective -> SelectBlock(directive, isDark, strong, muted, brandColor) { ids ->
                val reply = ids.joinToString(", ") { "\"$it\"" }
                answered = "selected: [$reply]"
                onReply("selected: [$reply]")
            }
            is ConfirmDirective -> ConfirmBlock(directive, isDark, strong, muted, brandColor) { reply ->
                answered = reply
                onReply(reply)
            }
            is PermissionDirective -> PermissionBlock(directive, isDark, strong, muted, brandColor) { reply ->
                answered = reply
                onReply(reply)
            }
            is GameDirective -> GameBlock(directive, isDark, strong, muted, brandColor, onReply)
            is EmojiDirective -> EmojiBlock(directive, isDark, strong, muted, brandColor) { emoji ->
                answered = "emoji: \"$emoji\""
                onReply("emoji: \"$emoji\"")
            }
            is CardsDirective -> CardsBlock(directive, isDark, strong, muted)
        }
        answered?.let { reply ->
            Text(
                text = "Sent · $reply",
                fontSize = 11.sp,
                color = muted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// chart
// ---------------------------------------------------------------------------

@Composable
private fun ChartBlock(d: ChartDirective, strong: Color, muted: Color, brand: Color) {
    if (d.title.isNotBlank()) {
        Text(d.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, color = strong)
    }
    val maxValue = d.series.flatMap { it.values }.filterNotNull().maxOrNull()?.takeIf { it > 0f } ?: 1f
    val reducedMotion = rememberReducedMotion()
    val enter by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spatialSpring(reducedMotion),
        label = "chart_enter"
    )
    val chartHeight = 150.dp
    Box(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val baseline = h - 6.dp.toPx()
            val top = 8.dp.toPx()
            val plotH = (baseline - top).coerceAtLeast(1f)
            // baseline hairline
            drawLine(muted.copy(alpha = 0.25f), Offset(0f, baseline), Offset(w, baseline), 1.dp.toPx())
            d.series.forEachIndexed { sIdx, series ->
                val color = SeriesPalette.getOrElse(sIdx) { brand }
                when (d.kind) {
                    "bar" -> {
                        val n = d.x.size
                        val groupW = w / n
                        val barW = (groupW * 0.62f / d.series.size).coerceAtLeast(3.dp.toPx())
                        series.values.forEachIndexed { i, v ->
                            if (v == null) return@forEachIndexed
                            val frac = (v / maxValue).coerceIn(0f, 1f) * enter
                            val barH = frac * plotH
                            val x0 = i * groupW + (groupW - barW * d.series.size) / 2f + sIdx * barW
                            drawRoundRect(
                                color = color.copy(alpha = 0.9f),
                                topLeft = Offset(x0, baseline - barH),
                                size = Size(barW - 1.5.dp.toPx(), barH),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )
                        }
                    }
                    "line", "area" -> {
                        val n = d.x.size
                        val step = if (n > 1) w / (n - 1) else w
                        val points = series.values.mapIndexed { i, v ->
                            v?.let { Offset(i * step, baseline - (it / maxValue).coerceIn(0f, 1f) * plotH * enter) }
                        }
                        if (d.kind == "area") {
                            val fill = Path()
                            var started = false
                            var firstX = 0f
                            points.forEach { p ->
                                if (p == null) { started = false; return@forEach }
                                if (!started) { fill.moveTo(p.x, p.y); firstX = p.x; started = true } else fill.lineTo(p.x, p.y)
                            }
                            if (started) {
                                fill.lineTo(points.lastOrNull { it != null }?.x ?: firstX, baseline)
                                fill.lineTo(firstX, baseline)
                                fill.close()
                                drawPath(fill, color.copy(alpha = 0.22f))
                            }
                        }
                        var prev: Offset? = null
                        points.forEach { p ->
                            if (p == null) { prev = null; return@forEach }
                            prev?.let { drawLine(color, it, p, 2.2.dp.toPx(), cap = StrokeCap.Round) }
                            drawCircle(color, 3.dp.toPx(), p)
                            prev = p
                        }
                    }
                }
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        d.x.forEach { label ->
            Text(
                text = label,
                fontSize = 11.sp,
                color = muted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
    if (d.series.size > 1 || d.unit.isNotBlank()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            d.series.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(SeriesPalette.getOrElse(i) { brand }))
                    Spacer(Modifier.width(5.dp))
                    Text("${s.name}${if (d.unit.isNotBlank()) " (${d.unit})" else ""}", fontSize = 11.sp, color = muted)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// select
// ---------------------------------------------------------------------------

@Composable
private fun SelectBlock(
    d: SelectDirective,
    isDark: Boolean,
    strong: Color,
    muted: Color,
    brand: Color,
    onSubmit: (List<String>) -> Unit
) {
    if (d.title.isNotBlank()) {
        Text(d.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, color = strong)
    }
    var selectedIds by remember(d) { mutableStateOf(d.options.filter { it.checked }.map { it.id }.toSet()) }
    Text(
        text = if (d.multi) "Pick any" else "Pick one",
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = muted
    )
    d.options.forEach { option ->
        val selected = selectedIds.contains(option.id)
        val rowShape = RoundedCornerShape(12.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(rowShape)
                .background(
                    if (selected) brand.copy(alpha = 0.13f)
                    else if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
                )
                .border(BorderStroke(0.8.dp, if (selected) brand.copy(alpha = 0.45f) else Color.Transparent), rowShape)
                .applePressable(0.97f, pressHaptic = true) {
                    selectedIds = if (d.multi) {
                        if (selected) selectedIds - option.id else selectedIds + option.id
                    } else {
                        setOf(option.id)
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(option.label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = strong)
                if (option.hint.isNotBlank()) Text(option.hint, fontSize = 11.sp, color = muted)
            }
            // tick (single) or checkbox (multi), drawn to the 18dp metric
            if (d.multi) {
                Box(
                    modifier = Modifier.size(18.dp).clip(RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.5.dp, if (selected) brand else muted.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
                        .background(if (selected) brand else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Text("✓", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                Box(
                    modifier = Modifier.size(18.dp).clip(CircleShape)
                        .border(BorderStroke(1.5.dp, if (selected) brand else muted.copy(alpha = 0.5f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Box(Modifier.size(9.dp).clip(CircleShape).background(brand))
                }
            }
        }
    }
    val canSubmit = selectedIds.isNotEmpty()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (canSubmit) brand else (if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)))
            .applePressable(0.96f, pressHaptic = true) { if (canSubmit) onSubmit(d.options.map { it.id }.filter { it in selectedIds }) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            d.submitLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (canSubmit) Color.White else muted
        )
    }
}

// ---------------------------------------------------------------------------
// confirm (countdown ring + default_on_timeout)
// ---------------------------------------------------------------------------

@Composable
private fun ConfirmBlock(
    d: ConfirmDirective,
    isDark: Boolean,
    strong: Color,
    muted: Color,
    brand: Color,
    onAnswer: (String) -> Unit
) {
    var remaining by remember(d) { mutableStateOf(d.deadlineSeconds ?: -1) }
    var answered by remember(d) { mutableStateOf(false) }
    LaunchedEffect(d) {
        val ttl = d.deadlineSeconds ?: return@LaunchedEffect
        while (remaining > 0 && !answered) {
            delay(1000)
            remaining -= 1
        }
        if (remaining == 0 && !answered) {
            answered = true
            onAnswer(if (d.defaultOnTimeout == "confirm") "confirm (timed out — default applied)" else "deny (timed out — default applied)")
        }
    }
    val urgent = d.deadlineSeconds != null && remaining in 0..15
    val ringTint = if (urgent) Color(0xFFFF453A) else brand
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(d.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, color = strong)
            if (d.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(d.body, fontSize = 13.sp, lineHeight = 18.sp, color = muted)
            }
            if (d.deadlineSeconds != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "If you don't answer in time, this ${if (d.defaultOnTimeout == "confirm") "confirms" else "denies"} automatically.",
                    fontSize = 11.sp,
                    color = muted
                )
            }
        }
        if (d.deadlineSeconds != null) {
            Spacer(Modifier.width(12.dp))
            val progress = if (d.deadlineSeconds > 0) (remaining.toFloat() / d.deadlineSeconds).coerceIn(0f, 1f) else 0f
            val animated by animateFloatAsState(progress, label = "confirm_ring")
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
                Canvas(Modifier.size(52.dp)) {
                    drawArc(muted.copy(alpha = 0.18f), -90f, 360f, useCenter = false, style = Stroke(3.dp.toPx()))
                    drawArc(ringTint, -90f, 360f * animated, useCenter = false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
                Text(
                    text = if (remaining <= 0) "0" else if (remaining < 60) "${remaining}s" else "${remaining / 60}m",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = ringTint
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BlockButton(d.denyLabel, Color(0xFFFF453A), filled = false, isDark = isDark, modifier = Modifier.weight(1f)) {
            if (!answered) { answered = true; onAnswer("deny") }
        }
        BlockButton(d.confirmLabel, brand, filled = true, isDark = isDark, modifier = Modifier.weight(1f)) {
            if (!answered) { answered = true; onAnswer("confirm") }
        }
    }
}

@Composable
private fun BlockButton(
    label: String,
    tint: Color,
    filled: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .then(
                if (filled) Modifier.background(tint)
                else Modifier.background(tint.copy(alpha = 0.12f)).border(BorderStroke(0.9.dp, tint.copy(alpha = 0.35f)), shape)
            )
            .applePressable(0.96f, pressHaptic = true) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (filled) Color.White else tint)
    }
}

// ---------------------------------------------------------------------------
// permission (Allow / Deny / Always allow for this session / Full control)
// ---------------------------------------------------------------------------

@Composable
private fun PermissionBlock(
    d: PermissionDirective,
    isDark: Boolean,
    strong: Color,
    muted: Color,
    brand: Color,
    onAnswer: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Permission", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = muted, modifier = Modifier.weight(1f))
        Text(d.tool, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = brand)
    }
    if (d.reason.isNotBlank()) Text(d.reason, fontSize = 14.sp, lineHeight = 19.sp, color = strong)
    if (d.command.isNotBlank()) {
        Text(
            d.command,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (isDark) Color(0xFF0D0D10) else Color(0xFFF2F2F7)).padding(12.dp),
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
    }
    val scopeLabels = mapOf(
        "once" to "Allow once",
        "session" to "Always allow for this session",
        "full" to "Full control"
    )
    d.scopes.forEach { scope ->
        BlockButton(scopeLabels[scope] ?: scope, brand, filled = scope == "once", isDark = isDark) {
            onAnswer("granted: \"$scope\"")
        }
    }
    BlockButton("Deny", Color(0xFFFF453A), filled = false, isDark = isDark) {
        onAnswer("granted: \"denied\"")
    }
}

// ---------------------------------------------------------------------------
// game
// ---------------------------------------------------------------------------

@Composable
private fun GameBlock(
    d: GameDirective,
    isDark: Boolean,
    strong: Color,
    muted: Color,
    brand: Color,
    onReply: (String) -> Unit
) {
    if (d.title.isNotBlank()) Text(d.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, color = strong)
    val round = (d.state["round"] as? Number)?.toInt()
    val player = (d.state["player"] as? Number)?.toInt()
    val agent = (d.state["agent"] as? Number)?.toInt()
    if (round != null || player != null || agent != null) {
        Text(
            buildString {
                if (round != null) append("Round $round")
                if (player != null) append("  ·  You $player")
                if (agent != null) append("  ·  Agent $agent")
            },
            fontSize = 11.sp, color = muted
        )
    }
    if (d.prompt.isNotBlank()) Text(d.prompt, fontSize = 13.sp, color = muted)

    when (d.game) {
        "rock-paper-scissors" -> {
            val moves = listOf("rock" to "✊", "paper" to "✋", "scissors" to "✌️")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                moves.forEach { (move, glyph) ->
                    Box(
                        modifier = Modifier.weight(1f).heightIn(min = 60.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
                            .applePressable(0.94f, pressHaptic = true) { onReply("move: \"$move\"") },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(glyph, fontSize = 24.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(move, fontSize = 11.sp, color = muted)
                        }
                    }
                }
            }
        }
        "tic-tac-toe" -> {
            val boardRaw = (d.state["board"] as? List<*>) ?: (d.state["cells"] as? List<*>)
            var localBoard by remember(d) { mutableStateOf(boardRaw?.map { it?.toString() ?: "" } ?: List(9) { "" }) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until 3) {
                            val i = row * 3 + col
                            val mark = localBoard.getOrNull(i).orEmpty()
                            Box(
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (mark == "X") brand.copy(alpha = 0.16f)
                                        else if (mark.isNotBlank()) Color(0xFFFF9F0A).copy(alpha = 0.16f)
                                        else if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
                                    )
                                    .applePressable(0.96f, pressHaptic = true) {
                                        if (mark.isBlank()) {
                                            localBoard = localBoard.toMutableList().apply { this[i] = "X" }
                                            onReply("move: ${i + 1}")
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (mark.isNotBlank()) Text(mark, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (mark == "X") brand else Color(0xFFFF9F0A))
                            }
                        }
                    }
                }
            }
        }
        "guess-number" -> {
            val min = (d.state["min"] as? Number)?.toInt() ?: 1
            val max = (d.state["max"] as? Number)?.toInt() ?: 100
            val attempts = (d.state["attempts"] as? Number)?.toInt()
            var guess by remember(d) { mutableStateOf(min) }
            Text(
                buildString {
                    append("Number between $min and $max")
                    if (attempts != null) append(" · attempt $attempts")
                },
                fontSize = 12.sp, color = muted
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepperButton("−", isDark, brand) { guess = max(min, guess - 1) }
                Text("$guess", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = strong, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                StepperButton("+", isDark, brand) { guess = min(max, guess + 1) }
            }
            BlockButton("Guess $guess", brand, filled = true, isDark = isDark) { onReply("move: $guess") }
        }
    }
}

@Composable
private fun StepperButton(label: String, isDark: Boolean, brand: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape)
            .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f))
            .applePressable(0.94f, pressHaptic = true) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = brand)
    }
}

// ---------------------------------------------------------------------------
// emoji
// ---------------------------------------------------------------------------

@Composable
private fun EmojiBlock(
    d: EmojiDirective,
    isDark: Boolean,
    strong: Color,
    muted: Color,
    brand: Color,
    onPick: (String) -> Unit
) {
    if (d.title.isNotBlank()) Text(d.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, color = strong)
    val cols = 4
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        d.options.chunked(cols).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { emoji ->
                    Box(
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
                            .applePressable(0.92f, pressHaptic = true) { onPick(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 26.sp)
                    }
                }
                repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// cards
// ---------------------------------------------------------------------------

@Composable
private fun CardsBlock(d: CardsDirective, isDark: Boolean, strong: Color, muted: Color) {
    if (d.title.isNotBlank()) Text(d.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, color = strong)
    d.items.forEach { item ->
        val accent = accentColor(item.accent)
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (item.badge.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(item.badge, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
                }
            }
        }
    }
}

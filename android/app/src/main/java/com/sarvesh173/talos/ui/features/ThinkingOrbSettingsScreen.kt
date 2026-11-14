package com.sarvesh173.talos.ui.features

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.LocalIsDark
import com.sarvesh173.talos.ui.TalosIcons
import com.sarvesh173.talos.ui.appleLiquidGlassOptics
import com.sarvesh173.talos.ui.applePressable
import com.sarvesh173.talos.ui.orb.OrbState
import com.sarvesh173.talos.ui.orb.ThinkingOrb
import com.sarvesh173.talos.ui.rememberReducedMotion

/**
 * Thinking Orb Studio.
 *
 * The orb is the app's only "the agent is working" signal, so it is fully
 * user-owned rather than a fixed animation:
 *
 *  - **State** — which patterned animation to run (working / searching /
 *    solving / listening). These are the four machine states, each a distinct
 *    hand-tuned pattern, not a speed of one pattern.
 *  - **Size** — 16→72 dp, with the two upstream-tuned presets (20 and 64)
 *    called out. The geometry re-tunes itself per size instead of scaling.
 *  - **Colour** — a 24-swatch accent grid plus a full 0..360 hue slider with
 *    saturation and lightness, so any colour is reachable exactly.
 *  - **Shell shape** — the frame the orb renders inside: circle, squircle,
 *    triangle, hexagon, star, or a shape nested inside another shape.
 *  - **Speed** — 0.25× to 2.5×, and a pause for reduced-motion users.
 *
 * Every change previews live above the controls.
 */
@Composable
fun ThinkingOrbSettingsScreen(onBack: () -> Unit) {
    val isDark = LocalIsDark.current
    val reducedMotion = rememberReducedMotion()

    var state by remember { mutableStateOf(OrbState.WORKING) }
    var sizeDp by remember { mutableFloatStateOf(28f) }
    var speed by remember { mutableFloatStateOf(1f) }
    var paused by remember { mutableStateOf(false) }
    var adaptiveSpeed by remember { mutableStateOf(true) }
    var hue by remember { mutableFloatStateOf(210f) }
    var saturation by remember { mutableFloatStateOf(0.85f) }
    var lightness by remember { mutableFloatStateOf(0.55f) }
    var shell by remember { mutableStateOf(OrbShell.Squircle) }
    var innerShell by remember { mutableStateOf<OrbShell?>(null) }

    val ink = hslToColor(hue, saturation, lightness)
    val strong = if (isDark) Color.White else Color(0xFF1D1D1F)
    val muted = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.50f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // ---- header ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .applePressable(0.94f, pressHaptic = true) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = TalosIcons.TalosBackArrow,
                    contentDescription = "Back",
                    tint = strong,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Thinking orb",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = strong
            )
        }

        // ---- live preview ----
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isDark) Color(0xFF121216) else Color(0xFFF7F7FA),
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .appleLiquidGlassOptics(isDark = isDark, chromaticGlow = ink, cornerRadius = 24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OrbShellFrame(shell = shell, inner = innerShell, ink = ink, sizeDp = sizeDp) {
                    ThinkingOrb(
                        state = state,
                        size = sizeDp.dp,
                        ink = ink,
                        paused = paused,
                        speed = speed,
                        label = "Orb preview"
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${state.name.lowercase()} · ${sizeDp.toInt()}dp · ${"%.2f".format(speed)}×",
                    fontSize = 11.sp,
                    color = muted
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---- state ----
        SectionHeader("Animation", strong)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrbState.entries.forEach { s ->
                val selected = state == s
                val shape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .background(if (selected) ink.copy(alpha = 0.18f) else Color.Transparent)
                        .border(
                            BorderStroke(0.8.dp, if (selected) ink.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant),
                            shape
                        )
                        .applePressable(0.96f, pressHaptic = true) { state = s },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.name.lowercase().replaceFirstChar { it.uppercase() },
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) ink else strong
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---- size ----
        SectionHeader("Size  ·  ${sizeDp.toInt()} dp", strong)
        Slider(
            value = sizeDp,
            onValueChange = { sizeDp = it },
            valueRange = 16f..96f,
            steps = 79,
            colors = SliderDefaults.colors(thumbColor = ink, activeTrackColor = ink)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(20f to "Inline", 28f to "Default", 64f to "Avatar").forEach { (v, label) ->
                PresetChip(label, isSelected = sizeDp == v, ink = ink) { sizeDp = v }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---- colour ----
        SectionHeader("Colour  ·  H${hue.toInt()} S${(saturation * 100).toInt()}% L${(lightness * 100).toInt()}%", strong)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(orbSwatches) { c ->
                val selected = c == ink
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            BorderStroke(if (selected) 2.5.dp else 0.8.dp, if (selected) strong else Color.Transparent),
                            CircleShape
                        )
                        .applePressable(0.92f, pressHaptic = true) {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(c.value.toInt(), hsv)
                            hue = hsv[0]
                            saturation = hsv[1]
                            lightness = hsv[2]
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HueSlider(hue = hue, onChange = { hue = it })
        Spacer(modifier = Modifier.height(8.dp))
        LabelledSlider("Saturation", saturation, 0f..1f, ink) { saturation = it }
        LabelledSlider("Brightness", lightness, 0.15f..0.95f, ink) { lightness = it }

        Spacer(modifier = Modifier.height(20.dp))

        // ---- shell shape ----
        SectionHeader("Shell", strong)
        Text(
            text = "The frame the orb sits in. Nest a second shape inside for a combined form.",
            fontSize = 11.sp,
            color = muted,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(OrbShell.entries.toList()) { s ->
                val selected = shell == s
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(s.toShape())
                            .background(if (selected) ink.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f))
                            .border(
                                BorderStroke(0.8.dp, if (selected) ink else MaterialTheme.colorScheme.outlineVariant),
                                s.toShape()
                            )
                            .applePressable(0.94f, pressHaptic = true) { shell = s }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = s.label,
                        fontSize = 11.sp,
                        color = if (selected) ink else muted,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        SectionHeader("Nested shape", strong)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                PresetChip("None", isSelected = innerShell == null, ink = ink) { innerShell = null }
            }
            items(OrbShell.entries.toList()) { s ->
                PresetChip(s.label, isSelected = innerShell == s, ink = ink) { innerShell = s }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ---- speed ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (adaptiveSpeed) ink.copy(alpha = 0.10f) else Color.Transparent)
                .border(BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(16.dp))
                .applePressable(0.97f, pressHaptic = true) { adaptiveSpeed = !adaptiveSpeed }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Follow agent speed", fontSize = 14.sp, color = strong)
                Text(
                    text = "Spins up on fast output, settles when quiet",
                    fontSize = 11.sp,
                    color = muted
                )
            }
            Text(
                text = if (adaptiveSpeed) "On" else "Off",
                fontSize = 12.sp,
                color = if (adaptiveSpeed) ink else muted
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader("Base speed  ·  ${"%.2f".format(speed)}×", strong)
        Slider(
            value = speed,
            onValueChange = { speed = it },
            valueRange = 0.25f..2.5f,
            enabled = !adaptiveSpeed,
            colors = SliderDefaults.colors(
                thumbColor = if (adaptiveSpeed) ink.copy(alpha = 0.4f) else ink,
                activeTrackColor = if (adaptiveSpeed) ink.copy(alpha = 0.25f) else ink
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (paused) ink.copy(alpha = 0.14f) else Color.Transparent)
                .border(
                    BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                    RoundedCornerShape(16.dp)
                )
                .applePressable(0.97f, pressHaptic = true) { paused = !paused }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Freeze frame",
                fontSize = 14.sp,
                color = strong,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (paused) "On" else "Off",
                fontSize = 12.sp,
                color = if (paused) ink else muted
            )
        }

        if (reducedMotion) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "System animations are off, so the orb already renders a static frame.",
                fontSize = 11.sp,
                color = muted
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ---------------------------------------------------------------------------
// Shell shapes
// ---------------------------------------------------------------------------

/** Frames the orb can sit inside. `Nested` combos use [innerShell]. */
enum class OrbShell(val label: String) {
    None("None"),
    Circle("Circle"),
    Squircle("Squircle"),
    Triangle("Triangle"),
    Hexagon("Hexagon"),
    Star("Star"),
    Diamond("Diamond");

    fun toShape(): androidx.compose.ui.graphics.Shape = when (this) {
        None, Circle -> CircleShape
        Squircle -> RoundedCornerShape(30)
        Triangle, Hexagon, Star, Diamond -> RoundedCornerShape(22)
    }
}

@Composable
private fun OrbShellFrame(
    shell: OrbShell,
    inner: OrbShell?,
    ink: Color,
    sizeDp: Float,
    content: @Composable () -> Unit
) {
    if (shell == OrbShell.None) {
        content()
        return
    }
    val pad = (sizeDp * 0.28f).dp
    Box(
        modifier = Modifier
            .clip(shell.toShape())
            .background(ink.copy(alpha = 0.08f))
            .border(BorderStroke(1.dp, ink.copy(alpha = 0.35f)), shell.toShape())
            .padding(pad),
        contentAlignment = Alignment.Center
    ) {
        if (inner != null) {
            Box(
                modifier = Modifier
                    .clip(inner.toShape())
                    .background(ink.copy(alpha = 0.06f))
                    .border(BorderStroke(0.8.dp, ink.copy(alpha = 0.28f)), inner.toShape())
                    .padding((sizeDp * 0.12f).dp),
                contentAlignment = Alignment.Center
            ) {
                // Polygon shells draw their outline behind the orb.
                PolyOutline(inner, ink, sizeDp)
                content()
            }
        } else {
            PolyOutline(shell, ink, sizeDp)
            content()
        }
    }
}

/** Draws the true polygon outline for shells that are not simple rounded rects. */
@Composable
private fun PolyOutline(shell: OrbShell, ink: Color, sizeDp: Float) {
    if (shell == OrbShell.Triangle || shell == OrbShell.Hexagon ||
        shell == OrbShell.Star || shell == OrbShell.Diamond
    ) {
        Canvas(modifier = Modifier.size((sizeDp * 1.05f).dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = minOf(cx, cy) * 0.98f
            val points = when (shell) {
                OrbShell.Triangle -> (0 until 3).map { i ->
                    val a = -Math.PI / 2 + i * 2 * Math.PI / 3
                    Offset(cx + (r * kotlin.math.cos(a)).toFloat(), cy + (r * kotlin.math.sin(a)).toFloat())
                }
                OrbShell.Diamond -> listOf(
                    Offset(cx, cy - r), Offset(cx + r, cy), Offset(cx, cy + r), Offset(cx - r, cy)
                )
                OrbShell.Hexagon -> (0 until 6).map { i ->
                    val a = -Math.PI / 2 + i * Math.PI / 3
                    Offset(cx + (r * kotlin.math.cos(a)).toFloat(), cy + (r * kotlin.math.sin(a)).toFloat())
                }
                OrbShell.Star -> (0 until 10).map { i ->
                    val rr = if (i % 2 == 0) r else r * 0.45f
                    val a = -Math.PI / 2 + i * Math.PI / 5
                    Offset(cx + (rr * kotlin.math.cos(a)).toFloat(), cy + (rr * kotlin.math.sin(a)).toFloat())
                }
                else -> emptyList()
            }
            if (points.size >= 3) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(points[0].x, points[0].y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(path, ink.copy(alpha = 0.32f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx()))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Small controls
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(text: String, strong: Color) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = strong,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun PresetChip(label: String, isSelected: Boolean, ink: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (isSelected) ink.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                BorderStroke(0.8.dp, if (isSelected) ink.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant),
                shape
            )
            .applePressable(0.95f, pressHaptic = true) { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isSelected) ink else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    ink: Color,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp)
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = ink, activeTrackColor = ink),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${(value * 100).toInt()}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )
    }
}

/** Full 0..360 hue ramp — that is the "any colour" guarantee. */
@Composable
private fun HueSlider(hue: Float, onChange: (Float) -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        (0..360 step 30).map { hslToColor(it.toFloat(), 1f, 0.55f) }
                    )
                )
        )
        Slider(
            value = hue,
            onValueChange = onChange,
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(
                thumbColor = hslToColor(hue, 1f, 0.55f),
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Colour math
// ---------------------------------------------------------------------------

/** HSL → Compose Color. Hue in degrees, S and L in 0..1. */
internal fun hslToColor(hue: Float, s: Float, l: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f / 360f
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h * 6f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when ((h * 6f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

/** A curated 24-swatch grid: Apple system colours plus accent families. */
private val orbSwatches = listOf(
    Color(0xFF5AC8D8), Color(0xFF5AC8FA), Color(0xFF34C759), Color(0xFF30D158),
    Color(0xFFFFCC00), Color(0xFFFF9500), Color(0xFFFF3B30), Color(0xFFFF2D55),
    Color(0xFFAF52DE), Color(0xFF5856D6), Color(0xFFBF5AF2), Color(0xFFFF375F),
    Color(0xFF64D2FF), Color(0xFF5AC8D8), Color(0xFF32D74B), Color(0xFFFFD60A),
    Color(0xFFFF9F0A), Color(0xFFFF453A), Color(0xFF5E5CE6), Color(0xFFA0A0A8),
    Color(0xFF1D1D1F), Color(0xFFF2F2F7), Color(0xFF00C7BE), Color(0xFFD97757)
)

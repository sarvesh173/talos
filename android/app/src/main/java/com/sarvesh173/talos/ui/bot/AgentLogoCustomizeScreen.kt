package com.sarvesh173.talos.ui.bot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.emblemByKey
import kotlin.math.roundToInt

// Monochrome instrument panel: #000000 canvas, #1C1C1E cards, white / #8E8E93
// text, ONE accent (white) reserved for the active slider thumb. No gradients,
// no glow, no emoji, sentence case, 4dp grid throughout.
private val CardSurface = Color(0xFF1C1C1E)
private val PrimaryText = Color(0xFFFFFFFF)
private val SecondaryText = Color(0xFF8E8E93)
private val ThumbAccent = Color(0xFFFFFFFF)
private val TrackGrey = Color(0xFF48484A)
private val TrackIdle = Color(0xFF2C2C2E)
private val BorderOptions = listOf(
    "#38383A" to Color(0xFF38383A),
    "#48484A" to Color(0xFF48484A),
    "#8E8E93" to Color(0xFF8E8E93),
    "#FFFFFF" to Color(0xFFFFFFFF)
)

/**
 * Settings sub-screen exposing every tuned parameter of the ThinkingLogoPro
 * point-cloud animation ([LogoCustomization]) for one agent, with a live
 * preview driven by a real [AgentLogoMark].
 *
 * Live wiring: `speedMultiplier` feeds [AgentLogoMark]'s `intensity`,
 * `tintMode`/`customTintHex` feed its `tint`, and bake-time dials (`count`,
 * `depth`, `shell`, `seed`) trigger a debounced rebake via
 * [AgentLogoBaker.bakeFromVectorDrawable] + [AgentLogoClouds]. `dwellSeconds`
 * / `morphSeconds` / `spin` / `band` / `wobble` / `scan` / `dimBase` / `spread`
 * are persisted per agent and resolved through
 * [LogoCustomization.effectiveFor] for the motion profiles that consume them.
 * Every change saves immediately ([LogoCustomization.save]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentLogoCustomizeScreen(
    agentId: String,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var custom by remember(agentId) {
        mutableStateOf(LogoCustomization.load(context, agentId))
    }
    var selectedState by remember(agentId) { mutableStateOf(AgentLogoState.Thinking) }
    var paused by remember(agentId) { mutableStateOf(false) }
    var rebakeTick by remember(agentId) { mutableIntStateOf(0) }

    // Persist every change immediately.
    LaunchedEffect(custom) {
        LogoCustomization.save(context, agentId, custom)
    }

    // Debounced rebake for bake-time dials so dragging a slider does not bake
    // on every tick; the preview rebuilds once the cloud is replaced.
    LaunchedEffect(custom.count, custom.depth, custom.shell, custom.seed) {
        kotlinx.coroutines.delay(400)
        val cloud = AgentLogoBaker.bakeFromVectorDrawable(
            context = context,
            drawableRes = emblemByKey(agentId.lowercase()).drawableRes,
            count = custom.count,
            seed = custom.seed.toLong(),
            depth = custom.bakeDepth()
        )
        AgentLogoClouds.clear()
        AgentLogoClouds.put(agentId, cloud)
        rebakeTick++
    }

    val resolved = custom.effectiveFor(selectedState)
    val borderTint = custom.borderTint()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header row (plain text back button, sentence case).
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Text(
                    text = "Back",
                    fontSize = 15.sp,
                    color = PrimaryText,
                    modifier = Modifier.clickable(onClick = onBack)
                )
                Spacer(Modifier.width(16.dp))
            }
            Text(
                text = "Logo customization",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryText
            )
        }
        Text(
            text = "Tune every dial of the ${agentId.lowercase()} point cloud. Changes apply to the preview immediately and save automatically.",
            fontSize = 13.sp,
            color = SecondaryText
        )

        // Live preview: a real AgentLogoMark, never a mock.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, borderTint, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            key(rebakeTick) {
                AgentLogoMark(
                    agentId = agentId,
                    state = selectedState,
                    size = 160.dp,
                    intensity = resolved.speedMultiplier,
                    tint = custom.resolvedTint(agentId),
                    paused = paused
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = if (paused) "Resume" else "Pause",
                fontSize = 14.sp,
                color = PrimaryText,
                modifier = Modifier.clickable { paused = !paused }
            )
            Text(
                text = "Global reset",
                fontSize = 14.sp,
                color = PrimaryText,
                modifier = Modifier.clickable {
                    LogoCustomization.reset(context, agentId)
                    custom = LogoCustomization.defaults(agentId)
                }
            )
        }

        // State presets: the seven ThinkingLogoPro states as plain text chips.
        GroupCard(title = "State", borderTint = borderTint, onReset = null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (state in AgentLogoState.entries) {
                    TextChip(
                        label = state.name.lowercase(),
                        selected = state == selectedState,
                        borderTint = borderTint,
                        onClick = { selectedState = state }
                    )
                }
            }
        }

        // Dots
        GroupCard(
            title = "Dots",
            borderTint = borderTint,
            onReset = { custom = custom.copy(count = 220, dotRadius = 1f, opacityCurve = OpacityCurve.Linear, opacityStrength = 1f) }
        ) {
            TuneSlider(
                label = "Count",
                value = custom.count.toFloat(),
                range = 40f..600f,
                step = 1f,
                format = { it.roundToInt().toString() },
                onChange = { custom = custom.copy(count = it.roundToInt()) }
            )
            TuneSlider(
                label = "Dot radius",
                value = custom.dotRadius,
                range = 0.5f..2.5f,
                step = 0.05f,
                format = { "%.2f".format(it) },
                onChange = { custom = custom.copy(dotRadius = snap(it, 0.05f)) }
            )
            TuneSlider(
                label = "Opacity strength",
                value = custom.opacityStrength,
                range = 0f..2f,
                step = 0.05f,
                format = { "%.2f".format(it) },
                onChange = { custom = custom.copy(opacityStrength = snap(it, 0.05f)) }
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (curve in OpacityCurve.entries) {
                    TextChip(
                        label = curve.name.lowercase(),
                        selected = custom.opacityCurve == curve,
                        borderTint = borderTint,
                        onClick = { custom = custom.copy(opacityCurve = curve) }
                    )
                }
            }
        }

        // Geometry (bake)
        GroupCard(
            title = "Geometry",
            borderTint = borderTint,
            onReset = { custom = custom.copy(shell = ShellStyle.Dome, depth = 0.34f, margin = 0.06f, threshold = 0.5f, resolution = 256, seed = 1) }
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (style in ShellStyle.entries) {
                    TextChip(
                        label = style.name.lowercase(),
                        selected = custom.shell == style,
                        borderTint = borderTint,
                        onClick = { custom = custom.copy(shell = style) }
                    )
                }
            }
            TuneSlider(
                label = "Depth",
                value = custom.depth,
                range = 0f..0.6f,
                step = 0.01f,
                format = { "%.2f".format(it) },
                onChange = { custom = custom.copy(depth = snap(it, 0.01f)) }
            )
            TuneSlider(
                label = "Margin",
                value = custom.margin,
                range = 0f..0.2f,
                step = 0.005f,
                format = { "%.3f".format(it) },
                onChange = { custom = custom.copy(margin = snap(it, 0.005f)) }
            )
            TuneSlider(
                label = "Threshold",
                value = custom.threshold,
                range = 0.3f..0.7f,
                step = 0.01f,
                format = { "%.2f".format(it) },
                onChange = { custom = custom.copy(threshold = snap(it, 0.01f)) }
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (res in LogoCustomization.RESOLUTIONS) {
                    TextChip(
                        label = res.toString(),
                        selected = custom.resolution == res,
                        borderTint = borderTint,
                        onClick = { custom = custom.copy(resolution = res) }
                    )
                }
            }
            TuneSlider(
                label = "Seed",
                value = custom.seed.toFloat(),
                range = 0f..999f,
                step = 1f,
                format = { it.roundToInt().toString() },
                onChange = { custom = custom.copy(seed = it.roundToInt()) }
            )
        }

        // Motion
        GroupCard(
            title = "Motion",
            borderTint = borderTint,
            onReset = {
                custom = custom.copy(
                    dwellSeconds = 5.5f, morphSeconds = 1.9f, speedMultiplier = 1f,
                    spinMultiplier = 1f, bandMultiplier = 1f, wobbleMultiplier = 1f,
                    scanMultiplier = 1f, dimBase = 0.4f, spreadMultiplier = 1f
                )
            }
        ) {
            TuneSlider(
                label = "Dwell seconds",
                value = custom.dwellSeconds,
                range = 1f..12f,
                step = 0.1f,
                format = { "%.1f".format(it) },
                onChange = { custom = custom.copy(dwellSeconds = snap(it, 0.1f)) }
            )
            TuneSlider(
                label = "Morph seconds",
                value = custom.morphSeconds,
                range = 0.5f..5f,
                step = 0.1f,
                format = { "%.1f".format(it) },
                onChange = { custom = custom.copy(morphSeconds = snap(it, 0.1f)) }
            )
            TuneSlider(
                label = "Speed",
                value = custom.speedMultiplier,
                range = 0.25f..4f,
                step = 0.05f,
                format = { "%.2f".format(it) },
                onChange = { custom = custom.copy(speedMultiplier = snap(it, 0.05f)) }
            )
            TuneSlider(
                label = "Spin",
                value = custom.spinMultiplier,
                range = 0f..4f,
                step = 0.05f,
                format = { "%.2f".format(it) },
                onChange = { custom = custom.copy(spinMultiplier = snap(it, 0.05f)) }
            )
            TuneSlider(
                label = "Band",
                value = custom.bandMultiplier,
                range = 0f..5f,
                step = 0.05f,
                format = { "%.2f".format(it) },
                onChange = { custom = custom.copy(bandMultiplier = snap(it, 0.05f)) }
            )
            TuneSlider(
                label = "Wobble",
                value = custom.wobbleMultiplier,
                range = 0f..3f,
                step = 0.05f,
                format = { "%.2f".format(it) },
                onChange = { custom = custom.copy(wobbleMultiplier = snap(it, 0.05f)) }
            )
            TuneSlider(
                label = "Scan",
                value = custom.scanMultiplier,
                range = 0f..5f,
                step = 0.05f,
                format = { "%.2f".format(it) },
                onChange = { custom = custom.copy(scanMultiplier = snap(it, 0.05f)) }
            )
            TuneSlider(
                label = "Dim base",
                value = custom.dimBase,
                range = 0f..1f,
                step = 0.01f,
                format = { "%.2f".format(it) },
                onChange = { custom = custom.copy(dimBase = snap(it, 0.01f)) }
            )
            TuneSlider(
                label = "Spread",
                value = custom.spreadMultiplier,
                range = 0.5f..2f,
                step = 0.05f,
                format = { "%.2f".format(it) },
                onChange = { custom = custom.copy(spreadMultiplier = snap(it, 0.05f)) }
            )
        }

        // Per-state overrides
        StateOverridesCard(
            custom = custom,
            borderTint = borderTint,
            onChange = { custom = it }
        )

        // Colour
        GroupCard(
            title = "Colour",
            borderTint = borderTint,
            onReset = { custom = custom.copy(tintMode = TintMode.Brand, customTintHex = "#FFFFFF", borderTintHex = "#38383A") }
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextChip(
                    label = "Brand",
                    selected = custom.tintMode == TintMode.Brand,
                    borderTint = borderTint,
                    onClick = { custom = custom.copy(tintMode = TintMode.Brand) }
                )
                TextChip(
                    label = "Mono white",
                    selected = custom.tintMode == TintMode.MonoWhite,
                    borderTint = borderTint,
                    onClick = { custom = custom.copy(tintMode = TintMode.MonoWhite) }
                )
                TextChip(
                    label = "Mono black",
                    selected = custom.tintMode == TintMode.MonoBlack,
                    borderTint = borderTint,
                    onClick = { custom = custom.copy(tintMode = TintMode.MonoBlack) }
                )
                TextChip(
                    label = "Custom",
                    selected = custom.tintMode == TintMode.Custom,
                    borderTint = borderTint,
                    onClick = { custom = custom.copy(tintMode = TintMode.Custom) }
                )
            }
            if (custom.tintMode == TintMode.Custom) {
                OutlinedTextField(
                    value = custom.customTintHex,
                    onValueChange = { custom = custom.copy(customTintHex = it) },
                    label = { Text("Custom hex", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText,
                        focusedLabelColor = SecondaryText,
                        unfocusedLabelColor = SecondaryText,
                        cursorColor = PrimaryText
                    )
                )
            }
            Text(
                text = "Border tint",
                fontSize = 13.sp,
                color = SecondaryText
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((hex, color) in BorderOptions) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                            .border(
                                2.dp,
                                if (custom.borderTintHex.equals(hex, ignoreCase = true)) PrimaryText else borderTint,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { custom = custom.copy(borderTintHex = hex) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Building blocks (private; Compose Material3 only — every import used exists)
// ---------------------------------------------------------------------------

@Composable
private fun GroupCard(
    title: String,
    borderTint: Color,
    onReset: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardSurface)
            .border(1.dp, borderTint, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryText
            )
            if (onReset != null) {
                Text(
                    text = "Reset",
                    fontSize = 13.sp,
                    color = SecondaryText,
                    modifier = Modifier.clickable(onClick = onReset)
                )
            }
        }
        content()
    }
}

@Composable
private fun TextChip(
    label: String,
    selected: Boolean,
    borderTint: Color,
    onClick: () -> Unit
) {
    Text(
        text = label,
        fontSize = 13.sp,
        color = if (selected) PrimaryText else SecondaryText,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(
                1.dp,
                if (selected) PrimaryText else borderTint,
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

/**
 * Labelled slider showing its numeric value, snapped to [step]. The single
 * accent (white) is reserved for the thumb; tracks stay neutral greys.
 */
@Composable
private fun TuneSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 13.sp, color = SecondaryText)
            Text(text = format(value), fontSize = 13.sp, color = PrimaryText)
        }
        val steps = sliderSteps(range, step)
        Slider(
            value = value,
            onValueChange = { onChange(snap(it, step)) },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = ThumbAccent,
                activeTrackColor = TrackGrey,
                inactiveTrackColor = TrackIdle
            )
        )
    }
}

@Composable
private fun StateOverridesCard(
    custom: LogoCustomization,
    borderTint: Color,
    onChange: (LogoCustomization) -> Unit
) {
    var editing by remember(custom) { mutableStateOf(AgentLogoState.Thinking) }
    val current = custom.stateOverrides[editing.name]

    GroupCard(
        title = "Per-state overrides",
        borderTint = borderTint,
        onReset = {
            onChange(custom.copy(stateOverrides = custom.stateOverrides - editing.name))
        }
    ) {
        Text(
            text = "Tune one state without touching the rest. Untouched dials inherit the global values.",
            fontSize = 13.sp,
            color = SecondaryText
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (state in AgentLogoState.entries) {
                TextChip(
                    label = state.name.lowercase(),
                    selected = state == editing,
                    borderTint = borderTint,
                    onClick = { editing = state }
                )
            }
        }
        if (current == null) {
            Text(
                text = "No override for ${editing.name.lowercase()} — moving a slider creates one.",
                fontSize = 13.sp,
                color = SecondaryText
            )
        }
        fun patch(update: (LogoStateOverride) -> LogoStateOverride) {
            val base = current ?: LogoStateOverride()
            onChange(
                custom.copy(
                    stateOverrides = custom.stateOverrides + (editing.name to update(base))
                )
            )
        }
        TuneSlider(
            label = "Speed (${editing.name.lowercase()})",
            value = current?.speedMultiplier ?: custom.speedMultiplier,
            range = 0.25f..4f,
            step = 0.05f,
            format = { "%.2f".format(it) },
            onChange = { patch { o -> o.copy(speedMultiplier = snap(it, 0.05f)) } }
        )
        TuneSlider(
            label = "Count (${editing.name.lowercase()})",
            value = (current?.count ?: custom.count).toFloat(),
            range = 40f..600f,
            step = 1f,
            format = { it.roundToInt().toString() },
            onChange = { patch { o -> o.copy(count = it.roundToInt()) } }
        )
        TuneSlider(
            label = "Dwell (${editing.name.lowercase()})",
            value = current?.dwellSeconds ?: custom.dwellSeconds,
            range = 1f..12f,
            step = 0.1f,
            format = { "%.1f".format(it) },
            onChange = { patch { o -> o.copy(dwellSeconds = snap(it, 0.1f)) } }
        )
        TuneSlider(
            label = "Morph (${editing.name.lowercase()})",
            value = current?.morphSeconds ?: custom.morphSeconds,
            range = 0.5f..5f,
            step = 0.1f,
            format = { "%.1f".format(it) },
            onChange = { patch { o -> o.copy(morphSeconds = snap(it, 0.1f)) } }
        )
        TuneSlider(
            label = "Spin (${editing.name.lowercase()})",
            value = current?.spinMultiplier ?: custom.spinMultiplier,
            range = 0f..4f,
            step = 0.05f,
            format = { "%.2f".format(it) },
            onChange = { patch { o -> o.copy(spinMultiplier = snap(it, 0.05f)) } }
        )
        TuneSlider(
            label = "Band (${editing.name.lowercase()})",
            value = current?.bandMultiplier ?: custom.bandMultiplier,
            range = 0f..5f,
            step = 0.05f,
            format = { "%.2f".format(it) },
            onChange = { patch { o -> o.copy(bandMultiplier = snap(it, 0.05f)) } }
        )
        TuneSlider(
            label = "Wobble (${editing.name.lowercase()})",
            value = current?.wobbleMultiplier ?: custom.wobbleMultiplier,
            range = 0f..3f,
            step = 0.05f,
            format = { "%.2f".format(it) },
            onChange = { patch { o -> o.copy(wobbleMultiplier = snap(it, 0.05f)) } }
        )
    }
}

// ---------------------------------------------------------------------------
// Slider snapping helpers (pure, testable)
// ---------------------------------------------------------------------------

/** Snap [value] to the nearest multiple of [step]. */
internal fun snap(value: Float, step: Float): Float {
    if (step <= 0f) return value
    return (value / step).roundToInt() * step
}

/** Compose `Slider(steps = ...)` count for a stepped range. */
internal fun sliderSteps(range: ClosedFloatingPointRange<Float>, step: Float): Int {
    if (step <= 0f) return 0
    val intervals = ((range.endInclusive - range.start) / step).roundToInt()
    return (intervals - 1).coerceAtLeast(0)
}

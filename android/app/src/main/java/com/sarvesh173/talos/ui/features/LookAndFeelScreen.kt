package com.sarvesh173.talos.ui.features

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.*
import kotlin.math.roundToInt

/**
 * Dedicated Apple HIG "Look & Feel" Screen.
 * Provides hierarchical customization for Appearance Mode (System/Light/Dark),
 * Apple Accent Tints, Background Canvas Depths (AMOLED Black, Space Gray, Paper White),
 * Typography Scaling, Loading Indicator Styles, Characters & Motion, Haptics, and App Icon Variants
 * with a live interactive conversation preview.
 */
@Composable
fun LookAndFeelScreen(
    client: TalosClient,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalIsDark.current
    val currentThemeMode by client.themeMode.collectAsState()
    val currentPresetName by client.themePreset.collectAsState()
    val currentAccentName by client.accentColor.collectAsState()
    val currentFontScale by client.fontScale.collectAsState()
    val currentLoadingStyle by client.loadingIndicatorStyle.collectAsState()
    val animateCharacters by client.animateCharacters.collectAsState()
    val reduceMotion by client.reduceMotion.collectAsState()
    val isHapticsEnabled by client.isHapticsEnabled.collectAsState()
    val currentAppIcon by client.appIcon.collectAsState()

    val activeAccent = AppleAccent.values().find {
        it.name.equals(currentAccentName, ignoreCase = true)
    } ?: AppleAccent.BLUE

    val effectiveAccentColor = resolveAccentColor(currentAccentName, isDark)
    val isCustomColor = parseHexColor(currentAccentName) != null

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = if (isDark) Color(0xFF000000).copy(alpha = 0.85f) else Color(0xFFFFFFFF).copy(alpha = 0.85f),
                border = BorderStroke(0.6.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val backInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onBack,
                        interactionSource = backInteraction,
                        modifier = Modifier
                            .size(38.dp)
                            .applePressScale(backInteraction, 0.97f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Look & Feel",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.25).sp,
                        color = if (isDark) Color.White else Color(0xFF1D1D1F)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // -----------------------------------------------------------------
            // 1. LIVE CONVERSATION PREVIEW (HERO CARD WITH MULTI-LAYER DEPTH)
            // -----------------------------------------------------------------
            Text(
                text = "LIVE PREVIEW",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            val accentGlowColor = effectiveAccentColor

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Layer 1: Ambient live accent glow behind the card
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    accentGlowColor.copy(alpha = if (isDark) 0.16f else 0.10f),
                                    Color.Transparent
                                ),
                                radius = 600f
                            )
                        )
                )

                // Layer 2: Frosted card backdrop with specular highlight rim
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.94f) else Color(0xFFF9F9FB).copy(alpha = 0.95f),
                    border = BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                if (isDark) Color.White.copy(alpha = 0.26f) else Color.White,
                                if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.06f)
                            )
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Top specular refraction edge
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            if (isDark) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.7f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // Header Status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF30D158).copy(alpha = 0.22f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.5.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF30D158))
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Alya · Emotional Anchor",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = (-0.15).sp,
                                        color = if (isDark) Color.White.copy(alpha = 0.9f) else Color(0xFF1D1D1F)
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = accentGlowColor.copy(alpha = 0.14f),
                                    border = BorderStroke(
                                        0.8.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                accentGlowColor.copy(alpha = 0.60f),
                                                accentGlowColor.copy(alpha = 0.25f)
                                            )
                                        )
                                    )
                                ) {
                                    Text(
                                        text = if (isCustomColor) "Spectrum Custom (${currentAccentName.uppercase()})" else activeAccent.displayName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.15).sp,
                                        color = accentGlowColor,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Simulated Assistant Message Bubble with realistic shadow
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                Surface(
                                    color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF),
                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
                                    shadowElevation = 2.dp,
                                    border = BorderStroke(
                                        0.8.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                if (isDark) Color.White.copy(alpha = 0.18f) else Color.White,
                                                if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)
                                            )
                                        )
                                    )
                                ) {
                                    Text(
                                        text = "Main yahan hoon Sarvesh. Sab agent cores linked hain aur smoothly operate kar rahe hain.",
                                        fontSize = 13.sp,
                                        lineHeight = 18.5.sp,
                                        letterSpacing = (-0.15).sp,
                                        color = if (isDark) Color.White else Color(0xFF1D1D1F),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.5.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Simulated User Message Bubble (Live Accent Tint + Specular Top Rim + Realistic Shadow)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Surface(
                                    color = accentGlowColor,
                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp),
                                    shadowElevation = 4.dp,
                                    border = BorderStroke(
                                        0.8.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.45f),
                                                Color.White.copy(alpha = 0.12f)
                                            )
                                        )
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color.White.copy(alpha = 0.16f),
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.10f)
                                                    )
                                                )
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.5.dp)
                                    ) {
                                        Text(
                                            text = "Perfect! Keep it clean and Apple-grade.",
                                            fontSize = 13.sp,
                                            lineHeight = 18.5.sp,
                                            letterSpacing = (-0.15).sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Simulated Hermes Typing / Loading Status with selected Indicator Style
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(effectiveAccentColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Hermes reasoning (${currentLoadingStyle})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.15).sp,
                                    color = talosTextMuted
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                LoadingIndicatorWidget(
                                    style = currentLoadingStyle,
                                    color = effectiveAccentColor
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 2. APPEARANCE MODE (SYSTEM / LIGHT / DARK PILL SLIDER)
            // -----------------------------------------------------------------
            Text(
                text = "APPEARANCE MODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            val appearanceModes = listOf(
                "SYSTEM" to "System Auto",
                "LIGHT" to "Light",
                "DARK" to "Dark"
            )
            val selectedAppearanceIndex = when (currentThemeMode.uppercase()) {
                "SYSTEM" -> 0
                "LIGHT" -> 1
                "DARK" -> 2
                else -> 0
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA))
                    .border(
                        0.8.dp,
                        if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(4.dp)
            ) {
                val segmentWidth = maxWidth / appearanceModes.size
                val pillOffset by animateDpAsState(
                    targetValue = segmentWidth * selectedAppearanceIndex,
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = 400f
                    ),
                    label = "appearance_pill_offset"
                )

                // Fluid sliding indicator pill with specular edge
                Box(
                    modifier = Modifier
                        .offset(x = pillOffset)
                        .width(segmentWidth)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF2C2C2E) else Color.White)
                        .border(
                            0.8.dp,
                            Brush.verticalGradient(
                                listOf(
                                    if (isDark) Color.White.copy(alpha = 0.22f) else Color.White,
                                    if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.08f)
                                )
                            ),
                            RoundedCornerShape(12.dp)
                        )
                )

                // Segment labels with subtle scale bounce on tap
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    appearanceModes.forEachIndexed { index, (modeKey, modeTitle) ->
                        val isSelected = selectedAppearanceIndex == index
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val tabScale by animateFloatAsState(
                            targetValue = if (isPressed) 0.95f else 1f,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                            label = "appearance_tab_scale"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .scale(tabScale)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    client.setThemeMode(modeKey)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = modeTitle,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = (-0.15).sp,
                                color = if (isSelected) {
                                    if (isDark) Color.White else Color(0xFF1D1D1F)
                                } else talosTextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 3. ACCENT TINTS & 360° COLOR SPECTRUM
            // -----------------------------------------------------------------
            Text(
                text = "ACCENT TINT & 360° SPECTRUM",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            Text(
                text = "Slide the 360° hue spectrum to choose any custom color, or select curated Apple swatches.",
                fontSize = 11.sp,
                letterSpacing = (-0.15).sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )

            // --- 360° HUE SPECTRUM CARD ---
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    var currentHue by remember(currentAccentName, isDark) {
                        mutableFloatStateOf(hueFromAccentName(currentAccentName, isDark))
                    }
                    val liveColor = if (isCustomColor) effectiveAccentColor else colorFromHue(currentHue, isDark)
                    val liveHex = if (isCustomColor) currentAccentName.uppercase() else hexFromHue(currentHue, isDark)

                    // Top Bar: Live Swatch Preview + Angle/Hex Info + Mode Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(liveColor)
                                    .border(
                                        1.5.dp,
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.65f),
                                                Color.White.copy(alpha = 0.15f)
                                            )
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.35f))
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                val hueDegrees = currentHue.roundToInt()
                                val hueFamily = when (hueDegrees) {
                                    in 0..15 -> "Crimson Red"
                                    in 16..45 -> "Sunset Coral"
                                    in 46..75 -> "Solar Amber"
                                    in 76..150 -> "Cyber Emerald"
                                    in 151..195 -> "Skyline Cyan"
                                    in 196..255 -> "Cupertino Blue"
                                    in 256..290 -> "Electric Indigo"
                                    in 291..335 -> "Orchid Violet"
                                    else -> "Crimson Red"
                                }
                                Text(
                                    text = hueFamily,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.15).sp,
                                    color = if (isDark) Color.White else Color(0xFF1D1D1F)
                                )
                                Text(
                                    text = "$hueDegrees° Spectrum  •  $liveHex",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = talosTextMuted
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = liveColor.copy(alpha = 0.14f),
                            border = BorderStroke(0.6.dp, liveColor.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "360° LIVE",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = liveColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                            )
                        }
                    }

                    // 360° Rainbow Bar & Slider
                    val rainbowGradient = remember {
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFF0000), // 0° Red
                                Color(0xFFFF7F00), // 30° Orange
                                Color(0xFFFFFF00), // 60° Yellow
                                Color(0xFF00FF00), // 120° Green
                                Color(0xFF00FFFF), // 180° Cyan
                                Color(0xFF0000FF), // 240° Blue
                                Color(0xFFFF00FF), // 300° Magenta
                                Color(0xFFFF0000)  // 360° Red
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Continuous 0° to 360° rainbow gradient bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(rainbowGradient)
                                .border(
                                    0.8.dp,
                                    if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.12f),
                                    RoundedCornerShape(7.dp)
                                )
                        )

                        // Interactive slider overlay
                        Slider(
                            value = currentHue,
                            onValueChange = { newHue ->
                                currentHue = newHue
                                val newHex = hexFromHue(newHue, isDark)
                                client.setAccentColor(newHex)
                            },
                            valueRange = 0f..360f,
                            colors = SliderDefaults.colors(
                                thumbColor = liveColor,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 8 Quick-Jump Hue Points across the 360° circle
                    val spectrumPoints = listOf(
                        0f to "Red",
                        40f to "Coral",
                        60f to "Amber",
                        120f to "Emerald",
                        180f to "Teal",
                        215f to "Blue",
                        270f to "Indigo",
                        315f to "Violet"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        spectrumPoints.forEach { (pointHue, _) ->
                            val isPointSelected = kotlin.math.abs(currentHue - pointHue) < 10f
                            val pointColor = colorFromHue(pointHue, isDark)
                            Box(
                                modifier = Modifier
                                    .size(if (isPointSelected) 22.dp else 17.dp)
                                    .clip(CircleShape)
                                    .background(pointColor)
                                    .border(
                                        if (isPointSelected) 2.dp else 0.8.dp,
                                        if (isPointSelected) Color.White else (if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f)),
                                        CircleShape
                                    )
                                    .clickable {
                                        currentHue = pointHue
                                        val newHex = hexFromHue(pointHue, isDark)
                                        client.setAccentColor(newHex)
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- CURATED APPLE HIG PRESETS ---
            Text(
                text = "CURATED APPLE HIG PRESETS",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val allAccents = AppleAccent.values()
                    val row1 = allAccents.take(4)
                    val row2 = allAccents.drop(4).take(4)

                    listOf(row1, row2).forEach { rowAccents ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            rowAccents.forEach { accent ->
                                val isSelected = !isCustomColor && activeAccent == accent
                                val swatchColor = if (isDark) accent.darkColor else accent.lightColor

                                val swatchInteraction = remember { MutableInteractionSource() }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .applePressScale(swatchInteraction, 0.94f)
                                        .clickable(
                                            interactionSource = swatchInteraction,
                                            indication = null
                                        ) {
                                            client.setAccentColor(accent.name)
                                        }
                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                ) {
                                    // Elegant outer selection ring container
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(
                                                        width = 2.dp,
                                                        color = swatchColor,
                                                        shape = CircleShape
                                                    )
                                                } else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Inner swatch circle with gradient & specular rim
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            swatchColor,
                                                            if (isDark) accent.darkColor.copy(alpha = 0.78f) else accent.lightColor.copy(alpha = 0.78f)
                                                        )
                                                    )
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    brush = Brush.verticalGradient(
                                                        listOf(
                                                            Color.White.copy(alpha = 0.65f),
                                                            Color.White.copy(alpha = 0.12f)
                                                        )
                                                    ),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = if (accent == AppleAccent.AMBER && !isDark) Color(0xFF1D1D1F) else Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = accent.displayName.substringBefore(" "),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        letterSpacing = (-0.15).sp,
                                        color = if (isSelected) (if (isDark) Color.White else Color(0xFF1D1D1F)) else talosTextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // -----------------------------------------------------------------
            // 4. BACKGROUND CANVAS FINISH (AMOLED BLACK / SPACE GRAY / PAPER WHITE)
            // -----------------------------------------------------------------
            Text(
                text = "BACKGROUND CANVAS FINISH",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            Text(
                text = "Monochromatic canvas tones matching Hermes official aesthetics.",
                fontSize = 11.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )

            val canvasPresets = listOf(
                Triple(MaterialThemePreset.APPLE_AMOLED_BLACK, "AMOLED True Black", "Pitch #000000, 0% OLED power, maximum contrast"),
                Triple(MaterialThemePreset.APPLE_DARK_SLATE, "Dark Space Gray", "Slate #121214, subtle Apple dark room tone"),
                Triple(MaterialThemePreset.APPLE_PAPER_WHITE, "Paper White", "Warm #F7F7F9 editorial cream surface"),
                Triple(MaterialThemePreset.APPLE_PURE_WHITE, "Pure White", "Stark #FFFFFF Apple clean light canvas")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                canvasPresets.forEach { (preset, title, desc) ->
                    val isSelected = currentPresetName == preset.name ||
                            (preset == MaterialThemePreset.APPLE_AMOLED_BLACK && (currentPresetName == "APPLE_CUPERTINO" || currentPresetName == "PURPLE_IRIS"))
                    val cardInteraction = remember { MutableInteractionSource() }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            effectiveAccentColor.copy(alpha = 0.12f)
                        } else {
                            if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.2.dp else 0.8.dp,
                            color = if (isSelected) {
                                effectiveAccentColor.copy(alpha = 0.65f)
                            } else {
                                if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .applePressScale(cardInteraction, 0.97f)
                            .clickable(
                                interactionSource = cardInteraction,
                                indication = null
                            ) {
                                client.setThemePreset(preset.name)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(preset.seedColor)
                                        .border(
                                            1.dp,
                                            Brush.verticalGradient(
                                                listOf(
                                                    if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.20f),
                                                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                                                )
                                            ),
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        letterSpacing = (-0.2).sp,
                                        color = if (isSelected) effectiveAccentColor else (if (isDark) Color.White else Color(0xFF1D1D1F))
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 10.5.sp,
                                        letterSpacing = (-0.15).sp,
                                        color = talosTextMuted
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Active",
                                    tint = effectiveAccentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 5. TEXT SIZE & OPTICAL TRACKING
            // -----------------------------------------------------------------
            Text(
                text = "TEXT SIZE & OPTICAL TRACKING",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            val scaleOptions = listOf(
                "compact" to "Compact",
                "standard" to "Standard",
                "spacious" to "Spacious"
            )
            val selectedScaleIndex = when (currentFontScale.lowercase()) {
                "compact" -> 0
                "standard" -> 1
                "spacious" -> 2
                else -> 1
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA))
                    .border(
                        0.8.dp,
                        if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(4.dp)
            ) {
                val segmentWidth = maxWidth / scaleOptions.size
                val pillOffset by animateDpAsState(
                    targetValue = segmentWidth * selectedScaleIndex,
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = 400f
                    ),
                    label = "font_scale_pill_offset"
                )

                // Fluid sliding indicator pill with specular border
                Box(
                    modifier = Modifier
                        .offset(x = pillOffset)
                        .width(segmentWidth)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF2C2C2E) else Color.White)
                        .border(
                            0.8.dp,
                            Brush.verticalGradient(
                                listOf(
                                    if (isDark) Color.White.copy(alpha = 0.22f) else Color.White,
                                    if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.08f)
                                )
                            ),
                            RoundedCornerShape(12.dp)
                        )
                )

                // Segment labels with subtle scale bounce on tap
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    scaleOptions.forEachIndexed { index, (scaleKey, scaleTitle) ->
                        val isSelected = selectedScaleIndex == index
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val tabScale by animateFloatAsState(
                            targetValue = if (isPressed) 0.95f else 1f,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                            label = "font_scale_tab_scale"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .scale(tabScale)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    client.setFontScale(scaleKey)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = scaleTitle,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = (-0.15).sp,
                                color = if (isSelected) {
                                    if (isDark) Color.White else Color(0xFF1D1D1F)
                                } else talosTextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 6. LOADING INDICATOR STYLE (Dots, Wave, Pulse)
            // -----------------------------------------------------------------
            Text(
                text = "LOADING INDICATOR STYLE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            val loadingOptions = listOf("Dots", "Wave", "Pulse")
            val selectedLoadingIndex = when (currentLoadingStyle.lowercase()) {
                "wave" -> 1
                "pulse" -> 2
                else -> 0
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(
                    0.8.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        loadingOptions.forEachIndexed { index, styleName ->
                            val isSelected = selectedLoadingIndex == index
                            val interactionSource = remember { MutableInteractionSource() }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) {
                                            effectiveAccentColor.copy(alpha = if (isDark) 0.16f else 0.10f)
                                        } else {
                                            if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
                                        }
                                    )
                                    .border(
                                        if (isSelected) 1.5.dp else 0.6.dp,
                                        if (isSelected) effectiveAccentColor else (if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) {
                                        TalosHaptics.sendHaptic(context)
                                        client.setLoadingIndicatorStyle(styleName)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier.height(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LoadingIndicatorWidget(
                                            style = styleName,
                                            color = if (isSelected) effectiveAccentColor else talosTextMuted
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = styleName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        letterSpacing = (-0.15).sp,
                                        color = if (isSelected) {
                                            if (isDark) Color.White else Color(0xFF1D1D1F)
                                        } else talosTextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 7. CHARACTERS & MOTION
            // -----------------------------------------------------------------
            Text(
                text = "CHARACTERS & MOTION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(
                    0.6.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Animate bot characters
                    LookAndFeelToggleRow(
                        title = "Animate bot characters",
                        description = "Enable micro-animations and expressive state reactions for bot avatars.",
                        icon = Icons.Default.Face,
                        iconTint = Color(0xFFAF52DE),
                        checked = animateCharacters,
                        onCheckedChange = {
                            TalosHaptics.sendHaptic(context)
                            client.setAnimateCharacters(it)
                        },
                        isDark = isDark
                    )

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 54.dp)
                    )

                    // Reduce motion
                    LookAndFeelToggleRow(
                        title = "Reduce motion",
                        description = "Minimize fluid spring transitions, parallax, and ambient effects.",
                        icon = Icons.Default.MotionPhotosOff,
                        iconTint = Color(0xFF5AC8FA),
                        checked = reduceMotion,
                        onCheckedChange = {
                            TalosHaptics.sendHaptic(context)
                            client.setReduceMotion(it)
                        },
                        isDark = isDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 8. INTERACTION & HAPTICS
            // -----------------------------------------------------------------
            Text(
                text = "INTERACTION & HAPTICS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(
                    0.6.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                LookAndFeelToggleRow(
                    title = "Haptics",
                    description = "Tactile haptic feedback on messages, button presses, and sliders.",
                    icon = Icons.Default.Vibration,
                    iconTint = Color(0xFFFF2D55),
                    checked = isHapticsEnabled,
                    onCheckedChange = {
                        client.setHapticsEnabled(it)
                        if (it) TalosHaptics.sendHaptic(context)
                    },
                    isDark = isDark
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 9. APP ICON SELECTION CARD (Nous / Talos, Alya, Orbs)
            // -----------------------------------------------------------------
            Text(
                text = "APP ICON",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            val appIcons = listOf(
                AppIconOption("nous", "Nous / Talos", "Signature brand emblem"),
                AppIconOption("alya", "Alya", "Companion crest"),
                AppIconOption("iris", "Iris", "Cyan / violet orb"),
                AppIconOption("aurora", "Aurora", "Polar emerald glow"),
                AppIconOption("bloom", "Bloom", "Amber / magenta bloom"),
                AppIconOption("orb_trio", "Orb Trio", "Triple chromatic spheres"),
                AppIconOption("orb_pair", "Orb Pair", "Binary orbital pair"),
                AppIconOption("original", "Original", "Obsidian sphere")
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(
                    0.8.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Choose your home screen icon theme. Changes update the in-app launcher badge and status surfaces.",
                        fontSize = 12.sp,
                        color = talosTextMuted,
                        lineHeight = 16.sp,
                        letterSpacing = (-0.1).sp,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )

                    // 2x4 Grid layout (2 items per row x 4 rows)
                    appIcons.chunked(2).forEachIndexed { rowIndex, pair ->
                        if (rowIndex > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { option ->
                                val isSelected = currentAppIcon.equals(option.id, ignoreCase = true)
                                val cardInteraction = remember { MutableInteractionSource() }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) {
                                                effectiveAccentColor.copy(alpha = if (isDark) 0.14f else 0.08f)
                                            } else {
                                                if (isDark) Color(0xFF2C2C2E).copy(alpha = 0.6f) else Color(0xFFF2F2F7)
                                            }
                                        )
                                        .border(
                                            if (isSelected) 1.5.dp else 0.6.dp,
                                            if (isSelected) effectiveAccentColor else (if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable(
                                            interactionSource = cardInteraction,
                                            indication = null
                                        ) {
                                            TalosHaptics.sendHaptic(context)
                                            client.setAppIcon(option.id)
                                        }
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(contentAlignment = Alignment.TopEnd) {
                                            AppIconGraphic(
                                                iconKey = option.id,
                                                modifier = Modifier.size(46.dp)
                                            )
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .offset(x = 4.dp, y = (-4).dp)
                                                        .size(16.dp)
                                                        .clip(CircleShape)
                                                        .background(effectiveAccentColor),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = option.label,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                letterSpacing = (-0.2).sp,
                                                color = if (isDark) Color.White else Color(0xFF1D1D1F),
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = option.sublabel,
                                                fontSize = 11.sp,
                                                letterSpacing = (-0.1).sp,
                                                color = talosTextMuted,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

private data class AppIconOption(
    val id: String,
    val label: String,
    val sublabel: String
)

@Composable
private fun LookAndFeelToggleRow(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                color = if (isDark) Color.White else Color(0xFF1D1D1F)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = (-0.1).sp,
                color = talosTextMuted
            )
        }

        AppleToggleSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun LoadingIndicatorWidget(
    style: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    when (style.lowercase()) {
        "wave" -> WaveLoadingIndicator(color = color, modifier = modifier)
        "pulse" -> PulseLoadingIndicator(color = color, modifier = modifier)
        else -> ThreeDotTypingIndicator(dotColor = color, dotSize = 5.dp, dotSpacing = 3.dp, modifier = modifier)
    }
}

@Composable
fun WaveLoadingIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    Row(
        modifier = modifier.height(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (i in 0 until 4) {
            val barHeight = 6.dp + (6.dp * (kotlin.math.sin(phase + i * 0.8f).coerceIn(-1f, 1f) * 0.5f + 0.5f))
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun PulseLoadingIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Box(
        modifier = modifier.size(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(16.dp * pulseScale)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.25f * pulseAlpha))
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = pulseAlpha))
        )
    }
}

@Composable
fun AppIconGraphic(
    iconKey: String,
    modifier: Modifier = Modifier
) {
    val squircleShape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(squircleShape)
            .border(
                0.8.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                squircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        when (iconKey.lowercase()) {
            "nous", "talos" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2C2C2E), Color(0xFF121214))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TalosSparkle,
                        contentDescription = null,
                        tint = Color(0xFFFFBF00),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            "alya" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFFFF85A1), Color(0xFF9D4EDD), Color(0xFF1A0A2A))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            "iris" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF67E8F9), Color(0xFF818CF8), Color(0xFF4F46E5), Color(0xFF090D1A)),
                            center = Offset(size.width * 0.4f, size.height * 0.4f),
                            radius = size.width * 0.75f
                        )
                    )
                }
            }
            "aurora" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF34D399), Color(0xFF06B6D4), Color(0xFF0284C7), Color(0xFF081225)),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        )
                    )
                }
            }
            "bloom" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFDE047), Color(0xFFFB923C), Color(0xFFF43F5E), Color(0xFF2E0234)),
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            radius = size.width * 0.7f
                        )
                    )
                }
            }
            "orb_trio" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(Color(0xFF0A0A0F))
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF38BDF8), Color(0xFF0284C7).copy(alpha = 0.2f), Color.Transparent),
                            center = Offset(size.width * 0.35f, size.height * 0.35f),
                            radius = size.width * 0.32f
                        ),
                        radius = size.width * 0.32f,
                        center = Offset(size.width * 0.35f, size.height * 0.35f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFF43F5E), Color(0xFFBE123C).copy(alpha = 0.2f), Color.Transparent),
                            center = Offset(size.width * 0.65f, size.height * 0.38f),
                            radius = size.width * 0.32f
                        ),
                        radius = size.width * 0.32f,
                        center = Offset(size.width * 0.65f, size.height * 0.38f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFFBBF24), Color(0xFFD97706).copy(alpha = 0.2f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.68f),
                            radius = size.width * 0.32f
                        ),
                        radius = size.width * 0.32f,
                        center = Offset(size.width * 0.5f, size.height * 0.68f)
                    )
                }
            }
            "orb_pair" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(Color(0xFF080811))
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF38BDF8), Color(0xFF1D4ED8), Color.Transparent),
                            center = Offset(size.width * 0.4f, size.height * 0.45f),
                            radius = size.width * 0.38f
                        ),
                        radius = size.width * 0.38f,
                        center = Offset(size.width * 0.4f, size.height * 0.45f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFA855F7), Color(0xFF6B21A8), Color.Transparent),
                            center = Offset(size.width * 0.65f, size.height * 0.58f),
                            radius = size.width * 0.34f
                        ),
                        radius = size.width * 0.34f,
                        center = Offset(size.width * 0.65f, size.height * 0.58f)
                    )
                }
            }
            else -> { // original
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF3F3F46), Color(0xFF18181B), Color(0xFF000000)),
                            center = Offset(size.width * 0.4f, size.height * 0.35f),
                            radius = size.width * 0.8f
                        )
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.35f),
                        radius = size.width * 0.22f,
                        center = Offset(size.width * 0.35f, size.height * 0.32f)
                    )
                }
            }
        }
    }
}

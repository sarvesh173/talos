package com.sarvesh173.talos.ui.features

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.*

/**
 * Dedicated Apple HIG "Look & Feel" Screen.
 * Provides hierarchical customization for Appearance Mode (System/Light/Dark),
 * Apple Accent Tints, Background Canvas Depths (AMOLED Black, Space Gray, Paper White),
 * and Typography Scaling with a live interactive conversation preview.
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

    val activeAccent = AppleAccent.values().find {
        it.name.equals(currentAccentName, ignoreCase = true)
    } ?: AppleAccent.BLUE

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
                        letterSpacing = (-0.2).sp,
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
            // 1. LIVE CONVERSATION PREVIEW (HERO CARD WITH DEPTH)
            // -----------------------------------------------------------------
            Text(
                text = "LIVE PREVIEW",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7),
                border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
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
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF30D158))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Alya · Emotional Anchor",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White.copy(alpha = 0.9f) else Color(0xFF1D1D1F)
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = (if (isDark) activeAccent.darkColor else activeAccent.lightColor).copy(alpha = 0.15f),
                            border = BorderStroke(0.8.dp, (if (isDark) activeAccent.darkColor else activeAccent.lightColor).copy(alpha = 0.40f))
                        ) {
                            Text(
                                text = activeAccent.displayName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) activeAccent.darkColor else activeAccent.lightColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Simulated Assistant Message Bubble
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Surface(
                            color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
                            border = BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f))
                        ) {
                            Text(
                                text = "Main yahan hoon Sarvesh. Sab agent cores linked hain aur smoothly operate kar rahe hain.",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = if (isDark) Color.White else Color(0xFF1D1D1F),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Simulated User Message Bubble (Styled in Chosen Accent)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Surface(
                            color = if (isDark) activeAccent.darkColor else activeAccent.lightColor,
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.30f))
                        ) {
                            Text(
                                text = "Perfect! Keep it clean and Apple-grade.",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

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
                        if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
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

                // Fluid sliding indicator pill
                Box(
                    modifier = Modifier
                        .offset(x = pillOffset)
                        .width(segmentWidth)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF2C2C2E) else Color.White)
                        .border(
                            0.8.dp,
                            if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f),
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
                                color = if (isSelected) {
                                    if (isDark) Color.White else Color(0xFF1D1D1F)
                                } else talosTextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // -----------------------------------------------------------------
            // 3. ACCENT TINTS (APPLE SYSTEM PALETTE SWATCHES)
            // -----------------------------------------------------------------
            Text(
                text = "ACCENT TINT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            Text(
                text = "Tint applied to buttons, selected chips, and message bubbles.",
                fontSize = 11.sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                val isSelected = activeAccent == accent
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
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(swatchColor)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = accent.displayName.substringBefore(" "),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
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
                            (if (isDark) activeAccent.darkColor else activeAccent.lightColor).copy(alpha = 0.12f)
                        } else {
                            if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.2.dp else 0.8.dp,
                            color = if (isSelected) {
                                (if (isDark) activeAccent.darkColor else activeAccent.lightColor).copy(alpha = 0.60f)
                            } else {
                                if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
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
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(preset.seedColor)
                                        .border(1.dp, if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) (if (isDark) activeAccent.darkColor else activeAccent.lightColor) else (if (isDark) Color.White else Color(0xFF1D1D1F))
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 10.sp,
                                        color = talosTextMuted
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Active",
                                    tint = if (isDark) activeAccent.darkColor else activeAccent.lightColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

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
                        if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
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

                // Fluid sliding indicator pill
                Box(
                    modifier = Modifier
                        .offset(x = pillOffset)
                        .width(segmentWidth)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF2C2C2E) else Color.White)
                        .border(
                            0.8.dp,
                            if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f),
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
                                color = if (isSelected) {
                                    if (isDark) Color.White else Color(0xFF1D1D1F)
                                } else talosTextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

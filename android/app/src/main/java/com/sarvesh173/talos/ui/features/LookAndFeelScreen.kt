package com.sarvesh173.talos.ui.features

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.*
import kotlin.math.roundToInt

/**
 * Curated Swatch definition matching the official Nous Mobile specification.
 * 9 curated system colors + 10th rainbow swatch.
 */
data class CuratedSwatch(
    val id: String,
    val name: String,
    val hex: String,
    val color: Color
)

val officialCuratedSwatches = listOf(
    CuratedSwatch("DEEP_BLUE", "Deep Blue", "#0022FF", Color(0xFF0022FF)),
    CuratedSwatch("ROYAL_BLUE", "Royal Blue", "#007AFF", Color(0xFF007AFF)),
    CuratedSwatch("COBALT", "Cobalt", "#5856D6", Color(0xFF5856D6)),
    CuratedSwatch("TEAL", "Teal", "#00A8A8", Color(0xFF00A8A8)),
    CuratedSwatch("GREEN", "Green", "#00A344", Color(0xFF00A344)),
    CuratedSwatch("GOLD", "Gold", "#A37800", Color(0xFFA37800)),
    CuratedSwatch("ORANGE", "Orange", "#E05500", Color(0xFFE05500)),
    CuratedSwatch("RED", "Red", "#E01020", Color(0xFFE01020)),
    CuratedSwatch("MAGENTA", "Magenta", "#D00060", Color(0xFFD00060))
)

/**
 * Dedicated Apple HIG "Look & Feel" Screen.
 * Overhauled to 100% faithful Nous Mobile design:
 * 1. Appearance 3-segment pill (System / Light / Dark)
 * 2. Accent 10 swatches (2 rows of 5, ending in 360° Chromatic Wheel)
 * 3. Custom color palette with (+) and (-) management
 * 4. Advanced AppleColorPickerDialog with 2D Chromatic Wheel, Brightness slider, RGB inputs, and Hex text
 * 5. Light Background cards (White, Paper White)
 * 6. Dark Background cards (Midnight, Black, Gray)
 * 7. Font selector dropdown (Nunito, SF Pro, Inter, System, Monospace)
 * 8. Text Size slider with A / A typography preview
 * 9. Secondary controls (Loading indicators, Motion, Haptics, App Icon variants)
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
    val currentFont by client.selectedFont.collectAsState()
    val lightBg by client.lightBackground.collectAsState()
    val darkBg by client.darkBackground.collectAsState()
    val customPalette by client.customPaletteColors.collectAsState()

    val currentLoadingStyle by client.loadingIndicatorStyle.collectAsState()
    val animateCharacters by client.animateCharacters.collectAsState()
    val reduceMotion by client.reduceMotion.collectAsState()
    val isHapticsEnabled by client.isHapticsEnabled.collectAsState()
    val currentAppIcon by client.appIcon.collectAsState()

    val effectiveAccentColor = resolveAccentColor(currentAccentName, isDark)
    val scrollState = rememberScrollState()

    var showColorPicker by remember { mutableStateOf(false) }
    var showFontMenu by remember { mutableStateOf(false) }

    if (showColorPicker) {
        AppleColorPickerDialog(
            initialColor = effectiveAccentColor,
            onColorSelected = { selectedColor ->
                val hex = String.format("#%06X", 0xFFFFFF and selectedColor.toArgb())
                client.setAccentColor(hex)
                showColorPicker = false
            },
            onAddToPalette = { hex ->
                client.addCustomColor(hex)
                client.setAccentColor(hex)
            },
            onDismissRequest = { showColorPicker = false }
        )
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Floating clean circular back button
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF))
                            .border(
                                BorderStroke(
                                    0.6.dp,
                                    if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                                ),
                                CircleShape
                            )
                            .applePressable(0.94f) { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Text(
                        text = "Look & Feel",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // -----------------------------------------------------------------
            // 1. APPEARANCE & ACCENT (MATCHING SCREENSHOT)
            // -----------------------------------------------------------------
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(
                    0.6.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Appearance",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = talosTextMuted,
                        letterSpacing = (-0.15).sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3-Segment Control (System / Light / Dark)
                    val appearanceModes = listOf(
                        "SYSTEM" to "System",
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
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
                            .padding(3.dp)
                    ) {
                        val segmentWidth = maxWidth / appearanceModes.size
                        val pillOffset by animateDpAsState(
                            targetValue = segmentWidth * selectedAppearanceIndex,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
                            label = "appearance_pill_offset"
                        )

                        // Elevated sliding pill
                        Box(
                            modifier = Modifier
                                .offset(x = pillOffset)
                                .width(segmentWidth)
                                .height(36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (isDark) Color(0xFF3A3A3C) else Color.White)
                                .border(
                                    BorderStroke(
                                        0.6.dp,
                                        if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
                                    ),
                                    RoundedCornerShape(11.dp)
                                )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            appearanceModes.forEachIndexed { index, (modeKey, modeTitle) ->
                                val isSelected = selectedAppearanceIndex == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .applePressable(0.96f) {
                                            TalosHaptics.sendHaptic(context)
                                            client.setThemeMode(modeKey)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = modeTitle,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        letterSpacing = (-0.15).sp,
                                        color = if (isSelected) {
                                            if (isDark) Color.White else Color(0xFF1D1D1F)
                                        } else talosTextMuted
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Accent",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = talosTextMuted,
                        letterSpacing = (-0.15).sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 10 Circular Swatches in 2 Rows of 5
                    val row1Swatches = officialCuratedSwatches.take(5)
                    val row2Swatches = officialCuratedSwatches.drop(5) // 4 items + 10th is Rainbow

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Row 1 (5 items)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row1Swatches.forEach { swatch ->
                                AccentSwatchCircle(
                                    color = swatch.color,
                                    isSelected = isSwatchActive(currentAccentName, swatch),
                                    onClick = {
                                        TalosHaptics.sendHaptic(context)
                                        client.setAccentColor(swatch.hex)
                                    }
                                )
                            }
                        }

                        // Row 2 (4 items + Rainbow Color Wheel)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row2Swatches.forEach { swatch ->
                                AccentSwatchCircle(
                                    color = swatch.color,
                                    isSelected = isSwatchActive(currentAccentName, swatch),
                                    onClick = {
                                        TalosHaptics.sendHaptic(context)
                                        client.setAccentColor(swatch.hex)
                                    }
                                )
                            }

                            // 10th Swatch: 360° Chromatic Rainbow Circle
                            val isRainbowActive = isCustomColorActive(currentAccentName)
                            RainbowColorWheelSwatch(
                                isSelected = isRainbowActive,
                                onClick = {
                                    TalosHaptics.sendHaptic(context)
                                    showColorPicker = true
                                }
                            )
                        }
                    }

                    // Optional Custom Palette Row with Add (+) & Delete (-)
                    if (customPalette.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Saved Shades",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = talosTextMuted
                            )

                            Text(
                                text = "Tap to select • Long-press to remove",
                                fontSize = 10.5.sp,
                                color = talosTextMuted.copy(alpha = 0.7f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            customPalette.forEach { hex ->
                                val color = parseHexColor(hex) ?: Color.White
                                val isSelected = currentAccentName.equals(hex, ignoreCase = true)

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            if (isSelected) 2.5.dp else 0.8.dp,
                                            if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                                            CircleShape
                                        )
                                        .applePressable(0.92f) {
                                            TalosHaptics.sendHaptic(context)
                                            client.setAccentColor(hex)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // -----------------------------------------------------------------
            // 2. LIGHT BACKGROUND (White & Paper White side by side)
            // -----------------------------------------------------------------
            Text(
                text = "Light Background",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = talosTextMuted,
                letterSpacing = (-0.15).sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // White Option Card
                val isWhiteSelected = lightBg == "white"
                BackgroundOptionCard(
                    title = "White",
                    previewColor = Color(0xFFFFFFFF),
                    isSelected = isWhiteSelected,
                    accentColor = effectiveAccentColor,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        TalosHaptics.sendHaptic(context)
                        client.setLightBackground("white")
                        client.setThemePreset(MaterialThemePreset.APPLE_PURE_WHITE.name)
                    }
                )

                // Paper White Option Card
                val isPaperSelected = lightBg == "paper_white"
                BackgroundOptionCard(
                    title = "Paper White",
                    previewColor = Color(0xFFF7F5F0),
                    isSelected = isPaperSelected,
                    accentColor = effectiveAccentColor,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        TalosHaptics.sendHaptic(context)
                        client.setLightBackground("paper_white")
                        client.setThemePreset(MaterialThemePreset.APPLE_PAPER_WHITE.name)
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // -----------------------------------------------------------------
            // 3. DARK BACKGROUND (Midnight, Black, Gray side by side)
            // -----------------------------------------------------------------
            Text(
                text = "Dark Background",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = talosTextMuted,
                letterSpacing = (-0.15).sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Midnight
                val isMidnightSelected = darkBg == "midnight"
                DarkBackgroundOptionCard(
                    title = "Midnight",
                    canvasColor = Color(0xFF0B0E14),
                    bubbleColor = Color(0xFF161B26),
                    isSelected = isMidnightSelected,
                    accentColor = effectiveAccentColor,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        TalosHaptics.sendHaptic(context)
                        client.setDarkBackground("midnight")
                        client.setThemePreset(MaterialThemePreset.APPLE_MIDNIGHT.name)
                    }
                )

                // Black (AMOLED)
                val isBlackSelected = darkBg == "black"
                DarkBackgroundOptionCard(
                    title = "Black",
                    canvasColor = Color(0xFF000000),
                    bubbleColor = Color(0xFF1C1C1E),
                    isSelected = isBlackSelected,
                    accentColor = effectiveAccentColor,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        TalosHaptics.sendHaptic(context)
                        client.setDarkBackground("black")
                        client.setThemePreset(MaterialThemePreset.APPLE_AMOLED_BLACK.name)
                    }
                )

                // Gray (Space Gray)
                val isGraySelected = darkBg == "gray"
                DarkBackgroundOptionCard(
                    title = "Gray",
                    canvasColor = Color(0xFF1C1C1E),
                    bubbleColor = Color(0xFF2C2C2E),
                    isSelected = isGraySelected,
                    accentColor = effectiveAccentColor,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        TalosHaptics.sendHaptic(context)
                        client.setDarkBackground("gray")
                        client.setThemePreset(MaterialThemePreset.APPLE_DARK_SLATE.name)
                    }
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // -----------------------------------------------------------------
            // 4. FONT DROPDOWN
            // -----------------------------------------------------------------
            Text(
                text = "Font",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = talosTextMuted,
                letterSpacing = (-0.15).sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(
                    0.6.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .applePressable(0.97f) { showFontMenu = !showFontMenu }
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currentFont,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F)
                        )

                        Icon(
                            imageVector = Icons.Default.UnfoldMore,
                            contentDescription = "Select Font",
                            tint = talosTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(visible = showFontMenu) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            val fontOptions = listOf("Nunito", "SF Pro", "Inter", "System", "Monospace")
                            fontOptions.forEachIndexed { index, fontName ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            TalosHaptics.sendHaptic(context)
                                            client.setSelectedFont(fontName)
                                            showFontMenu = false
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = fontName,
                                        fontSize = 15.sp,
                                        color = if (currentFont == fontName) effectiveAccentColor else (if (isDark) Color.White else Color(0xFF1D1D1F)),
                                        fontWeight = if (currentFont == fontName) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (currentFont == fontName) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = effectiveAccentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (index < fontOptions.size - 1) {
                                    HorizontalDivider(
                                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "A clear view of your conversations.",
                fontSize = 13.5.sp,
                letterSpacing = (-0.15).sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // -----------------------------------------------------------------
            // 5. TEXT SIZE SLIDER (WITH A / A LABELS)
            // -----------------------------------------------------------------
            Text(
                text = "Text Size",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = talosTextMuted,
                letterSpacing = (-0.15).sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                border = BorderStroke(
                    0.6.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "A",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = talosTextMuted
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    val scaleFloat = when (currentFontScale.lowercase()) {
                        "compact" -> 0f
                        "spacious" -> 2f
                        else -> 1f
                    }

                    Slider(
                        value = scaleFloat,
                        onValueChange = { value ->
                            val round = value.roundToInt()
                            val key = when (round) {
                                0 -> "compact"
                                2 -> "spacious"
                                else -> "standard"
                            }
                            client.setFontScale(key)
                        },
                        valueRange = 0f..2f,
                        steps = 1,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = effectiveAccentColor,
                            inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "A",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF1D1D1F)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/**
 * Clean circular accent swatch with continuous selection ring & checkmark.
 */
@Composable
private fun AccentSwatchCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = LocalIsDark.current

    Box(
        modifier = Modifier
            .size(46.dp)
            .then(
                if (isSelected) {
                    Modifier.border(2.5.dp, if (isDark) Color.White else Color(0xFF1D1D1F), CircleShape)
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color)
                .applePressable(0.92f) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Active",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 10th Swatch: 360° Chromatic Rainbow Circle.
 * Opens the AppleColorPickerDialog.
 */
@Composable
private fun RainbowColorWheelSwatch(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = LocalIsDark.current

    Box(
        modifier = Modifier
            .size(46.dp)
            .then(
                if (isSelected) {
                    Modifier.border(2.5.dp, if (isDark) Color.White else Color(0xFF1D1D1F), CircleShape)
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .applePressable(0.92f) { onClick() }
        ) {
            val sweepColors = listOf(
                Color.Red,
                Color.Yellow,
                Color.Green,
                Color.Cyan,
                Color.Blue,
                Color.Magenta,
                Color.Red
            )
            drawCircle(
                brush = Brush.sweepGradient(sweepColors, center),
                radius = size.width / 2f
            )
        }
    }
}

@Composable
private fun BackgroundOptionCard(
    title: String,
    previewColor: Color,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDark = LocalIsDark.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = previewColor,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 0.8.dp,
            color = if (isSelected) accentColor else (if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f))
        ),
        modifier = modifier
            .height(96.dp)
            .applePressable(0.96f) { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.04f))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = (-0.15).sp,
                    color = Color(0xFF1D1D1F)
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .border(1.2.dp, Color.Black.copy(alpha = 0.25f), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun DarkBackgroundOptionCard(
    title: String,
    canvasColor: Color,
    bubbleColor: Color,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = canvasColor,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 0.8.dp,
            color = if (isSelected) accentColor else Color.White.copy(alpha = 0.12f)
        ),
        modifier = modifier
            .height(115.dp)
            .applePressable(0.96f) { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Simulated chat bubble preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bubbleColor)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = (-0.15).sp,
                    color = Color.White
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .border(1.2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    )
                }
            }
        }
    }
}

private fun isSwatchActive(currentAccent: String, swatch: CuratedSwatch): Boolean {
    return currentAccent.equals(swatch.id, ignoreCase = true) ||
            currentAccent.equals(swatch.hex, ignoreCase = true)
}

private fun isCustomColorActive(currentAccent: String): Boolean {
    if (officialCuratedSwatches.any { isSwatchActive(currentAccent, it) }) {
        return false
    }
    return parseHexColor(currentAccent) != null
}

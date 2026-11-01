package com.sarvesh173.talos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.*

/**
 * Authentic Apple iOS Style Advanced Color Picker Dialog.
 *
 * Includes:
 * 1. 2D Chromatic Hue & Saturation Wheel with interactive touch crosshair.
 * 2. Continuous Brightness/Value slider with gradient track.
 * 3. Bidirectional RGB numeric sliders and inputs (0..255).
 * 4. Hex field with auto-capitalization and validation.
 * 5. Current vs. New comparison swatches.
 * 6. "Add to Palette" action to save favorite shades.
 */
@Composable
fun AppleColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onAddToPalette: ((String) -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalIsDark.current
    val focusManager = LocalFocusManager.current

    // Convert initial Color to HSV
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hsv
    }

    var hue by remember { mutableStateOf(initialHsv[0]) } // 0f..360f
    var saturation by remember { mutableStateOf(initialHsv[1]) } // 0f..1f
    var brightness by remember { mutableStateOf(initialHsv[2]) } // 0f..1f

    // Derived Color from HSV
    val currentColor = remember(hue, saturation, brightness) {
        val hsv = floatArrayOf(hue, saturation, brightness)
        Color(android.graphics.Color.HSVToColor(hsv))
    }

    // RGB components (0..255)
    val red = (currentColor.red * 255f).roundToInt().coerceIn(0, 255)
    val green = (currentColor.green * 255f).roundToInt().coerceIn(0, 255)
    val blue = (currentColor.blue * 255f).roundToInt().coerceIn(0, 255)

    // Formatted Hex
    val hexString = String.format("#%02X%02X%02X", red, green, blue)
    var hexInputText by remember(hexString) { mutableStateOf(hexString) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
            border = BorderStroke(
                0.8.dp,
                if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
            ),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Color Spectrum",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.25).sp,
                        color = if (isDark) Color.White else Color(0xFF1D1D1F)
                    )

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = talosTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Current vs New Swatch Comparison
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Current Color
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PREVIOUS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = talosTextMuted,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(width = 72.dp, height = 36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(initialColor)
                                .border(
                                    BorderStroke(
                                        0.8.dp,
                                        if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)
                                    ),
                                    RoundedCornerShape(10.dp)
                                )
                        )
                    }

                    // Hex Display Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                        border = BorderStroke(
                            0.6.dp,
                            if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                        )
                    ) {
                        BasicTextField(
                            value = hexInputText,
                            onValueChange = { input ->
                                hexInputText = input.uppercase()
                                val clean = input.trim().removePrefix("#")
                                if (clean.length == 6) {
                                    try {
                                        val parsed = android.graphics.Color.parseColor("#$clean")
                                        val newHsv = FloatArray(3)
                                        android.graphics.Color.colorToHSV(parsed, newHsv)
                                        hue = newHsv[0]
                                        saturation = newHsv[1]
                                        brightness = newHsv[2]
                                    } catch (_: Exception) {}
                                }
                            },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = if (isDark) Color.White else Color(0xFF1D1D1F)
                            ),
                            cursorBrush = SolidColor(if (isDark) Color.White else Color(0xFF1D1D1F)),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    // New Color
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "NEW SHADE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = talosTextMuted,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(width = 72.dp, height = 36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(currentColor)
                                .border(
                                    BorderStroke(
                                        0.8.dp,
                                        if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f)
                                    ),
                                    RoundedCornerShape(10.dp)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // -------------------------------------------------------------
                // 2D Chromatic Spectrum Wheel (Interactive Canvas)
                // -------------------------------------------------------------
                val wheelSizeDp = 210.dp
                Box(
                    modifier = Modifier.size(wheelSizeDp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val radius = size.width / 2f
                                    val dx = offset.x - radius
                                    val dy = offset.y - radius
                                    val dist = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
                                    saturation = (dist / radius).coerceIn(0f, 1f)
                                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    if (angle < 0) angle += 360f
                                    hue = angle
                                    TalosHaptics.sendHaptic(context)
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val radius = size.width / 2f
                                    val dx = change.position.x - radius
                                    val dy = change.position.y - radius
                                    val dist = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
                                    saturation = (dist / radius).coerceIn(0f, 1f)
                                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    if (angle < 0) angle += 360f
                                    hue = angle
                                }
                            }
                    ) {
                        val radius = size.width / 2f
                        val center = Offset(radius, radius)

                        // 360 Degree Hue Sweep Gradient
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
                            radius = radius,
                            center = center
                        )

                        // Center-to-edge White Radial Gradient for Saturation
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White, Color.Transparent),
                                center = center,
                                radius = radius
                            ),
                            radius = radius,
                            center = center
                        )

                        // Subtle hairline outer rim
                        drawCircle(
                            color = Color.White.copy(alpha = 0.25f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )

                        // Interactive Crosshair Thumb
                        val thumbAngleRad = Math.toRadians(hue.toDouble())
                        val thumbDist = saturation * radius
                        val thumbX = (center.x + thumbDist * cos(thumbAngleRad)).toFloat()
                        val thumbY = (center.y + thumbDist * sin(thumbAngleRad)).toFloat()

                        drawCircle(
                            color = Color.Black.copy(alpha = 0.35f),
                            radius = 12.dp.toPx(),
                            center = Offset(thumbX, thumbY + 1.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 11.dp.toPx(),
                            center = Offset(thumbX, thumbY)
                        )
                        drawCircle(
                            color = currentColor,
                            radius = 8.5.dp.toPx(),
                            center = Offset(thumbX, thumbY)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // -------------------------------------------------------------
                // Brightness / Value Slider
                // -------------------------------------------------------------
                Text(
                    text = "LUMINANCE / BRIGHTNESS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = talosTextMuted,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))

                val fullBrightnessColor = remember(hue, saturation) {
                    val hsv = floatArrayOf(hue, saturation, 1f)
                    Color(android.graphics.Color.HSVToColor(hsv))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black, fullBrightnessColor)
                            )
                        )
                        .border(
                            BorderStroke(
                                0.6.dp,
                                if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.10f)
                            ),
                            RoundedCornerShape(15.dp)
                        )
                ) {
                    Slider(
                        value = brightness,
                        onValueChange = {
                            brightness = it
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // -------------------------------------------------------------
                // RGB Sliders & Numeric Inputs
                // -------------------------------------------------------------
                RgbChannelRow(
                    label = "R",
                    value = red,
                    tint = Color(0xFFFF3B30),
                    onValueChange = { newR ->
                        val hsv = FloatArray(3)
                        android.graphics.Color.RGBToHSV(newR, green, blue, hsv)
                        hue = hsv[0]
                        saturation = hsv[1]
                        brightness = hsv[2]
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                RgbChannelRow(
                    label = "G",
                    value = green,
                    tint = Color(0xFF34C759),
                    onValueChange = { newG ->
                        val hsv = FloatArray(3)
                        android.graphics.Color.RGBToHSV(red, newG, blue, hsv)
                        hue = hsv[0]
                        saturation = hsv[1]
                        brightness = hsv[2]
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                RgbChannelRow(
                    label = "B",
                    value = blue,
                    tint = Color(0xFF007AFF),
                    onValueChange = { newB ->
                        val hsv = FloatArray(3)
                        android.graphics.Color.RGBToHSV(red, green, newB, hsv)
                        hue = hsv[0]
                        saturation = hsv[1]
                        brightness = hsv[2]
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // -------------------------------------------------------------
                // Bottom Action Buttons
                // -------------------------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onAddToPalette != null) {
                        OutlinedButton(
                            onClick = {
                                onAddToPalette(hexString)
                                TalosHaptics.sendHaptic(context)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                0.8.dp,
                                if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Save",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onColorSelected(currentColor)
                            TalosHaptics.sendHaptic(context)
                            onDismissRequest()
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = currentColor,
                            contentColor = if (brightness > 0.65f && saturation < 0.5f) Color.Black else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Apply Shade",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RgbChannelRow(
    label: String,
    value: Int,
    tint: Color,
    onValueChange: (Int) -> Unit
) {
    val isDark = LocalIsDark.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
            modifier = Modifier.width(18.dp)
        )

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = tint,
                activeTrackColor = tint,
                inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
            border = BorderStroke(
                0.6.dp,
                if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
            ),
            modifier = Modifier.width(42.dp)
        ) {
            Text(
                text = value.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                color = if (isDark) Color.White else Color(0xFF1D1D1F),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

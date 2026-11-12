package com.sarvesh173.talos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Share
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.data.Message
import com.sarvesh173.talos.data.Role
import com.sarvesh173.talos.data.ToolCall

/**
 * Reusable Material 3 Expressive PressableSurface composable.
 *
 * Implements smooth tactile press animation:
 * - Pressed scale: 0.96f
 * - Spring physics: Spring.DampingRatioMediumBouncy (0.5f), Spring.StiffnessMedium (1500f)
 * - Rounded corners: Strictly >= 16dp (No square boxes anywhere in M3 UI)
 * - Physical haptic feedback on actuation
 */
@Composable

@Composable
fun PressableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    /** Optional M3E press morph target. When set, the surface tightens toward this
     *  corner shape while held and springs back on release. Only RoundedCornerShape
     *  pairs are morphed; any other shape keeps its resting geometry. */
    pressedShape: RoundedCornerShape? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    pressedScale: Float = 0.96f,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.62f,
            stiffness = 420f
        ),
        label = "PressableSurfaceScale"
    )

    // Shape morph: spatial spring, overshoot allowed (M3E press signature).
    val morphProgress by animateFloatAsState(
        targetValue = if (isPressed && enabled && pressedShape != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "PressableSurfaceMorph"
    )

    val resolvedShape: Shape = run {
        val from = shape as? RoundedCornerShape
        if (from != null && pressedShape != null && morphProgress > 0.001f) {
            MorphRoundedShape(from, pressedShape, morphProgress)
        } else shape
    }

    // M3 state layer: content-colored overlay at 12% while pressed.
    // Effects spring (no overshoot) — the layer must never flash past 12%.
    val stateLayerAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.12f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 3800f
        ),
        label = "PressableSurfaceStateLayer"
    )

    val activeBorder = if (isPressed && enabled) {
        BorderStroke(
            1.2.dp,
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                )
            )
        )
    } else border

    Surface(
        shape = resolvedShape,
        color = color,
        contentColor = contentColor,
        border = activeBorder,
        shadowElevation = shadowElevation,
        tonalElevation = 0.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            // State layer sits UNDER the content so glyphs keep full contrast.
            if (stateLayerAlpha > 0.001f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = stateLayerAlpha }
                        .background(contentColor)
                )
            }
            content()
        }
    }
}

@Composable
fun ThreeDotTypingIndicator(
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.primary,
    dotSize: Dp = 6.dp,
    dotSpacing: Dp = 4.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "TypingIndicatorTransition")

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 0 using FastOutSlowInEasing
                1.0f at 300 using FastOutSlowInEasing
                0.3f at 600 using FastOutSlowInEasing
                0.3f at 1200 using FastOutSlowInEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot1Alpha"
    )

    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 200 using FastOutSlowInEasing
                1.0f at 500 using FastOutSlowInEasing
                0.3f at 800 using FastOutSlowInEasing
                0.3f at 1200 using FastOutSlowInEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot2Alpha"
    )

    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 400 using FastOutSlowInEasing
                1.0f at 700 using FastOutSlowInEasing
                0.3f at 1000 using FastOutSlowInEasing
                0.3f at 1200 using FastOutSlowInEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot3Alpha"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing)
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    alpha = dot1Alpha
                    translationY = -(dot1Alpha - 0.3f) * 4f
                }
                .background(dotColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    alpha = dot2Alpha
                    translationY = -(dot2Alpha - 0.3f) * 4f
                }
                .background(dotColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    alpha = dot3Alpha
                    translationY = -(dot3Alpha - 0.3f) * 4f
                }
                .background(dotColor, CircleShape)
        )
    }
}

@Composable
fun AppleToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeTrackColor: Color = Color(0xFF34C759)
) {
    val isDark = LocalIsDark.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val thumbOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (checked) 22.dp else 3.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.82f,
            stiffness = 450f
        ),
        label = "apple_switch_thumb"
    )

    // Outer Liquid Glass Enclosure Capsule
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (checked) {
                    Brush.verticalGradient(
                        listOf(
                            activeTrackColor.copy(alpha = 0.95f),
                            activeTrackColor.copy(alpha = 0.82f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            if (isDark) Color(0xFF2C2C30).copy(alpha = 0.85f) else Color(0xFFE5E5EA).copy(alpha = 0.90f),
                            if (isDark) Color(0xFF1C1C20).copy(alpha = 0.85f) else Color(0xFFD1D1D6).copy(alpha = 0.90f)
                        )
                    )
                }
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (checked) 0.55f else (if (isDark) 0.28f else 0.65f)),
                            Color.White.copy(alpha = if (checked) 0.12f else (if (isDark) 0.05f else 0.15f))
                        )
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .applePressable(0.95f, pressHaptic = true) {
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Specular ambient light sheen on glass top arc
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.22f else 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Floating Liquid Glass Thumb Lens
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(26.dp)
                .shadow(
                    elevation = if (checked) 4.dp else 2.dp,
                    shape = CircleShape,
                    ambientColor = if (checked) activeTrackColor.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.20f)
                )
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White,
                            Color(0xFFF2F4F8)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        0.8.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White,
                                Color.White.copy(alpha = 0.40f)
                            )
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Internal 3D convex optical highlight
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color.White.copy(alpha = 0.70f),
                                Color.Transparent
                            ),
                            radius = 20f
                        )
                    )
            )
        }
    }
}

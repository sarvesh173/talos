package com.sarvesh173.talos.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.core.motion.MotionTokens

/**
 * TALOS Metallic UI Kit — Precision machined microgeometry with fluid motion.
 */

enum class MetalButtonVariant { Primary, Tonal, Outlined }

@Composable
fun MetalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MetalButtonVariant = MetalButtonVariant.Primary,
    leadingIcon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) MotionTokens.PRESS_SCALE else 1.0f,
        animationSpec = MotionTokens.fastSpatial(),
        label = "MetalButtonScale"
    )

    val containerColor = when (variant) {
        MetalButtonVariant.Primary -> MaterialTheme.colorScheme.primary
        MetalButtonVariant.Tonal -> MaterialTheme.colorScheme.surfaceContainerHighest
        MetalButtonVariant.Outlined -> Color.Transparent
    }

    val contentColor = when (variant) {
        MetalButtonVariant.Primary -> MaterialTheme.colorScheme.onPrimary
        MetalButtonVariant.Tonal -> MaterialTheme.colorScheme.onSurface
        MetalButtonVariant.Outlined -> MaterialTheme.colorScheme.primary
    }

    val borderStroke = when (variant) {
        MetalButtonVariant.Primary -> BorderStroke(1.2.dp, metallicBorderBrush())
        MetalButtonVariant.Tonal -> BorderStroke(1.dp, metallicBorderBrush())
        MetalButtonVariant.Outlined -> BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.38f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
        border = borderStroke,
        interactionSource = interactionSource,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .scale(scale)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Crossfade(targetState = isLoading, animationSpec = MotionTokens.defaultEffects(), label = "BtnLoadingCrossfade") { loading ->
                if (loading) {
                    MetallicCircularLoader(
                        size = 20.dp,
                        strokeWidth = 2.dp,
                        accentColor = contentColor
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (leadingIcon != null) {
                            Icon(
                                imageVector = leadingIcon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetalIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    size: Dp = 48.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1.0f,
        animationSpec = MotionTokens.fastSpatial(),
        label = "MetalIconButtonScale"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .size(size)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(BorderStroke(1.dp, metallicBorderBrush()), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun MetalCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) MotionTokens.PRESS_SCALE else 1.0f,
        animationSpec = MotionTokens.fastSpatial(),
        label = "MetalCardScale"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        border = BorderStroke(1.2.dp, metallicBorderBrush()),
        modifier = modifier
            .scale(scale)
            .shadow(4.dp, shape, spotColor = Color.Black.copy(alpha = 0.15f))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun MetalStatusPill(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = StatusGreen,
    inactiveColor: Color = StatusRed
) {
    val statusColor = if (isActive) activeColor else inactiveColor
    Surface(
        shape = CircleShape,
        color = statusColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.40f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text.uppercase(),
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun MetalContextRing(
    usagePct: Float,
    totalTokens: Long,
    limit: Long,
    modifier: Modifier = Modifier,
    onDoubleTap: (() -> Unit)? = null,
    size: Dp = 48.dp
) {
    val sweepAngle by animateFloatAsState(
        targetValue = (usagePct.coerceIn(0f, 1f)) * 360f,
        animationSpec = MotionTokens.defaultSpatial(),
        label = "ContextRingSweep"
    )

    val arcColor = when {
        usagePct >= 0.85f -> StatusRed
        usagePct >= 0.65f -> StatusAmber
        else -> StatusGreen
    }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (onDoubleTap != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { onDoubleTap() })
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            // Track background
            drawCircle(
                color = Color.Gray.copy(alpha = 0.20f),
                style = Stroke(width = 3.5.dp.toPx())
            )
            // Active usage arc
            drawArc(
                brush = Brush.sweepGradient(listOf(arcColor.copy(alpha = 0.7f), arcColor)),
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            text = "${(usagePct * 100).toInt()}%",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * TALOS Metallic UI 3 Loading Bar.
 * Precision-machined progress indicator with recessed track, specular sheen sweep,
 * and beveled chamfered corners.
 */
@Composable
fun MetallicLoadingBar(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    height: Dp = 8.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "MetallicSheenTransition")
    val sheenOffset by infiniteTransition.animateFloat(
        initialValue = -1.0f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
            repeatMode = RepeatMode.Restart
        ),
        label = "SheenOffset"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 1.0f,
        animationSpec = MotionTokens.defaultSpatial(),
        label = "MetallicProgressAnim"
    )

    Surface(
        shape = RoundedCornerShape(height / 2),
        color = trackColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalWidth = size.width
            val totalHeight = size.height

            if (progress != null) {
                val fillWidth = totalWidth * animatedProgress
                if (fillWidth > 0) {
                    val brush = Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.85f),
                            Color(0xFFFFD580),
                            accentColor
                        ),
                        startX = 0f,
                        endX = fillWidth
                    )
                    drawRoundRect(
                        brush = brush,
                        size = Size(fillWidth, totalHeight),
                        cornerRadius = CornerRadius(totalHeight / 2, totalHeight / 2)
                    )
                }
            } else {
                val bandWidth = totalWidth * 0.45f
                val startX = totalWidth * sheenOffset
                val brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        accentColor.copy(alpha = 0.3f),
                        Color(0xFFFFD580),
                        accentColor.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    startX = startX,
                    endX = startX + bandWidth
                )
                drawRoundRect(
                    brush = brush,
                    size = Size(totalWidth, totalHeight),
                    cornerRadius = CornerRadius(totalHeight / 2, totalHeight / 2)
                )
            }
        }
    }
}

/**
 * TALOS Metallic UI 3 Circular Loader.
 * High-performance rotating metallic bezel ring with specular reflection.
 */
@Composable
fun MetallicCircularLoader(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    strokeWidth: Dp = 3.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "MetallicCircularRing")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CircularRotation"
    )

    Canvas(modifier = modifier.size(size).padding(strokeWidth / 2)) {
        val sweepBrush = Brush.sweepGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.1f),
                accentColor.copy(alpha = 0.4f),
                Color(0xFFFFD580),
                accentColor,
                accentColor.copy(alpha = 0.1f)
            )
        )
        drawCircle(
            color = Color.Gray.copy(alpha = 0.25f),
            style = Stroke(width = strokeWidth.toPx())
        )
        rotate(rotation) {
            drawArc(
                brush = sweepBrush,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

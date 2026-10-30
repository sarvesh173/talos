package com.sarvesh173.talos.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable Material 3 Metallic UI Components
 *
 * Implements Google Material 3 Expressive and precision metallic microgeometry:
 * 1. MetallicCard: High-performance card container with multi-stop brushed metallic borders
 *    and subtle specular top sheen.
 * 2. MachinedIconWell: Recessed socket with dark obsidian tile (#0F172A) ensuring
 *    100% vector contrast across both light and dark themes.
 * 3. MetallicButton: Tactile spring button with metallic hairline edge.
 */

@Composable
fun MetallicCard(
    modifier: Modifier = Modifier,
    shape: Shape = ShapeLargeIncreased,
    isDark: Boolean = LocalIsDark.current,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "MetallicCardScale"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        border = BorderStroke(1.2.dp, metallicBorderBrush()),
        modifier = modifier
            .scale(scale)
            .shadow(4.dp, shape, spotColor = Color.Black.copy(alpha = 0.20f))
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
            modifier = Modifier.padding(MdSpacing.sm),
            content = content
        )
    }
}

@Composable
fun MachinedIconWell(
    emblem: AgentEmblem,
    size: Dp = 44.dp,
    iconSize: Dp = 28.dp,
    forceDark: Boolean = false,
    isDark: Boolean = LocalIsDark.current,
    modifier: Modifier = Modifier
) {
    val wellBg = if (forceDark) {
        MaterialTheme.colorScheme.surfaceContainerHighest // one tile for both modes
    } else {
        emblem.accent.copy(alpha = 0.12f)
    }

    val wellBorder = if (forceDark) {
        BorderStroke(1.dp, Color(0xFFEA580C).copy(alpha = 0.45f))
    } else {
        BorderStroke(1.dp, emblem.accent.copy(alpha = 0.35f))
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(wellBg)
            .border(wellBorder, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        AgentEmblemIcon(emblem = emblem, size = iconSize, tint = null)
    }
}

@Composable
fun MetallicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = LocalIsDark.current,
    isPrimary: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "MetallicButtonScale"
    )

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (isPrimary) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, metallicBorderBrush()),
        modifier = modifier
            .height(52.dp)
            .scale(scale)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = 0.4.sp
        )
    }
}

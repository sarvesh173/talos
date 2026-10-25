package com.sarvesh173.talos.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph

/**
 * M3 Expressive component kit.
 *
 * Color contract (craft floor pairing rule): every fill is paired with its
 * on-color from the SAME scheme role - never hardcoded Color.White. All
 * components re-theme instantly when talos-core serves new tokens.
 */

private enum class M3EButtonState { Idle, Pressed, Loading }

/**
 * Filled M3E button: scheme color roles, spatial-spring press physics,
 * circle-to-scallop shape morph on press, and the canonical M3E
 * [LoadingIndicator] as its loading state (M3Expressive "loading in button").
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun M3EButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val state = when {
        loading -> M3EButtonState.Loading
        pressed -> M3EButtonState.Pressed
        else -> M3EButtonState.Idle
    }

    val scheme = MaterialTheme.colorScheme
    val container = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = 0.12f)
    val contentColor = if (enabled) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.38f)

    val transition = updateTransition(state, label = "m3e_button")

    // Spatial family: shape morph + scale (springs, interruptible).
    val morphProgress by transition.animateFloat(
        transitionSpec = { M3EMotion.fastSpatial() },
        label = "m3e_btn_morph",
    ) { s -> if (s == M3EButtonState.Pressed) 1f else 0f }

    // Effects family: alpha (critically damped, never bounces).
    val contentAlpha by transition.animateFloat(
        transitionSpec = { M3EMotion.defaultEffects() },
        label = "m3e_btn_alpha",
    ) { s -> if (s == M3EButtonState.Loading) 0f else 1f }

    val loadingAlpha by transition.animateFloat(
        transitionSpec = { M3EMotion.defaultEffects() },
        label = "m3e_btn_loading",
    ) { s -> if (s == M3EButtonState.Loading) 1f else 0f }

    val shape: Shape = if (morphProgress > 0.01f) {
        M3EMorphShape(Morph(M3EShapes.circle(), M3EShapes.scallop12()), morphProgress)
    } else {
        RoundedCornerShape(percent = 50)
    }

    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = shape,
        color = container,
        contentColor = contentColor,
        interactionSource = interactionSource,
        modifier = modifier.m3ePressScale(interactionSource),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .graphicsAlphaFx(contentAlpha),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (loadingAlpha > 0.01f) {
                LoadingIndicator(
                    color = contentColor,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(28.dp)
                        .graphicsAlphaFx(loadingAlpha),
                )
            }
        }
    }
}

/**
 * Tonal variant: secondary-container roles - the M3 "softer emphasis" button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun M3ETonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (enabled) scheme.secondaryContainer else scheme.onSurface.copy(alpha = 0.12f)
    val contentColor = if (enabled) scheme.onSecondaryContainer else scheme.onSurface.copy(alpha = 0.38f)

    val interaction = remember { MutableInteractionSource() }

    // Morph budget discipline (55-app M3E census): raw Morph stays reserved
    // for the primary CTA identity moment - tonal buttons use press physics only.
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(percent = 50),
        color = container,
        contentColor = contentColor,
        interactionSource = interaction,
        modifier = modifier.m3ePressScale(interaction),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                LoadingIndicator(color = contentColor, modifier = Modifier.size(26.dp).padding(6.dp))
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        leadingIcon()
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Spring-eased typing dots in scheme primary (M3 emphasized easing loops -
 * the sanctioned tween path for infinite animations per M3E guidance).
 */
@Composable
fun M3ETypingDots(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    dotSize: Dp = 6.dp,
    dotSpacing: Dp = 4.dp,
) {
    val infinite = rememberInfiniteTransition(label = "m3e_dots")

    @Composable
    fun dotAlpha(delayRatio: Float): Float = infinite.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                0.3f at 0 using M3EMotion.EmphasizedDecelerate
                1.0f at (900 * delayRatio).toInt().coerceIn(150, 750) using M3EMotion.EmphasizedDecelerate
                0.3f at 900 using M3EMotion.EmphasizedDecelerate
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "m3e_dot_$delayRatio"
    ).value

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
    ) {
        listOf(1f / 3f, 2f / 3f, 1f).forEach { ratio ->
            val alpha = dotAlpha(ratio)
            Box(
                Modifier
                    .size(dotSize)
                    .graphicsAlphaFx(alpha)
                    .background(color, CircleShape)
            )
        }
    }
}

/** Alpha via graphicsLayer - no recomposition of children on change. */
private fun Modifier.graphicsAlphaFx(alpha: Float): Modifier =
    this.graphicsLayer { this.alpha = alpha }

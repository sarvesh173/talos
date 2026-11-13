package com.sarvesh173.talos.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The stop control: a filled disc carrying a square.
 *
 * It is the send button's other half, not a separate widget — same 48dp disc,
 * same slot. While the agent holds the turn the slot shows this; the first
 * character of a draft morphs it into the arrow, because the harness queues a
 * prompt sent mid-turn instead of rejecting it.
 *
 * @param color the disc fill. White in dark mode, near-black in light: the
 *   square reads as a gap either way, which is the point.
 */
@Composable
fun StopButton(
    color: Color,
    surface: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .applePressable(0.90f, pressHaptic = true) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // The square is cut out of the disc by drawing it in the background
        // colour, so it reads as a hole rather than a shape sitting on top.
        Box(
            modifier = Modifier
                .size(15.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(surface)
        )
    }
}

/**
 * Cross-fades the trailing slot between stop and send.
 *
 * `AnimatedContent` rather than a tween on alpha: the two states are different
 * composables, and a shared transition keeps the morph to one movement instead
 * of two overlapping ones.
 */
@Composable
fun ComposerActionSlot(
    showStop: Boolean,
    stopColor: Color,
    sendContent: @Composable () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = showStop,
        transitionSpec = {
            (fadeIn(tween(140)) + scaleIn(tween(180, initialScale = 0.82f)))
                .togetherWith(fadeOut(tween(110)) + scaleOut(tween(140, targetScale = 0.86f)))
        },
        label = "composer-action",
        modifier = modifier
    ) { stop ->
        if (stop) {
            StopButton(color = stopColor, onClick = onStop)
        } else {
            sendContent()
        }
    }
}

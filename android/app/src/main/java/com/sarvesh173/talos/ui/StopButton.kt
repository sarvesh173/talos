package com.sarvesh173.talos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

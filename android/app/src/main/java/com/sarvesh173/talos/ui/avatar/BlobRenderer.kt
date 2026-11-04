package com.sarvesh173.talos.ui.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sarvesh173.talos.ui.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * BlobRenderer — Compose side of the blob identity form.
 *
 * Animation here is radius + alpha only: the path is rebuilt from BlobMath
 * every frame, and the aura dots move by position/alpha, never by width or
 * height, so layout never relayouts. No shadow layers, no blur — one fill
 * path + one stroke path + six dots is all a Celeron-class machine is asked
 * to do per frame.
 *
 * Frame timing comes from [withFrameNanos], never from a data rate.
 * When the system animator duration scale is 0 ([rememberReducedMotion]),
 * time is frozen at t = 0: the blob renders its resting shape.
 */

private const val PATH_STEPS = 72
private const val AURA_DOTS = 6

/** Builds the closed outline for [params] at time [tSeconds] around the centre. */
fun blobPath(
    params: BlobMotionParams,
    tSeconds: Float,
    cx: Float,
    cy: Float,
    base: Float
): Path {
    return Path().apply {
        for (i in 0..PATH_STEPS) {
            val theta = (i.toFloat() / PATH_STEPS) * 2f * PI.toFloat()
            val r = blobRadius(theta, tSeconds, params) * base
            val px = cx + r * cos(theta)
            val py = cy + r * sin(theta)
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
}

@Composable
fun BlobAvatar(
    params: BlobMotionParams,
    energy: Float,
    avatarSize: Dp,
    tint: Color,
    paused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val frozen = paused || reducedMotion
    var tSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(params, frozen) {
        if (frozen) {
            tSeconds = 0f
            return@LaunchedEffect
        }
        var startNanos = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val elapsed = (now - startNanos) / 1_000_000_000f
            if (elapsed > 3600f) startNanos = now
            tSeconds = elapsed
        }
    }

    val t = if (frozen) 0f else tSeconds
    val e = if (frozen) 0f else energy.coerceIn(0f, 1f)

    Box(modifier = modifier.size(avatarSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(avatarSize)) {
            val canvasW = this.size.width
            val canvasH = this.size.height
            val cx = canvasW / 2f
            val cy = canvasH / 2f
            val base = minOf(canvasW, canvasH) * 0.36f
            val path = blobPath(params, t, cx, cy, base)
            drawPath(path, color = tint, style = Stroke(width = base * 0.06f))
            // Aura dots: alpha + position only, scaled by wobble energy.
            if (e > 0.02f) {
                for (i in 0 until AURA_DOTS) {
                    val ang = t * 0.9f + (i.toFloat() / AURA_DOTS) * 2f * PI.toFloat()
                    val orbit = base * (1.25f + 0.25f * e)
                    val dx = cx + orbit * cos(ang)
                    val dy = cy + orbit * sin(ang)
                    val dotR = base * (0.03f + 0.05f * e)
                    drawCircle(
                        color = tint.copy(alpha = 0.25f + 0.55f * e),
                        radius = dotR,
                        center = androidx.compose.ui.geometry.Offset(dx, dy)
                    )
                }
            }
        }
    }
}

/**
 * Browse grid over the catalogue. Renders the first [pageSize] presets in a
 * scrollable grid so a user can compare animations before choosing one.
 * Each cell is independently animated from the frame clock.
 */
@Composable
fun BlobAvatarPreview(
    pageSize: Int = 24,
    cellSize: Dp = 72.dp,
    tint: Color = Color(0xFF5B8DEF),
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val catalogue = remember { BlobAnimationLibrary.presets.take(pageSize) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Blob catalogue — ${BlobAnimationLibrary.count} presets",
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(cellSize),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(catalogue, key = { BlobAnimationLibrary.key(it) }) { preset ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BlobAvatar(
                        params = preset,
                        energy = wobbleEnergy(preset, 1.25f),
                        avatarSize = cellSize,
                        tint = tint,
                        paused = reducedMotion
                    )
                    Text(
                        text = "${preset.archetype.name.lowercase()}/${preset.lobeCount}",
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

package com.sarvesh173.talos.ui.bot

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sarvesh173.talos.ui.emblemByKey

/**
 * The agent's own SVG mark as its loading animation.
 *
 * Bakes the agent's VectorDrawable into a point cloud (cached per agent id),
 * then plays one of the seven [AgentLogoState] motion profiles. Dots are
 * drawn as small circles whose radius and ink value encode depth — depth IS
 * the ink value, never a separate shading pass.
 *
 * @param intensity animation-clock multiplier (1.0 = baseline). It scales
 *   the clock only; frame timing always comes from withFrameNanos, never
 *   from a token rate.
 */
@Composable
fun AgentLogoMark(
    agentId: String,
    state: AgentLogoState,
    size: Dp,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    tint: Color? = null,
    paused: Boolean = false
) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    // Cached per agent id — re-entering a screen does not re-bake.
    val cloud = remember(agentId, context) { AgentLogoClouds.cloudFor(agentId, context) }
    val brandTint = tint ?: emblemByKey(agentId.lowercase()).accent

    // Honour the system animator duration scale: 0 disables animation, so
    // the mark rests face-on instead of running its cycle.
    val animatorScale = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
    }

    var tSeconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state, intensity, animatorScale, paused) {
        if (paused || animatorScale <= 0f) return@LaunchedEffect
        var lastNanos = -1L
        var elapsed = tSeconds
        while (true) {
            val now = withFrameNanos { it }
            if (lastNanos >= 0L) {
                val dt = (now - lastNanos) / 1_000_000_000f
                if (dt.isFinite() && dt >= 0f) {
                    elapsed += dt * intensity * animatorScale
                    tSeconds = elapsed
                }
            }
            lastNanos = now
        }
    }

    val showStatic = paused || animatorScale <= 0f
    // Static fallback: the mark itself, face-on (m = 1 at the morph crest).
    val dots = remember(cloud, state, tSeconds, showStatic) {
        if (showStatic) {
            // dwel+ morph lands the cycle exactly on the mark.
            LogoMotion.frame(state, cloud, state.dwell + state.morph)
        } else {
            LogoMotion.frame(state, cloud, tSeconds)
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val canvasSizePx = this.size.minDimension
        if (canvasSizePx <= 0f || dots.isEmpty()) return@Canvas
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val frameScale = (this.size.minDimension / 2f) * 0.82f
        val rs = LogoMotion.radiusScale(canvasSizePx, 0.6f)

        for (dot in dots) {
            val px = cx + dot.x * frameScale
            val py = cy - dot.y * frameScale
            val radiusPx = (dot.r * rs).coerceAtLeast(0.3f)
            // Depth through ink: the ink value is the dot's brightness.
            val w = dot.white.coerceIn(0f, 1f)
            val brightness = if (darkTheme) 1f - w else w
            val grey = Color(brightness, brightness, brightness)
            val base = if (tint != null) {
                // Ramp from substrate grey to the brand colour; tintK lets a
                // state (Generating) keep unworked dots neutral.
                lerp(grey, brandTint, dot.tintK.coerceIn(0f, 1f))
            } else {
                grey
            }
            drawCircle(
                color = base.copy(alpha = dot.alpha.coerceIn(0f, 1f)),
                radius = radiusPx,
                center = androidx.compose.ui.geometry.Offset(px, py)
            )
        }
    }
}

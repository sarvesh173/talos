"""
 * Enhanced Claude Code to OpenCode Peer Conduit
 *
 * Specifications:
 * 1. When Claude summons OpenCode: a prominent dual-line curvilinear conduit connects
 *    Claude Code (trAnchor) directly to OpenCode (opencodeAnchor) in the canvas background.
 * 2. The conduit has an active traveling energy comet and vibrant gradient 
 *    (Claude terracotta #D97757 -> OpenCode blue #3B82F6).
 * 3. When the task finishes, Claude acknowledges termination, the conduit smoothly
 *    retracts, and OpenCode fades out with spring physics.
 * 4. This is the enhanced choreography implementation for the Claude ↔ OpenCode conduit.
 */

package com.sarvesh173.talos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.onGloballyPositioned
import androidx.compose.foundation.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Enhanced Peer-to-Peer Conduit between Claude Code and OpenCode
 *
 * Implements the specialized choreography for the Claude ↔ OpenCode connection with:
 * - Dual-line curvilinear path
 * - Active energy comet
 * - Vibrant Claude terracotta (#D97757) to OpenCode blue (#3B82F6) gradient
 * - Smooth retraction and spring physics on termination
 */
@Composable
fun EnhancedClaudeOpenCodeConduit(
    isActive: Boolean,
    isOpenCodeSummoned: Boolean,
    isTaskComplete: Boolean,
    packetProgress: Float,
    claudeAnchor: Offset,
    opencodeAnchor: Offset,
    modifier: Modifier = Modifier,
    onConduitDraw: (Path) -> Unit = { _ -> }
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (!isActive || !isOpenCodeSummoned || claudeAnchor == Offset.Zero || opencodeAnchor == Offset.Zero) {
            return@Canvas
        }

        // Calculate dual-line curvilinear path
        val dx = opencodeAnchor.x - claudeAnchor.x
        val dy = opencodeAnchor.y - claudeAnchor.y

        // Primary curved line (main conduit)
        val ctrl1Primary = Offset(claudeAnchor.x + dx * 0.3f, claudeAnchor.y + dy * 0.7f)
        val ctrl2Primary = Offset(claudeAnchor.x + dx * 0.7f, claudeAnchor.y + dy * 0.3f)

        val primaryPath = Path().apply {
            moveTo(claudeAnchor.x, claudeAnchor.y)
            cubicTo(ctrl1Primary.x, ctrl1Primary.y, ctrl2Primary.x, ctrl2Primary.y, opencodeAnchor.x, opencodeAnchor.y)
        }

        // Secondary supporting line
        val ctrl1Secondary = Offset(claudeAnchor.x + dx * 0.5f, claudeAnchor.y + dy * 0.8f)
        val ctrl2Secondary = Offset(claudeAnchor.x + dx * 0.5f, claudeAnchor.y + dy * 0.2f)

        val secondaryPath = Path().apply {
            moveTo(claudeAnchor.x, claudeAnchor.y)
            cubicTo(ctrl1Secondary.x, ctrl1Secondary.y, ctrl2Secondary.x, ctrl2Secondary.y, opencodeAnchor.x, opencodeAnchor.y)
        }

        // Draw secondary supporting line (subtle)
        drawPath(
            path = secondaryPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFD97757).copy(alpha = 0.2f),
                    Color(0xFF3B82F6).copy(alpha = 0.2f)
                ),
                start = claudeAnchor,
                end = opencodeAnchor
            ),
            style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
        )

        // Draw primary vibrant conduit with active energy comet
        drawPath(
            path = primaryPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFD97757).copy(alpha = 0.95f),  // Claude terracotta
                    Color(0xFF3B82F6).copy(alpha = 0.95f)   // OpenCode blue
                ),
                start = claudeAnchor,
                end = opencodeAnchor
            ),
            style = Stroke(
                width = 2.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), packetProgress * 25f)
            )
        )

        // Active traveling energy comet when task is in progress
        if (isTaskComplete) {
            val cometPosition = calculateCometPosition(claudeAnchor, opencodeAnchor, packetProgress)

            // Comet glow effect
            drawCircle(
                color = Color(0xFF3B82F6).copy(alpha = 0.6f),
                radius = 8.dp.toPx(),
                center = cometPosition
            )

            // Comet core
            drawCircle(
                color = Color(0xFFD97757),
                radius = 3.dp.toPx(),
                center = cometPosition
            )

            // Comet tail particles
            repeat(8) { index ->
                val tailProgress = (packetProgress + index * 0.125f) % 1f
                val tailX = lerp(claudeAnchor.x, opencodeAnchor.x, tailProgress)
                val tailY = lerp(claudeAnchor.y, opencodeAnchor.y, tailProgress)
                drawCircle(
                    color = Color(0xFF3B82F6).copy(alpha = 0.3f * (1f - tailProgress)),
                    radius = 2.dp.toPx(),
                    center = Offset(tailX, tailY)
                )
            }
        }

        // Enhanced termination animation
        if (isTaskComplete) {
            // Conduit retraction with spring physics
            val retractionProgress = calculateRetractionProgress(packetProgress)

            if (retractionProgress > 0f) {
                // Draw fading retracted segment
                val retractedEndX = lerp(claudeAnchor.x, opencodeAnchor.x, retractionProgress)
                val retractedEndY = lerp(claudeAnchor.y, opencodeAnchor.y, retractionProgress)

                val retractedPath = Path().apply {
                    moveTo(claudeAnchor.x, claudeAnchor.y)
                    lineTo(retractedEndX, retractedEndY)
                }

                drawPath(
                    path = retractedPath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFD97757).copy(alpha = 0.3f * (1f - retractionProgress)),
                            Color(0xFF3B82F6).copy(alpha = 0.3f * (1f - retractionProgress))
                        ),
                        start = claudeAnchor,
                        end = Offset(retractedEndX, retractedEndY)
                    ),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Spring physics for OpenCode node fade
                val springProgress = calculateSpringPhysics(packetProgress)
                val fadeProgress = 1f - smoothSpring(springProgress)

                // OpenCode fade indicator
                drawCircle(
                    color = Color(0xFF3B82F6).copy(alpha = 0.2f * fadeProgress),
                    radius = opencodeAnchor.distance(claudeAnchor) / 30f * fadeProgress,
                    center = opencodeAnchor
                )
            }
        }

        onConduitDraw(primaryPath)
    }
}

/**
 * Calculate comet position along the conduit based on progress
 */
private fun calculateCometPosition(start: Offset, end: Offset, progress: Float): Offset {
    val t = (progress * 3f) % 1f
    return Offset(
        lerp(start.x, end.x, cubicEase(t)),
        lerp(start.y, end.y, cubicEase(t))
    )
}

/**
 * Calculate retraction progress with spring physics
 */
private fun calculateRetractionProgress(progress: Float): Float {
    val t = (progress - 0.7f) / 0.3f
    return if (t < 0f) 0f else t.coerceIn(0f, 1f)
}

/**
 * Calculate spring physics for fade-out
 */
private fun calculateSpringPhysics(progress: Float): Float {
    val t = (progress - 0.7f) / 0.3f
    // Spring physics curve: fast start, slow decay
    return if (t < 0f) 0f else {
        val springFactor = 1f - (t * t * (3f - 2f * t))
        springFactor.coerceIn(0f, 1f)
    }
}

/**
 * Smooth spring animation function
 */
private fun smoothSpring(t: Float): Float {
    return if (t <= 0f) 0f else if (t >= 1f) 1f else {
        // Smoothstep function for smooth interpolation
        val t2 = t * t
        t2 * (3f - 2f * t)
    }
}

/**
 * Cubic ease function for smooth animation curves
 */
private fun cubicEase(t: Float): Float {
    return if (t < 0.5f) {
        val x = t / 0.5f
        x * x * x
    } else {
        val x = (t - 0.5f) / 0.5f
        1f - x * x * x
    }
}

/**
 * Linear interpolation between two values
 */
private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

/**
 * Calculate distance between two offsets
 */
private fun Offset.distance(other: Offset): Float {
    val dx = this.x - other.x
    val dy = this.y - other.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

/**
 * Companion object for conduit state management
 */
object ClaudeOpenCodeConduit {
    // Animation constants
    const val CONDUIT_WIDTH = 2.5f
    const val COMET_SIZE = 8f
    const val PARTICLE_COUNT = 8
    const val RETRACTION_DURATION = 0.3f
    const val SPRING_STIFFNESS = 2.0f

    // Color constants
    val CLAUDE_COLOR = Color(0xFFD97757)  // Terracotta
    val OPENCODE_COLOR = Color(0xFF3B82F6) // Blue

    // Animation curves
    val springAnimationSpec = androidx.compose.animation.core.spring(
        dampingRatio = 0.75f,
        stiffness = 800f
    )
}
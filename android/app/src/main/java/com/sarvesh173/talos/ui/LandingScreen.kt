package com.sarvesh173.talos.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient

@Composable
fun LandingScreen(
    client: TalosClient,
    onGetStarted: () -> Unit,
    onConnect: ((String, String) -> Unit)? = null,
    onOpenWizard: (() -> Unit)? = null
) {
    // Independent floating animation clocks with varied periods
    val infiniteTransition = rememberInfiniteTransition(label = "agent_float_loop")

    val floatTopLeft by infiniteTransition.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tl_float"
    )
    val floatTopRight by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = -8f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tr_float"
    )
    val floatMidLeft by infiniteTransition.animateFloat(
        initialValue = -7f, targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ml_float"
    )
    val floatMidRight by infiniteTransition.animateFloat(
        initialValue = 9f, targetValue = -9f,
        animationSpec = infiniteRepeatable(tween(3100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mr_float"
    )
    val floatLowerLeft by infiniteTransition.animateFloat(
        initialValue = -8f, targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(3300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ll_float"
    )
    val floatLowerMid by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(2900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "lm_float"
    )
    val floatLowerRight by infiniteTransition.animateFloat(
        initialValue = -7f, targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "lr_float"
    )

    // Pulse animation for the beacon above Get Started
    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beacon_pulse"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E12))
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        // ==============================================================
        // FLOATING AGENT CREATURES (PIXEL-PERFECT REPLICA OF SCREENSHOT)
        // ==============================================================

        // 1. Top-Left: Green Cloud Creature (Hermes)
        Box(
            modifier = Modifier
                .offset(
                    x = screenWidth * 0.12f,
                    y = screenHeight * 0.08f + floatTopLeft.dp
                )
        ) {
            GreenCloudCreature(
                modifier = Modifier.size(width = 86.dp, height = 72.dp),
                color = Color(0xFF00D26A)
            )
        }

        // 2. Top-Right: Magenta/Pink Sphere Creature (Hot Pink)
        Box(
            modifier = Modifier
                .offset(
                    x = screenWidth * 0.62f,
                    y = screenHeight * 0.09f + floatTopRight.dp
                )
        ) {
            PinkSphereCreature(
                modifier = Modifier.size(72.dp),
                color = Color(0xFFF43F5E)
            )
        }

        // 3. Mid-Left (Upper): Purple Sphere Creature peeking from border
        Box(
            modifier = Modifier
                .offset(
                    x = (-20).dp,
                    y = screenHeight * 0.27f + floatMidLeft.dp
                )
        ) {
            PurplePeekingCreature(
                modifier = Modifier.size(68.dp),
                color = Color(0xFF8B5CF6)
            )
        }

        // 4. Mid-Right (Upper): Tan/Golden Creature peeking from right border
        Box(
            modifier = Modifier
                .offset(
                    x = screenWidth - 46.dp,
                    y = screenHeight * 0.25f + floatMidRight.dp
                )
        ) {
            TanPeekingCreature(
                modifier = Modifier.size(80.dp),
                color = Color(0xFFD97706)
            )
        }

        // 5. Mid-Left (Lower): Orange Arc Creature peeking
        Box(
            modifier = Modifier
                .offset(
                    x = (-32).dp,
                    y = screenHeight * 0.44f + floatLowerLeft.dp
                )
        ) {
            OrangeArcCreature(
                modifier = Modifier.size(width = 84.dp, height = 96.dp),
                color = Color(0xFFFF6B00)
            )
        }

        // 6. Mid-Right (Lower): Red Semi-Sphere Creature peeking
        Box(
            modifier = Modifier
                .offset(
                    x = screenWidth - 36.dp,
                    y = screenHeight * 0.43f + floatLowerRight.dp
                )
        ) {
            RedPeekingCreature(
                modifier = Modifier.size(84.dp),
                color = Color(0xFFFF2D55)
            )
        }

        // 7. Bottom-Left: Electric Blue Squircle Creature
        Box(
            modifier = Modifier
                .offset(
                    x = screenWidth * 0.10f,
                    y = screenHeight * 0.64f + floatLowerLeft.dp
                )
        ) {
            BlueSquircleCreature(
                modifier = Modifier.size(64.dp),
                color = Color(0xFF0084FF)
            )
        }

        // 8. Bottom-Center: Bright Orange Hexagon Creature (The Council)
        Box(
            modifier = Modifier
                .offset(
                    x = screenWidth * 0.42f - 40.dp,
                    y = screenHeight * 0.65f + floatLowerMid.dp
                )
        ) {
            OrangeHexagonCreature(
                modifier = Modifier.size(width = 80.dp, height = 88.dp),
                color = Color(0xFFFF5722)
            )
        }

        // 9. Bottom-Right: Cyan / Mint Green Creature
        Box(
            modifier = Modifier
                .offset(
                    x = screenWidth * 0.78f,
                    y = screenHeight * 0.62f + floatLowerRight.dp
                )
        ) {
            CyanCircleCreature(
                modifier = Modifier.size(66.dp),
                color = Color(0xFF00D1B2)
            )
        }

        // ==============================================================
        // CENTER HERO TYPOGRAPHY (CRISP, BOLD, FAITHFUL TO SCREENSHOT)
        // ==============================================================
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-20).dp)
                .padding(horizontal = 32.dp)
        ) {
            Text(
                text = "TALOS",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Your team of always-on Bots that\nfinish the work",
                color = Color(0xFF94A3B8),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }

        // ==============================================================
        // BOTTOM ACTION AREA (ANIMATION ABOVE GET STARTED + WHITE PILL)
        // ==============================================================
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            // Pulse animation / Thinking Orb above "Get Started"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                ThinkingOrb(
                    state = OrbState.WEAVING,
                    size = 32.dp,
                    dotColor = Color.White,
                    secondaryColor = Color(0xFF38BDF8),
                    speedMultiplier = 1.3f
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .scale(beaconPulse)
                        .clip(CircleShape)
                        .background(Color(0xFF00D26A))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Mesh Online · Always-on",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // High-Contrast Pure-White Pill Button: "Get Started"
            Button(
                onClick = onGetStarted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0D0E12)
                ),
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Get Started",
                    color = Color(0xFF0D0E12),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subtitle Terms notice
            Text(
                text = "By using TALOS, you additionally agree to these Terms.",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// =====================================================================
// VECTOR CANVASES FOR ORGANIC BOT CREATURES (EXACT SCREENSHOT DETAILS)
// =====================================================================

@Composable
fun GreenCloudCreature(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Overlapping cloud circles
        drawCircle(color, radius = w * 0.32f, center = Offset(w * 0.30f, h * 0.52f))
        drawCircle(color, radius = w * 0.36f, center = Offset(w * 0.52f, h * 0.40f))
        drawCircle(color, radius = w * 0.30f, center = Offset(w * 0.72f, h * 0.54f))
        drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.44f, h * 0.66f))

        // Two vertical pill eyes
        val eyeW = w * 0.08f
        val eyeH = h * 0.26f
        val eyeY = h * 0.38f
        drawRoundRect(
            color = Color(0xFF0D0E12),
            topLeft = Offset(w * 0.36f, eyeY),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
        drawRoundRect(
            color = Color(0xFF0D0E12),
            topLeft = Offset(w * 0.56f, eyeY),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
    }
}

@Composable
fun PinkSphereCreature(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = w * 0.48f, center = Offset(w * 0.5f, h * 0.5f))

        // Slanted pill eyes looking down-left
        val eyeW = w * 0.11f
        val eyeH = h * 0.28f
        drawRoundRect(
            color = Color(0xFF0D0E12),
            topLeft = Offset(w * 0.48f, h * 0.34f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
        drawRoundRect(
            color = Color(0xFF0D0E12),
            topLeft = Offset(w * 0.70f, h * 0.40f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
    }
}

@Composable
fun PurplePeekingCreature(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = w * 0.48f, center = Offset(w * 0.4f, h * 0.5f))

        // Eye peeking rightward
        val eyeR = w * 0.16f
        drawCircle(Color(0xFF0D0E12), radius = eyeR, center = Offset(w * 0.68f, h * 0.48f))
    }
}

@Composable
fun TanPeekingCreature(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = w * 0.48f, center = Offset(w * 0.6f, h * 0.5f))

        // Two arched eyes peeking leftwards
        val eyeR = w * 0.12f
        drawCircle(Color(0xFF0D0E12), radius = eyeR, center = Offset(w * 0.28f, h * 0.42f))
        drawCircle(Color(0xFF0D0E12), radius = eyeR, center = Offset(w * 0.50f, h * 0.42f))
    }
}

@Composable
fun OrangeArcCreature(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawOval(color, topLeft = Offset(-w * 0.35f, 0f), size = Size(w * 1.35f, h))

        // Two curved eyes peeking inwards
        val eyeR = w * 0.12f
        drawCircle(Color(0xFF0D0E12), radius = eyeR, center = Offset(w * 0.48f, h * 0.42f))
        drawCircle(Color(0xFF0D0E12), radius = eyeR, center = Offset(w * 0.72f, h * 0.48f))
    }
}

@Composable
fun RedPeekingCreature(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = w * 0.48f, center = Offset(w * 0.65f, h * 0.5f))

        // Eye peeking from edge
        val eyeW = w * 0.11f
        val eyeH = h * 0.28f
        drawRoundRect(
            color = Color(0xFF0D0E12),
            topLeft = Offset(w * 0.25f, h * 0.38f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
    }
}

@Composable
fun BlueSquircleCreature(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(w * 0.32f, h * 0.32f)
        )
        // Two round dot eyes
        val eyeR = w * 0.08f
        drawCircle(Color(0xFF0D0E12), radius = eyeR, center = Offset(w * 0.38f, h * 0.48f))
        drawCircle(Color(0xFF0D0E12), radius = eyeR, center = Offset(w * 0.64f, h * 0.48f))
    }
}

@Composable
fun OrangeHexagonCreature(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val r = w * 0.48f
        val path = Path().apply {
            for (i in 0 until 6) {
                val angle = Math.toRadians((60.0 * i - 30.0)).toFloat()
                val x = cx + r * kotlin.math.cos(angle)
                val y = cy + r * kotlin.math.sin(angle)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(path, color)

        // Two vertical pill eyes (signature Grok bot hexagon)
        val eyeW = w * 0.11f
        val eyeH = h * 0.34f
        drawRoundRect(
            color = Color(0xFF0D0E12),
            topLeft = Offset(cx - eyeW * 1.5f, cy - eyeH / 2),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
        drawRoundRect(
            color = Color(0xFF0D0E12),
            topLeft = Offset(cx + eyeW * 0.5f, cy - eyeH / 2),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
    }
}

@Composable
fun CyanCircleCreature(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = w * 0.48f, center = Offset(w * 0.5f, h * 0.5f))

        // Two vertical pill eyes
        val eyeW = w * 0.10f
        val eyeH = h * 0.28f
        drawRoundRect(
            color = Color(0xFF0D0E12),
            topLeft = Offset(w * 0.36f, h * 0.36f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
        drawRoundRect(
            color = Color(0xFF0D0E12),
            topLeft = Offset(w * 0.56f, h * 0.36f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
    }
}

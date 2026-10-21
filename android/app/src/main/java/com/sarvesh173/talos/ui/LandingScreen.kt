package com.sarvesh173.talos.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.data.MeshInfo
import com.sarvesh173.talos.network.TalosClient
import kotlinx.coroutines.launch

@Composable
fun LandingScreen(
    client: TalosClient,
    onConnect: (String, String) -> Unit,
    onOpenWizard: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(true) }
    var meshInfo by remember { mutableStateOf<MeshInfo?>(null) }
    var isPairing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showEndpointEdit by remember { mutableStateOf(false) }
    var customUrl by remember { mutableStateOf(client.baseUrl) }

    // Floating animation loops with staggered periods
    val infiniteTransition = rememberInfiniteTransition(label = "floating_agents")
    val floatOffset1 by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float1"
    )
    val floatOffset2 by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float2"
    )
    val floatOffset3 by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float3"
    )

    LaunchedEffect(Unit) {
        isChecking = true
        meshInfo = client.checkMeshStatus()
        isChecking = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1117))
    ) {
        // --- FLOATING AGENT BLOBS (Inspired by Grok Bot, Vector/Canvas only) ---

        // 1. Top-Left: Green Cloud Agent (Hermes) with winged-H emblem badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 36.dp, y = (70 + floatOffset1).dp)
        ) {
            CloudAgentBlob(
                modifier = Modifier.size(width = 92.dp, height = 76.dp),
                color = Color(0xFF22C55E)
            )
            AgentEmblemBadge(
                emblem = emblemByKey("hermes"),
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        // 2. Top-Right: Warm Coral Agent (Claude Code) with spark emblem badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-42).dp, y = (85 + floatOffset2).dp)
        ) {
            PinkAgentBlob(
                modifier = Modifier.size(68.dp),
                color = Color(0xFFD97757)
            )
            AgentEmblemBadge(
                emblem = emblemByKey("claude"),
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // 3. Mid-Left: Purple Droplet Agent (OmniRush) with bolt emblem badge
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-16).dp, y = (-120 + floatOffset3).dp)
        ) {
            PurpleAgentBlob(
                modifier = Modifier.size(width = 54.dp, height = 62.dp),
                color = Color(0xFFA855F7)
            )
            AgentEmblemBadge(
                emblem = emblemByKey("omnirush"),
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        // 4. Mid-Right: Terracotta Sphere peeking (Claude Code) with spark emblem
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 24.dp, y = (-90 + floatOffset1).dp)
        ) {
            TerracottaAgentBlob(
                modifier = Modifier.size(80.dp),
                color = Color(0xFFB45309)
            )
            AgentEmblemBadge(
                emblem = emblemByKey("claude"),
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // 5. Lower-Left: Large Orange Curved Arc peeking (OpenClaw) with claw emblem
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-30).dp, y = (40 + floatOffset2).dp)
        ) {
            OrangeArcAgentBlob(
                modifier = Modifier.size(width = 84.dp, height = 96.dp),
                color = Color(0xFFF97316)
            )
            AgentEmblemBadge(
                emblem = emblemByKey("openclaw"),
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        // 6. Lower-Right: Red Sphere peeking
        RedAgentBlob(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 28.dp, y = (30 + floatOffset3).dp)
                .size(84.dp),
            color = Color(0xFFEF4444)
        )

        // 7. Bottom-Left: Sky Blue Squircle Agent (Local AI / Companion)
        BlueSquircleAgentBlob(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 36.dp, y = (-230 + floatOffset1).dp)
                .size(62.dp),
            color = Color(0xFF0EA5E9)
        )

        // 8. Bottom-Center: Orange Hexagon Agent with vertical pill eyes (The Council)
        HexagonAgentBlob(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = 0.dp, y = (-220 + floatOffset2).dp)
                .size(width = 80.dp, height = 88.dp),
            color = Color(0xFFEA580C)
        )

        // 9. Bottom-Right: Teal Capsule Agent
        TealAgentBlob(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-40).dp, y = (-240 + floatOffset3).dp)
                .size(64.dp),
            color = Color(0xFF14B8A6)
        )

        // --- CENTER BRAND & HERO SECTION ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-30).dp)
                .padding(horizontal = 32.dp)
        ) {
            Text(
                text = "TALOS",
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Your team of always-on agents that finish the work",
                color = Color(0xFF94A3B8),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Dynamic Mesh Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E2433).copy(alpha = 0.8f))
                    .border(1.dp, Color(0xFF2E384D), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (meshInfo?.active == true) StatusGreen else if (isChecking) PrimaryAccent else Color(0xFFEF4444))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (meshInfo?.active == true) "Tailscale Verified (${meshInfo?.self_ip ?: "100.75.107.79"})"
                           else if (isChecking) "Checking Tailscale Mesh..."
                           else "Mesh Standby",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // --- BOTTOM ACTION SECTION (Clean White Pill Button) ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 36.dp)
                .fillMaxWidth()
        ) {
            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // High-Contrast Grok-Style White Pill Button
            Button(
                onClick = {
                    scope.launch {
                        isPairing = true
                        errorMessage = null
                        val token = client.pairDevice()
                        if (token != null) {
                            onConnect(client.baseUrl, token)
                        } else {
                            errorMessage = "Could not link with agent. Verify Tailscale network."
                        }
                        isPairing = false
                    }
                },
                enabled = !isPairing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0F1117),
                    disabledContainerColor = Color.White.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isPairing) {
                    CircularProgressIndicator(
                        color = Color(0xFF0F1117),
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Linking Agent...",
                        color = Color(0xFF0F1117),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                } else {
                    Text(
                        text = "Connect & Enter Chamber",
                        color = Color(0xFF0F1117),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            if (onOpenWizard != null) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = { onOpenWizard() }) {
                    Text(
                        text = "Guided Setup",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Vector emblem roster (no emoji, no text glyphs)
            AgentRoster()

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle / Disclaimer line
            Text(
                text = "Autonomous Multi-Agent Mesh \u00B7 Hermes, Claude, OpenClaw & OmniRush",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )

            // Subtle Custom Endpoint Toggle
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (showEndpointEdit) "Hide Custom Endpoint" else "Custom Endpoint",
                color = Color(0xFF475569),
                fontSize = 10.sp,
                modifier = Modifier
                    .clickable { showEndpointEdit = !showEndpointEdit }
                    .padding(4.dp)
            )

            if (showEndpointEdit) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customUrl,
                    onValueChange = {
                        customUrl = it
                        client.updateConfig(it, client.apiToken)
                    },
                    label = { Text("Endpoint URL", color = TextMuted, fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E2433),
                        unfocusedContainerColor = Color(0xFF1E2433),
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ==========================================
// CANVASES FOR ORGANIC AGENT BLOBS (NO EMOJIS)
// ==========================================

@Composable
fun CloudAgentBlob(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Draw cloud body using overlapping circles
        drawCircle(color, radius = w * 0.30f, center = Offset(w * 0.32f, h * 0.52f))
        drawCircle(color, radius = w * 0.34f, center = Offset(w * 0.54f, h * 0.40f))
        drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.72f, h * 0.58f))
        drawCircle(color, radius = w * 0.26f, center = Offset(w * 0.42f, h * 0.66f))

        // Two vertical pill eyes
        val eyeW = w * 0.08f
        val eyeH = h * 0.25f
        val eyeY = h * 0.40f
        drawRoundRect(
            color = Color(0xFF0F1117),
            topLeft = Offset(w * 0.38f, eyeY),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
        drawRoundRect(
            color = Color(0xFF0F1117),
            topLeft = Offset(w * 0.56f, eyeY),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
    }
}

@Composable
fun PinkAgentBlob(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = w * 0.48f, center = Offset(w * 0.5f, h * 0.5f))

        // Two pill eyes looking down-left
        val eyeW = w * 0.11f
        val eyeH = h * 0.28f
        drawRoundRect(
            color = Color(0xFF0F1117),
            topLeft = Offset(w * 0.46f, h * 0.36f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
        drawRoundRect(
            color = Color(0xFF0F1117),
            topLeft = Offset(w * 0.68f, h * 0.42f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
    }
}

@Composable
fun PurpleAgentBlob(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawOval(color, topLeft = Offset(0f, 0f), size = Size(w, h))

        // Eyes peeking right
        val eyeR = w * 0.15f
        drawCircle(Color(0xFF0F1117), radius = eyeR, center = Offset(w * 0.70f, h * 0.48f))
        drawCircle(Color.White, radius = eyeR * 0.35f, center = Offset(w * 0.73f, h * 0.45f))
    }
}

@Composable
fun TerracottaAgentBlob(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = w * 0.48f, center = Offset(w * 0.5f, h * 0.5f))

        // Two arched eyes
        val eyeR = w * 0.12f
        drawCircle(Color(0xFF0F1117), radius = eyeR, center = Offset(w * 0.28f, h * 0.44f))
        drawCircle(Color(0xFF0F1117), radius = eyeR, center = Offset(w * 0.52f, h * 0.44f))
    }
}

@Composable
fun OrangeArcAgentBlob(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawOval(color, topLeft = Offset(-w * 0.3f, 0f), size = Size(w * 1.3f, h))

        // Eyes peeking from left arc
        val eyeR = w * 0.12f
        drawCircle(Color(0xFF0F1117), radius = eyeR, center = Offset(w * 0.50f, h * 0.45f))
        drawCircle(Color(0xFF0F1117), radius = eyeR, center = Offset(w * 0.74f, h * 0.50f))
    }
}

@Composable
fun RedAgentBlob(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = w * 0.48f, center = Offset(w * 0.5f, h * 0.5f))

        // Subtle eyes peeking from edge
        val eyeW = w * 0.10f
        val eyeH = h * 0.26f
        drawRoundRect(
            color = Color(0xFF0F1117),
            topLeft = Offset(w * 0.25f, h * 0.40f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
    }
}

@Composable
fun BlueSquircleAgentBlob(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(w * 0.32f, h * 0.32f)
        )
        // Two round dot eyes
        val eyeR = w * 0.08f
        drawCircle(Color(0xFF0F1117), radius = eyeR, center = Offset(w * 0.40f, h * 0.50f))
        drawCircle(Color(0xFF0F1117), radius = eyeR, center = Offset(w * 0.65f, h * 0.50f))
    }
}

@Composable
fun HexagonAgentBlob(modifier: Modifier = Modifier, color: Color) {
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

        // Two vertical pill eyes (Grok bot signature)
        val eyeW = w * 0.11f
        val eyeH = h * 0.34f
        drawRoundRect(
            color = Color(0xFF0F1117),
            topLeft = Offset(cx - eyeW * 1.5f, cy - eyeH / 2),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
        drawRoundRect(
            color = Color(0xFF0F1117),
            topLeft = Offset(cx + eyeW * 0.5f, cy - eyeH / 2),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
    }
}

@Composable
fun TealAgentBlob(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = w * 0.48f, center = Offset(w * 0.5f, h * 0.5f))

        // Two vertical pill eyes
        val eyeW = w * 0.10f
        val eyeH = h * 0.28f
        drawRoundRect(
            color = Color(0xFF0F1117),
            topLeft = Offset(w * 0.36f, h * 0.36f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
        drawRoundRect(
            color = Color(0xFF0F1117),
            topLeft = Offset(w * 0.56f, h * 0.36f),
            size = Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2, eyeW / 2)
        )
    }
}

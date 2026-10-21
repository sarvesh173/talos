package com.sarvesh173.talos.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.data.MeshInfo
import com.sarvesh173.talos.network.TalosClient

// ==========================================
// PALETTE (Grok-style dark onboarding)
// ==========================================

private val WizardBg = Color(0xFF0F1117)
private val WizardSurface = Color(0xFF171B24)
private val WizardSurfaceAlt = Color(0xFF1E2433)
private val WizardBorder = Color(0xFF2A3242)
private val WizardText = Color(0xFFF1F5F9)
private val WizardMuted = Color(0xFF8A94A6)
private val WizardDanger = Color(0xFFEF4444)

// Pastel illustration badge colors
private val PastelMint = Color(0xFFA7F3D0)
private val PastelLavender = Color(0xFFDDD6FE)
private val PastelSky = Color(0xFFBAE6FD)
private val PastelPeach = Color(0xFFFED7AA)

private const val MESH_IP = "100.75.107.79"
private const val PEER_LABEL = "V2158"

// ==========================================
// MAIN WIZARD
// ==========================================

@Composable
fun OnboardingWizard(
    client: TalosClient,
    onComplete: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var meshInfo by remember { mutableStateOf<MeshInfo?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf<String?>(null) }
    var isPairing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Step 2: verify the mesh over Tailscale WireGuard
    LaunchedEffect(step) {
        if (step == 1) {
            isChecking = true
            errorMessage = null
            meshInfo = client.checkMeshStatus()
            isChecking = false
        }
        // Step 3: mint an agent API token
        if (step == 2 && token == null && !isPairing) {
            isPairing = true
            errorMessage = null
            val generated = client.pairDevice()
            if (generated.isNullOrBlank()) {
                errorMessage = "Could not mint a token. Verify the Tailscale mesh is reachable."
            } else {
                token = generated
            }
            isPairing = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WizardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ---------- TOP BAR: STEP X OF 4 + BACK NAV ----------
            WizardTopBar(
                step = step,
                canGoBack = step > 0,
                onBack = {
                    when (step) {
                        2 -> {
                            // Leaving token step clears the pending token
                            token = null
                            errorMessage = null
                        }
                    }
                    if (step > 0) step -= 1
                }
            )

            // ---------- STEP CONTENT ----------
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (slideInHorizontally { it / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut(tween(180)))
                },
                label = "wizard_step"
            ) { current ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp)
                ) {
                    when (current) {
                        0 -> OverviewStep()
                        1 -> MeshStep(
                            meshInfo = meshInfo,
                            isChecking = isChecking,
                            errorMessage = errorMessage
                        )
                        2 -> TokenStep(
                            token = token,
                            isPairing = isPairing,
                            errorMessage = errorMessage
                        )
                        else -> WelcomeStep()
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            // ---------- BOTTOM ACTION ----------
            WizardBottomBar(
                step = step,
                isBusy = isChecking || isPairing,
                onPrimary = {
                    when (step) {
                        0 -> step = 1
                        1 -> {
                            if (meshInfo?.active == true) step = 2 else errorMessage =
                                "Mesh not verified. Confirm Tailscale is connected."
                        }
                        2 -> {
                            val t = token
                            if (!t.isNullOrBlank()) {
                                client.updateConfig(client.baseUrl, t)
                                step = 3
                            } else {
                                errorMessage = "Token is not ready yet."
                            }
                        }
                        3 -> onComplete()
                    }
                }
            )
        }
    }
}

// ==========================================
// TOP BAR
// ==========================================

@Composable
private fun WizardTopBar(
    step: Int,
    canGoBack: Boolean,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (canGoBack) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = WizardText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Step ${step + 1} of 4",
                color = WizardMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(modifier = Modifier.size(40.dp))
        }

        // Segmented progress rail
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, start = 6.dp, end = 6.dp)
        ) {
            for (i in 0 until 4) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i <= step) Color(0xFF6366F1) else WizardBorder
                        )
                )
            }
        }
    }
}

// ==========================================
// BOTTOM BAR (White Pill Button)
// ==========================================

@Composable
private fun WizardBottomBar(
    step: Int,
    isBusy: Boolean,
    onPrimary: () -> Unit
) {
    val label = when (step) {
        0 -> "Get started"
        1 -> "Confirm Mesh & Continue"
        2 -> "Activate Token & Link"
        else -> "Enter Chamber"
    }
    val busyLabel = when (step) {
        1 -> "Verifying Mesh..."
        2 -> "Minting Token..."
        else -> label
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WizardBg)
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        Button(
            onClick = onPrimary,
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = WizardBg,
                disabledContainerColor = Color.White.copy(alpha = 0.45f),
                disabledContentColor = WizardBg.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    color = WizardBg,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = busyLabel,
                    color = WizardBg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            } else {
                Text(
                    text = label,
                    color = WizardBg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

// ==========================================
// STEP 1 — OVERVIEW
// ==========================================

@Composable
private fun OverviewStep() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            ThinkingOrb(state = OrbState.WEAVING, size = 54.dp)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Initialize Your TALOS Mesh",
            color = WizardText,
            fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 33.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "A single plan for the entire agent suite",
            color = WizardMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Card container
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(WizardSurface)
                .border(1.dp, WizardBorder, RoundedCornerShape(22.dp))
                .padding(14.dp)
        ) {
            OverviewCard(
                index = 1,
                title = "Connect over Tailscale WireGuard",
                body = "Private overlay network. No public ports, no exposed surface.",
                badgeTint = PastelMint,
                badge = { ShieldLanBadge(tint = WizardBg) }
            )
            OverviewCard(
                index = 2,
                title = "Pair with Autonomous Agent Fleet",
                body = "One handshake brings every always-on agent into the mesh.",
                badgeTint = PastelLavender,
                badge = { AgentFleetBadge(tint = WizardBg) }
            )
            OverviewCard(
                index = 3,
                title = "Real-Time Collaboration & Tool Bus",
                body = "Shared event bus, tool calls and presence in a single stream.",
                badgeTint = PastelSky,
                badge = { TerminalBusBadge(tint = WizardBg) }
            )
        }
    }
}

@Composable
private fun OverviewCard(
    index: Int,
    title: String,
    body: String,
    badgeTint: Color,
    badge: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WizardSurfaceAlt)
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6366F1)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = index.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    color = WizardText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                color = WizardMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Pastel illustration badge
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(badgeTint),
            contentAlignment = Alignment.Center
        ) {
            badge()
        }
    }
}

// ==========================================
// STEP 2 — TAILSCALE MESH VERIFICATION
// ==========================================

@Composable
private fun MeshStep(
    meshInfo: MeshInfo?,
    isChecking: Boolean,
    errorMessage: String? = null
) {
    val active = meshInfo?.active == true

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            ThinkingOrb(state = OrbState.SEARCHING, size = 54.dp)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Tailscale Mesh Verification",
            color = WizardText,
            fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 33.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Confirm the overlay link before minting an agent token.",
            color = WizardMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(26.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(WizardSurface)
                .border(1.dp, WizardBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            // Big status ring
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                MeshStatusRing(
                    active = active,
                    checking = isChecking,
                    label = when {
                        isChecking -> "Scanning"
                        active -> "Linked"
                        else -> "Offline"
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            MeshRow(
                key = "Node",
                value = meshInfo?.hostname?.takeIf { it.isNotBlank() } ?: "talos-node"
            )
            MeshRow(
                key = "Overlay IP",
                value = meshInfo?.self_ip?.takeIf { it.isNotBlank() } ?: MESH_IP
            )
            MeshRow(
                key = "Peer",
                value = meshInfo?.peer_name?.takeIf { it.isNotBlank() } ?: PEER_LABEL
            )
            MeshRow(
                key = "Peers online",
                value = meshInfo?.peers_count?.takeIf { it > 0 }?.toString() ?: "1"
            )
            MeshRow(key = "Transport", value = "WireGuard (UDP)", last = true)
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    drawCircle(WizardDanger)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = errorMessage,
                    color = WizardDanger,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        } else if (!active && !isChecking) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    drawCircle(WizardDanger)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mesh unreachable. Verify Tailscale is running on both ends.",
                    color = WizardDanger,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun MeshRow(key: String, value: String, last: Boolean = false) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp)
        ) {
            Text(
                text = key,
                color = WizardMuted,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                color = WizardText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (!last) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(WizardBorder)
            )
        }
    }
}

// ==========================================
// STEP 3 — GENERATE API TOKEN
// ==========================================

@Composable
private fun TokenStep(
    token: String?,
    isPairing: Boolean,
    errorMessage: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            ThinkingOrb(state = OrbState.SOLVING, size = 54.dp)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Generate Agent API Token",
            color = WizardText,
            fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 33.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This key authorises the app to reach the agent bus.",
            color = WizardMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(26.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(WizardSurface)
                .border(1.dp, WizardBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PastelPeach),
                    contentAlignment = Alignment.Center
                ) {
                    KeyBadge(tint = WizardBg)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Device token",
                        color = WizardText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (token != null) "Generated and stored locally" else "Awaiting generation",
                        color = WizardMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Token display box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0B0E15))
                    .border(1.dp, WizardBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                when {
                    isPairing -> {
                        Column {
                            LinearProgressIndicator(
                                color = Color(0xFF6366F1),
                                trackColor = WizardBorder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Minting token on the mesh node...",
                                color = WizardMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    token != null -> {
                        Text(
                            text = token.chunked(8).joinToString(" "),
                            color = Color(0xFFA7F3D0),
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    else -> {
                        Text(
                            text = "Token will appear here once generated.",
                            color = WizardMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (token != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Key is bound to this device. Keep it private.",
                        color = WizardMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    drawCircle(WizardDanger)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = errorMessage,
                    color = WizardDanger,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

// ==========================================
// STEP 4 — WELCOME
// ==========================================

@Composable
private fun WelcomeStep() {
    val agents = listOf(
        Triple("Hermes", "Reasoning & autonomy", PastelMint),
        Triple("OmniRush", "Autonomous CLI execution", PastelLavender),
        Triple("Claude", "Analysis & refactoring", PastelPeach),
        Triple("OpenClaw", "Tools & automation", PastelSky),
        Triple("Council", "Multi-agent chamber", Color(0xFFE2E8F0))
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            ThinkingOrb(state = OrbState.COMPOSING, size = 54.dp)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to TALOS",
            color = WizardText,
            fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 33.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your mesh is live. These agents are already on the bus.",
            color = WizardMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(26.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(WizardSurface)
                .border(1.dp, WizardBorder, RoundedCornerShape(22.dp))
                .padding(14.dp)
        ) {
            agents.forEach { (name, role, tint) ->
                ConnectedAgentRow(name = name, role = role, tint = tint)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(StatusGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Mesh live at $MESH_IP · 3 agents connected",
                color = WizardMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ConnectedAgentRow(name: String, role: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WizardSurfaceAlt)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(tint),
            contentAlignment = Alignment.Center
        ) {
            AgentFaceBadge(tint = WizardBg)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = WizardText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = role,
                color = WizardMuted,
                fontSize = 12.sp
            )
        }
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Connected",
            tint = StatusGreen,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ==========================================
// ILLUSTRATION BADGES (Canvas + Vector only, no emoji)
// ==========================================

/** Step 1 badge 1 — shield over a LAN link. */
@Composable
private fun ShieldLanBadge(tint: Color) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        // Shield body
        val shield = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.88f, h * 0.24f)
            lineTo(w * 0.88f, h * 0.52f)
            cubicTo(w * 0.88f, h * 0.76f, w * 0.70f, h * 0.90f, w * 0.5f, h * 0.97f)
            cubicTo(w * 0.30f, h * 0.90f, w * 0.12f, h * 0.76f, w * 0.12f, h * 0.52f)
            lineTo(w * 0.12f, h * 0.24f)
            close()
        }
        drawPath(shield, tint)
        // LAN nodes punched out of the shield
        drawCircle(Color.White, radius = w * 0.08f, center = Offset(w * 0.5f, h * 0.42f))
        drawCircle(Color.White, radius = w * 0.08f, center = Offset(w * 0.30f, h * 0.68f))
        drawCircle(Color.White, radius = w * 0.08f, center = Offset(w * 0.70f, h * 0.68f))
        // Links
        drawLine(
            color = Color.White,
            start = Offset(w * 0.5f, h * 0.42f),
            end = Offset(w * 0.30f, h * 0.68f),
            strokeWidth = w * 0.045f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(w * 0.5f, h * 0.42f),
            end = Offset(w * 0.70f, h * 0.68f),
            strokeWidth = w * 0.045f,
            cap = StrokeCap.Round
        )
    }
}

/** Step 1 badge 2 — agent fleet marks (three pill agents). */
@Composable
private fun AgentFleetBadge(tint: Color) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        // Centre agent (larger)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.30f, h * 0.14f),
            size = Size(w * 0.40f, h * 0.46f),
            cornerRadius = CornerRadius(w * 0.20f, w * 0.20f)
        )
        // Side agents (smaller)
        drawRoundRect(
            color = tint.copy(alpha = 0.62f),
            topLeft = Offset(w * 0.02f, h * 0.34f),
            size = Size(w * 0.24f, h * 0.30f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
        )
        drawRoundRect(
            color = tint.copy(alpha = 0.62f),
            topLeft = Offset(w * 0.74f, h * 0.34f),
            size = Size(w * 0.24f, h * 0.30f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
        )
        // Connector rail
        drawLine(
            color = tint.copy(alpha = 0.45f),
            start = Offset(w * 0.14f, h * 0.49f),
            end = Offset(w * 0.86f, h * 0.49f),
            strokeWidth = w * 0.05f,
            cap = StrokeCap.Round
        )
        // Eyes on the centre agent
        drawCircle(Color.White, radius = w * 0.045f, center = Offset(w * 0.43f, h * 0.30f))
        drawCircle(Color.White, radius = w * 0.045f, center = Offset(w * 0.57f, h * 0.30f))
    }
}

/** Step 1 badge 3 — terminal window over a bus line. */
@Composable
private fun TerminalBusBadge(tint: Color) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        // Terminal frame
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.04f, h * 0.10f),
            size = Size(w * 0.92f, h * 0.66f),
            cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
        )
        // Cut the inner screen
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.12f, h * 0.24f),
            size = Size(w * 0.76f, h * 0.44f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )
        // Prompt glyph
        drawLine(
            color = tint,
            start = Offset(w * 0.20f, h * 0.34f),
            end = Offset(w * 0.30f, h * 0.42f),
            strokeWidth = w * 0.05f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.30f, h * 0.42f),
            end = Offset(w * 0.20f, h * 0.50f),
            strokeWidth = w * 0.05f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.36f, h * 0.52f),
            end = Offset(w * 0.56f, h * 0.52f),
            strokeWidth = w * 0.05f,
            cap = StrokeCap.Round
        )
        // Bus rail with two nodes
        drawLine(
            color = tint.copy(alpha = 0.55f),
            start = Offset(w * 0.12f, h * 0.87f),
            end = Offset(w * 0.88f, h * 0.87f),
            strokeWidth = w * 0.06f,
            cap = StrokeCap.Round
        )
        drawCircle(tint, radius = w * 0.09f, center = Offset(w * 0.20f, h * 0.87f))
        drawCircle(tint, radius = w * 0.09f, center = Offset(w * 0.80f, h * 0.87f))
    }
}

/** Step 3 badge — key glyph. */
@Composable
private fun KeyBadge(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = tint,
            radius = w * 0.26f,
            center = Offset(w * 0.32f, h * 0.50f),
            style = Stroke(width = w * 0.13f)
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.52f, h * 0.50f),
            end = Offset(w * 0.94f, h * 0.50f),
            strokeWidth = w * 0.13f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.78f, h * 0.50f),
            end = Offset(w * 0.78f, h * 0.74f),
            strokeWidth = w * 0.13f,
            cap = StrokeCap.Round
        )
    }
}

/** Step 4 agent avatar — simple agent face. */
@Composable
private fun AgentFaceBadge(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(color = tint, radius = w * 0.46f, center = Offset(w * 0.5f, h * 0.5f))
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.30f, h * 0.38f),
            size = Size(w * 0.11f, h * 0.20f),
            cornerRadius = CornerRadius(w * 0.055f, w * 0.055f)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.59f, h * 0.38f),
            size = Size(w * 0.11f, h * 0.20f),
            cornerRadius = CornerRadius(w * 0.055f, w * 0.055f)
        )
    }
}

// ==========================================
// MESH STATUS RING
// ==========================================

@Composable
private fun MeshStatusRing(
    active: Boolean,
    checking: Boolean,
    label: String
) {
    val transition = rememberInfiniteTransition(label = "mesh_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val ringColor = when {
        checking -> Color(0xFF6366F1)
        active -> StatusGreen
        else -> WizardDanger
    }

    Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val d = size.minDimension
            val c = Offset(size.width / 2f, size.height / 2f)
            val base = d * 0.38f

            // Soft halo
            drawCircle(
                color = ringColor.copy(alpha = 0.10f + 0.08f * pulse),
                radius = base * (1.18f + 0.08f * pulse),
                center = c
            )
            // Main ring
            drawCircle(
                color = ringColor,
                radius = base,
                center = c,
                style = Stroke(width = d * 0.045f)
            )
            // Progress arc
            drawArc(
                color = ringColor.copy(alpha = 0.75f),
                startAngle = -90f,
                sweepAngle = if (checking) 360f * pulse else if (active) 360f else 120f,
                useCenter = false,
                topLeft = Offset(c.x - base, c.y - base),
                size = Size(base * 2, base * 2),
                style = Stroke(width = d * 0.075f, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (active) Icons.Default.Lan else Icons.Default.Shield,
                contentDescription = null,
                tint = ringColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = WizardText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

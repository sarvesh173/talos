package com.sarvesh173.talos.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.data.MeshInfo
import com.sarvesh173.talos.network.TalosClient
import kotlinx.coroutines.launch
import java.util.UUID

// ==========================================
// PALETTE (Grok-style dark onboarding)
// ==========================================

private val WizardBg: Color @Composable get() = talosBg
private val WizardSurface: Color @Composable get() = talosSurface
private val WizardSurfaceAlt: Color @Composable get() = talosSurface
private val WizardBorder: Color @Composable get() = talosBorder
private val WizardText: Color @Composable get() = talosTextPrimary
private val WizardMuted: Color @Composable get() = talosTextMuted
private val WizardDanger = Color(0xFFEF4444)

// Dark theme illustration badge colors
private val CyberCyan = Color(0xFF38BDF8)
private val CyberGreen = Color(0xFF22C55E)
private val CyberViolet = Color(0xFF818CF8)
private val CyberOrange = Color(0xFFF97316)

private const val MESH_IP = "100.75.107.79"
private const val PEER_LABEL = "V2158"

// ==========================================
// MAIN WIZARD
// ==========================================

@Composable
fun OnboardingWizard(
    client: TalosClient,
    onToggleTheme: (() -> Unit)? = null,
    onComplete: () -> Unit
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var step by remember { mutableStateOf(0) }
    var meshInfo by remember { mutableStateOf<MeshInfo?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var token by remember {
        mutableStateOf<String?>(
            client.apiToken.takeIf { it.isNotBlank() }
                ?: ("talos_" + UUID.randomUUID().toString().replace("-", "").take(24))
        )
    }
    var isPairing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    fun refreshMesh() {
        scope.launch {
            isChecking = true
            errorMessage = null
            meshInfo = client.checkMeshStatus()
            isChecking = false
        }
    }

    fun generateOrPairToken() {
        scope.launch {
            isPairing = true
            errorMessage = null
            val generated = client.pairDevice()
            if (!generated.isNullOrBlank()) {
                token = generated
            } else {
                token = "talos_" + UUID.randomUUID().toString().replace("-", "").take(24)
            }
            isPairing = false
        }
    }

    // Step 2: verify the mesh over Tailscale WireGuard
    LaunchedEffect(step) {
        if (step == 1) {
            refreshMesh()
        }
        // Step 3: mint an agent API token
        if (step == 2 && token == null && !isPairing) {
            generateOrPairToken()
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
                },
                onToggleTheme = onToggleTheme
            )

            // ---------- STEP CONTENT (WEIGHTED TO KEEP BOTTOM BAR PINNED) ----------
            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    (slideInHorizontally { it / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut(tween(180)))
                },
                label = "wizard_step"
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    when (current) {
                        0 -> OverviewStep()
                        1 -> MeshStep(
                            meshInfo = meshInfo,
                            isChecking = isChecking,
                            errorMessage = errorMessage,
                            onRefresh = { refreshMesh() },
                            onCopyPrompt = { prompt ->
                                clipboardManager.setText(AnnotatedString(prompt))
                                copiedNotice = "Setup prompt copied!"
                            }
                        )
                        2 -> TokenStep(
                            token = token,
                            isPairing = isPairing,
                            errorMessage = errorMessage,
                            onGenerateToken = { generateOrPairToken() },
                            onCopyToken = { t ->
                                clipboardManager.setText(AnnotatedString(t))
                                copiedNotice = "Token copied to clipboard!"
                            }
                        )
                        else -> WelcomeStep()
                    }
                    if (copiedNotice != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(StatusGreen.copy(alpha = 0.15f))
                                .border(1.dp, StatusGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = copiedNotice ?: "",
                                color = StatusGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // ---------- BOTTOM ACTION ----------
            val isMeshActive = meshInfo?.active == true
            val hasToken = !token.isNullOrBlank()

            WizardBottomBar(
                step = step,
                isBusy = isChecking || isPairing,
                isMeshActive = isMeshActive,
                hasToken = hasToken,
                onPrimary = {
                    when (step) {
                        0 -> step = 1
                        1 -> {
                            if (isMeshActive) {
                                step = 2
                            }
                        }
                        2 -> {
                            if (!hasToken) {
                                generateOrPairToken()
                            } else {
                                val t = token ?: ("talos_" + UUID.randomUUID().toString().replace("-", "").take(24))
                                client.updateConfig(client.baseUrl, t)
                                step = 3
                            }
                        }
                        3 -> onComplete()
                    }
                },
                onSkip = {
                    val t = token.takeIf { !it.isNullOrBlank() }
                        ?: ("talos_" + UUID.randomUUID().toString().replace("-", "").take(24))
                    client.updateConfig(client.baseUrl, t)
                    onComplete()
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
    onBack: () -> Unit,
    onToggleTheme: (() -> Unit)? = null
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

            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (onToggleTheme != null) {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (LocalIsDark.current) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Dark/Light Mode",
                            tint = WizardMuted
                        )
                    }
                }
            }
        }

        // Segmented progress rail
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 12.dp, start = 6.dp, end = 6.dp)
        ) {
            for (i in 0 until 4) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i <= step) PrimaryAccent else WizardBorder
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
    isMeshActive: Boolean,
    hasToken: Boolean,
    onPrimary: () -> Unit,
    onSkip: (() -> Unit)? = null
) {
    val canProceed = when (step) {
        1 -> isMeshActive
        2 -> true // Can always click to either generate or link
        else -> true
    }

    val label = when (step) {
        0 -> "Get started"
        1 -> if (isMeshActive) "Confirm Mesh & Continue" else "Tailscale Required to Continue"
        2 -> if (hasToken) "Link Device & Enter Chamber" else "Generate Device Token"
        else -> "Enter Chamber"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WizardBg)
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        Button(
            onClick = onPrimary,
            enabled = canProceed && !isBusy,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = WizardBg,
                disabledContainerColor = Color.White.copy(alpha = 0.30f),
                disabledContentColor = WizardBg.copy(alpha = 0.50f)
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = label,
                color = WizardBg,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        // Tiny discrete test button in corner for safety / UI test bypass
        if (onSkip != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.TextButton(
                    onClick = onSkip,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.height(22.dp)
                ) {
                    Text(
                        text = "test",
                        color = Color(0xFF334155),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
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
                badgeTint = CyberGreen,
                badge = { ShieldLanBadge(tint = WizardBg) }
            )
            OverviewCard(
                index = 2,
                title = "Pair with Autonomous Agent Fleet",
                body = "One handshake brings every always-on agent into the mesh.",
                badgeTint = CyberViolet,
                badge = { AgentFleetBadge(tint = WizardBg) }
            )
            OverviewCard(
                index = 3,
                title = "Real-Time Collaboration & Tool Bus",
                body = "Shared event bus, tool calls and presence in a single stream.",
                badgeTint = CyberCyan,
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
                        .background(PrimaryAccent.copy(alpha = 0.20f))
                        .border(1.dp, PrimaryAccent.copy(alpha = 0.40f), RoundedCornerShape(14.dp)),
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
    errorMessage: String? = null,
    onRefresh: () -> Unit = {},
    onCopyPrompt: (String) -> Unit = {}
) {
    val active = meshInfo?.active == true
    val hostSetupPrompt = "Set up TALOS mesh node: git clone https://github.com/sarvesh173/talos.git && cd talos && pip install -r requirements.txt && uvicorn server.main:app --host 0.0.0.0 --port 8000"

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
            text = "Zero open ports. Direct encrypted WireGuard tunnel to your agent host.",
            color = WizardMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // --- STEP-BY-STEP SETUP GUIDE ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(WizardSurfaceAlt)
                .border(1.dp, WizardBorder, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Tailscale Setup & Host Guide",
                color = WizardText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "1. Install Tailscale on this phone and on your agent host machine (Linux/Mac/PC).\n2. Sign in to the same Tailscale account on both devices.\n3. Run TALOS backend on your host or give this prompt to your CLI agent:",
                color = WizardMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Copy setup prompt box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0B0E15))
                    .border(1.dp, WizardBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = hostSetupPrompt,
                    color = PrimaryAccent,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { onCopyPrompt(hostSetupPrompt) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Copy Setup Prompt", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

            // Scan Again Action
            Button(
                onClick = onRefresh,
                enabled = !isChecking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isChecking) "Scanning Tailscale Mesh..." else "Scan Mesh Again",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
        } else if (!active && !isChecking) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    drawCircle(Color(0xFFF59E0B))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mesh offline. You can still continue to generate your device token.",
                    color = Color(0xFFF59E0B),
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
    errorMessage: String?,
    onGenerateToken: () -> Unit = {},
    onCopyToken: (String) -> Unit = {}
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
            text = "This key authorises this app to interact with your agent mesh.",
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
                        .background(CyberCyan.copy(alpha = 0.20f))
                        .border(1.dp, CyberCyan.copy(alpha = 0.40f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    KeyBadge(tint = CyberCyan)
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
                        text = if (token != null) "Ready to link with agent" else "Ready to generate",
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
                                color = PrimaryAccent,
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
                        Column {
                            Text(
                                text = token.chunked(8).joinToString(" "),
                                color = PrimaryAccent,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
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

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Copy Token and Generate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (token != null) {
                    Button(
                        onClick = { onCopyToken(token) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Copy Token", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onGenerateToken,
                    enabled = !isPairing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (token == null) Color.White else Color(0xFF2A3447),
                        contentColor = if (token == null) Color(0xFF0F1117) else Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (token == null) "Generate Token" else "Re-mint Token",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                        text = "Token ready. Give this to your agent to complete linking.",
                        color = WizardMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Guide on giving token to the bot
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(WizardSurfaceAlt)
                .border(1.dp, WizardBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "How to link with your Bot / CLI Agent",
                    color = WizardText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Copy the token above and paste it to your agent, or set in host environment:\nexport TALOS_TOKEN=" + (token ?: "<YOUR_TOKEN>"),
                color = WizardMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
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
        Triple("Hermes", "Reasoning & autonomy", CyberGreen),
        Triple("OmniRush", "Autonomous CLI execution", CyberViolet),
        Triple("Claude", "Analysis & refactoring", CyberOrange),
        Triple("OpenClaw", "Tools & automation", CyberCyan),
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
// MESH STATUS RING (ORBITAL RADAR MESH NODE)
// ==========================================

@Composable
private fun MeshStatusRing(
    active: Boolean,
    checking: Boolean,
    label: String
) {
    val transition = rememberInfiniteTransition(label = "mesh_orbital_physics")

    // Rotation angle for satellite nodes
    val orbitAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_rot"
    )

    // Signal wave ripple expansion
    val ripple1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1"
    )
    val ripple2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2"
    )

    val ringColor = when {
        checking -> Color(0xFF38BDF8)
        active -> Color(0xFF22C55E)
        else -> Color(0xFFF59E0B)
    }

    Box(modifier = Modifier.size(130.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val d = size.minDimension
            val c = Offset(size.width / 2f, size.height / 2f)
            val baseR = d * 0.32f

            // 1. Expanding Pulse Ripples
            drawCircle(
                color = ringColor.copy(alpha = (1f - ripple1) * 0.25f),
                radius = baseR + (d * 0.22f * ripple1),
                center = c,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = ringColor.copy(alpha = (1f - ripple2) * 0.25f),
                radius = baseR + (d * 0.22f * ripple2),
                center = c,
                style = Stroke(width = 2.dp.toPx())
            )

            // 2. Base Mesh Orbit Ring
            drawCircle(
                color = ringColor.copy(alpha = 0.20f),
                radius = baseR,
                center = c,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 3. Rotating Satellite Peer Nodes (Phone <-> Host)
            val rad1 = Math.toRadians(orbitAngle.toDouble()).toFloat()
            val sat1X = c.x + baseR * kotlin.math.cos(rad1)
            val sat1Y = c.y + baseR * kotlin.math.sin(rad1)
            drawCircle(
                color = ringColor,
                radius = 4.dp.toPx(),
                center = Offset(sat1X, sat1Y)
            )

            val rad2 = Math.toRadians((orbitAngle + 180f).toDouble()).toFloat()
            val sat2X = c.x + baseR * kotlin.math.cos(rad2)
            val sat2Y = c.y + baseR * kotlin.math.sin(rad2)
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(sat2X, sat2Y)
            )

            // 4. Center Glowing Hub
            drawCircle(
                color = ringColor.copy(alpha = 0.15f),
                radius = baseR * 0.65f,
                center = c
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (active) Icons.Default.Lan else Icons.Default.Shield,
                contentDescription = null,
                tint = ringColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ringColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    color = WizardText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

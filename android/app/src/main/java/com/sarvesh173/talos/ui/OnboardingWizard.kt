@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sarvesh173.talos.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.R
import com.sarvesh173.talos.data.MeshInfo
import com.sarvesh173.talos.network.MeshDiscovery
import com.sarvesh173.talos.network.TalosClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

// ==========================================
// PALETTE (Grok-style dark onboarding)
// ==========================================

private val WizardBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val WizardSurface: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
private val WizardSurfaceAlt: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
private val WizardBorder: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val WizardText: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val WizardMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val WizardDanger: Color @Composable get() = MaterialTheme.colorScheme.error

// Illustration badge accents - scheme-driven so both modes stay consistent.
private val CyberGreen: Color @Composable get() = MaterialTheme.colorScheme.tertiary
private val CyberViolet: Color @Composable get() = MaterialTheme.colorScheme.primary
private val CyberOrange: Color @Composable get() = MaterialTheme.colorScheme.tertiary


// ==========================================
// MAIN WIZARD
// ==========================================

@Composable
fun OnboardingWizard(
    client: TalosClient,
    onToggleTheme: (() -> Unit)? = null,
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
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

    fun refreshMesh(autoPairKey: Boolean = false) {
        scope.launch {
            isChecking = true
            errorMessage = null

            // Zero-config and bounded: resolveCore() tries the endpoint we
            // already know, then the same host on the standard TALOS ports,
            // then the LAN discovery beacon - and it always returns within a
            // hard timeout, so the wizard can never sit in "Scanning" forever.
            val status = client.resolveCore()
            meshInfo = status

            if (status != null && autoPairKey && client.apiToken.isBlank()) {
                client.pairDevice()
            }

            if (status == null) {
                errorMessage = "No node answered. Make sure Tailscale is on and the " +
                    "talos-core is running on your host, then scan again."
            }
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

    // Step 2: verify the mesh over Tailscale WireGuard (auto-discovery)
    LaunchedEffect(step) {
        if (step == 1) {
            refreshMesh(autoPairKey = true)
        }
        // Step 3: mint an agent API token
        if (step == 2 && token == null && !isPairing) {
            generateOrPairToken()
        }
    }

    // Split-screen: the window can be very short (landscape 1/2 split) or
    // very narrow (1/3 split). Measure the window - not the screen - and
    // compact the chrome so the content always stays usable.
    val windowSize = rememberWindowSize()
    val compact = windowSize.isShort || windowSize.isNarrow

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WizardBg)
    ) {
        // Ambient cosmic stardust embers: continuous aesthetic matching from LandingScreen
        val infiniteDust = rememberInfiniteTransition(label = "wizard_dust")
        val dustPhase by infiniteDust.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(14000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "dust_phase"
        )

        // High-performance lightweight stardust embers (zero GPU overhead during step transitions)
        val particlePrimary = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (i in 0 until 8) {
                val seedX = ((i * 37 + 13) % 100) / 100f
                val seedY = ((i * 59 + 29) % 100) / 100f
                val speed = 0.35f + ((i * 17) % 30) / 100f
                val currentY = (seedY - dustPhase * speed) % 1f
                val y = if (currentY < 0f) (currentY + 1f) * h else currentY * h
                val x = seedX * w

                drawCircle(
                    color = if (i % 2 == 0) PrimaryAccent.copy(alpha = 0.14f) else particlePrimary.copy(alpha = 0.12f),
                    radius = 1.5.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }

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

            // ---------- STEP CONTENT WITH M3 EXPRESSIVE VERTICAL MOTION (↑) ----------
            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    // M3E physics: spatial springs for position/scale (fast,
                    // expressive, INTERRUPTIBLE - a mid-transition press never
                    // queues or reverses weirdly), effects springs for alpha.
                    // The old fixed-duration tweens made rapid taps feel like
                    // a slide with a stutter - the reported "slide bug".
                    val forward = targetState > initialState
                    val dir = if (forward) 1 else -1
                    val moveIn = M3EMotion.fastSpatial<IntOffset>()
                    val scaleInSpec = M3EMotion.fastSpatial<Float>()
                    val fadeSpec = M3EMotion.defaultEffects<Float>()

                    (
                        slideInVertically(
                            animationSpec = moveIn,
                            initialOffsetY = { height -> (height / 6) * dir }
                        ) + scaleIn(
                            animationSpec = scaleInSpec,
                            initialScale = 0.96f
                        ) + fadeIn(animationSpec = fadeSpec)
                    ).togetherWith(
                        fadeOut(animationSpec = M3EMotion.fastEffects())
                    )
                },
                label = "wizard_step"
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = if (compact) 12.dp else 20.dp,
                            vertical = if (compact) 8.dp else 12.dp
                        )
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
                        else -> WelcomeStep(onEnterChamber = onComplete)
                    }
                    AnimatedVisibility(
                        visible = copiedNotice != null,
                        enter = expandVertically(animationSpec = tween(260)) + fadeIn(animationSpec = tween(260)),
                        exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
                    ) {
                        Column {
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
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // ---------- BOTTOM ACTION ----------
            val isMeshActive = meshInfo?.active == true
            val hasToken = !token.isNullOrBlank()

            WizardBottomBar(
                step = step,
                compactHeight = windowSize.isShort,
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
    val isDark = LocalIsDark.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (canGoBack) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Navigate to previous step" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
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
                letterSpacing = 0.6.sp,
                modifier = Modifier.semantics {
                    contentDescription = "Onboarding progress: step ${step + 1} of 4"
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (onToggleTheme != null) {
                    val themeRot by animateFloatAsState(
                        targetValue = if (isDark) 0f else 180f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
                        label = "wiz_theme_rot"
                    )
                    val themeScale by animateFloatAsState(
                        targetValue = if (isDark) 1.0f else 1.15f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
                        label = "wiz_theme_scale"
                    )
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.semantics {
                            contentDescription = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode"
                        }
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFFFFD54F) else Color(0xFF6750A4),
                            modifier = Modifier.graphicsLayer {
                                rotationZ = themeRot
                                scaleX = themeScale
                                scaleY = themeScale
                            }
                        )
                    }
                }
            }
        }

        // Dynamic Segmented Progress Rail with M3 Morphing Weight
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 12.dp, start = 6.dp, end = 6.dp)
        ) {
            for (i in 0 until 4) {
                val isCurrent = i == step
                val isPassed = i < step
                val animatedWeight by animateFloatAsState(
                    targetValue = if (isCurrent) 1.45f else 0.85f,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 450f),
                    label = "rail_weight_$i"
                )
                val animatedColor by animateColorAsState(
                    targetValue = when {
                        isCurrent -> PrimaryAccent
                        isPassed -> PrimaryAccent.copy(alpha = 0.70f)
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                    animationSpec = tween(320, easing = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.00f)),
                    label = "rail_color_$i"
                )
                Box(
                    modifier = Modifier
                        .weight(animatedWeight)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(animatedColor)
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
    compactHeight: Boolean = false,
) {
    val canProceed = when (step) {
        1 -> isMeshActive
        2 -> true // Can always click to either generate or link
        else -> true
    }

    val label = when (step) {
        0 -> "Get started"
        1 -> if (isMeshActive) "Confirm Mesh & Continue" else "Scan Again"
        2 -> if (hasToken) "Link Device & Enter Chamber" else "Generate Device Token"
        else -> "Enter Chamber"
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "wizard_btn_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WizardBg)
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        // M3E CTA: scheme color roles, spring press physics, circle->scallop
        // morph on press, canonical LoadingIndicator while busy.
        M3EButton(
            text = label,
            onClick = onPrimary,
            enabled = canProceed,
            loading = isBusy,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compactHeight) 44.dp else 52.dp)
        )

        // No dev-only bypass in the shipping UI: every path goes through a
        // real, verified node.
    }
}

// ==========================================
// STEP 1 — OVERVIEW (M3)
// ==========================================

@Composable
private fun OverviewStep() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))

        // M3 Surface Header with authentic TALOS mesh vector emblem
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = WizardSurface,
                border = BorderStroke(1.dp, talosBorder),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_core),
                        contentDescription = "TALOS Mesh Core",
                        modifier = Modifier.size(32.dp),
                        colorFilter = ColorFilter.tint(PrimaryAccent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Initialize Your TALOS Mesh",
            color = WizardText,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "A single plan for the entire agent suite",
            color = WizardMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 3 elevated cards with 1px talosBorder
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OverviewElevatedCard(
                index = 1,
                title = "Connect over Tailscale WireGuard",
                body = "Private overlay network. No public ports, no exposed surface.",
                badgeTint = CyberGreen,
                badge = {
                    Image(
                        painter = painterResource(R.drawable.ic_step_mesh),
                        contentDescription = "Tailscale Mesh",
                        modifier = Modifier.size(28.dp),
                        colorFilter = ColorFilter.tint(CyberGreen)
                    )
                }
            )
            OverviewElevatedCard(
                index = 2,
                title = "Pair with Autonomous Agent Fleet",
                body = "One handshake brings every always-on agent into the mesh.",
                badgeTint = CyberViolet,
                badge = {
                    Image(
                        painter = painterResource(R.drawable.ic_step_fleet),
                        contentDescription = "Agent Fleet",
                        modifier = Modifier.size(28.dp),
                        colorFilter = ColorFilter.tint(CyberViolet)
                    )
                }
            )
            OverviewElevatedCard(
                index = 3,
                title = "Real-Time Collaboration & Tool Bus",
                body = "Shared event bus, tool calls and presence in a single stream.",
                badgeTint = PrimaryAccent,
                badge = {
                    Image(
                        painter = painterResource(R.drawable.ic_step_token),
                        contentDescription = "Cryptographic Key",
                        modifier = Modifier.size(28.dp),
                        colorFilter = ColorFilter.tint(PrimaryAccent)
                    )
                }
            )
        }
    }
}

@Composable
private fun OverviewElevatedCard(
    index: Int,
    title: String,
    body: String,
    badgeTint: Color,
    badge: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "card_scale"
    )

    val isDark = LocalIsDark.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clickable(interactionSource = interactionSource, indication = null) { },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = WizardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // Zero shadow pass = 60fps locked
        border = BorderStroke(1.2.dp, metallicBorderBrush())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(PrimaryAccent.copy(alpha = 0.20f))
                            .border(1.dp, PrimaryAccent.copy(alpha = 0.40f), CircleShape),
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

            val isDark = LocalIsDark.current
            // High-contrast Machined Socket (Carbon & M3 Expressive)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.dp, badgeTint.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                badge()
            }
        }
    }
}

// ==========================================
// STEP 2 — TAILSCALE MESH VERIFICATION (M3)
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
    // Agent-facing prompt: dependency preflight first, Tailscale before the
    // core, never overwrite an existing install, no hardcoded addresses.
    val hostSetupPrompt = """Set up the TALOS node (agent mode).

1. Preflight - check before installing, never overwrite a working install:
   for dep in git cargo rustc python3 pip sqlite3 curl; do command -v ${'$'}dep >/dev/null 2>&1 || echo "MISSING: ${'$'}dep"; done
2. Tailscale (required):
   - if command -v tailscale fails -> install Tailscale, then ask the user to log in ONCE on the host and on their phone with the same account
   - verify: tailscale status --json  (BackendState must be Running)
3. Build and run the Rust core:
   cd talos/crates/talos-core && cargo build --release
   PORT=8010 ./target/release/talos-core
   (it answers the app's zero-config discovery beacon automatically)
4. Verify: curl -s http://127.0.0.1:8010/api/mesh/status   -> tailscale_enabled: true
5. Never hardcode an IP, hostname or token in code; discover everything at runtime."""

    val statusLabel = when {
        isChecking -> "Scanning"
        active -> "Linked"
        else -> "Offline"
    }
    val statusColor = when {
        isChecking -> MaterialTheme.colorScheme.primary
        active -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    val infinitePulse = rememberInfiniteTransition(label = "mesh_pulse")
    val beaconScale by infinitePulse.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_scale"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))

        // M3 Expressive Machined Header Socket for Mesh with Radar Ping Wave
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val infinitePing = rememberInfiniteTransition(label = "mesh_ping_wave")
            val pingProgress by infinitePing.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ping_progress"
            )

            if (active) {
                Box(
                    modifier = Modifier
                        .size((64 + (pingProgress * 36)).dp)
                        .clip(CircleShape)
                        .border(
                            1.5.dp,
                            statusColor.copy(alpha = (1f - pingProgress) * 0.65f),
                            CircleShape
                        )
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = WizardSurface,
                border = BorderStroke(1.dp, WizardBorder),
                modifier = Modifier
                    .size(64.dp)
                    .scale(if (active) beaconScale else 1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_step_mesh),
                        contentDescription = "Tailscale Mesh Verification",
                        modifier = Modifier.size(32.dp),
                        colorFilter = ColorFilter.tint(statusColor)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

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

        // --- TAILSCALE SETUP & HOST GUIDE (M3 METALLIC CARD) ---
        val isDark = LocalIsDark.current
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = WizardSurfaceAlt),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.2.dp, metallicBorderBrush())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        .background(talosBg)
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
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- MESH STATUS (M3 METALLIC CARD + LINEAR PROGRESS + STATUS CHIP) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = WizardSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.2.dp, metallicBorderBrush())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // M3E: morphing-shape LoadingIndicator while scanning
                // (canonical wait cue), wavy progress only when linked.
                if (isChecking) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoadingIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Scanning private mesh...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LinearWavyProgressIndicator(
                        progress = { if (active) 1f else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = statusColor,
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // M3E success state: state-change entrance (spring scale-in),
                    // icon + label in one filled container - not a bare dot chip.
                    val successEntrance = remember { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(active) {
                        if (active) {
                            successEntrance.snapTo(0f)
                            successEntrance.animateTo(
                                targetValue = 1f,
                                animationSpec = M3EMotion.fastSpatial()
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                        modifier = Modifier.graphicsLayer {
                            scaleX = 0.92f + 0.08f * successEntrance.value
                            scaleY = 0.92f + 0.08f * successEntrance.value
                            alpha = successEntrance.value
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (active) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusLabel,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Privacy: the host's name, overlay IP and peer identities are
                // private information and are never rendered in the app.
                MeshRow(
                    key = "Node",
                    value = if (active) "TALOS node" else "Not connected"
                )
                MeshRow(
                    key = "Peers online",
                    value = if (active) {
                        meshInfo?.peers_count?.takeIf { it > 0 }?.toString() ?: "1"
                    } else {
                        "-"
                    }
                )
                MeshRow(
                    key = "Transport",
                    value = if (active) "Encrypted overlay" else "Offline",
                    last = true
                )

                // No manual re-scan button: the scan runs once on entry and
                // the resolver auto-retries. The primary CTA doubles as
                // "Scan Again" when the mesh is not linked - a second button
                // would just replay the scanning indicator for no reason.
                if (isChecking) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Scanning resolves the node once - no manual rescans needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            val danger = WizardDanger
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    drawCircle(danger)
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
            val errorColor = MaterialTheme.colorScheme.error
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    drawCircle(errorColor)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mesh offline. You can still continue to generate your device token.",
                    color = MaterialTheme.colorScheme.error,
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
// STEP 3 — GENERATE API TOKEN (M3)
// ==========================================

@Composable
private fun TokenStep(
    token: String?,
    isPairing: Boolean,
    errorMessage: String?,
    onGenerateToken: () -> Unit = {},
    onCopyToken: (String) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))

        // M3 Key Emblem Surface Header
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = WizardSurface,
                border = BorderStroke(1.dp, talosBorder),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_step_token),
                        contentDescription = "Cryptographic Device Key",
                        modifier = Modifier.size(32.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Generate Agent API Token",
            color = WizardText,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This key authorises this app to interact with your agent mesh.",
            color = WizardMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        val isDark = LocalIsDark.current
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = WizardSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.2.dp, metallicBorderBrush())
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PrimaryAccent.copy(alpha = 0.20f))
                            .border(1.dp, PrimaryAccent.copy(alpha = 0.40f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        KeyBadge(tint = PrimaryAccent)
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

                // Clean mono token surface
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, talosBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        when {
                            isPairing -> {
                                Column {
                                    LinearProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Minting token on the mesh node...",
                                        color = WizardMuted,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            token != null -> {
                                Text(
                                    text = token.chunked(8).joinToString(" "),
                                    color = PrimaryAccent,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            else -> {
                                Text(
                                    text = "Token will appear here once generated.",
                                    color = WizardMuted,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
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
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isCopied = true
                                onCopyToken(token)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCopied) StatusGreen.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (isCopied) StatusGreen else Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = if (isCopied) BorderStroke(1.dp, StatusGreen.copy(alpha = 0.50f)) else null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isCopied) "Copied!" else "Copy Token",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = onGenerateToken,
                        enabled = !isPairing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (token == null) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (token == null) MaterialTheme.colorScheme.onPrimary
                                           else MaterialTheme.colorScheme.onSurface
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
        }

        // Guide on giving token to the bot
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = WizardSurfaceAlt),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.2.dp, metallicBorderBrush())
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            val danger = WizardDanger
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    drawCircle(danger)
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
// STEP 4 — WELCOME (M3)
// ==========================================

@Composable
private fun WelcomeStep(onEnterChamber: () -> Unit = {}) {
    val isDark = LocalIsDark.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))

        // M3 Surface Header with Groups emblem
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = WizardSurface,
                border = BorderStroke(1.dp, talosBorder),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_step_chamber),
                        contentDescription = "Council Chamber Portal",
                        modifier = Modifier.size(34.dp),
                        colorFilter = ColorFilter.tint(PrimaryAccent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Welcome to TALOS",
            color = WizardText,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your mesh is live. All 10 registered agents are ready on the bus.",
            color = WizardMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Registered Agents Grid Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REGISTERED AGENTS",
                color = WizardMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StatusGreen.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(StatusGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${AgentEmblems.size} Online",
                        color = StatusGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Display all 10 registered agents using AgentEmblemIcon (tint=null) in a clean 2-column grid
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AgentEmblems.chunked(2).forEach { rowPair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowPair.forEach { emblem ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = WizardSurfaceAlt),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.2.dp, metallicBorderBrush()),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(emblem.accent.copy(alpha = 0.14f))
                                        .border(1.dp, emblem.accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AgentEmblemIcon(emblem = emblem, size = 22.dp, tint = null)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = emblem.label,
                                        color = WizardText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = emblem.role.substringBefore("·").trim(),
                                        color = WizardMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(StatusGreen)
                                )
                            }
                        }
                    }
                    if (rowPair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Chamber entry button
        Button(
            onClick = onEnterChamber,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryAccent,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Enter Agent Chamber",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(StatusGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Mesh linked · auto-detected via private overlay",
                color = WizardMuted,
                fontSize = 12.sp
            )
        }
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
        drawCircle(tint, radius = w * 0.80f, center = Offset(w * 0.80f, h * 0.87f))
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

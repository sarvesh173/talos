@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sarvesh173.talos.ui

import com.sarvesh173.talos.ui.onboarding.MeshStep
import com.sarvesh173.talos.ui.onboarding.OverviewStep
import com.sarvesh173.talos.ui.onboarding.TokenStep
import com.sarvesh173.talos.ui.onboarding.WelcomeStep

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.OutlinedTextField
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingWizard(
    client: TalosClient,
    onToggleTheme: (() -> Unit)? = null,
    onExitToLanding: () -> Unit = {},
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var step by remember { mutableStateOf(0) }
    var meshInfo by remember { mutableStateOf<MeshInfo?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var token by remember {
        mutableStateOf<String?>(client.apiToken.takeIf { it.isNotBlank() })
    }
    var isPairing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    fun refreshMesh() {
        scope.launch {
            isChecking = true
            errorMessage = null
            val status = client.checkMeshStatus(client.baseUrl)
            meshInfo = status

            if (status == null) {
                errorMessage = "No node answered at ${client.baseUrl}. Ensure Tailscale is active and talos-core is running."
            }
            isChecking = false
        }
    }

    LaunchedEffect(step) {
        if (step == 1 && client.baseUrl.isNotBlank()) {
            refreshMesh()
        }
    }

    BackHandler {
        if (step > 0) {
            step -= 1
        } else {
            onExitToLanding()
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
                    color = particlePrimary.copy(alpha = if (i % 2 == 0) 0.14f else 0.12f),
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
                .imePadding()
        ) {
            // ---------- TOP BAR: STEP X OF 4 + BACK NAV ----------
            WizardTopBar(
                step = step,
                canGoBack = true,
                onBack = {
                    when (step) {
                        2 -> {
                            token = null
                            errorMessage = null
                        }
                    }
                    if (step > 0) {
                        step -= 1
                    } else {
                        onExitToLanding()
                    }
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
                        .imeNestedScroll()
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
                            initialEndpoint = client.baseUrl,
                            initialToken = token ?: client.apiToken,
                            onUpdateEndpoint = { newUrl ->
                                client.updateConfig(newUrl, token ?: client.apiToken)
                            },
                            onTokenChanged = { newToken ->
                                token = newToken
                                client.updateConfig(client.baseUrl, newToken)
                            },
                            onRefresh = { refreshMesh() },
                            onCopyPrompt = { prompt ->
                                clipboardManager.setText(AnnotatedString(prompt))
                                copiedNotice = "Setup prompt copied"
                            }
                        )
                        2 -> TokenStep(
                            token = token,
                            isPairing = isPairing,
                            errorMessage = errorMessage,
                            onTokenChanged = { newToken ->
                                token = newToken
                            },
                            onGenerateToken = {},
                            onCopyToken = { t ->
                                clipboardManager.setText(AnnotatedString(t))
                                copiedNotice = "Token copied to clipboard"
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
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(StatusGreen.copy(alpha = 0.15f))
                                    .border(1.dp, StatusGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
                            val t = token?.trim() ?: ""
                            if (t.isNotBlank()) {
                                scope.launch {
                                    isPairing = true
                                    errorMessage = null
                                    val (ok, msg) = client.verifyAndConnect(client.baseUrl, t)
                                    isPairing = false
                                    if (ok) {
                                        step = 3
                                    } else {
                                        errorMessage = msg
                                    }
                                }
                            } else {
                                refreshMesh()
                                if (isMeshActive) {
                                    step = 2
                                }
                            }
                        }
                        2 -> {
                            val t = token?.trim() ?: ""
                            if (t.isNotBlank()) {
                                scope.launch {
                                    isPairing = true
                                    errorMessage = null
                                    val (ok, msg) = client.verifyAndConnect(client.baseUrl, t)
                                    isPairing = false
                                    if (ok) {
                                        step = 3
                                    } else {
                                        errorMessage = msg
                                    }
                                }
                            } else {
                                errorMessage = "Please enter the Host API Key generated on your laptop."
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
                            contentDescription = "Navigate to previous step",
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
                            contentDescription = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode",
                            tint = MaterialTheme.colorScheme.primary,
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp, start = 8.dp, end = 8.dp)
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
                        isCurrent -> MaterialTheme.colorScheme.primary
                        isPassed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                    animationSpec = tween(320, easing = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.00f)),
                    label = "rail_color_$i"
                )
                Box(
                    modifier = Modifier
                        .weight(animatedWeight)
                        .height(6.dp)
                        .clip(RoundedCornerShape(8.dp))
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
        1 -> true // Always active: triggers rescan when inactive, confirms when active
        2 -> hasToken
        else -> true
    }

    val label = when (step) {
        0 -> "Get started"
        1 -> if (isMeshActive) "Confirm Mesh & Continue" else "Scan Again"
        2 -> if (hasToken) "Authorize & Enter Chamber" else "Paste Host Key to Continue"
        else -> "Enter Chamber"
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "wizard_btn_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WizardBg)
            .padding(horizontal = 24.dp, vertical = 12.dp)
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
    }
}

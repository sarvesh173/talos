package com.sarvesh173.talos.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.core.motion.MotionTokens

enum class ServiceGateState {
    Checking,
    Ready,
    TailscaleRequired,
    NodeUnreachable
}

private fun systemAnimationsEnabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) > 0.01f
}.getOrDefault(true)

@Composable
fun ServiceGateScreen(
    state: ServiceGateState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onManualSetup: (() -> Unit)? = null,
    onBypass: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val motionOn = remember { systemAnimationsEnabled(context) }
    val scrollState = rememberScrollState()

    val iconScale = remember { Animatable(0.8f) }

    LaunchedEffect(state) {
        if (motionOn) {
            iconScale.snapTo(0.8f)
            iconScale.animateTo(1f, MotionTokens.fastSpatial())
        } else {
            iconScale.snapTo(1f)
        }
    }

    val checking = state == ServiceGateState.Checking
    val blocked = state == ServiceGateState.TailscaleRequired
    val unreachable = state == ServiceGateState.NodeUnreachable

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // Breathing halo container
            Box(contentAlignment = Alignment.Center) {
                if (motionOn) {
                    val halo = rememberInfiniteTransition(label = "gate_halo")
                    val pulse by halo.animateFloat(
                        initialValue = 0.95f,
                        targetValue = 1.05f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1800),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "gate_halo_scale"
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier
                            .size(140.dp)
                            .scale(pulse)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        (if (blocked || unreachable) scheme.error else scheme.primary).copy(alpha = 0.20f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    ) {}
                }

                Surface(
                    shape = CircleShape,
                    color = if (blocked || unreachable) scheme.errorContainer else scheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, metallicBorderBrush()),
                    modifier = Modifier
                        .size(92.dp)
                        .graphicsLayer {
                            scaleX = iconScale.value
                            scaleY = iconScale.value
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (checking) {
                            CircularProgressIndicator(
                                color = scheme.primary,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = when {
                                    blocked -> Icons.Default.Lock
                                    unreachable -> Icons.Default.Router
                                    else -> Icons.Default.Router
                                },
                                contentDescription = null,
                                tint = if (blocked || unreachable) scheme.onErrorContainer else scheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Title
            Text(
                text = when {
                    checking -> "Verifying Mesh Connection"
                    blocked -> "Tailscale Connection Required"
                    unreachable -> "Mesh Node Unreachable"
                    else -> "Connection Ready"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // Body
            Text(
                text = when {
                    checking -> "Probing TALOS Core daemon across Tailscale mesh..."
                    blocked -> "TALOS communicates over your encrypted Tailscale overlay. Please connect Tailscale VPN on your phone, then retry."
                    unreachable -> "Could not reach TALOS Core on the mesh. Verify your laptop daemon is running and Tailscale is active on both devices."
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(12.dp))

            // Auto-retrying status pill
            if (!checking) {
                MetalStatusPill(
                    text = "Auto-Retrying Every 3s",
                    isActive = false,
                    activeColor = StatusGreen,
                    inactiveColor = StatusAmber
                )
            } else {
                MetalStatusPill(
                    text = "Probing Endpoints...",
                    isActive = true,
                    activeColor = StatusGreen,
                    inactiveColor = StatusAmber
                )
            }

            Spacer(Modifier.height(28.dp))

            // Action Buttons - ALWAYS visible and immediately clickable
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetalButton(
                    text = if (checking) "Checking Connection..." else "Retry Connection",
                    onClick = onRetry,
                    isLoading = checking,
                    leadingIcon = Icons.Default.Refresh,
                    variant = MetalButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                )

                if (blocked || unreachable) {
                    MetalButton(
                        text = "Open Tailscale App",
                        onClick = { launchTailscale(context) },
                        leadingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                        variant = MetalButtonVariant.Tonal,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (onManualSetup != null) {
                    MetalButton(
                        text = "Configure Core Address",
                        onClick = onManualSetup,
                        leadingIcon = Icons.Default.Settings,
                        variant = MetalButtonVariant.Outlined,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (onBypass != null) {
                    TextButton(
                        onClick = onBypass,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "Continue to Interface Anyway",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Open the Tailscale app if installed, else fall back to the web download. */
private fun launchTailscale(context: Context) {
    val intents = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("tailscale://")),
        Intent(Intent.ACTION_VIEW, Uri.parse("package:com.tailscale.ipn")),
    )
    for (intent in intents) {
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
    }
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://tailscale.com/download"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

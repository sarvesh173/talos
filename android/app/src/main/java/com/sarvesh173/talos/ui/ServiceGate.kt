package com.sarvesh173.talos.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.delay

/**
 * Service gate: TALOS needs the private overlay (Tailscale) because it
 * carries the encrypted transport between agents. Without it the app must not
 * run, so the user gets one clear instruction instead of silent failures.
 *
 * Motion follows the M3E rules: spatial springs for position/size/shape,
 * effects springs for alpha (never a spatial spring on opacity), slower
 * entrances than exits, and no decorative motion when the system has
 * animations turned off.
 */
enum class ServiceGateState {
    /** Verifying core reachability + overlay state. */
    Checking,

    /** Core reachable and overlay up - the app may run. */
    Ready,

    /** Overlay down (or node unreachable over it) - the app stays closed. */
    TailscaleRequired,
}

private fun systemAnimationsEnabled(context: android.content.Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) > 0.01f
}.getOrDefault(true)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServiceGateScreen(
    state: ServiceGateState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onManualSetup: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val motionOn = remember { systemAnimationsEnabled(context) }

    // Staggered entrance: icon -> title -> body -> actions. Spatial springs for
    // movement/scale, effects springs for alpha.
    val iconScale = remember { Animatable(0.6f) }
    val titleAlpha = remember { Animatable(0f) }
    val bodyAlpha = remember { Animatable(0f) }
    val actionsAlpha = remember { Animatable(0f) }

    LaunchedEffect(state) {
        if (!motionOn) {
            iconScale.snapTo(1f); titleAlpha.snapTo(1f)
            bodyAlpha.snapTo(1f); actionsAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        iconScale.snapTo(0.6f); titleAlpha.snapTo(0f)
        bodyAlpha.snapTo(0f); actionsAlpha.snapTo(0f)
        iconScale.animateTo(1f, M3EMotion.fastSpatial())
        delay(40)
        titleAlpha.animateTo(1f, M3EMotion.defaultEffects())
        delay(40)
        bodyAlpha.animateTo(1f, M3EMotion.defaultEffects())
        delay(40)
        actionsAlpha.animateTo(1f, M3EMotion.defaultEffects())
    }

    val checking = state == ServiceGateState.Checking
    val blocked = state == ServiceGateState.TailscaleRequired

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- expressive icon container -------------------------------
            Box(contentAlignment = Alignment.Center) {
                // Soft breathing halo - a loop, so a tween curve is correct here.
                if (motionOn) {
                    val halo = rememberInfiniteTransition(label = "gate_halo")
                    val pulse by halo.animateFloat(
                        initialValue = 0.96f,
                        targetValue = 1.08f,
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
                            .size(168.dp)
                            .scale(pulse)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        scheme.primary.copy(alpha = 0.22f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    ) {}
                }

                Surface(
                    shape = CircleShape,
                    color = if (blocked) scheme.errorContainer else scheme.primaryContainer,
                    modifier = Modifier
                        .size(104.dp)
                        .graphicsLayer {
                            scaleX = iconScale.value
                            scaleY = iconScale.value
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (checking) {
                            // Canonical M3E morphing loader for a short wait.
                            androidx.compose.material3.LoadingIndicator(
                                color = scheme.onPrimaryContainer,
                                modifier = Modifier.size(44.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (blocked) Icons.Default.Lock else Icons.Default.Router,
                                contentDescription = null,
                                tint = if (blocked) scheme.onErrorContainer else scheme.onPrimaryContainer,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // --- title ---------------------------------------------------
            Text(
                text = when {
                    checking -> "Connecting to TALOS"
                    blocked -> "Tailscale required"
                    else -> "Ready"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = titleAlpha.value }
            )

            // --- body ----------------------------------------------------
            Text(
                text = when {
                    checking -> "Verifying your node and the private overlay."
                    blocked -> "TALOS runs over your private overlay, where the encrypted link between agents lives. Turn Tailscale on, then check again."
                    else -> ""
                },
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = bodyAlpha.value }
            )

            // --- actions -------------------------------------------------
            AnimatedVisibility(
                visible = !checking,
                enter = fadeIn(animationSpec = M3EMotion.defaultEffects()),
                exit = fadeOut(animationSpec = M3EMotion.fastEffects())
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = actionsAlpha.value }
                ) {
                    Spacer(Modifier.height(10.dp))
                    M3EButton(
                        text = "Check again",
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (blocked) {
                        Spacer(Modifier.height(4.dp))
                        M3ETonalButton(
                            text = "Open Tailscale",
                            onClick = { launchTailscale(context) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (onManualSetup != null) {
                        TextButton(onClick = onManualSetup) {
                            Text(
                                text = "Enter address manually",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.primary
                            )
                        }
                    }
                }
            }

            // --- checking indicator -------------------------------------
            if (checking) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer { alpha = bodyAlpha.value }
                ) {
                    M3ETypingDots(
                        color = scheme.primary,
                        dotSize = 5.dp,
                        dotSpacing = 3.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Checking",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Open the Tailscale app if installed, else fall back to the web download. */
private fun launchTailscale(context: android.content.Context) {
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

package com.sarvesh173.talos.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Service gate: TALOS requires the private overlay (Tailscale) because it
 * carries the encrypted transport between agents. Without it the app must not
 * run - so the user gets one clear instruction instead of silent failures.
 *
 * Everything here is scheme-driven: the same screen reads correctly in light
 * and dark because the color roles come from the active ColorScheme.
 */
enum class ServiceGateState {
    /** Checking core reachability + overlay state. */
    Checking,

    /** Core reachable and overlay up - the app may run. */
    Ready,

    /** Core reachable, but the overlay tunnel is not up. */
    TailscaleRequired,

    /** No core found on the network (auto-discovery failed). */
    CoreUnavailable,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServiceGateScreen(
    state: ServiceGateState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onManualSetup: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme

    val (icon, title, body) = when (state) {
        ServiceGateState.Checking -> Triple(
            Icons.Default.Router,
            "Connecting to TALOS",
            "Looking for your node and verifying the private overlay."
        )
        ServiceGateState.TailscaleRequired -> Triple(
            Icons.Default.Lock,
            "Tailscale required",
            "TALOS runs over your private overlay, where the encrypted link between agents lives. Turn Tailscale on, then retry."
        )
        ServiceGateState.CoreUnavailable -> Triple(
            Icons.Default.Router,
            "Node not found",
            "No TALOS node answered on this network. Keep the core running and retry."
        )
        ServiceGateState.Ready -> Triple(
            Icons.Default.Router,
            "Ready",
            ""
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Halo: slow breathing ring in the primary role - pairs with the
            // M3E loading cue used elsewhere in the app.
            val transition = rememberInfiniteTransition(label = "gate_halo")
            val halo by transition.animateFloat(
                initialValue = 0.94f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "gate_halo_scale"
            )

            Box(contentAlignment = Alignment.Center) {
                Surface(
                    shape = CircleShape,
                    color = scheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier
                        .size(132.dp)
                        .scale(halo)
                ) {}
                Surface(
                    shape = CircleShape,
                    color = scheme.primaryContainer,
                    modifier = Modifier.size(92.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (state != ServiceGateState.Checking) {
                Spacer(Modifier.height(6.dp))
                M3EButton(
                    text = "Check again",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state == ServiceGateState.CoreUnavailable && onManualSetup != null) {
                    TextButton(
                        onClick = onManualSetup,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "Enter address manually",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
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

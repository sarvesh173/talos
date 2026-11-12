@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sarvesh173.talos.ui.onboarding

import com.sarvesh173.talos.ui.*

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
internal fun OverviewStep() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(12.dp))

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
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Initialize your TALOS mesh",
            color = WizardText,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
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
                title = "Pair with autonomous agent fleet",
                body = "One handshake brings every always-on agent into the mesh.",
                badgeTint = CyberViolet,
                badge = {
                    Image(
                        painter = painterResource(R.drawable.ic_step_fleet),
                        contentDescription = "Agent fleet",
                        modifier = Modifier.size(28.dp),
                        colorFilter = ColorFilter.tint(CyberViolet)
                    )
                }
            )
            OverviewElevatedCard(
                index = 3,
                title = "Real-time collaboration & tool bus",
                body = "Shared event bus, tool calls and presence in a single stream.",
                badgeTint = MaterialTheme.colorScheme.primary,
                badge = {
                    Image(
                        painter = painterResource(R.drawable.ic_step_token),
                        contentDescription = "Cryptographic Key",
                        modifier = Modifier.size(28.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                    )
                }
            )
        }
    }
}

@Composable
internal fun OverviewElevatedCard(
    index: Int,
    title: String,
    body: String,
    badgeTint: Color,
    badge: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "card_scale"
    )

    val isDark = LocalIsDark.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clickable(interactionSource = interactionSource, indication = null) { },
        shape = RoundedCornerShape(20.dp),
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = index.toString(),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
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

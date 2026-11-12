package com.sarvesh173.talos.ui.onboarding

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

@Composable
internal fun MeshStep(
    meshInfo: MeshInfo?,
    isChecking: Boolean,
    errorMessage: String? = null,
    initialEndpoint: String = "",
    initialToken: String = "",
    onUpdateEndpoint: (String) -> Unit = {},
    onTokenChanged: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    onCopyPrompt: (String) -> Unit = {}
) {
    val active = meshInfo?.active == true
    val hostSetupPrompt = "Read INSTALLING.md, run bash scripts/agent_setup.sh to register a 16-digit API key on talos-core, and output the Tailscale IP and Host Key."
    var endpointInput by remember(initialEndpoint) { mutableStateOf(initialEndpoint) }
    var tokenInput by remember(initialToken) { mutableStateOf(initialToken) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(12.dp))

        // Static Machined Header
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = WizardSurface,
                border = BorderStroke(1.dp, WizardBorder),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_step_mesh),
                        contentDescription = "Tailscale Mesh",
                        modifier = Modifier.size(32.dp),
                        colorFilter = ColorFilter.tint(if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Tailscale mesh & host link",
            color = WizardText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 30.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Direct encrypted WireGuard connection to your laptop agent. No public ports.",
            color = WizardMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Card 1: Agent Setup Prompt (One-Click Copy)
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "1. Agent setup prompt (laptop / desktop)",
                    color = WizardText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Run this or paste to your AI coding agent on your computer:",
                    color = WizardMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(talosBg)
                        .border(1.dp, WizardBorder, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = hostSetupPrompt,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { onCopyPrompt(hostSetupPrompt) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Copy agent prompt", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 2: Tailscale Host Address & API Key Form
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = WizardSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.2.dp, metallicBorderBrush())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. Host Tailscale IP & Key",
                        color = WizardText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (active) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Connected (200 ok)",
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Core tailscale address:",
                    color = WizardMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = endpointInput,
                    onValueChange = { 
                        endpointInput = it
                        onUpdateEndpoint(it.trim().trimEnd('/'))
                    },
                    placeholder = { Text("http://100.x.y.z:8010", fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "API:",
                    color = WizardMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { 
                        tokenInput = it
                        onTokenChanged(it.trim())
                    },
                    placeholder = { Text("Paste API", fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = WizardDanger,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                Button(
                    onClick = {
                        if (endpointInput.isNotBlank()) {
                            onUpdateEndpoint(endpointInput.trim().trimEnd('/'))
                        }
                        if (tokenInput.isNotBlank()) {
                            onTokenChanged(tokenInput.trim())
                        }
                        onRefresh()
                    },
                    enabled = !isChecking,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Text(
                        text = if (isChecking) "Checking..." else "Verify Connection",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
internal fun MeshRow(key: String, value: String, last: Boolean = false) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
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

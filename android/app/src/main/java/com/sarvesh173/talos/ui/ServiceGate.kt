package com.sarvesh173.talos.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.core.motion.MotionTokens
import kotlinx.coroutines.launch

enum class ServiceGateState {
    Checking,
    Ready,
    TailscaleRequired,
    NodeUnreachable,
    TailscaleTimeout
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
    initialUrl: String = "",
    initialToken: String = "",
    onUpdateUrl: ((String) -> Unit)? = null,
    onVerifyAndConnect: (suspend (String, String) -> Pair<Boolean, String>)? = null,
    onManualSetup: (() -> Unit)? = null,
    onToggleTheme: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = LocalIsDark.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val motionOn = remember { systemAnimationsEnabled(context) }
    val scrollState = rememberScrollState()

    val iconScale = remember { Animatable(0.9f) }

    LaunchedEffect(state) {
        if (motionOn) {
            iconScale.snapTo(0.9f)
            iconScale.animateTo(1f, MotionTokens.fastSpatial())
        } else {
            iconScale.snapTo(1f)
        }
    }

    val checking = state == ServiceGateState.Checking
    val blocked = state == ServiceGateState.TailscaleRequired
    val unreachable = state == ServiceGateState.NodeUnreachable
    val timeout = state == ServiceGateState.TailscaleTimeout

    var customEndpoint by remember(initialUrl) { mutableStateOf(initialUrl) }
    var customApiKey by remember(initialToken) { mutableStateOf(initialToken) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var verifyError by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // Top Bar: Brand + Quick Theme & Settings Access
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (checking) scheme.primary else if (timeout || unreachable || blocked) Color(0xFFFF9F0A) else Color(0xFF30D158))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "TALOS MESH",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = scheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onToggleTheme != null) {
                        Surface(
                            onClick = onToggleTheme,
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA),
                            border = androidx.compose.foundation.BorderStroke(
                                0.8.dp,
                                if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
                            ),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle theme",
                                    tint = scheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (onManualSetup != null) {
                        Surface(
                            onClick = onManualSetup,
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA),
                            border = androidx.compose.foundation.BorderStroke(
                                0.8.dp,
                                if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
                            ),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = scheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Apple-Standard Squircle Glass Status Emblem (64dp, 16dp radius)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF1C1C1E) else Color.White,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
                ),
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        scaleX = iconScale.value
                        scaleY = iconScale.value
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (checking) {
                        // Apple-style quiet activity indicator
                        CircularProgressIndicator(
                            color = scheme.primary,
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.4.dp
                        )
                    } else {
                        Icon(
                            imageVector = when {
                                state == ServiceGateState.Ready -> Icons.Default.Check
                                blocked -> Icons.Default.Lock
                                timeout -> Icons.Default.WarningAmber
                                else -> Icons.Default.Router
                            },
                            contentDescription = null,
                            tint = when {
                                state == ServiceGateState.Ready -> Color(0xFF30D158)
                                timeout -> Color(0xFFFF9F0A)
                                blocked -> Color(0xFFFF453A)
                                else -> scheme.primary
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Title
            Text(
                text = when {
                    checking -> "Verifying mesh connection"
                    timeout -> "Tailscale may be inactive"
                    blocked -> "Tailscale required on host"
                    unreachable -> "Mesh node unreachable"
                    else -> "Connection ready"
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
                    timeout -> "No response after multiple connection attempts. Please verify Tailscale is running on both this phone and your host computer."
                    blocked -> "The core reports Tailscale overlay is inactive on the host. Ensure the computer has Tailscale active."
                    unreachable -> "Could not connect to TALOS Core. Enter the computer's Tailscale IP or tap retry below."
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Apple Progress Capsule (4dp height on 4px grid)
            if (checking) {
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "apple_progress")
                    val sweep by infiniteTransition.animateFloat(
                        initialValue = -0.5f,
                        targetValue = 1.5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1400, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "apple_progress_sweep"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.35f)
                            .align(Alignment.CenterStart)
                            .graphicsLayer {
                                translationX = sweep * 160.dp.toPx()
                            }
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        scheme.primary,
                                        scheme.primary,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            } else {
                MetalStatusPill(
                    text = if (timeout) "Tailscale check recommended" else "Retrying connection",
                    isActive = false,
                    activeColor = StatusGreen,
                    inactiveColor = if (timeout) Color(0xFFFF9F0A) else StatusAmber
                )
            }

            Spacer(Modifier.height(20.dp))

            // Tailscale Diagnostic Card (Shows when timed out or blocked)
            if (timeout || blocked) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color(0xFFFF9F0A).copy(alpha = 0.40f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFFF9F0A),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Tailscale connection checklist",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "1. Confirm Tailscale VPN is turned ON on this phone.\n2. Confirm Tailscale is running on your host computer.\n3. Make sure the Tailscale IP below matches your host's 100.x.y.z address.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        Spacer(Modifier.height(12.dp))

                        MetalButton(
                            text = "Open Tailscale app",
                            onClick = { launchTailscale(context) },
                            leadingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                            variant = MetalButtonVariant.Primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Direct Address Configuration Card
            MetalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Core Tailscale address & host key",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = customEndpoint,
                    onValueChange = { customEndpoint = it; verifyError = null },
                    label = { Text("Core Tailscale URL") },
                    placeholder = { Text("http://100.x.y.z:8010") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // API Key with Password Visibility Toggle (Hidden Button)
                OutlinedTextField(
                    value = customApiKey,
                    onValueChange = { customApiKey = it; verifyError = null },
                    label = { Text("Host API key (talos_sk_...)") },
                    placeholder = { Text("Paste key from laptop") },
                    singleLine = true,
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { isKeyVisible = !isKeyVisible },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isKeyVisible) "Hide API key" else "Show API key",
                                tint = scheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (verifyError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = verifyError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error
                    )
                }

                Spacer(Modifier.height(12.dp))

                MetalButton(
                    text = if (isVerifying) "Verifying..." else "Verify & connect to laptop",
                    isLoading = isVerifying,
                    onClick = {
                        if (customEndpoint.isNotBlank() && onVerifyAndConnect != null) {
                            coroutineScope.launch {
                                isVerifying = true
                                val (ok, msg) = onVerifyAndConnect(customEndpoint, customApiKey)
                                isVerifying = false
                                if (ok) {
                                    onRetry()
                                } else {
                                    verifyError = msg
                                }
                            }
                        } else if (customEndpoint.isNotBlank()) {
                            onUpdateUrl?.invoke(customEndpoint.trim().trimEnd('/'))
                            onRetry()
                        }
                    },
                    variant = MetalButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetalButton(
                    text = if (checking) "Checking connection..." else "Retry connection",
                    onClick = onRetry,
                    isLoading = checking,
                    leadingIcon = Icons.Default.Refresh,
                    variant = MetalButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                )

                if (blocked || unreachable || timeout) {
                    MetalButton(
                        text = "Open Tailscale app",
                        onClick = { launchTailscale(context) },
                        leadingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                        variant = MetalButtonVariant.Tonal,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (onManualSetup != null) {
                    MetalButton(
                        text = "Continue to app / Setup",
                        onClick = onManualSetup,
                        variant = MetalButtonVariant.Outlined,
                        modifier = Modifier.fillMaxWidth()
                    )
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

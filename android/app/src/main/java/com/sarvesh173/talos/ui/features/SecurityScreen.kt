package com.sarvesh173.talos.ui.features

import com.sarvesh173.talos.ui.TalosIcons
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.sarvesh173.talos.ui.entry.AppLockStore
import com.sarvesh173.talos.ui.entry.PinHasher
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Timer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.AppleToggleSwitch
import com.sarvesh173.talos.ui.LocalIsDark
import com.sarvesh173.talos.ui.appleLiquidGlassOptics
import com.sarvesh173.talos.ui.applePressable
import com.sarvesh173.talos.ui.talosTextMuted

/**
 * Dedicated Apple HIG Security Screen.
 * Provides biometric lock controls, device authentication fallback,
 * and encrypted credential coverage matching Nous Mobile specifications.
 */
@Composable
fun SecurityScreen(
    client: TalosClient,
    onBack: () -> Unit
) {
    val isDark = LocalIsDark.current
    val context = LocalContext.current
    val lockStore = remember { AppLockStore(context) }
    var appLockEnabled by remember { mutableStateOf(lockStore.enabled) }
    var hasPin by remember { mutableStateOf(lockStore.hasPin) }
    var graceSeconds by remember { mutableStateOf(lockStore.graceSeconds) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
    val requireBiometrics by client.requireBiometrics.collectAsState()

    val appleGreen = Color(0xFF34C759)
    val appleIndigo = Color(0xFF5856D6)
    val appleGray = Color(0xFF8E8E93)

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = BorderStroke(0.6.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .applePressable(0.94f) { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = TalosIcons.TalosBackArrow,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Security",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.25).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // -----------------------------------------------------------------
            // 1. BIOMETRICS & APP LOCK (Apple Inset Grouped Card)
            // -----------------------------------------------------------------
            Text(
                text = "Biometrics & app lock",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.15).sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(0.6.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .appleLiquidGlassOptics(
                        isDark = isDark,
                        chromaticGlow = MaterialTheme.colorScheme.primary,
                        cornerRadius = 20.dp
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Row 1: Require Biometrics Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Require biometrics",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.2).sp,
                                color = if (isDark) Color.White else Color(0xFF1D1D1F)
                            )
                        }
                        AppleToggleSwitch(
                            checked = requireBiometrics,
                            onCheckedChange = { client.setRequireBiometrics(it) }
                        )
                    }


                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 56.dp)
                    )

                    // Row 2: Passcode Lock (AppLockStore)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Passcode app lock",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.2).sp,
                                    color = if (isDark) Color.White else Color(0xFF1D1D1F)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (hasPin) "4-digit PIN configured" else "No PIN set (tap to configure)",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = talosTextMuted
                                )
                            }
                        }
                        AppleToggleSwitch(
                            checked = appLockEnabled,
                            onCheckedChange = { enable ->
                                if (enable && !hasPin) {
                                    showPinDialog = true
                                } else {
                                    appLockEnabled = enable
                                    lockStore.enabled = enable
                                }
                            }
                        )
                    }

                    if (appLockEnabled) {
                        HorizontalDivider(
                            color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 56.dp)
                        )

                        // Row 3: Lock timeout selector
                        val timeoutOptions = listOf(
                            0 to "Immediately",
                            60 to "1 minute",
                            300 to "5 minutes",
                            900 to "15 minutes",
                            3600 to "1 hour"
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF1D1D1F),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Lock timeout",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDark) Color.White else Color(0xFF1D1D1F)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                timeoutOptions.forEach { (sec, label) ->
                                    val isSelected = graceSeconds == sec
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else (if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .applePressable(0.94f) {
                                                graceSeconds = sec
                                                lockStore.graceSeconds = sec
                                            }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else (if (isDark) Color.White.copy(alpha = 0.8f) else Color(0xFF1D1D1F)),
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 56.dp)
                    )

                    // Explanatory text inside the Inset Grouped card
                    Text(
                        text = "Asked for when the app opens. Conversations, files and instance credentials stay covered until the device owner is authenticated. The device screen lock always works as a fallback, so a failed scan cannot lock you out.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        letterSpacing = (-0.1).sp,
                        color = talosTextMuted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // -----------------------------------------------------------------
            // 2. DEVICE CREDENTIAL COVERAGE
            // -----------------------------------------------------------------
            Text(
                text = "Credential coverage",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.15).sp,
                color = talosTextMuted,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(0.6.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .appleLiquidGlassOptics(
                        isDark = isDark,
                        chromaticGlow = MaterialTheme.colorScheme.primary,
                        cornerRadius = 20.dp
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Row 1: Instance Tokens & Keys
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Instance tokens & keys",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (requireBiometrics) "Covered" else "Protected",
                            fontSize = 12.sp,
                            color = talosTextMuted,
                            letterSpacing = (-0.15).sp
                        )
                    }

                    HorizontalDivider(
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 56.dp)
                    )

                    // Row 2: Device Screen Lock Fallback
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Screen lock fallback",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.2).sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Enabled",
                            fontSize = 12.sp,
                            color = Color(0xFF34C759),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.15).sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pinInput = ""
                pinError = null
            },
            title = {
                Text(text = "Set 4-digit passcode", fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text(
                        text = "Enter a 4-digit PIN to lock TALOS when leaving the app.",
                        fontSize = 13.sp,
                        color = talosTextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pinInput = it
                                pinError = null
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = pinError != null,
                        supportingText = pinError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pinInput.length == 4) {
                            lockStore.setPin(pinInput)
                            lockStore.enabled = true
                            appLockEnabled = true
                            hasPin = true
                            showPinDialog = false
                            pinInput = ""
                            pinError = null
                        } else {
                            pinError = "PIN must be exactly 4 digits"
                        }
                    }
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPinDialog = false
                        pinInput = ""
                        pinError = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

}
package com.sarvesh173.talos.ui.features

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.*
import kotlinx.coroutines.launch

/**
 * Apple HIG / Nous Mobile settings root.
 *
 * The root owns the account surface and the quick controls. Dedicated screens
 * remain available through the trailing detail affordances, while the primary
 * switch targets are useful without an extra navigation step.
 */
@Composable
fun SettingsScreen(
    client: TalosClient,
    onBack: () -> Unit,
    onNavigateToLookAndFeel: () -> Unit,
    onNavigateToChatPreferences: () -> Unit = {},
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToCaching: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    nodeEncryptionFingerprint: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val currentThemeMode by client.themeMode.collectAsState()
    val biometricsState by client.requireBiometrics.collectAsState()
    val notificationsAllowed by client.notificationsAllowed.collectAsState()
    val cacheChatList by client.cacheChatList.collectAsState()
    val cacheMessages by client.cacheMessages.collectAsState()

    var showTransportConfig by remember { mutableStateOf(false) }
    var showAccountDetails by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf(client.baseUrl) }
    var tokenText by remember { mutableStateOf(client.apiToken) }
    var tokenVisible by remember { mutableStateOf(false) }
    var isTransportUpdating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    var transportEnabled by remember(client.baseUrl) {
        mutableStateOf(client.baseUrl.isNotBlank())
    }

    val displayMode = when (currentThemeMode.uppercase()) {
        "LIGHT" -> "Light"
        "DARK" -> "Dark"
        else -> "System"
    }
    val cacheEnabled = cacheChatList && cacheMessages
    val cacheSummary = when {
        cacheChatList && cacheMessages -> "Chat list & messages"
        cacheChatList -> "Chat list only"
        cacheMessages -> "Messages only"
        else -> "Off"
    }

    fun updateTransport(enabled: Boolean) {
        if (enabled && urlText.isBlank()) {
            transportEnabled = true
            showTransportConfig = true
            isSuccess = false
            statusMessage = "Add a Tailscale address to enable transport"
            return
        }

        transportEnabled = enabled
        showTransportConfig = enabled && showTransportConfig
        scope.launch {
            isTransportUpdating = true
            try {
                client.updateConfig(
                    if (enabled) urlText.trim() else "",
                    if (enabled) tokenText else ""
                )
                isSuccess = true
                statusMessage = if (enabled) "Transport enabled" else "Transport paused"
            } catch (_: Exception) {
                transportEnabled = !enabled
                isSuccess = false
                statusMessage = "Transport could not be updated"
            } finally {
                isTransportUpdating = false
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
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
                        text = "Settings",
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
            AccountCard(onClick = { showAccountDetails = true })

            Spacer(modifier = Modifier.height(16.dp))

            SettingsGroup {
                SettingsToggleRow(
                    title = "Transport protocol",
                    subtitle = if (transportEnabled) {
                        "Tailscale encrypted subnet"
                    } else {
                        "Paused"
                    },
                    icon = TalosIcons.TalosHub,
                    checked = transportEnabled,
                    onCheckedChange = ::updateTransport,
                    onDetailsClick = { showTransportConfig = !showTransportConfig }
                )

                SettingsDivider()

                SettingsToggleRow(
                    title = "Security & authentication",
                    subtitle = if (biometricsState) "Biometrics on" else "Biometrics off",
                    icon = TalosIcons.TalosVault,
                    checked = biometricsState,
                    onCheckedChange = { client.setRequireBiometrics(it) },
                    onDetailsClick = onNavigateToSecurity
                )

                SettingsDivider()

                SettingsToggleRow(
                    title = "Caching",
                    subtitle = cacheSummary,
                    icon = TalosIcons.TalosStorage,
                    checked = cacheEnabled,
                    onCheckedChange = {
                        client.setCacheChatList(it)
                        client.setCacheMessages(it)
                    },
                    onDetailsClick = onNavigateToCaching
                )
            }

            AnimatedVisibility(
                visible = showTransportConfig,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                TransportConfigCard(
                    urlText = urlText,
                    onUrlChange = {
                        urlText = it
                        statusMessage = null
                        isSuccess = false
                    },
                    tokenText = tokenText,
                    onTokenChange = {
                        tokenText = it
                        statusMessage = null
                        isSuccess = false
                    },
                    tokenVisible = tokenVisible,
                    onTokenVisibilityChange = { tokenVisible = !tokenVisible },
                    isUpdating = isTransportUpdating,
                    statusMessage = statusMessage,
                    isSuccess = isSuccess,
                    onSave = {
                        if (urlText.isBlank()) {
                            isSuccess = false
                            statusMessage = "Enter a Tailscale address first"
                        } else {
                            scope.launch {
                                isTransportUpdating = true
                                try {
                                    client.updateConfig(urlText.trim(), tokenText)
                                    transportEnabled = true
                                    isSuccess = true
                                    statusMessage = "Handshake verified successfully"
                                    Toast.makeText(context, "Transport updated", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    isSuccess = false
                                    statusMessage = "Handshake could not be verified"
                                } finally {
                                    isTransportUpdating = false
                                }
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsGroup {
                SettingsMinimalRow(
                    title = "Look & feel",
                    subtitle = displayMode,
                    icon = TalosIcons.TalosPalette,
                    onClick = onNavigateToLookAndFeel
                )

                SettingsDivider()

                SettingsMinimalRow(
                    title = "Chat",
                    subtitle = "Bubbles",
                    icon = TalosIcons.TalosChat,
                    onClick = onNavigateToChatPreferences
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsGroup {
                SettingsToggleRow(
                    title = "Notifications",
                    subtitle = if (notificationsAllowed) "Allowed" else "Off",
                    icon = TalosIcons.TalosBell,
                    checked = notificationsAllowed,
                    onCheckedChange = { client.setNotificationsAllowed(it) },
                    onDetailsClick = onNavigateToNotifications
                )

                SettingsDivider()

                SettingsMinimalRow(
                    title = "Contact",
                    subtitle = "7 active council agents",
                    icon = TalosIcons.TalosMail,
                    onClick = {
                        Toast.makeText(
                            context,
                            "Council agents are active and responding",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsGroup {
                SettingsMinimalRow(
                    title = "Documentation",
                    subtitle = "TALOS v2.10.82",
                    icon = TalosIcons.TalosDoc,
                    onClick = {
                        Toast.makeText(
                            context,
                            "TALOS core documentation",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showAccountDetails) {
        AccountDetailBottomSheet(
            nodeEncryptionFingerprint = nodeEncryptionFingerprint,
            activeSessionStatus = if (transportEnabled) {
                "Active on this device"
            } else {
                "Transport paused"
            },
            onManageTransport = {
                showAccountDetails = false
                showTransportConfig = true
            },
            onOpenSecurity = {
                showAccountDetails = false
                onNavigateToSecurity()
            },
            onDismiss = { showAccountDetails = false }
        )
    }
}

@Composable
private fun AccountCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .applePressable(0.94f) { onClick() },
        shape = shape,
        color = scheme.surfaceContainerHigh,
        border = BorderStroke(
            0.8.dp,
            Brush.linearGradient(
                listOf(
                    scheme.primary.copy(alpha = 0.48f),
                    scheme.tertiary.copy(alpha = 0.30f),
                    scheme.secondary.copy(alpha = 0.24f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TALOS portal",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.1).sp
                    ),
                    color = scheme.onSurface
                )

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = scheme.surfaceContainer,
                    border = BorderStroke(0.6.dp, scheme.outlineVariant)
                ) {
                    Text(
                        text = "Autonomous core",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = (-0.1).sp
                        ),
                        color = scheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonogramAvatar()
                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sarvesh",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.25).sp
                        ),
                        color = scheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Owner • Autonomous mesh architect",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            letterSpacing = (-0.1).sp
                        ),
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TalosIcons.TalosChevronRight,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val tagShape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(tagShape)
                    .background(scheme.surfaceContainer)
                    .border(BorderStroke(0.6.dp, scheme.outlineVariant), tagShape)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = TalosIcons.TalosHub,
                    contentDescription = null,
                    tint = scheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Tailscale encrypted subnet • Node verified",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        letterSpacing = (-0.1).sp
                    ),
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MonogramAvatar() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(68.dp)
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.86f),
                        scheme.tertiary.copy(alpha = 0.72f),
                        scheme.secondary.copy(alpha = 0.66f)
                    )
                ),
                CircleShape
            )
            .padding(4.dp)
            .background(scheme.surfaceContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "S",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.25).sp
            ),
            color = scheme.onSurface
        )
    }
}

@Composable
private fun TransportConfigCard(
    urlText: String,
    onUrlChange: (String) -> Unit,
    tokenText: String,
    onTokenChange: (String) -> Unit,
    tokenVisible: Boolean,
    onTokenVisibilityChange: () -> Unit,
    isUpdating: Boolean,
    statusMessage: String?,
    isSuccess: Boolean,
    onSave: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    SettingsGroup(modifier = Modifier.padding(top = 16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Transport protocol",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.15).sp
                ),
                color = scheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Link a Tailscale address and verify the encrypted handshake.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    letterSpacing = (-0.1).sp
                ),
                color = scheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = urlText,
                onValueChange = onUrlChange,
                label = { Text("Daemon URL / Tailscale address") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = tokenText,
                onValueChange = onTokenChange,
                label = { Text("Bearer / host API token") },
                singleLine = true,
                visualTransformation = if (tokenVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .applePressable(0.94f) { onTokenVisibilityChange() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (tokenVisible) "Hide" else "Show",
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = (-0.1).sp
                            ),
                            color = scheme.primary
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(12.dp)
            )

            if (statusMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = TalosIcons.TalosHub,
                        contentDescription = null,
                        tint = if (isSuccess) scheme.tertiary else scheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            letterSpacing = (-0.1).sp
                        ),
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val buttonShape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(buttonShape)
                    .background(scheme.primary)
                    .applePressable(0.94f) { if (!isUpdating) onSave() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        color = scheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Save & verify handshake",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            letterSpacing = (-0.15).sp
                        ),
                        color = scheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDetailsClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .applePressable(0.97f) { onCheckedChange(!checked) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        letterSpacing = (-0.1).sp
                    ),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            AppleToggleSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }

        if (onDetailsClick != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .applePressable(0.94f) { onDetailsClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TalosIcons.TalosChevronRight,
                    contentDescription = "Open $title details",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsMinimalRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .applePressable(0.97f) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = scheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    letterSpacing = (-0.2).sp
                ),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    letterSpacing = (-0.1).sp
                ),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = TalosIcons.TalosChevronRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 56.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDetailBottomSheet(
    nodeEncryptionFingerprint: String?,
    activeSessionStatus: String,
    onManageTransport: () -> Unit,
    onOpenSecurity: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fingerprint = nodeEncryptionFingerprint
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "Protected by the linked node"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainer,
        contentColor = scheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Sarvesh's account",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.25).sp
                ),
                color = scheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Identity and node access",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    letterSpacing = (-0.1).sp
                ),
                color = scheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            AccountDetailValue(
                label = "Node encryption fingerprint",
                value = fingerprint
            )
            Spacer(modifier = Modifier.height(8.dp))
            AccountDetailValue(
                label = "Active session",
                value = activeSessionStatus
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Member actions",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp
                ),
                color = scheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            AccountActionRow(
                title = "Manage transport",
                subtitle = "Review the encrypted endpoint and handshake",
                icon = TalosIcons.TalosHub,
                onClick = onManageTransport
            )
            Spacer(modifier = Modifier.height(8.dp))
            AccountActionRow(
                title = "Review security",
                subtitle = "Manage biometrics and credential coverage",
                icon = TalosIcons.TalosVault,
                onClick = onOpenSecurity
            )

            Spacer(modifier = Modifier.height(16.dp))

            val buttonShape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(buttonShape)
                    .background(scheme.primary)
                    .applePressable(0.94f) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        letterSpacing = (-0.15).sp
                    ),
                    color = scheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AccountDetailValue(
    label: String,
    value: String
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .border(BorderStroke(0.6.dp, scheme.outlineVariant), shape)
            .padding(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = (-0.1).sp
            ),
            color = scheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                letterSpacing = (-0.1).sp
            ),
            color = scheme.onSurface
        )
    }
}

@Composable
private fun AccountActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .border(BorderStroke(0.6.dp, scheme.outlineVariant), shape)
            .applePressable(0.97f) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = scheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    letterSpacing = (-0.1).sp
                ),
                color = scheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    letterSpacing = (-0.1).sp
                ),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = TalosIcons.TalosChevronRight,
                contentDescription = "Open $title",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

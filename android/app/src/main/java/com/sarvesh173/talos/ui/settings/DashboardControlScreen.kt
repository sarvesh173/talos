package com.sarvesh173.talos.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.net.NetworkInterface

// ---------------------------------------------------------------------------
// Monochrome palette (per user rejection of colour-heavy design).
// Only exceptions: the iOS green for an on-toggle and red for errors.
// ---------------------------------------------------------------------------
private val RowSurface = Color(0xFF1C1C1E)
private val PrimaryText = Color(0xFFFFFFFF)
private val SecondaryText = Color(0xFF8E8E93)
private val ToggleGreen = Color(0xFF30D158)
private val ErrorRed = Color(0xFFFF453A)

// ---------------------------------------------------------------------------
// Status vocabulary: exactly these four, nothing else.
// ---------------------------------------------------------------------------
enum class ServiceStatus(val label: String) {
    RUNNING("Running"),
    STOPPED("Stopped"),
    NOT_INSTALLED("Not installed"),
    TAILSCALE_OFF("Tailscale off");

    /** Plain-words subtitle naming the real state. */
    val subtitle: String
        get() = when (this) {
            RUNNING -> "Service is running on the host."
            STOPPED -> "Service is stopped. Turn on to start it."
            NOT_INSTALLED -> "Service is not installed on the host."
            TAILSCALE_OFF -> "Tailscale is off. This device cannot reach the host."
        }

    /** Dot colour: only green/red are allowed; everything else is grey. */
    val dotColor: Color
        get() = when (this) {
            RUNNING -> ToggleGreen
            STOPPED -> SecondaryText
            NOT_INSTALLED -> SecondaryText
            TAILSCALE_OFF -> ErrorRed
        }
}

/** Why the host could not be reached. Drives the honest error banner. */
enum class UnreachableDiagnosis {
    /** Device has no 100.x address: Tailscale itself is off on the phone. */
    TAILSCALE_OFF,

    /** Device is on the tailnet but the host node is unreachable. */
    HOST_UNREACHABLE,

    /** No request was made / nothing failed. */
    NONE
}

/** Display info for one service row. */
data class ServiceInfo(
    val id: String,
    val displayName: String
)

val DashboardServices: List<ServiceInfo> = listOf(
    ServiceInfo(ServiceToggleStore.SERVICE_DASHBOARD, "Hermes Dashboard"),
    ServiceInfo(ServiceToggleStore.SERVICE_OPENCODE_SERVER, "OpenCode Server"),
    ServiceInfo(ServiceToggleStore.SERVICE_COMMANDCODE_PROXY, "CommandCode Proxy"),
    ServiceInfo(ServiceToggleStore.SERVICE_OPENCODE_BRIDGE, "OpenCode Bridge")
)

private const val HOST_ADDRESS = "100.75.107.79"

/**
 * Decide which banner (if any) an unreachable host deserves.
 *
 * Rule: request failed + no local 100.x address = Tailscale is off on this
 * device. Request failed + local 100.x address exists = this device is on
 * the tailnet, so the problem is the host node itself. Never returns a
 * generic "Connection failed" — every branch names the actual cause.
 */
fun diagnoseUnreachable(requestFailed: Boolean, hasTailnetAddress: Boolean): UnreachableDiagnosis =
    when {
        !requestFailed -> UnreachableDiagnosis.NONE
        !hasTailnetAddress -> UnreachableDiagnosis.TAILSCALE_OFF
        else -> UnreachableDiagnosis.HOST_UNREACHABLE
    }

/**
 * True if this device currently holds a Tailscale 100.x address.
 * Checks every network interface for an IPv4 address in 100.64.0.0/10.
 */
fun hasTailnetAddress(): Boolean {
    return try {
        NetworkInterface.getNetworkInterfaces().toList().any { iface ->
            iface.isUp && !iface.isLoopback && runCatching {
                iface.inetAddresses.toList().any { addr ->
                    val host = addr.hostAddress ?: ""
                    val parts = host.split('.')
                    parts.size == 4 && parts[0] == "100" &&
                        parts[1].toIntOrNull()?.let { it in 64..127 } == true
                }
            }.getOrDefault(false)
        }
    } catch (e: Exception) {
        false
    }
}

/**
 * Open the Tailscale app. If it is not installed, fall back to the Play
 * Store listing instead of failing silently.
 */
fun openTailscaleApp(context: Context) {
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("package:com.tailscale.ipn"))
    val storeIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=com.tailscale.ipn")
    )
    val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=com.tailscale.ipn")
    )
    for (intent in listOf(appIntent, storeIntent, webIntent)) {
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

/**
 * Apple HIG inset-grouped monochrome settings screen for the Hermes
 * dashboard and each CLI service.
 *
 * @param controller talks to the host; pass null for offline/preview use.
 *   When null, toggles only update the stored desired state and the status
 *   line keeps showing the last known live state.
 * @param liveStatuses last known live state per service id. Defaults to
 *   RUNNING for the dashboard and STOPPED for the rest only when the host
 *   cannot be checked; callers with real data should pass it in.
 * @param onOpenTailscale override for the banner action (tests/preview).
 * @param onProbe optional health probe (suspend, returns true if the host
 *   answered). When supplied it runs on entry and on banner Retry, and its
 *   failure is fed through [diagnoseUnreachable] for the honest banner.
 */
@Composable
fun DashboardControlScreen(
    controller: ServiceController? = null,
    liveStatuses: Map<String, ServiceStatus> = emptyMap(),
    onProbe: (suspend () -> Boolean)? = null,
    onOpenTailscale: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var desired by remember { mutableStateOf(ServiceToggleStore.load(context)) }
    var borderTint by remember { mutableStateOf(ServiceToggleStore.loadBorderTint(context)) }
    var statuses by remember(liveStatuses) {
        mutableStateOf(
            liveStatuses.ifEmpty {
                mapOf(
                    ServiceToggleStore.SERVICE_DASHBOARD to ServiceStatus.RUNNING,
                    ServiceToggleStore.SERVICE_OPENCODE_SERVER to ServiceStatus.STOPPED,
                    ServiceToggleStore.SERVICE_COMMANDCODE_PROXY to ServiceStatus.STOPPED,
                    ServiceToggleStore.SERVICE_OPENCODE_BRIDGE to ServiceStatus.STOPPED
                )
            }
        )
    }
    var diagnosis by remember { mutableStateOf(UnreachableDiagnosis.NONE) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun runProbe() {
        if (onProbe == null) return
        busy = true
        val ok = runCatching { onProbe() }.getOrDefault(false)
        diagnosis = diagnoseUnreachable(
            requestFailed = !ok,
            hasTailnetAddress = hasTailnetAddress()
        )
        busy = false
    }

    CompositionLocalProvider(LocalBorderTint provides borderTint) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { onBack() },
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "Services",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
            }

            // ---- Honest error banner: names the real cause. ----
            when (diagnosis) {
                UnreachableDiagnosis.TAILSCALE_OFF -> ErrorBanner(
                    title = "Tailscale is off on this device. Turn it on to reach 100.75.107.79",
                    actionLabel = "Open Tailscale app",
                    onAction = {
                        if (onOpenTailscale != null) onOpenTailscale()
                        else openTailscaleApp(context)
                    },
                    onRetry = { scope.launch { runProbe() } },
                    retrying = busy
                )
                UnreachableDiagnosis.HOST_UNREACHABLE -> ErrorBanner(
                    title = "Cannot reach 100.75.107.79. This device is on Tailscale, so the host service is not answering. Check that Tailscale is running on the host.",
                    actionLabel = "Open Tailscale app",
                    onAction = {
                        if (onOpenTailscale != null) onOpenTailscale()
                        else openTailscaleApp(context)
                    },
                    onRetry = { scope.launch { runProbe() } },
                    retrying = busy
                )
                UnreachableDiagnosis.NONE -> Unit
            }

            if (lastError != null) {
                Text(
                    text = lastError ?: "",
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            GroupCard(borderTint = borderTint) {
                DashboardServices.forEachIndexed { index, info ->
                    val on = desired[info.id] ?: false
                    val status = statuses[info.id] ?: ServiceStatus.STOPPED
                    ServiceRow(
                        name = info.displayName,
                        status = status,
                        enabled = on,
                        onToggle = { next ->
                            desired = desired + (info.id to next)
                            ServiceToggleStore.set(context, info.id, next)
                            lastError = null
                            val c = controller
                            if (c != null) {
                                scope.launch {
                                    busy = true
                                    val result = runCatching { c.setEnabled(info.id, next) }
                                    busy = false
                                    if (result.isFailure) {
                                        // Revert the optimistic flip and say why.
                                        desired = desired + (info.id to !next)
                                        ServiceToggleStore.set(context, info.id, !next)
                                        lastError = if (diagnoseUnreachable(true, hasTailnetAddress()) == UnreachableDiagnosis.TAILSCALE_OFF) {
                                            "Could not change " + info.displayName +
                                                ". Tailscale is off on this device. Turn it on to reach 100.75.107.79."
                                        } else {
                                            "Could not change " + info.displayName +
                                                ". The host did not answer."
                                        }
                                    } else {
                                        statuses = statuses + (info.id to
                                            if (next) ServiceStatus.RUNNING else ServiceStatus.STOPPED)
                                    }
                                }
                            }
                        }
                    )
                    if (index < DashboardServices.lastIndex) {
                        HorizontalDivider(
                            color = borderTint,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            BorderTintSection(
                current = borderTint,
                storedHex = ServiceToggleStore.loadBorderTintHex(context),
                onPick = { color ->
                    borderTint = color
                    ServiceToggleStore.setBorderTint(context, color)
                },
                onApplyHex = { color, hex ->
                    borderTint = color
                    ServiceToggleStore.setBorderTint(context, color)
                    ServiceToggleStore.setBorderTintHex(context, hex)
                }
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Toggles set the desired state. The status line always shows the live state reported by the host.",
                color = SecondaryText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------

/** Inset-grouped card with a user-changeable hairline border. */
@Composable
private fun GroupCard(
    borderTint: Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RowSurface)
            .border(BorderStroke(1.dp, borderTint), RoundedCornerShape(12.dp))
            .padding(vertical = 4.dp)
    ) {
        content()
    }
}

/** One service: name, live status dot, plain-words subtitle, iOS toggle. */
@Composable
private fun ServiceRow(
    name: String,
    status: ServiceStatus,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(status.dotColor)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = PrimaryText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = status.label + ". " + status.subtitle,
                color = SecondaryText,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 13.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = ToggleGreen,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF39393D),
                uncheckedThumbColor = Color.White
            )
        )
    }
}

/** Red-accent error banner with a hairline-outlined action button. */
@Composable
private fun ErrorBanner(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    onRetry: () -> Unit,
    retrying: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(RowSurface)
            .border(BorderStroke(1.dp, ErrorRed), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = title,
            color = PrimaryText,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onAction,
                border = BorderStroke(1.dp, PrimaryText),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = PrimaryText
                ),
                modifier = Modifier.height(36.dp)
            ) {
                Text(actionLabel, fontSize = 14.sp)
            }
            OutlinedButton(
                onClick = onRetry,
                enabled = !retrying,
                border = BorderStroke(1.dp, SecondaryText),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = SecondaryText,
                    disabledContentColor = SecondaryText.copy(alpha = 0.5f)
                ),
                modifier = Modifier.height(36.dp)
            ) {
                Text(if (retrying) "Retrying..." else "Retry", fontSize = 14.sp)
            }
        }
    }
}

/** Border tint swatches (white 8/16/24%) plus a custom hex field. */
@Composable
private fun BorderTintSection(
    current: Color,
    storedHex: String,
    onPick: (Color) -> Unit,
    onApplyHex: (Color, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            text = "Border color",
            color = PrimaryText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BorderTintPresets.forEach { preset ->
                val selected = preset.alpha == current.alpha &&
                    preset.red == current.red && preset.green == current.green &&
                    preset.blue == current.blue
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(preset)
                        .border(
                            BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) PrimaryText else SecondaryText.copy(alpha = 0.4f)
                            ),
                            CircleShape
                        )
                        .clickable { onPick(preset) }
                )
            }
            Spacer(Modifier.width(4.dp))
            var hex by remember(storedHex) { mutableStateOf(storedHex) }
            TextField(
                value = hex,
                onValueChange = { hex = it },
                placeholder = { Text("Custom hex, e.g. #FFFFFF", color = SecondaryText) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = RowSurface,
                    unfocusedContainerColor = RowSurface,
                    focusedTextColor = PrimaryText,
                    unfocusedTextColor = PrimaryText,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = PrimaryText
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, current), RoundedCornerShape(8.dp))
            )
            OutlinedButton(
                onClick = {
                    val parsed = parseBorderHex(hex)
                    if (parsed != null) onApplyHex(parsed, hex)
                },
                border = BorderStroke(1.dp, PrimaryText),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText),
                modifier = Modifier.height(44.dp)
            ) {
                Text("Apply", fontSize = 14.sp)
            }
        }
    }
}

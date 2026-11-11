package com.sarvesh173.talos.ui.features

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.data.CliRosterEntry
import com.sarvesh173.talos.network.HostResult
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.network.fetchCliRoster
import com.sarvesh173.talos.ui.LocalIsDark
import com.sarvesh173.talos.ui.TalosIcons
import com.sarvesh173.talos.ui.appleLiquidGlassOptics
import com.sarvesh173.talos.ui.applePressable

/**
 * The CLIs TALOS can actually drive.
 *
 * Deliberately short. An engine appears here only once TALOS has a plugin or an
 * integration for it, so the list is a promise the app can keep rather than a
 * catalogue of things that exist elsewhere. OpenCode is the only one today.
 */
private val SUPPORTED_CLI_IDS = setOf("opencode")

/**
 * Supported CLIs, as the host reports them.
 *
 * Nothing on this screen is decided on the phone. The host runs the probe and
 * returns the resolved path, so "installed" means a binary was found, and the
 * row shows the command that was run and what it returned. When the probe found
 * nothing the row says so instead of guessing.
 */
@Composable
fun SupportedClisScreen(
    client: TalosClient,
    onBack: () -> Unit
) {
    val isDark = LocalIsDark.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var entries by remember { mutableStateOf<List<CliRosterEntry>>(emptyList()) }
    var failure by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        loading = true
        when (val result = client.fetchCliRoster()) {
            is HostResult.Ok -> {
                entries = result.value.agents
                failure = null
            }
            is HostResult.Failed -> failure = result.reason
        }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    // Keep the host's order, drop the engines TALOS cannot drive yet.
    val visible = entries.filter { it.id in SUPPORTED_CLI_IDS }

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
                        text = "Supported CLIs",
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
            Text(
                text = "Checked on the host, not assumed. Each row shows the command that was run and what it found.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    letterSpacing = (-0.1).sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            when {
                loading && entries.isEmpty() -> StatusCard(
                    title = "Checking the host",
                    body = "Asking which CLIs are installed.",
                    isDark = isDark
                )

                failure != null -> StatusCard(
                    title = "Couldn't read the host",
                    body = failure!!,
                    isDark = isDark,
                    actionLabel = "Retry",
                    onAction = { scope.launch { load() } }
                )

                visible.isEmpty() -> StatusCard(
                    title = "Nothing to show yet",
                    body = "The host answered, but none of the engines TALOS can drive were in the list.",
                    isDark = isDark
                )

                else -> visible.forEach { entry ->
                    CliRow(entry = entry, isDark = isDark)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "An agent shows as installed only when its binary was found on the host's PATH.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    letterSpacing = (-0.1).sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun CliRow(entry: CliRosterEntry, isDark: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    val accent = if (entry.installed) Color(0xFF34C759) else Color(0xFFFF9F0A)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .appleLiquidGlassOptics(
                isDark = isDark,
                chromaticGlow = accent,
                cornerRadius = 20.dp
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = entry.id,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(installed = entry.installed, accent = accent)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // The probe itself, so the answer can be checked rather than trusted.
            val probe = entry.binaryName.ifBlank { entry.id }
            Text(
                text = "which $probe",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    letterSpacing = (-0.1).sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.binary ?: "not found on the host",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = if (entry.installed) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun StatusPill(installed: Boolean, accent: Color) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(accent)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (installed) "Installed" else "Not installed",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = accent
        )
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    isDark: Boolean,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .appleLiquidGlassOptics(isDark = isDark, cornerRadius = 20.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
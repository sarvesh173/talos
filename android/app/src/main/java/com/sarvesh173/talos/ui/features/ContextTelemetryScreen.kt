package com.sarvesh173.talos.ui.features

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.data.ContextTelemetry
import com.sarvesh173.talos.data.RoomContextBreakdown
import com.sarvesh173.talos.network.*
import com.sarvesh173.talos.ui.*
import kotlinx.coroutines.launch

@Composable
fun ContextTelemetryScreen(client: TalosClient) {
    val scope = rememberCoroutineScope()
    var telemetry by remember { mutableStateOf<ContextTelemetry?>(null) }
    var roomBreakdown by remember { mutableStateOf<RoomContextBreakdown?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    // null = never tried, "" = the host answered, anything else = why it failed
    var failure by remember { mutableStateOf<String?>(null) }
    var hasLoaded by remember { mutableStateOf(false) }

    val currentRoomId by client.currentRoomId.collectAsState()

    fun refresh() {
        scope.launch {
            isLoading = true
            val result = client.fetchAgentContext("hermes")
            telemetry = result.valueOrNull()
            failure = (result as? HostResult.Failed)?.reason
            if (currentRoomId.isNotBlank()) {
                roomBreakdown = client.fetchRoomBreakdown(currentRoomId).valueOrNull()
            } else {
                roomBreakdown = null
            }
            hasLoaded = true
            isLoading = false
        }
    }

    LaunchedEffect(currentRoomId) {
        refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Context telemetry",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Live 1M token saturation & velocity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MetalIconButton(
                icon = Icons.Default.Refresh,
                onClick = { refresh() },
                contentDescription = "Refresh telemetry"
            )
        }

        // Four distinct states, never collapsed into one another.
        if (isLoading && !hasLoaded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text(
                    text = "Reading telemetry from the host…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (failure != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Couldn't read telemetry",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = failure!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(onClick = { refresh() }) {
                        Text("Retry")
                    }
                }
            }
        }

        // Hero Gauge Card
        MetalCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hermes agent window",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Absolute values, never a substitute zero: "0 / 1,000,000"
                    // read as a real measurement when the fetch had actually
                    // failed. A dash says "not measured" instead.
                    val t = telemetry
                    Text(
                        text = if (t == null) "Token usage: not measured"
                        else "Total tokens: ${t.totalTokens} / ${t.contextWindowLimit}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (t == null) "Velocity: not measured"
                        else "Velocity: %.1f tokens/sec".format(t.generationRatePerSec),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (t == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (telemetry != null) {
                    MetalContextRing(
                        usagePct = telemetry!!.usagePct,
                        totalTokens = telemetry!!.totalTokens,
                        limit = telemetry!!.contextWindowLimit,
                        size = 56.dp
                    )
                }
            }
        }

        // Room Breakdown Card
        MetalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Active chamber context breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("System prompt tokens:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${roomBreakdown?.systemTokens ?: 0}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Message history tokens:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${roomBreakdown?.historyTokens ?: 0}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Completion tokens:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${roomBreakdown?.completionTokens ?: 0}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Usage saturation:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("%.2f%%".format((roomBreakdown?.usagePct ?: 0f) * 100), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

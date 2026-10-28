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

    val currentRoomId by client.currentRoomId.collectAsState()

    fun refresh() {
        scope.launch {
            isLoading = true
            telemetry = client.fetchAgentContext("hermes")
            if (currentRoomId.isNotBlank()) {
                roomBreakdown = client.fetchRoomBreakdown(currentRoomId)
            }
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
                    text = "Context Telemetry",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Live 1M Token Saturation & Velocity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MetalIconButton(
                icon = Icons.Default.Refresh,
                onClick = { refresh() },
                contentDescription = "Refresh Telemetry"
            )
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
                        text = "Hermes Agent Window",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Total Tokens: ${telemetry?.totalTokens ?: 0} / ${telemetry?.contextWindowLimit ?: 1_000_000}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Velocity: %.1f tokens/sec".format(telemetry?.generationRatePerSec ?: 0.0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                MetalContextRing(
                    usagePct = telemetry?.usagePct ?: 0.01f,
                    totalTokens = telemetry?.totalTokens ?: 0,
                    limit = telemetry?.contextWindowLimit ?: 1_000_000,
                    size = 56.dp
                )
            }
        }

        // Room Breakdown Card
        MetalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Active Chamber Context Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("System Prompt Tokens:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${roomBreakdown?.systemTokens ?: 0}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Message History Tokens:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${roomBreakdown?.historyTokens ?: 0}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Completion Tokens:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${roomBreakdown?.completionTokens ?: 0}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Usage Saturation:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("%.2f%%".format((roomBreakdown?.usagePct ?: 0f) * 100), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

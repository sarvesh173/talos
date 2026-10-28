package com.sarvesh173.talos.ui.features

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sarvesh173.talos.data.CustomProvider
import com.sarvesh173.talos.network.*
import com.sarvesh173.talos.ui.*
import kotlinx.coroutines.launch

@Composable
fun ProviderVaultScreen(client: TalosClient) {
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf<List<CustomProvider>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            isLoading = true
            providers = client.fetchCustomProviders()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Provider Vault",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Encrypted 0600 Core Secret Storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetalIconButton(
                    icon = Icons.Default.Refresh,
                    onClick = { refresh() },
                    contentDescription = "Refresh Providers"
                )
                MetalIconButton(
                    icon = Icons.Default.Add,
                    onClick = { showAddDialog = true },
                    contentDescription = "Add Provider"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (providers.isEmpty() && !isLoading) {
            MetalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No custom providers registered. Tap '+' to vault a new LLM provider.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(providers, key = { it.id }) { provider ->
                    MetalCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = provider.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    MetalStatusPill(
                                        text = if (provider.enabled) "Active" else "Disabled",
                                        isActive = provider.enabled
                                    )
                                }
                                Text(
                                    text = provider.baseUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Key: ${provider.apiKey}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            MetalIconButton(
                                icon = Icons.Default.Delete,
                                onClick = {
                                    scope.launch {
                                        client.deleteCustomProvider(provider.id)
                                        refresh()
                                    }
                                },
                                contentDescription = "Delete Provider",
                                tint = StatusRed
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var baseUrl by remember { mutableStateOf("") }
        var apiKey by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(text = "Add Custom Provider", color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Provider Name (e.g. OpenRouter)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL (e.g. https://api.xyz/v1)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                MetalButton(
                    text = "Save",
                    onClick = {
                        if (name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                            scope.launch {
                                client.addCustomProvider(name, baseUrl, apiKey)
                                showAddDialog = false
                                refresh()
                            }
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

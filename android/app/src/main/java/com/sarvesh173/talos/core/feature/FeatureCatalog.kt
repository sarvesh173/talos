package com.sarvesh173.talos.core.feature

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.mutableStateListOf
import com.sarvesh173.talos.ui.features.AgentFleetScreen
import com.sarvesh173.talos.ui.features.ContextTelemetryScreen
import com.sarvesh173.talos.ui.features.ProviderVaultScreen

/**
 * Global Extensible Feature Catalog for TALOS.
 *
 * External features can be registered dynamically by adding an entry to this catalog.
 * The UI automatically adapts, renders the metallic navigation controls, and hosts
 * the feature slot with zero changes to existing codebase lines.
 */
object FeatureCatalog {

    private val _slots = mutableStateListOf<FeatureSlot>()
    val slots: List<FeatureSlot> get() = _slots.sortedBy { it.order }

    init {
        // Core built-in feature slots
        registerFeature(
            featureSlot(
                id = "chambers",
                title = "Chambers",
                summary = "Multi-agent autonomous collaborative bus",
                icon = Icons.Default.Forum,
                order = 0
            ) { /* Rendered by MainScreen chat container */ }
        )

        registerFeature(
            featureSlot(
                id = "fleet",
                title = "Agent Fleet",
                summary = "CLI agent fleet inspection & model routing",
                icon = Icons.Default.Groups,
                order = 10
            ) { scope ->
                AgentFleetScreen(client = scope.client)
            }
        )

        registerFeature(
            featureSlot(
                id = "vault",
                title = "Provider Vault",
                summary = "Encrypted 0600 provider & secret management",
                icon = Icons.Default.Key,
                order = 20
            ) { scope ->
                ProviderVaultScreen(client = scope.client)
            }
        )

        registerFeature(
            featureSlot(
                id = "telemetry",
                title = "Telemetry",
                summary = "1M token saturation & context velocity",
                icon = Icons.Default.Speed,
                order = 30
            ) { scope ->
                ContextTelemetryScreen(client = scope.client)
            }
        )
    }

    /**
     * Register an external feature slot.
     * Can be called at runtime or on app initialization.
     */
    fun registerFeature(slot: FeatureSlot) {
        if (_slots.none { it.id == slot.id }) {
            _slots.add(slot)
        }
    }

    /**
     * Unregister a feature slot by id.
     */
    fun unregisterFeature(slotId: String) {
        _slots.removeAll { it.id == slotId }
    }
}

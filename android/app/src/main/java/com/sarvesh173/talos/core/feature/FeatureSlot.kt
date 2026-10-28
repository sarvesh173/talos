package com.sarvesh173.talos.core.feature

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The extensibility contract for every TALOS feature slot.
 *
 * A "feature" is a self-contained unit of capability: it owns its own screen,
 * its own state and its own data access. It NEVER reaches into another
 * feature's internals — features communicate only through the shared
 * repositories and the WebSocket bus.
 *
 * Adding a new (including externally authored) feature is therefore:
 *   1. write one file under `feature/<name>/` implementing this interface
 *   2. add one `FeatureSlot(...)` entry to `feature/registry/FeatureCatalog.kt`
 *
 * The shell renders whatever the catalog contains, so a new slot appears with
 * zero changes to shell code.
 */
interface FeatureSlot {

    /** Stable machine id, kebab-case. Never localised. */
    val id: String

    /** Human title shown in the navigation rail. Sentence case. */
    val title: String

    /** Short one-line purpose, shown as supporting text in the slot header. */
    val summary: String

    /** Leading icon for the navigation rail. Must be a Material vector icon. */
    val icon: ImageVector

    /** Sort order in the navigation rail. Lower comes first. */
    val order: Int

    /** When false the slot is hidden but its implementation stays untouched. */
    val enabled: Boolean get() = true

    /**
     * The slot's content.
     *
     * @param scope the coroutine scope owned by the shell for this slot
     */
    @Composable
    fun Content(scope: FeatureScope)
}

/**
 * Everything a feature is allowed to touch.
 *
 * Deliberately small: a coroutine scope and the shared client. No repository
 * internals, no other feature's state.
 */
interface FeatureScope {
    /** Scope for long-lived feature work (polling, websockets). */
    val coroutineScope: kotlinx.coroutines.CoroutineScope

    /**
     * The shared core client. Features use it for REST + WebSocket access.
     * It resolves its endpoint at runtime — never hardcode an address.
     */
    val client: com.sarvesh173.talos.network.TalosClient
}

/**
 * Convenience factory for the common case: a slot whose content only needs the
 * scope and the client.
 */
fun featureSlot(
    id: String,
    title: String,
    summary: String,
    icon: ImageVector,
    order: Int,
    enabled: Boolean = true,
    content: @Composable (FeatureScope) -> Unit
): FeatureSlot = object : FeatureSlot {
    override val id: String = id
    override val title: String = title
    override val summary: String = summary
    override val icon: ImageVector = icon
    override val order: Int = order
    override val enabled: Boolean = enabled
    @Composable
    override fun Content(scope: FeatureScope) = content(scope)
}

/**
 * A slot advertised by the core that this client build does not know about.
 *
 * The shell renders these as a safe read-only capability card instead of
 * crashing, so a newer core can expose more than this APK understands.
 */
data class ExternalFeatureDescriptor(
    val id: String,
    val title: String,
    val summary: String = "",
    val iconKey: String = "hub"
) {
    /** Best-effort icon mapping for externally advertised slots. */
    val icon: ImageVector
        get() = when (iconKey.lowercase()) {
            "dashboard" -> Icons.Default.Dashboard
            "forum" -> Icons.Default.Forum
            "groups" -> Icons.Default.Groups
            "hub" -> Icons.Default.Hub
            "key" -> Icons.Default.Key
            "speed" -> Icons.Default.Speed
            "terminal" -> Icons.Default.Terminal
            else -> Icons.Default.Hub
        }
}

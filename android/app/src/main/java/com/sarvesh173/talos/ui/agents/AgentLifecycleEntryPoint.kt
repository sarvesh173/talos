package com.sarvesh173.talos.ui.agents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.TalosIcons
import com.sarvesh173.talos.ui.applePressable

/**
 * INTEGRATION — Agent lifecycle screen as a drop-in Settings navigation target.
 *
 * AgentLifecycleScreen's real signature is `AgentLifecycleScreen(onBack: () ->
 * Unit)` (no client, no onNavigate). SettingsScreen does NOT have an
 * `onNavigate: (String) -> Unit` parameter — it has one `onNavigateToX: () ->
 * Unit` lambda per destination (lines 43-52) and each is wired through
 * MainActivity's `appScreen` string. The row below is a standalone inset-grouped
 * row (SettingsScreen's `SettingsGroup` / `SettingsMinimalRow` are `private`, so
 * they cannot be reused from this package); it replicates SettingsMinimalRow's
 * exact idiom so the orchestrator does not need to touch SettingsScreen internals.
 *
 * 1) SettingsScreen.kt — add a navigation parameter after
 *    `onNavigateToThinkingOrb: () -> Unit = {}` (line 51):
 *    onNavigateToAgentLifecycle: () -> Unit = {},
 *
 * 2) SettingsScreen.kt — in the "Look & feel / Chat / Thinking Orb" SettingsGroup
 *    (lines 264-289), after the "Thinking orb" SettingsMinimalRow (lines 283-288),
 *    add:
 *    SettingsDivider()
 *
 *    AgentLifecycleSettingsRow(onOpen = onNavigateToAgentLifecycle)
 *    This places the row last in that group, after "Thinking orb"; the divider
 *    keeps the inset-grouped separator rhythm.
 *
 * 3) SettingsScreen.kt — add the import (next to other ui imports, line 32):
 *    import com.sarvesh173.talos.ui.agents.AgentLifecycleSettingsRow
 *    import com.sarvesh173.talos.ui.agents.ROUTE_AGENT_LIFECYCLE
 *
 * 4) MainActivity.kt — imports next to the features imports (after line 109):
 *    import com.sarvesh173.talos.ui.agents.AgentLifecycleScreen
 *    import com.sarvesh173.talos.ui.agents.ROUTE_AGENT_LIFECYCLE
 *
 * 5) MainActivity.kt — route matcher `val activeTarget = when (appScreen)`
 *    (lines 381-398), after `"thinking_orb" -> "thinking_orb"` (line 396), add:
 *    "agent_lifecycle" -> "agent_lifecycle"
 *
 * 6) MainActivity.kt — BackHandler (lines 401-419): add `"agent_lifecycle"` to
 *    the settings-returning branch (line 403):
 *    "look_and_feel", "chat_preferences", "security", "caching", "notifications", "agent_lifecycle" -> {
 *        appScreen = "settings"
 *    }
 *    Also add `"agent_lifecycle"` to `isSettingsSub` / `fromSettingsSub` lists
 *    (lines 429-430) so the push/pop transition matches Look & Feel & friends:
 *    val isSettingsSub = targetState in listOf("look_and_feel", "chat_preferences", "security", "caching", "notifications", "agent_lifecycle")
 *    val fromSettingsSub = initialState in listOf("look_and_feel", "chat_preferences", "security", "caching", "notifications", "agent_lifecycle")
 *
 * 7) MainActivity.kt — settings branch (line 599-610), add the lambda:
 *    onNavigateToAgentLifecycle = { appScreen = ROUTE_AGENT_LIFECYCLE }
 *
 * 8) MainActivity.kt — AnimatedContent screen branch, after `"thinking_orb"`
 *    (lines 596-598), add:
 *    "agent_lifecycle" -> {
 *        AgentLifecycleScreen(onBack = { appScreen = "settings" })
 *    }
 *
 * No changes to AgentLifecycleScreen.kt are required.
 */
const val ROUTE_AGENT_LIFECYCLE: String = "agent_lifecycle"

/**
 * Inset-grouped settings row for the agent lifecycle screen.
 *
 * Byte-for-byte idiom match of SettingsScreen's SettingsMinimalRow (lines
 * 800-858): 48dp minimum height, applePressable(0.97f), 16dp/12dp padding, 22dp
 * leading glyph, 16sp title / 12sp subtitle, 20dp chevron in a 48dp trailing
 * box, same letter-spacing and colours. Single accent (MaterialTheme primary
 * only via the theme); no gradients, no emoji.
 */
@Composable
fun AgentLifecycleSettingsRow(onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .applePressable(0.97f) { onOpen() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = TalosIcons.TalosHub,
            contentDescription = null,
            tint = scheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Agent lifecycle",
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
                text = "Advanced · which agents stay running",
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

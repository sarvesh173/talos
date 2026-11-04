package com.sarvesh173.talos.ui.kanban

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.LocalIsDark
import com.sarvesh173.talos.ui.TalosIcons
import com.sarvesh173.talos.ui.applePressable

/**
 * INTEGRATION — Kanban board as a drop-in navigation target.
 *
 * File: MainActivity.kt (com.sarvesh173.talos.MainActivity, setContent block).
 * Route convention: `var appScreen by remember { mutableStateOf("main") }` with
 * plain strings ("settings", "look_and_feel", "canvas", ...). KanbanScreen's real
 * signature is `KanbanScreen(client: TalosClient, roomId: String =
 * KanbanApi.DEFAULT_ROOM_ID, onBack: (() -> Unit)? = null, modifier: Modifier =
 * Modifier)`. The Activity's existing `client` instance is passed directly
 * (TalosClient.shared(context) returns the Activity-created instance, so no
 * factory call is needed).
 *
 * 1) Imports — add next to the other `com.sarvesh173.talos.ui.features.*` imports
 *    (after line 109, `import com.sarvesh173.talos.ui.features.ThinkingOrbSettingsScreen`):
 *    import com.sarvesh173.talos.ui.kanban.KanbanScreen
 *    import com.sarvesh173.talos.ui.kanban.KanbanOverflowItem
 *    import com.sarvesh173.talos.ui.kanban.ROUTE_KANBAN
 *    import com.sarvesh173.talos.ui.kanban.KanbanToolbarButton
 *
 * 2) Route matcher — inside `val activeTarget = when (appScreen)` (lines 381-398),
 *    after `"thinking_orb" -> "thinking_orb"` (line 396), add:
 *    "kanban" -> "kanban"
 *
 * 3) BackHandler — inside `BackHandler(enabled = true)` (lines 401-419), add
 *    `"kanban"` to the main-returning branch (line 406):
 *    "settings", "canvas", "diff", "hub", "gallery", "cli_manager", "chat", "kanban" -> {
 *        appScreen = "main"
 *    }
 *
 * 4) Screen branch — inside `AnimatedContent { screenKey -> when (screenKey) ... }`
 *    (starts line 524), add after the `"thinking_orb"` branch (lines 596-598):
 *    "kanban" -> {
 *        KanbanScreen(
 *            client = client,
 *            roomId = client.currentRoomId.value,
 *            onBack = { appScreen = "main" }
 *        )
 *    }
 *    Note: `client.currentRoomId.value` (the StateFlow's current value) makes the
 *    board follow the active chamber; use `KanbanApi.DEFAULT_ROOM_ID`
 *    ("the-council") instead if a fixed room is preferred.
 *
 * 5) Entry point — AppleAgentChatScreen's top bar (lines 366-484) already carries
 *    three 48dp icon actions (Back, Past sessions, MoreHoriz telemetry toggle).
 *    Per Apple HIG (max 2-3 icon actions in a mobile header) the bar is full, so
 *    the RECOMMENDED integration is the overflow menu row, not a fourth icon:
 *    inside the MoreHoriz menu/sheet, add:
 *    KanbanOverflowItem(onOpen = { appScreen = ROUTE_KANBAN })
 *    If the header later drops an action and has room, use the toolbar button
 *    instead, placed after the history button (after line 466):
 *    KanbanToolbarButton(onOpen = { appScreen = ROUTE_KANBAN })
 *    Both match AppleAgentChatScreen's press behaviour exactly: 48dp hit target,
 *    clip(RoundedCornerShape(12.dp)), applePressable(0.92f, pressHaptic = true),
 *    20dp glyph.
 *
 * No changes to KanbanScreen.kt or KanbanModels.kt are required.
 */
const val ROUTE_KANBAN: String = "kanban"

/**
 * Toolbar icon button for the Kanban (Tool Calls) board.
 *
 * Drop-in replacement for one of AppleAgentChatScreen's top-bar actions:
 * 48dp hit target, RoundedCornerShape(12.dp) clip, applePressable(0.92f,
 * pressHaptic = true), 20dp TalosBoard glyph, same tint idiom as the telemetry
 * (MoreHoriz) button. Only use when the header has fewer than 3 icon actions;
 * otherwise prefer [KanbanOverflowItem].
 */
@Composable
fun KanbanToolbarButton(onOpen: () -> Unit) {
    val isDark = LocalIsDark.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .applePressable(0.92f, pressHaptic = true) { onOpen() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = TalosIcons.TalosBoard,
            contentDescription = "Tool Calls board",
            tint = if (isDark) Color.White.copy(alpha = 0.80f) else Color(0xFF1D1D1F),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Overflow menu row for the Kanban board.
 *
 * Recommended entry point while AppleAgentChatScreen's header is full (Back +
 * Past sessions + MoreHoriz = 3 actions). Place inside whatever hosts the
 * MoreHoriz menu (DropdownMenu / bottom sheet) so the board is reachable
 * without exceeding the 2-3 icon-action limit. Same haptic press idiom as
 * SettingsScreen's rows (applePressable(0.97f)), 48dp minimum height.
 */
@Composable
fun KanbanOverflowItem(onOpen: () -> Unit) {
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
            imageVector = TalosIcons.TalosBoard,
            contentDescription = null,
            tint = scheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Tool Calls",
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
                text = "Live operations board",
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

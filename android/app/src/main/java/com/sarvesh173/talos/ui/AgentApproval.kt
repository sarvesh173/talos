package com.sarvesh173.talos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.orb.OrbState
import com.sarvesh173.talos.ui.orb.ThinkingOrb

/**
 * Permission POSTURE for the agent, not a boolean.
 *
 * "Auto-approve everything" is a real mode people want, but shipping it as a
 * single on/off switch teaches users to leave it on. Three rungs make the
 * trade-off legible:
 *
 *  - **Ask** — every tool call waits for the human. Safest, slowest.
 *  - **Ask once** — read-only work proceeds, anything that writes asks.
 *  - **YOLO** — everything runs. Loud, and visibly different, on purpose.
 *
 * The chosen posture is shown in the chat header, so a user can never be unsure
 * which mode they left the agent in.
 */
enum class AgentApprovalMode(val label: String, val blurb: String) {
    Ask("Ask", "Every tool call waits for you"),
    AskOnce("Ask once", "Reads run, writes wait"),
    Yolo("YOLO", "Full access, no prompts")
}

/**
 * Approval banner: appears only when the agent is blocked on a decision.
 *
 * Approve / Deny are the two 48dp targets; the destructive side (Deny) is
 * outlined rather than filled so a mis-tap does not silently kill a long job.
 */
@Composable
fun ApprovalBanner(
    request: AgentApprovalRequest,
    isDark: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onAlwaysAllow: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val amber = Color(0xFFFF9F0A)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isDark) Color(0xFF1E1A12) else Color(0xFFFFF8EC))
            .border(BorderStroke(0.8.dp, amber.copy(alpha = 0.45f)), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThinkingOrb(
                state = OrbState.SOLVING,
                size = 20.dp,
                ink = amber,
                label = "Waiting for approval",
                announce = false
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Needs your approval",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else Color(0xFF1D1D1F)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = request.summary,
            fontSize = 12.sp,
            color = if (isDark) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.65f)
        )

        if (request.command.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = request.command,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF101014) else Color(0xFFF2F2F7))
                    .padding(10.dp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF34C759))
                    .applePressable(0.96f, pressHaptic = true) { onApprove() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Approve", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(0.9.dp, if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.15f)), RoundedCornerShape(12.dp))
                    .applePressable(0.96f, pressHaptic = true) { onDeny() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "Deny",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF1D1D1F)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .applePressable(0.97f, pressHaptic = true) { onAlwaysAllow() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Always allow this kind of action",
                fontSize = 11.sp,
                color = amber
            )
        }
    }
}

/** One pending permission request from the agent. */
data class AgentApprovalRequest(
    val id: String,
    val summary: String,
    val command: String = "",
    val toolName: String = ""
)

/**
 * Past sessions for this agent.
 *
 * Reads the core's room list and the local message cache so a user can return
 * to yesterday's thread without hunting. A session with no cached messages is
 * still listed (the core knows it exists) but shows "no local history" instead
 * of an empty-looking row that implies data loss.
 */
@Composable
fun SessionHistorySheet(
    sessions: List<SessionSummary>,
    isDark: Boolean,
    brandColor: Color,
    currentId: String?,
    onOpen: (SessionSummary) -> Unit,
    onDismiss: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val strong = if (isDark) Color.White else Color(0xFF1D1D1F)
    val muted = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.45f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isDark) Color(0xFF141418) else Color.White)
            .border(BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Past sessions",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = strong,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Close",
                fontSize = 11.sp,
                color = brandColor,
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .applePressable(0.95f, pressHaptic = true) { onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (sessions.isEmpty()) {
            Text(
                text = "No earlier sessions for this agent.",
                fontSize = 11.sp,
                color = muted,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions, key = { it.id }) { s ->
                    val isCurrent = s.id == currentId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isCurrent) brandColor.copy(alpha = 0.14f)
                                else if (isDark) Color.White.copy(alpha = 0.04f)
                                else Color.Black.copy(alpha = 0.03f)
                            )
                            .applePressable(0.97f, pressHaptic = true) { onOpen(s) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.title,
                                fontSize = 13.sp,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isCurrent) brandColor else strong,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(s.relativeTime)
                                    if (s.messageCount > 0) append(" · ${s.messageCount} messages")
                                    else append(" · no local history")
                                },
                                fontSize = 11.sp,
                                color = muted,
                                maxLines = 1
                            )
                        }
                        if (isCurrent) {
                            Text("Now", fontSize = 11.sp, color = brandColor)
                        }
                    }
                }
            }
        }
    }
}

/** A session row for the history list. */
data class SessionSummary(
    val id: String,
    val title: String,
    val relativeTime: String,
    val messageCount: Int = 0
)

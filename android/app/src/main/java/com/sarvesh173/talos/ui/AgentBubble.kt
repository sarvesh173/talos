package com.sarvesh173.talos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.orb.OrbState
import com.sarvesh173.talos.ui.orb.ThinkingOrb
import kotlin.math.roundToInt

/**
 * A floating agent bubble, and the stack behind it.
 *
 * This is the WhatsApp-style affordance the user asked for: when the app is not
 * on screen, the agent leaves a small bubble in the corner. Tap it and you get
 * the thread in a compact sheet — a mini view, not the whole app — so a quick
 * "approve this" or "what did you mean?" never costs a full context switch.
 *
 * Two states:
 *  - **collapsed** — a 56dp glass puck with an unread count;
 *  - **expanded** — the agent's most recent notifications, each with the exact
 *    command it is waiting on, plus an inline reply field.
 *
 * Positions are remembered per session so the puck stays where the user dragged
 * it instead of jumping back to the corner on every recomposition.
 */

/** One entry in the bubble stack. */
data class BubbleNotice(
    val id: String,
    val agentName: String,
    val roomId: String,
    val title: String,
    val body: String,
    val command: String = "",
    val needsApproval: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val brandColor: Color = MaterialTheme.colorScheme.primary
)

/**
 * The bubble itself. Drag to move, tap to expand, long-press to dismiss.
 *
 * @param notices newest first. An empty list hides the bubble entirely — a
 *   floating puck with nothing behind it is clutter.
 */
@Composable
fun AgentBubble(
    notices: List<BubbleNotice>,
    expanded: Boolean,
    isDark: Boolean,
    onToggle: () -> Unit,
    onDismissNotice: (String) -> Unit,
    onReply: (BubbleNotice, String) -> Unit,
    onOpenRoom: (BubbleNotice) -> Unit,
    onApprove: (BubbleNotice) -> Unit,
    modifier: Modifier = Modifier
) {
    if (notices.isEmpty()) return

    var offset by remember { mutableStateOf(Offset.Zero) }
    val unread = notices.size
    val accent = notices.first().brandColor

    val scale by animateFloatAsState(
        targetValue = if (expanded) 1f else 1f,
        label = "bubble_scale"
    )

    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offset += drag
                }
            }
    ) {
        if (!expanded) {
            // ---- collapsed puck ----
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(10.dp, CircleShape, ambientColor = accent.copy(alpha = 0.45f))
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                if (isDark) Color(0xFF1C1C1E) else Color.White,
                                if (isDark) Color(0xFF121214) else Color(0xFFF2F2F7)
                            )
                        )
                    )
                    .border(BorderStroke(1.dp, accent.copy(alpha = 0.45f)), CircleShape)
                    .applePressable(0.92f, pressHaptic = true) { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                ThinkingOrb(
                    state = OrbState.WORKING,
                    size = 26.dp,
                    ink = accent,
                    label = "TALOS agent activity",
                    announce = false
                )
                // Unread pill, top-right, so the count is readable at a glance.
                if (unread > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF3B30))
                            .border(BorderStroke(1.5.dp, if (isDark) Color.Black else Color.White), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unread > 9) "9+" else unread.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        } else {
            // ---- expanded mini view ----
            Column(
                modifier = Modifier
                    // A fixed 320dp card overflows a 320dp phone (and the 360dp
                    // compact case once the chat gutter is taken out). Cap at a
                    // fraction of the window instead, so the bubble always fits.
                    .widthIn(max = 320.dp)
                    .fillMaxWidth(0.86f)
                    .shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.35f))
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isDark) Color(0xFF141418).copy(alpha = 0.98f) else Color.White)
                    .border(
                        BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.07f)),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThinkingOrb(
                        state = OrbState.WORKING,
                        size = 20.dp,
                        ink = accent,
                        label = null,
                        announce = false
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Agents",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color.White else Color(0xFF1D1D1F),
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .applePressable(0.94f, pressHaptic = true) { onToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Collapse",
                            tint = if (isDark) Color.White.copy(alpha = 0.60f) else Color.Black.copy(alpha = 0.50f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notices, key = { it.id }) { n ->
                        BubbleNoticeRow(
                            notice = n,
                            isDark = isDark,
                            onDismiss = { onDismissNotice(n.id) },
                            onReply = { text -> onReply(n, text) },
                            onOpen = { onOpenRoom(n) },
                            onApprove = { onApprove(n) }
                        )
                    }
                }

                if (notices.isEmpty()) {
                    Text(
                        text = "No recent bubbles",
                        fontSize = 11.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.40f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleNoticeRow(
    notice: BubbleNotice,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onReply: (String) -> Unit,
    onOpen: () -> Unit,
    onApprove: () -> Unit
) {
    var replyOpen by remember(notice.id) { mutableStateOf(false) }
    var replyText by remember(notice.id) { mutableStateOf("") }
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.035f))
            .border(
                BorderStroke(
                    0.8.dp,
                    if (notice.needsApproval) Color(0xFFFF9F0A).copy(alpha = 0.40f) else Color.Transparent
                ),
                shape
            )
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = notice.agentName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = notice.brandColor,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .applePressable(0.94f, pressHaptic = true) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = if (isDark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.40f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Text(
            text = notice.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDark) Color.White else Color(0xFF1D1D1F),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (notice.body.isNotBlank()) {
            Text(
                text = notice.body,
                fontSize = 11.sp,
                color = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.50f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (notice.command.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notice.command,
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF0E0E10) else Color(0xFFF2F2F7))
                    .padding(6.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (notice.needsApproval) {
                MiniAction("Approve", Color(0xFF34C759), onApprove, isDark)
                MiniAction("Open", null, onOpen, isDark)
            } else {
                MiniAction("Reply", notice.brandColor, { replyOpen = !replyOpen }, isDark)
                MiniAction("Open", null, onOpen, isDark)
            }
        }

        AnimatedVisibility(
            visible = replyOpen,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = if (isDark) Color.White else Color(0xFF1D1D1F)
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(notice.brandColor),
                    decorationBox = { inner ->
                        if (replyText.isEmpty()) {
                            Text(
                                text = "Reply to ${notice.agentName}",
                                fontSize = 12.sp,
                                color = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.30f)
                            )
                        }
                        inner()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                MiniAction(
                    "Send",
                    notice.brandColor,
                    {
                        if (replyText.isNotBlank()) {
                            onReply(replyText.trim())
                            replyText = ""
                            replyOpen = false
                        }
                    },
                    isDark
                )
            }
        }
    }
}

@Composable
private fun MiniAction(
    label: String,
    tint: Color?,
    onClick: () -> Unit,
    isDark: Boolean
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(
                tint?.copy(alpha = 0.16f)
                    ?: (if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f))
            )
            .border(
                BorderStroke(0.7.dp, tint?.copy(alpha = 0.40f) ?: Color.Transparent),
                shape
            )
            .applePressable(0.95f, pressHaptic = true) { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = tint ?: (if (isDark) Color.White.copy(alpha = 0.80f) else Color(0xFF1D1D1F))
        )
    }
}

/**
 * Empty-state copy for the bubble stack, kept next to the component so the two
 * never drift apart.
 */
const val BUBBLE_EMPTY_TITLE = "No recent bubbles"
const val BUBBLE_EMPTY_BODY = "Recent bubbles and dismissed bubbles will appear here"

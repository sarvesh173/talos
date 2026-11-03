package com.sarvesh173.talos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.data.Role
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.orb.OrbState
import com.sarvesh173.talos.ui.orb.ThinkingOrb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TALOS Apple Liquid Glass Agent Chat.
 *
 * Apple HIG + WWDC25 Liquid Glass, with the real transport underneath:
 *
 * - messages come from the live room stream (`client.messages`), never a
 *   scripted reply;
 * - the agent's working state drives a Thinking Orb (native port of
 *   Jakubantalik/thinking-orbs), so "thinking" is visible instead of implied;
 * - the model picker sits at the bottom beside the composer and collapses the
 *   moment the message field takes focus — the field always wins;
 * - attach opens one sheet for camera, photos, files, drawings and stickers; a
 *   picked image is previewed for confirmation before it is sent, and can be
 *   annotated or discarded;
 * - the telemetry strip shows live context, tool activity and the edit diff for
 *   the current turn, with a red/green line viewer.
 */

data class ChatMessage(
    val id: String,
    val sender: String, // "user" or "agent"
    val text: String,
    val timestamp: String,
    val codeBlock: String? = null
)

/** Clock label for a core timestamp (epoch seconds). */
private fun formatClock(epochSeconds: Double): String =
    SimpleDateFormat("HH:mm", Locale.getDefault())
        .format(Date((epochSeconds * 1000).toLong()))

@Composable
fun AppleAgentChatScreen(
    client: TalosClient,
    sessionId: String,
    sessionTitle: String,
    agentName: String,
    agentIconRes: Int,
    brandColor: Color,
    isDark: Boolean = true,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf<String?>(null) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf<PendingImage?>(null) }
    var showTelemetry by remember { mutableStateOf(false) }
    var composerFocused by remember { mutableStateOf(false) }

    // Live room stream. The screen renders whatever the core actually sent;
    // it never fabricates an agent reply.
    val liveMessages by client.messages.collectAsState()
    val isThinking by client.isAgentTyping.collectAsState()

    // The composer always wins over the picker.
    LaunchedEffect(composerFocused) {
        if (composerFocused) showModelPicker = false
    }

    BackHandler {
        TalosHaptics.sendHaptic(context)
        onBack()
    }

    val messages = remember(liveMessages) {
        liveMessages.map { msg ->
            ChatMessage(
                id = msg.id,
                sender = if (msg.sender_role == Role.human) "user" else "agent",
                text = msg.content,
                timestamp = formatClock(msg.created_at),
                codeBlock = null
            )
        }
    }

    LaunchedEffect(sessionId) {
        if (client.isPaired.value) client.switchRoom(sessionId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val backgroundColor = if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF)

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        // ---------------- message stream ----------------
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 104.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                if (msg.sender == "user") {
                    UserBubble(msg, isDark, brandColor)
                } else {
                    AgentBubble(msg, isDark, brandColor, agentIconRes, agentName)
                }
            }

            if (isThinking) {
                item(key = "thinking_orb") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 36.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThinkingOrb(
                            state = OrbState.WORKING,
                            size = 28.dp,
                            ink = brandColor,
                            label = "$agentName is working"
                        )
                        Text(
                            text = "$agentName is working",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.60f)
                        )
                    }
                }
            }
        }

        // ---------------- telemetry strip ----------------
        AnimatedVisibility(
            visible = showTelemetry,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp)
        ) {
            AgentTelemetryStrip(
                agentName = agentName,
                isDark = isDark,
                brandColor = brandColor,
                modelLabel = selectedModel ?: "auto",
                client = client
            )
        }

        // ---------------- top glass bar ----------------
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.95f),
            border = BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        if (isDark) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.85f),
                        if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
                    )
                )
            ),
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .applePressable(0.92f, pressHaptic = true) { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to hub",
                        tint = if (isDark) Color.White else Color(0xFF1D1D1F),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brandColor.copy(alpha = if (isDark) 0.18f else 0.12f))
                        .border(BorderStroke(0.8.dp, brandColor.copy(alpha = 0.35f)), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = agentIconRes),
                        contentDescription = agentName,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = agentName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F),
                            letterSpacing = (-0.15).sp
                        )
                        Box(
                            modifier = Modifier.size(5.dp).clip(CircleShape)
                                .background(if (isThinking) StatusAmber else StatusGreen)
                        )
                    }
                    Text(
                        text = sessionTitle,
                        fontSize = 11.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.45f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Telemetry toggle: context, tools, and diff for this agent.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .applePressable(0.92f, pressHaptic = true) { showTelemetry = !showTelemetry },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = if (showTelemetry) "Hide agent status" else "Show agent status",
                        tint = if (showTelemetry) brandColor else if (isDark) Color.White.copy(alpha = 0.80f) else Color(0xFF1D1D1F),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ---------------- bottom dock ----------------
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Model picker: sits directly above the composer, collapses on focus.
            AnimatedVisibility(
                visible = showModelPicker && !composerFocused,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ModelPickerBar(
                    client = client,
                    selectedModel = selectedModel,
                    isDark = isDark,
                    brandColor = brandColor,
                    onSelect = { selectedModel = it; showModelPicker = false },
                    onClose = { showModelPicker = false }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = if (isDark) Color(0xFF161820).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.96f),
                border = BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            if (isDark) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.85f),
                            if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
                        )
                    )
                ),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Plus: camera, photos, files, drawing, stickers.
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
                            .applePressable(0.92f, pressHaptic = true) { showAttachSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach",
                            tint = if (isDark) Color.White else Color(0xFF1D1D1F),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Model chip — tap opens the picker, focus closes it.
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(brandColor.copy(alpha = 0.14f))
                            .applePressable(0.94f, pressHaptic = true) { showModelPicker = !showModelPicker }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (selectedModel ?: "auto").substringAfterLast("/"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = brandColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 84.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = if (isDark) Color.White else Color(0xFF1D1D1F)
                        ),
                        cursorBrush = SolidColor(brandColor),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        decorationBox = { inner ->
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "Message $agentName",
                                    fontSize = 15.sp,
                                    color = if (isDark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.35f)
                                )
                            }
                            inner()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .onFocusChanged { composerFocused = it.isFocused }
                            .padding(vertical = 12.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    val hasText = inputText.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (hasText) brandColor else (if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)))
                            .applePressable(0.90f, pressHaptic = true) {
                                if (hasText && !isThinking) {
                                    val textToSend = inputText.trim()
                                    inputText = ""
                                    client.sendMessage(textToSend)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = if (hasText) Color.White else (if (isDark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.35f)),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAttachSheet) {
        AttachmentSheet(
            client = client,
            isDark = isDark,
            onDismiss = { showAttachSheet = false },
            onPickImage = { pendingImage = it; showAttachSheet = false },
            onSendText = { text -> client.sendMessage(text); showAttachSheet = false }
        )
    }

    pendingImage?.let { image ->
        ImageConfirmSheet(
            image = image,
            client = client,
            isDark = isDark,
            brandColor = brandColor,
            caption = inputText,
            onDismiss = { pendingImage = null },
            onSend = { caption, bytes ->
                pendingImage = null
                inputText = ""
                client.sendMediaMessage(bytes, caption)
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Bubbles
// ---------------------------------------------------------------------------

@Composable
private fun UserBubble(msg: ChatMessage, isDark: Boolean, brandColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        val shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
        Surface(
            shape = shape,
            color = brandColor,
            shadowElevation = 3.dp,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .appleLiquidGlassOptics(
                    isDark = isDark,
                    chromaticGlow = Color.White.copy(alpha = 0.5f),
                    shape = shape
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = msg.text,
                    fontSize = 15.sp,
                    color = Color.White,
                    letterSpacing = (-0.1).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = msg.timestamp,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun AgentBubble(
    msg: ChatMessage,
    isDark: Boolean,
    brandColor: Color,
    agentIconRes: Int,
    agentName: String
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(brandColor.copy(alpha = if (isDark) 0.20f else 0.12f))
                .border(BorderStroke(0.8.dp, brandColor.copy(alpha = 0.35f)), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = agentIconRes),
                contentDescription = agentName,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        val shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
        Surface(
            shape = shape,
            color = if (isDark) Color(0xFF161822).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.94f),
            shadowElevation = 3.dp,
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(shape)
                .appleLiquidGlassOptics(isDark = isDark, chromaticGlow = brandColor, shape = shape)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = msg.text,
                    fontSize = 15.sp,
                    color = if (isDark) Color.White else Color(0xFF1D1D1F),
                    letterSpacing = (-0.1).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = msg.timestamp,
                    fontSize = 11.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.35f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pending image (confirm before send, annotate, or discard)
// ---------------------------------------------------------------------------

/** A picked image waiting for the user's confirmation. */
data class PendingImage(
    val bytes: ByteArray,
    val label: String,
    val width: Int = 0,
    val height: Int = 0
) {
    // Identity semantics: two picks of the same photo are still two events, and
    // the byte array must never be compared structurally in a snapshot check.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

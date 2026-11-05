package com.sarvesh173.talos.ui

import com.sarvesh173.talos.network.*

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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CallSplit
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
import kotlinx.coroutines.launch
import com.sarvesh173.talos.data.Role
import com.sarvesh173.talos.ui.agent.*
import com.sarvesh173.talos.ui.avatar.AgentAvatar
import com.sarvesh173.talos.ui.avatar.AgentAvatarEngine
import com.sarvesh173.talos.ui.avatar.AvatarState
import com.sarvesh173.talos.ui.avatar.AvatarIdentity
import com.sarvesh173.talos.ui.chat.FileTouchSummary
import com.sarvesh173.talos.ui.chat.MessageFooterView
import com.sarvesh173.talos.ui.chat.MessageStats
import com.sarvesh173.talos.ui.chat.ToolCallSummary
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.orb.OrbState
import com.sarvesh173.talos.ui.orb.rememberAdaptiveOrbSpeed
import com.sarvesh173.talos.ui.bot.AgentLogoMark
import com.sarvesh173.talos.ui.bot.AgentLogoState
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
 * - the model picker sits at the bottom beside the composer and stays hidden
 *   by default — it appears only while the composer text starts with '/' and
 *   hides again the moment the '/' is deleted;
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
    val codeBlock: String? = null,
    val stats: MessageStats? = null,
    val agentId: String = ""
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
    var showPromptLibrary by remember { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf<PendingImage?>(null) }
    var showTelemetry by remember { mutableStateOf(false) }
    var composerFocused by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var approvalMode by remember { mutableStateOf(AgentApprovalMode.AskOnce) }

    // Live room stream. The screen renders whatever the core actually sent;
    // it never fabricates an agent reply.
    val liveMessages by client.messages.collectAsState()
    val isThinking by client.isAgentTyping.collectAsState()
    val pendingApproval by client.pendingApproval.collectAsState()
    val pendingQuestion by client.pendingQuestion.collectAsState()

    val activeQuestion: AgentQuestion? = remember(pendingQuestion, pendingApproval, brandColor) {
        val q = pendingQuestion
        if (q != null) {
            AgentQuestion(
                id = q.id,
                prompt = q.prompt,
                detail = q.detail,
                command = q.command,
                agentName = q.agentName,
                kind = if (q.kind.equals("permission", ignoreCase = true)) QuestionKind.Permission else QuestionKind.Question,
                options = q.options.map { opt ->
                    QuestionOption(
                        label = opt.effectiveLabel,
                        value = opt.effectiveValue,
                        kind = when (opt.kind.lowercase()) {
                            "affirmative" -> OptionKind.Affirmative
                            "negative" -> OptionKind.Negative
                            else -> OptionKind.Neutral
                        }
                    )
                },
                allowCustom = q.allowCustom,
                timeoutSeconds = q.timeoutSeconds,
                onTimeout = when (q.onTimeout?.lowercase()) {
                    "skip" -> TimeoutBehaviour.Skip
                    "ask_again" -> TimeoutBehaviour.AskAgain
                    else -> TimeoutBehaviour.Deny
                },
                brandColor = brandColor
            )
        } else {
            pendingApproval?.let { r ->
                AgentQuestion(
                    id = r.id,
                    prompt = r.summary.ifBlank { "Permission requested" },
                    detail = "",
                    command = r.command,
                    agentName = r.toolName.ifBlank { "Agent" },
                    kind = QuestionKind.Permission,
                    options = emptyList(),
                    allowCustom = true,
                    timeoutSeconds = null,
                    onTimeout = TimeoutBehaviour.Deny,
                    brandColor = brandColor
                )
            }
        }
    }
    val rooms by client.rooms.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var selectedMessageForAction by remember { mutableStateOf<ChatMessage?>(null) }
    var isForking by remember { mutableStateOf(false) }

    // Slash commands stay hidden until the user actually types '/'.
    // The model palette + chip are the command surface; they activate only
    // while the composer text starts with '/' and hide again when it is deleted.
    val slashActive = inputText.startsWith("/")

    // The command surface is slash-gated, not focus-gated: typing '/' shows it
    // even though the field is focused, deleting it hides it again.
    LaunchedEffect(slashActive) {
        showModelPicker = slashActive
    }

    BackHandler {
        TalosHaptics.sendHaptic(context)
        onBack()
    }

    val messages = remember(liveMessages) {
        liveMessages.map { msg ->
            val stats = msg.metrics?.let { met ->
                MessageStats(
                    latencyMs = met.latency_ms,
                    ttftMs = met.ttft_ms,
                    promptTokens = met.prompt_tokens,
                    completionTokens = met.completion_tokens,
                    tokensPerSecond = met.tokens_per_second,
                    model = met.model,
                    reasoningTokens = met.reasoning_tokens,
                    costUsd = met.cost_usd,
                    toolCalls = msg.tool_records.map { tr ->
                        ToolCallSummary(
                            name = tr.name,
                            summary = tr.summary,
                            status = tr.status ?: "success",
                            durationMs = tr.ended_at?.let { end -> ((end - tr.started_at) * 1000).toInt() }
                        )
                    },
                    filesTouched = msg.files_touched.map { ft ->
                        FileTouchSummary(
                            path = ft.path,
                            kind = ft.kind,
                            additions = ft.additions,
                            deletions = ft.deletions,
                            diff = ft.diff,
                            mediaUrl = ft.media_url
                        )
                    }
                )
            }
            ChatMessage(
                id = msg.id,
                sender = if (msg.sender_role == Role.human) "user" else "agent",
                text = msg.content,
                timestamp = formatClock(msg.created_at),
                codeBlock = null,
                stats = stats,
                agentId = msg.sender_id
            )
        }
    }

    // The agent's internal reasoning for the turn in flight. The core streams it
    // as `thought_delta`; if the provider sends no reasoning the field is empty
    // and the strip simply shows the orb and "working".
    val liveThought = remember(liveMessages, isThinking) {
        liveMessages.lastOrNull { it.thought?.isNotBlank() == true }?.thought.orEmpty()
    }

    // Orb speed tracks how much work is actually landing right now: a burst of
    // tokens spins it up, silence settles it down. Characters are used because
    // they change continuously during streaming.
    val workUnits = liveMessages.sumOf { it.content.length } + liveThought.length
    val orbSpeed = rememberAdaptiveOrbSpeed(chunkCount = workUnits)

    // Which pattern to show follows what the agent is doing. Searching reads
    // differently from writing, so the orb is not one animation for all work.
    val orbState = when {
        !isThinking -> OrbState.SOLVING
        liveThought.contains("search", true) -> OrbState.SEARCHING
        else -> OrbState.WORKING
    }

    LaunchedEffect(sessionId) {
        // An empty id means "no session chosen yet". Mint a real one locally
        // rather than inventing a plausible-looking constant that never existed
        // on the host — the server would treat it as a live session.
        val resolved = sessionId.ifBlank {
            "ses_" + java.util.UUID.randomUUID().toString().replace("-", "").take(24)
        }
        if (client.isPaired.value) client.switchRoom(resolved)
    }

    // Past sessions: the core's rooms, with the local cache count so a room
    // that exists but has no history says so instead of looking empty.
    val sessionSummaries = remember(rooms) {
        rooms.map { room ->
            SessionSummary(
                id = room.id,
                title = room.name.ifBlank { room.id },
                relativeTime = "Room",
                messageCount = 0
            )
        }
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
                    UserBubble(msg, isDark, brandColor, onLongClick = { selectedMessageForAction = msg })
                } else {
                    AgentBubble(
                        msg = msg,
                        isDark = isDark,
                        brandColor = brandColor,
                        agentIconRes = agentIconRes,
                        agentName = agentName,
                        onDirectiveReply = { reply -> client.sendMessage(reply) },
                        onLongClick = { selectedMessageForAction = msg }
                    )
                }
            }

            if (isThinking) {
                item(key = "thinking_orb") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 36.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // The agent's own mark is the loading animation, not a
                        // generic orb: OpenCode's logo for OpenCode, Hermes' for
                        // Hermes. Derived from the display name so the mark and
                        // the header can never disagree about who is working.
                        AgentLogoMark(
                            agentId = agentName.lowercase().replace(" ", ""),
                            state = AgentLogoState.Thinking,
                            size = 28.dp,
                            intensity = orbSpeed,
                            paused = !isThinking
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$agentName is working",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.60f)
                            )
                            if (liveThought.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = liveThought.takeLast(220),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = if (isDark) Color.White.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.40f),
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
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

                // Past sessions.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .applePressable(0.92f, pressHaptic = true) { showHistory = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TalosIcons.TalosStorage,
                        contentDescription = "Past sessions",
                        tint = if (isDark) Color.White.copy(alpha = 0.80f) else Color(0xFF1D1D1F),
                        modifier = Modifier.size(20.dp)
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

        // ---------------- approval & question overlay ----------------
        // Sits above everything except the dock: a blocked agent is the most
        // important thing on the screen while it lasts.
        activeQuestion?.let { q ->
            AgentQuestionOverlay(
                question = q,
                isDark = isDark,
                onAnswer = { answer ->
                    when (answer) {
                        is Answer.Granted -> scope.launch {
                            client.resolveApproval(answer.id, true, answer.scope.name.lowercase())
                        }
                        is Answer.Denied -> scope.launch {
                            client.resolveApproval(answer.id, false, if (answer.remember) "session" else "once")
                        }
                        is Answer.Choice -> scope.launch {
                            client.answerQuestion(answer.id, answer.label, answer.value)
                        }
                        is Answer.Custom -> scope.launch {
                            if (q.kind == QuestionKind.Permission) {
                                client.resolveApproval(answer.id, true, answer.scope?.name?.lowercase() ?: "once")
                            } else {
                                client.answerQuestion(answer.id, answer.text)
                            }
                        }
                        is Answer.Skipped -> scope.launch {
                            client.answerQuestion(answer.id, "skip")
                        }
                        is Answer.Expired -> scope.launch {
                            if (q.kind == QuestionKind.Permission) {
                                client.resolveApproval(answer.id, false, "once")
                            } else {
                                client.answerQuestion(answer.id, "expired")
                            }
                        }
                        is Answer.NeedsAttention -> { /* re-prompt or maintain state */ }
                    }
                },
                onDismiss = {
                    scope.launch {
                        if (q.kind == QuestionKind.Permission) {
                            client.resolveApproval(q.id, false, "once")
                        } else {
                            client.answerQuestion(q.id, "dismissed")
                        }
                    }
                }
            )
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
            // Slash-gated command surface: visible only while the composer
            // text starts with '/'. Deleting the '/' hides it again.
            AnimatedVisibility(
                visible = showModelPicker && slashActive,
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

            if (showModelPicker && slashActive) {
                Spacer(modifier = Modifier.height(6.dp))
            }

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

                    // Model chip: hidden by default. Only appears as part of
                    // the slash command surface when the composer starts with '/'.
                    if (slashActive) {
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
                    }

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

    if (showHistory) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.navigationBarsPadding().padding(12.dp)) {
                SessionHistorySheet(
                    sessions = sessionSummaries,
                    isDark = isDark,
                    brandColor = brandColor,
                    currentId = sessionId,
                    onOpen = { s ->
                        showHistory = false
                        if (client.isPaired.value) client.switchRoom(s.id)
                    },
                    onDismiss = { showHistory = false }
                )
            }
        }
    }

    if (showAttachSheet) {
        AttachmentSheet(
            client = client,
            isDark = isDark,
            onDismiss = { showAttachSheet = false },
            onPickImage = { pendingImage = it; showAttachSheet = false },
            onSendText = { text -> client.sendMessage(text); showAttachSheet = false },
            onOpenPrompts = { showPromptLibrary = true }
        )
    }

    if (showPromptLibrary) {
        PromptLibrarySheet(
            client = client,
            isDark = isDark,
            brandColor = brandColor,
            onDismiss = { showPromptLibrary = false },
            onSelectPrompt = { promptText ->
                inputText = promptText
                showPromptLibrary = false
            }
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

    // Message context menu dialog (Fork, Copy, Prompt Library)
    selectedMessageForAction?.let { targetMsg ->
        AlertDialog(
            onDismissRequest = { if (!isForking) selectedMessageForAction = null },
            title = {
                Text(
                    text = "Message Actions",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else Color(0xFF1D1D1F)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = targetMsg.text.take(80) + if (targetMsg.text.length > 80) "..." else "",
                        fontSize = 13.sp,
                        color = talosTextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // 1. Branch / Fork conversation
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .applePressable(0.96f) {
                                if (!isForking) {
                                    scope.launch {
                                        isForking = true
                                        val activeRoom = sessionId.ifBlank { client.currentRoomId.value }
                                        val title = "Fork: ${targetMsg.text.take(20)}"
                                        val result = client.forkRoom(activeRoom, targetMsg.id, title)
                                        isForking = false
                                        selectedMessageForAction = null
                                        if (result?.ok == true && result.room_id != null) {
                                            client.switchRoom(result.room_id)
                                        }
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallSplit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isForking) "Forking conversation..." else "Fork from here",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDark) Color.White else Color(0xFF1D1D1F)
                                )
                                Text(
                                    text = "Branch into a new thread from this point",
                                    fontSize = 11.sp,
                                    color = talosTextMuted
                                )
                            }
                        }
                    }

                    // 2. Copy message text
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .applePressable(0.96f) {
                                clipboardManager.setText(AnnotatedString(targetMsg.text))
                                selectedMessageForAction = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF1D1D1F),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Copy text",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) Color.White else Color(0xFF1D1D1F)
                            )
                        }
                    }

                    // 3. Save as reusable prompt
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .applePressable(0.96f) {
                                scope.launch {
                                    client.createPrompt(
                                        title = targetMsg.text.take(30),
                                        body = targetMsg.text
                                    )
                                    selectedMessageForAction = null
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF1D1D1F),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Save as reusable prompt",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDark) Color.White else Color(0xFF1D1D1F)
                                )
                                Text(
                                    text = "Add to your prompt library",
                                    fontSize = 11.sp,
                                    color = talosTextMuted
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { if (!isForking) selectedMessageForAction = null }
                ) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

}

// ---------------------------------------------------------------------------
// Bubbles
// ---------------------------------------------------------------------------

@Composable
private fun UserBubble(
    msg: ChatMessage,
    isDark: Boolean,
    brandColor: Color,
    onLongClick: () -> Unit = {}
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        val shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
        Surface(
            shape = shape,
            color = brandColor,
            shadowElevation = 3.dp,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .pointerInput(msg.id) {
                    detectTapGestures(onLongPress = { onLongClick() })
                }
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
    agentName: String,
    onDirectiveReply: (String) -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    // SYSTEM.md rich directives: prose stays in the bubble, :::talos blocks
    // render as full-width interactive cards below it. Interactive answers
    // go back as the next user turn via onDirectiveReply.
    val parsed = remember(msg.id, msg.text) { TalosDirectiveParser.parse(msg.text) }
    val hasDirectives = parsed.directives.isNotEmpty()
    Column(modifier = Modifier.fillMaxWidth()) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        val avatarEngine = remember(msg.agentId) {
            AgentAvatarEngine(AvatarIdentity.of(msg.agentId.ifBlank { "agent" }, agentName))
        }
        // The avatar is the agent's identity mark, not a spinner: it settles into
        // a finished/settled expression once the turn has landed, and speeds up
        // its breathing when the turn was produced fast.
        LaunchedEffect(msg.id, msg.stats) {
            avatarEngine.setState(
                when {
                    msg.stats == null -> AvatarState.Idle
                    else -> AvatarState.Happy
                }
            )
            msg.stats?.tokensPerSecond?.let { avatarEngine.setTempoFromRate(it) }
        }
        AgentAvatar(
            engine = avatarEngine,
            size = 32.dp,
            isDark = isDark
        )

        Spacer(modifier = Modifier.width(8.dp))

        val shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
        Surface(
            shape = shape,
            color = if (isDark) Color(0xFF161822).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.94f),
            shadowElevation = 3.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .appleLiquidGlassOptics(isDark = isDark, chromaticGlow = brandColor, shape = shape)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                val bubbleText = if (hasDirectives) parsed.prose else msg.text
                if (bubbleText.isNotBlank()) {
                Text(
                    text = bubbleText,
                    fontSize = 15.sp,
                    color = if (isDark) Color.White else Color(0xFF1D1D1F),
                    letterSpacing = (-0.1).sp
                )
                }
                msg.stats?.let { stats ->
                    Spacer(modifier = Modifier.height(6.dp))
                    MessageFooterView(
                        stats = stats,
                        isDark = isDark,
                        accent = brandColor
                    )
                }
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
    if (hasDirectives) {
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            parsed.directives.forEach { directive ->
                TalosDirectiveHost(
                    directive = directive,
                    isDark = isDark,
                    brandColor = brandColor,
                    onReply = onDirectiveReply
                )
            }
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

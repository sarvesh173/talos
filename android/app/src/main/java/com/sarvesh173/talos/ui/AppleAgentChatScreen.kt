package com.sarvesh173.talos.ui

import com.sarvesh173.talos.network.*
import com.sarvesh173.talos.data.Message
import com.sarvesh173.talos.ui.chat.fetchRemoteMessages
import com.sarvesh173.talos.ui.chat.fetchRemoteSessions
import com.sarvesh173.talos.ui.chat.isRemoteSessionId
import com.sarvesh173.talos.ui.chat.sendRemotePrompt

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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.delay
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
    val agentId: String = "",
    /**
     * True when the text is a canned template the core fell back to, not
     * something a model wrote. Carried from the wire so the bubble can say so
     * instead of passing canned prose off as an answer.
     */
    val simulated: Boolean = false,
    /** Why the fallback happened, when it did ("model unreachable", ...). */
    val simulatedReason: String? = null
)

/** Clock label for a core timestamp (epoch seconds) in 12-hour format with AM/PM. */
private fun formatClock(epochSeconds: Double): String {
    if (epochSeconds <= 0.0) return ""
    return SimpleDateFormat("h:mm a", Locale.getDefault())
        .format(Date((epochSeconds * 1000).toLong()))
}

/**
 * The label for a reply the core produced without a model.
 *
 * When no provider answers, the orchestrator falls back to a canned template
 * so the room keeps moving. That text is indistinguishable from a real answer
 * on screen, so the bubble says what it is. The reason the core gave
 * ("model unreachable", "live models disabled") rides along in parentheses.
 */
private fun simulatedLabel(reason: String?): String {
    val base = "Simulated — no model was reached"
    val why = reason?.trim().orEmpty()
    return if (why.isEmpty()) base else "$base ($why)"
}

@Composable
fun AppleAgentChatScreen(
    client: TalosClient,
    sessionId: String,
    remoteSession: Boolean = false,
    sessionTitle: String,
    agentName: String,
    agentIconRes: Int,
    brandColor: Color,
    isDark: Boolean = true,
    onBack: () -> Unit
) {
    // The dot was green whenever the agent was not mid-thought, with no regard
    // for whether the socket was up: a false "all good" on a dead connection.
    val isConnected by client.isConnected.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf<String?>(null) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showPromptLibrary by remember { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf<PendingImage?>(null) }
    var showTelemetry by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // Live room stream. The screen renders whatever the core actually sent;
    // it never fabricates an agent reply.
    val liveMessages by client.messages.collectAsState()

    // An OpenCode session is not a room: its history is fetched, not streamed.
    // `sessionId` used to be only a room key, which is why opening a session
    // showed that agent's generic room and none of its conversation.
    var activeSessionId by remember(sessionId) { mutableStateOf(sessionId) }
    var activeSessionTitle by remember(sessionTitle) { mutableStateOf(sessionTitle) }
    val remoteMode = remoteSession || isRemoteSessionId(activeSessionId)
    var remoteMessages by remember(activeSessionId) { mutableStateOf<List<Message>>(emptyList()) }
    var remoteSessions by remember(remoteMode) { mutableStateOf<List<SessionSummary>>(emptyList()) }
    var remoteBusy by remember(activeSessionId) { mutableStateOf(false) }
    var remoteError by remember(activeSessionId) { mutableStateOf<String?>(null) }
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

    val sourceMessages = if (remoteMode) remoteMessages else liveMessages
    val messages = remember(sourceMessages) {
        sourceMessages.map { msg ->
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
                agentId = msg.sender_id,
                simulated = if (remoteMode) false else msg.simulated,
                simulatedReason = msg.simulatedReason
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

    // A remote session's history is fetched once on open. Failures are shown
    // rather than swallowed: an empty list here looks identical to a real
    // conversation with nothing in it.
    LaunchedEffect(activeSessionId, remoteMode) {
        if (!remoteMode) return@LaunchedEffect
        remoteError = null
        remoteMessages = runCatching { fetchRemoteMessages(client, activeSessionId) }
            .fold({ it }, { error -> emptyList<Message>().also { remoteError = error.message ?: "Could not load this session." } })
    }

    LaunchedEffect(remoteMode) {
        if (remoteMode) {
            remoteSessions = fetchRemoteSessions(client)
        }
    }

    LaunchedEffect(activeSessionId) {
        // An empty id means "no session chosen yet". Mint a real one locally
        // rather than inventing a plausible-looking constant that never existed
        // on the host — the server would treat it as a live session.
        val resolved = activeSessionId.ifBlank {
            "ses_" + java.util.UUID.randomUUID().toString().replace("-", "").take(24)
        }
        if (!remoteMode && client.isPaired.value) client.switchRoom(resolved)
    }

    // A remote session is driven from the host: another agent can post into it
    // and the phone has to catch up on its own. Poll while the screen is open so
    // a conversation started elsewhere shows up here without a restart.
    LaunchedEffect(activeSessionId, remoteMode) {
        if (!remoteMode) return@LaunchedEffect
        while (true) {
            delay(2_500)
            val fresh = runCatching { fetchRemoteMessages(client, activeSessionId) }.getOrNull() ?: continue
            if (fresh.size != remoteMessages.size) {
                remoteMessages = fresh
                remoteError = null
            }
        }
    }

    // Past sessions: OpenCode sessions when remote, or TALOS rooms when local.
    val sessionSummaries = remember(remoteMode, remoteSessions, rooms) {
        if (remoteMode && remoteSessions.isNotEmpty()) {
            remoteSessions
        } else {
            rooms.map { room ->
                SessionSummary(
                    id = room.id,
                    title = room.name.ifBlank { room.id },
                    relativeTime = "Room",
                    messageCount = 0
                )
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val backgroundColor = if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF)

    // Real glass for the chrome only: the backdrop is the scrolling list, so
    // the bars sample what is actually behind them. On API 31+ this is a true
    // blur; below that Haze degrades to a tint, which is exactly what a bar
    // should do anyway. Bubbles never opt in — they are content, not chrome.
    val hazeState = remember { HazeState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // ---------------- message stream ----------------
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState)
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 116.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = messages,
                key = { it.id },
                // Without contentType Compose cannot reuse composition slots
                // per message type, so every scroll allocates fresh bubble
                // nodes. jetpack-compose-performance §3 makes both mandatory.
                contentType = { it.sender }
            ) { msg ->
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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

        // ---------------- top bar (real glass: samples the list under it) ----------------
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .talosGlassSurface(
                    hazeState = hazeState,
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.55f)
                                      else Color.White.copy(alpha = 0.70f),
                    blurRadius = 28.dp
                )
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
                                .background(
                                    when {
                                        !isConnected -> StatusRed
                                        isThinking -> StatusAmber
                                        else -> StatusGreen
                                    }
                                )
                        )
                    }
                    Text(
                        text = activeSessionTitle,
                        fontSize = 11.sp,
                        color = if (isDark) Color.White.copy(alpha = 0.50f) else Color.Black.copy(alpha = 0.45f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // One menu holds everything that used to be three separate buttons.
                // On a 360dp phone the top bar is the most expensive row on the
                // screen: every 48dp key here is 13% of the width. Sessions and
                // telemetry are both "sometimes" actions, so they live behind
                // one affordance instead of two.
                Box {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .applePressable(0.92f, pressHaptic = true) { menuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "More",
                            tint = if (menuOpen || showTelemetry) brandColor
                                   else if (isDark) Color.White.copy(alpha = 0.80f) else Color(0xFF1D1D1F),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        modifier = Modifier.background(
                            if (isDark) Color(0xFF1C1C1E) else Color.White,
                            RoundedCornerShape(14.dp)
                        )
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Past sessions",
                                    fontSize = 14.sp,
                                    color = if (isDark) Color.White else Color(0xFF1D1D1F)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = TalosIcons.TalosStorage,
                                    contentDescription = null,
                                    tint = if (isDark) Color.White.copy(alpha = 0.80f) else Color(0xFF1D1D1F),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = { menuOpen = false; showHistory = true }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (showTelemetry) "Hide agent status" else "Agent status",
                                    fontSize = 14.sp,
                                    color = if (isDark) Color.White else Color(0xFF1D1D1F)
                                )
                            },
                            onClick = { menuOpen = false; showTelemetry = !showTelemetry }
                        )
                    }
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
                Spacer(modifier = Modifier.height(8.dp))
            }

            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.Transparent,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .talosGlassSurface(
                        hazeState = hazeState,
                        shape = RoundedCornerShape(26.dp),
                        backgroundColor = if (isDark) Color(0xFF161820).copy(alpha = 0.62f)
                                          else Color.White.copy(alpha = 0.78f),
                        blurRadius = 30.dp
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
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

                    Spacer(modifier = Modifier.width(8.dp))

                    // Model chip: hidden by default. Only appears as part of
                    // the slash command surface when the composer starts with '/'.
                    if (slashActive) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(brandColor.copy(alpha = 0.14f))
                                .applePressable(0.94f, pressHaptic = true) { showModelPicker = !showModelPicker }
                                .padding(horizontal = 12.dp),
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

                        Spacer(modifier = Modifier.width(8.dp))
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
                            .padding(vertical = 12.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val hasText = inputText.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (hasText) brandColor else (if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)))
                            .applePressable(0.90f, pressHaptic = true) {
                                if (hasText && !(if (remoteMode) remoteBusy else isThinking)) {
                                    val textToSend = inputText.trim()
                                    inputText = ""
                                    if (remoteMode) {
                                        // Optimistic: the turn appears at once,
                                        // then the reply lands when it lands.
                                        remoteBusy = true
                                        remoteError = null
                                        remoteMessages = remoteMessages + Message(
                                            id = "local-" + System.currentTimeMillis(),
                                            room_id = activeSessionId,
                                            sender_id = "user",
                                            sender_name = "You",
                                            sender_role = Role.human,
                                            content = textToSend,
                                            created_at = System.currentTimeMillis() / 1000.0,
                                        )
                                        scope.launch {
                                            // One throw here used to kill the coroutine and leave
                                            // the composer stuck busy forever.
                                            val reply = runCatching {
                                                sendRemotePrompt(client, activeSessionId, textToSend)
                                            }.getOrElse { error ->
                                                "Could not send: " + (error.message ?: error::class.java.simpleName)
                                            }
                                            remoteMessages = runCatching {
                                                fetchRemoteMessages(client, activeSessionId)
                                            }.getOrElse { remoteMessages }
                                            if (reply.isBlank()) remoteError = "The agent sent nothing back."
                                            else if (reply.startsWith("Could not send")) remoteError = reply
                                            remoteBusy = false
                                        }
                                    } else {
                                        client.sendMessage(textToSend)
                                    }
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
                    currentId = activeSessionId,
                    onOpen = { s ->
                        showHistory = false
                        activeSessionId = s.id
                        activeSessionTitle = s.title
                        if (!remoteMode && client.isPaired.value) client.switchRoom(s.id)
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
                    text = "Message actions",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else Color(0xFF1D1D1F)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
    if (msg.text.isBlank() || msg.text == "null") return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        val shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
        // A bubble is content, not chrome: it sits on a flat backdrop with
        // nothing to refract, so translucency is decoration — and on API 30
        // (no backdrop blur) it just lets the list behind bleed through.
        // Solid fill, no rim, no sheen.
        Surface(
            shape = shape,
            color = brandColor,
            shadowElevation = 0.dp,
            modifier = Modifier
                .widthIn(max = 290.dp)
                .pointerInput(msg.id) {
                    detectTapGestures(onLongPress = { onLongClick() })
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = msg.text,
                    fontSize = 15.sp,
                    // The accent is user-chosen and can be any colour, including
                    // bright yellow (1.43:1) or cyan (1.30:1) against white.
                    // Pick the ink by measured luminance, not by habit.
                    color = onAccent(brandColor),
                    letterSpacing = (-0.1).sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (msg.timestamp.isNotBlank()) {
                    Text(
                        text = msg.timestamp,
                        fontSize = 10.sp,
                        color = onAccent(brandColor).copy(alpha = 0.70f),
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
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
    val parsed = remember(msg.id, msg.text) { TalosDirectiveParser.parse(msg.text) }
    val hasDirectives = parsed.directives.isNotEmpty()
    val bubbleText = if (hasDirectives) parsed.prose else msg.text
    if (bubbleText.isBlank() && !hasDirectives) return
    if (bubbleText == "null" && !hasDirectives) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            // Crisp agent SVG icon in Apple rounded squircle container
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

            val shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
            // Same reasoning as the user bubble: content sits on a flat
            // backdrop, so it gets a solid tonal fill. The agent's identity is
            // carried by the avatar mark beside it, not by a glass treatment.
            Surface(
                shape = shape,
                color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7),
                shadowElevation = 0.dp,
                modifier = Modifier.widthIn(max = 290.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (bubbleText.isNotBlank() && bubbleText != "null") {
                            Text(
                                text = bubbleText,
                                fontSize = 15.sp,
                                color = if (isDark) Color.White else Color(0xFF1D1D1F),
                                letterSpacing = (-0.1).sp,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        if (msg.timestamp.isNotBlank() && !msg.simulated && msg.stats == null) {
                            Text(
                                text = msg.timestamp,
                                fontSize = 10.sp,
                                color = if (isDark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.35f),
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }
                    }
                    if (msg.simulated) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(StatusAmber)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = simulatedLabel(msg.simulatedReason),
                                fontSize = 11.sp,
                                color = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.50f)
                            )
                        }
                    }
                    msg.stats?.let { stats ->
                        Spacer(modifier = Modifier.height(4.dp))
                        MessageFooterView(
                            stats = stats,
                            isDark = isDark,
                            accent = brandColor
                        )
                    }
                    if (msg.timestamp.isNotBlank() && (msg.simulated || msg.stats != null)) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = msg.timestamp,
                            fontSize = 10.sp,
                            color = if (isDark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.35f),
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }
        if (hasDirectives) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 36.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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

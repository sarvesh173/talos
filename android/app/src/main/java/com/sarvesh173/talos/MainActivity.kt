package com.sarvesh173.talos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.sarvesh173.talos.data.Message
import com.sarvesh173.talos.data.Room
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var client: TalosClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        client = TalosClient(this, lifecycleScope)
        if (client.isPaired.value) {
            client.refreshRooms()
            client.switchRoom("the-council")
        }

        setContent {
            var themeMode by remember { mutableStateOf(AppThemeMode.DARK) }
            var appScreen by remember { mutableStateOf("landing") }

            TalosTheme(themeMode = themeMode) {
                val isPaired by client.isPaired.collectAsState()

                val activeTarget = when (appScreen) {
                    "landing" -> "landing"
                    "wizard" -> "wizard"
                    else -> if (isPaired) "main" else "landing"
                }

                AnimatedContent(
                    targetState = activeTarget,
                    transitionSpec = {
                        if (targetState == "main" || targetState == "wizard") {
                            (fadeIn(animationSpec = tween(240)) + slideInHorizontally(
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                                initialOffsetX = { it / 4 }
                            )).togetherWith(
                                fadeOut(animationSpec = tween(180)) + slideOutHorizontally(
                                    animationSpec = tween(180),
                                    targetOffsetX = { -it / 4 }
                                )
                            )
                        } else {
                            (fadeIn(animationSpec = tween(220)) + slideInHorizontally(
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                                initialOffsetX = { -it / 4 }
                            )).togetherWith(
                                fadeOut(animationSpec = tween(180)) + slideOutHorizontally(
                                    animationSpec = tween(180),
                                    targetOffsetX = { it / 4 }
                                )
                            )
                        }
                    },
                    label = "AppScreenTransition"
                ) { screenKey ->
                    when (screenKey) {
                        "main" -> {
                            MainScreen(
                                client = client,
                                isDark = themeMode == AppThemeMode.DARK,
                                onToggleTheme = {
                                    themeMode = if (themeMode == AppThemeMode.DARK) AppThemeMode.LIGHT else AppThemeMode.DARK
                                },
                                onOpenLanding = {
                                    appScreen = "landing"
                                }
                            )
                        }
                        "wizard" -> {
                            OnboardingWizard(
                                client = client,
                                onToggleTheme = {
                                    themeMode = if (themeMode == AppThemeMode.DARK) AppThemeMode.LIGHT else AppThemeMode.DARK
                                },
                                onComplete = {
                                    client.refreshRooms()
                                    client.switchRoom("the-council")
                                    appScreen = "main"
                                }
                            )
                        }
                        else -> {
                            LandingScreen(
                                client = client,
                                isDark = themeMode == AppThemeMode.DARK,
                                onToggleTheme = {
                                    themeMode = if (themeMode == AppThemeMode.DARK) AppThemeMode.LIGHT else AppThemeMode.DARK
                                },
                                onGetStarted = {
                                    appScreen = if (client.isPaired.value) "main" else "wizard"
                                },
                                onOpenWizard = {
                                    appScreen = "wizard"
                                },
                                onConnect = { url, token ->
                                    client.updateConfig(url, token)
                                    appScreen = "main"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    client: TalosClient,
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onOpenLanding: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val rooms by client.rooms.collectAsState()
    val currentRoomId by client.currentRoomId.collectAsState()
    val messages by client.messages.collectAsState()
    val isConnected by client.isConnected.collectAsState()
    val isTyping by client.isAgentTyping.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val activeRoom = rooms.firstOrNull { it.id == currentRoomId }
    val roomTitle = activeRoom?.name ?: "TALOS"

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = talosSurface,
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TALOS Chambers",
                        color = talosTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Quick Navigation back to Developer Console & Fleet Hub
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaryAccent.copy(alpha = 0.10f))
                        .clickable {
                            scope.launch { drawerState.close() }
                            onOpenLanding()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dashboard,
                        contentDescription = null,
                        tint = PrimaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Fleet Hub & Agent Bench",
                        color = talosTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                HorizontalDivider(color = talosBorder, modifier = Modifier.padding(top = 8.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    items(rooms) { room: Room ->
                        val isSelected = room.id == currentRoomId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) PrimaryAccent.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable {
                                    client.switchRoom(room.id)
                                    scope.launch { drawerState.close() }
                                }
                                .padding(12.dp)
                        ) {
                            val roomEmblem = when {
                                room.id == "core-dm" || room.mode == "dm" -> emblemByKey("hermes")
                                room.id == "the-council" -> emblemByKey("omnirush")
                                else -> emblemBySender(room.name)
                            }
                            AgentEmblemIcon(
                                emblem = roomEmblem,
                                size = 20.dp,
                                tint = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = room.name,
                                    color = if (isSelected) PrimaryAccent else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = room.description,
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.background(talosBg)) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = talosBg,
                            titleContentColor = TextPrimary
                        ),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val roomEmblem = activeRoom?.let { room ->
                                    when {
                                        room.id == "core-dm" || room.mode == "dm" -> emblemByKey("hermes")
                                        room.id == "the-council" -> emblemByKey("omnirush")
                                        else -> emblemBySender(room.name)
                                    }
                                }
                                if (roomEmblem != null) {
                                    AgentEmblemIcon(
                                        emblem = roomEmblem,
                                        size = 20.dp,
                                        tint = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = roomTitle,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isConnected) StatusGreen else Color(0xFFEF4444))
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.semantics { contentDescription = "Open chamber navigation menu" }
                            ) {
                                Icon(imageVector = Icons.Default.Menu, contentDescription = null, tint = talosTextPrimary)
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = onOpenLanding,
                                modifier = Modifier.semantics {
                                    contentDescription = "Switch to Developer Fleet Hub"
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dashboard,
                                    contentDescription = null,
                                    tint = talosTextMuted
                                )
                            }
                            IconButton(
                                onClick = onToggleTheme,
                                modifier = Modifier.semantics {
                                    contentDescription = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode"
                                }
                            ) {
                                Icon(
                                    imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = null,
                                    tint = talosTextMuted
                                )
                            }
                            IconButton(
                                onClick = { showSettingsDialog = true },
                                modifier = Modifier.semantics { contentDescription = "Open server connection settings" }
                            ) {
                                Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = talosTextMuted)
                            }
                        }
                    )

                    // Horizontal Chamber Quick Switcher Rail
                    if (rooms.size > 1) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(rooms) { room: Room ->
                                val isSelected = room.id == currentRoomId
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) PrimaryAccent.copy(alpha = 0.2f) else talosSurface)
                                        .border(
                                            1.dp,
                                            if (isSelected) PrimaryAccent else SurfaceBorder,
                                            RoundedCornerShape(20.dp)
                                        )
                                        .clickable { client.switchRoom(room.id) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    val quickEmblem = when {
                                        room.id == "core-dm" || room.mode == "dm" -> emblemByKey("hermes")
                                        room.id == "the-council" -> emblemByKey("omnirush")
                                        else -> emblemBySender(room.name)
                                    }
                                    AgentEmblemIcon(
                                        emblem = quickEmblem,
                                        size = 14.dp,
                                        tint = null
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = room.name,
                                        color = if (isSelected) PrimaryAccent else TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = talosBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Column {
                        if (isTyping) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                ThreeDotTypingIndicator(
                                    dotColor = PrimaryAccent,
                                    dotSize = 6.dp,
                                    dotSpacing = 4.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Agent fleet is deliberating...",
                                    color = PrimaryAccent,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text(text = "Message $roomTitle...", color = TextMuted, fontSize = 14.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = talosSurface,
                                    unfocusedContainerColor = talosSurface,
                                    focusedBorderColor = PrimaryAccent,
                                    unfocusedBorderColor = SurfaceBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics {
                                        contentDescription = "Message composition input field for $roomTitle"
                                    }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            val sendInteraction = remember { MutableInteractionSource() }
                            val isSendPressed by sendInteraction.collectIsPressedAsState()
                            val sendScale by animateFloatAsState(
                                targetValue = if (isSendPressed) 0.90f else 1.0f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
                                label = "sendScale"
                            )

                            IconButton(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        client.sendMessage(inputText)
                                        inputText = ""
                                    }
                                },
                                interactionSource = sendInteraction,
                                modifier = Modifier
                                    .size(48.dp)
                                    .scale(sendScale)
                                    .clip(CircleShape)
                                    .background(PrimaryAccent)
                                    .semantics {
                                        contentDescription = "Send message to chamber"
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            containerColor = talosBg
        ) { paddingValues ->
            if (messages.isEmpty()) {
                ChamberHero(
                    room = activeRoom,
                    onPromptClick = { prompt -> inputText = prompt },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    items(messages) { msg: Message ->
                        MessageBubble(msg = msg)
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            client = client,
            onDismiss = { showSettingsDialog = false },
            onSave = { newUrl, newToken ->
                client.updateConfig(newUrl, newToken)
                showSettingsDialog = false
            }
        )
    }
}

@Composable
fun SettingsDialog(
    client: TalosClient,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var urlText by remember { mutableStateOf(client.baseUrl) }
    var tokenText by remember { mutableStateOf(client.apiToken) }
    var isGenerating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = talosSurface,
        title = {
            Text(text = "Endpoint & Token Settings", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column {
                Text(
                    text = "Tailscale Host URL:",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = talosBg,
                        unfocusedContainerColor = talosBg,
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Bot API Token:",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = tokenText,
                    onValueChange = { tokenText = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = talosBg,
                        unfocusedContainerColor = talosBg,
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isGenerating = true
                            client.updateConfig(urlText, "")
                            val genToken = client.generateQuickToken()
                            if (genToken != null) {
                                tokenText = genToken
                            }
                            isGenerating = false
                        }
                    },
                    border = BorderStroke(1.dp, PrimaryAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = PrimaryAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGenerating) "Generating..." else "Generate Additional Token",
                        color = PrimaryAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(urlText, tokenText) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
            ) {
                Text(text = "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
fun ChamberHero(
    room: Room?,
    onPromptClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDark.current
    val boxBg = if (isDark) Color(0xFF161F2E) else Color(0xFFF1F5F9)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Active Chamber Title with Live Mesh Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(StatusGreen)
            )
            Text(
                text = room?.name ?: "TALOS Chamber",
                color = talosTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = room?.description ?: "Multi-agent autonomous collaborative bus via Tailscale encrypted subnet.",
            color = talosTextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Participating Agent Roster Dock
        Surface(
            color = talosSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, talosBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Participating Fleet",
                        color = talosTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${AgentEmblems.size} Registered",
                        color = talosTextMuted,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AgentEmblems, key = { it.key }) { emblem ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(boxBg)
                                .border(1.dp, talosBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AgentEmblemIcon(emblem = emblem, size = 18.dp, tint = null)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Slash Command Action Grid (Raycast Mobile Style)
        val commands = listOf(
            Triple("/consensus", "Propose democratic vote across registered agents", Icons.Default.Groups),
            Triple("/benchmark", "Ping AI providers and recalculate latency route", Icons.Default.Speed),
            Triple("/mesh", "Scan Tailscale peers on 100.75.107.79 subnet", Icons.Default.Hub)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((cmd, desc, icon) in commands) {
                Surface(
                    color = talosSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, talosBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { onPromptClick(cmd) }
                        .semantics { contentDescription = "Execute command $cmd: $desc" }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(PrimaryAccent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = PrimaryAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cmd,
                                color = talosTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Text(
                                text = desc,
                                color = talosTextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

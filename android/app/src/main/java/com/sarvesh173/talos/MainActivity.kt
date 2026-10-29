package com.sarvesh173.talos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.lifecycleScope
import com.sarvesh173.talos.data.Message
import com.sarvesh173.talos.data.Room
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.core.feature.FeatureCatalog
import com.sarvesh173.talos.core.feature.FeatureScope
import com.sarvesh173.talos.core.motion.MotionTokens
import com.sarvesh173.talos.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var client: TalosClient

    /**
     * Accept a pairing link from the agent or Chrome (link or QR) and store the
     * endpoint + device token. Supports both talos://pair and http://.../pair
     */
    private fun handlePairingIntent(intent: Intent?) {
        val data = intent?.data ?: return
        var url: String? = null
        var token: String? = null

        if (data.scheme == "talos" && data.host == "pair") {
            url = data.getQueryParameter("url")?.trim()?.trimEnd('/')
                ?: data.getQueryParameter("endpoint")?.trim()?.trimEnd('/')
            token = data.getQueryParameter("token")?.trim()
        } else if (data.scheme == "http" || data.scheme == "https") {
            val port = if (data.port != -1) ":${data.port}" else ""
            url = "${data.scheme}://${data.host}$port"
            token = data.getQueryParameter("token")?.trim()
        }

        if (url.isNullOrBlank() || !url.startsWith("http")) return
        client.updateConfig(url, token ?: "")
        lifecycleScope.launch(Dispatchers.IO) {
            client.pairDevice()
            withContext(Dispatchers.Main) {
                client.refreshRooms()
                client.switchRoom("the-council")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePairingIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::client.isInitialized) {
            client.disconnect()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        client = TalosClient(this, lifecycleScope)

        // Agent-first pairing link: talos://pair?url=<core>&token=<token>
        handlePairingIntent(intent)

        if (client.isPaired.value) {
            client.refreshRooms()
            client.switchRoom("the-council")
        }

        setContent {
            val storedTheme by client.themeMode.collectAsState()
            val activeThemeMode = if (storedTheme == "LIGHT") AppThemeMode.LIGHT else AppThemeMode.DARK
            var appScreen by remember { mutableStateOf(if (client.hasLaunched || client.isPaired.value) "main" else "landing") }

            // Flexible theming: tokens from talos-core /api/theme (cached),
            // falling back to the built-in scheme when unreachable.
            var remoteTheme by remember { mutableStateOf<TalosThemeConfig?>(null) }
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    ThemeRepository.load(client.baseUrl, applicationContext)
                }?.let { remoteTheme = it }
            }

            // Service gate: TALOS needs the private overlay (Tailscale) for its
            // encrypted agent transport. Auto-detect the core, verify the
            // overlay, and block the app with clear guidance when it is down.
            var gateState by remember { mutableStateOf(ServiceGateState.Checking) }
            var gateNonce by remember { mutableStateOf(0) }
            var manualSetup by remember { mutableStateOf(false) }

            // Automatic periodic retry when gate is not ready
            LaunchedEffect(gateState) {
                if (gateState != ServiceGateState.Ready) {
                    while (gateState != ServiceGateState.Ready) {
                        kotlinx.coroutines.delay(3000)
                        gateNonce++
                    }
                }
            }

            // Immediately re-verify on app foreground resume (e.g. returning from Tailscale)
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        gateNonce++
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(gateNonce) {
                gateState = ServiceGateState.Checking

                val mesh = withContext(Dispatchers.IO) {
                    var status = client.resolveCore()
                    if (status == null) {
                        kotlinx.coroutines.delay(500)
                        status = client.resolveCore()
                    }
                    status
                }

                gateState = when {
                    mesh != null && mesh.tailscale_enabled != false -> ServiceGateState.Ready
                    mesh != null && mesh.tailscale_enabled == false -> ServiceGateState.TailscaleRequired
                    else -> ServiceGateState.NodeUnreachable
                }
            }

            TalosTheme(themeMode = activeThemeMode, remoteConfig = remoteTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isPaired by client.isPaired.collectAsState()
                    val isCurrentlyDark = LocalIsDark.current

                if (isPaired && gateState != ServiceGateState.Ready && !manualSetup) {
                    ServiceGateScreen(
                        state = gateState,
                        onRetry = { gateNonce++ },
                        initialUrl = client.baseUrl,
                        initialToken = client.apiToken,
                        onUpdateUrl = { newUrl ->
                            client.updateConfig(newUrl, client.apiToken)
                        },
                        onVerifyAndConnect = { endpoint, key ->
                            client.verifyAndConnect(endpoint, key)
                        },
                        modifier = Modifier.fillMaxSize(),
                        onManualSetup = {
                            manualSetup = true
                            appScreen = "wizard"
                        }
                    )
                } else {
                val activeTarget = when (appScreen) {
                    "landing" -> "landing"
                    "wizard" -> "wizard"
                    else -> if (isPaired) "main" else "landing"
                }

                AnimatedContent(
                    targetState = activeTarget,
                    transitionSpec = {
                        // M3E physics: spatial springs for screen-level transitions.
                        // Slow spatial (0.8/200) for full-screen movement - expressive, interruptible.
                        val offsetIn = M3EMotion.slowSpatial<IntOffset>()
                        val floatIn = M3EMotion.slowSpatial<Float>()
                        val fadeSpec = M3EMotion.defaultEffects<Float>()
                        if (initialState == "landing" && (targetState == "wizard" || targetState == "main")) {
                            // Ascension: incoming screen glides upward from below with spring overshoot
                            (
                                fadeIn(animationSpec = fadeSpec) +
                                slideInVertically(
                                    animationSpec = offsetIn,
                                    initialOffsetY = { height -> height / 4 }
                                ) +
                                scaleIn(animationSpec = floatIn, initialScale = 0.96f)
                            ).togetherWith(
                                fadeOut(animationSpec = M3EMotion.fastEffects()) +
                                slideOutVertically(
                                    animationSpec = offsetIn,
                                    targetOffsetY = { height -> -height / 8 }
                                )
                            )
                        } else if ((initialState == "wizard" || initialState == "main") && targetState == "landing") {
                            // Return to landing: reverse vertical flow
                            (
                                fadeIn(animationSpec = fadeSpec) +
                                slideInVertically(
                                    animationSpec = offsetIn,
                                    initialOffsetY = { height -> -height / 6 }
                                )
                            ).togetherWith(
                                fadeOut(animationSpec = M3EMotion.fastEffects()) +
                                slideOutVertically(
                                    animationSpec = offsetIn,
                                    targetOffsetY = { height -> height / 6 }
                                )
                            )
                        } else {
                            val forward = targetState == "main" || targetState == "wizard"
                            val dir = if (forward) 1 else -1
                            (
                                fadeIn(animationSpec = fadeSpec) +
                                slideInVertically(
                                    animationSpec = offsetIn,
                                    initialOffsetY = { height -> (height / 5) * dir }
                                ) +
                                scaleIn(animationSpec = floatIn, initialScale = 0.96f)
                            ).togetherWith(
                                fadeOut(animationSpec = M3EMotion.fastEffects()) +
                                slideOutVertically(
                                    animationSpec = offsetIn,
                                    targetOffsetY = { height -> -(height / 5) * dir }
                                ) +
                                scaleOut(animationSpec = floatIn, targetScale = 1.04f)
                            )
                        }
                    },
                    label = "AppScreenTransition"
                ) { screenKey ->
                    when (screenKey) {
                        "main" -> {
                            MainScreen(
                                client = client,
                                isDark = isCurrentlyDark,
                                onToggleTheme = {
                                    client.toggleTheme()
                                },
                                onExitToLanding = {
                                    appScreen = "landing"
                                }
                            )
                        }
                        "wizard" -> {
                            OnboardingWizard(
                                client = client,
                                onToggleTheme = {
                                    client.toggleTheme()
                                },
                                onExitToLanding = {
                                    appScreen = "landing"
                                },
                                onComplete = {
                                    client.markLaunched()
                                    client.refreshRooms()
                                    client.switchRoom("the-council")
                                    appScreen = "main"
                                }
                            )
                        }
                        else -> {
                            LandingScreen(
                                client = client,
                                isDark = isCurrentlyDark,
                                onToggleTheme = {
                                    client.toggleTheme()
                                },
                                onGetStarted = {
                                    client.markLaunched()
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    client: TalosClient,
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onExitToLanding: () -> Unit = {}
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
    var activeSlotId by remember { mutableStateOf("chambers") }

    BackHandler {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (activeSlotId != "chambers") {
            activeSlotId = "chambers"
        } else {
            onExitToLanding()
        }
    }
    val featureScope = remember(client, scope) {
        object : FeatureScope {
            override val coroutineScope = scope
            override val client = client
        }
    }

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
                // In a 1/3-width split a fixed 300dp drawer would overflow.
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth(0.85f)
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



                HorizontalDivider(color = talosBorder, modifier = Modifier.padding(top = 8.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    items(rooms) { room: Room ->
                        val isSelected = room.id == currentRoomId && activeSlotId == "chambers"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) PrimaryAccent.copy(alpha = 0.12f) else Color.Transparent)
                                .then(
                                    if (isSelected) Modifier.border(1.dp, PrimaryAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    else Modifier
                                )
                                .clickable {
                                    activeSlotId = "chambers"
                                    client.switchRoom(room.id)
                                    scope.launch { drawerState.close() }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            val roomEmblem = when {
                                room.id == "core-dm" || room.mode == "dm" -> emblemByKey("hermes")
                                room.id == "the-council" -> emblemByKey("omnirush")
                                else -> emblemBySender(room.name)
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(EmblemSocket)
                                    .border(1.dp, if (isSelected) PrimaryAccent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AgentEmblemIcon(
                                    emblem = roomEmblem,
                                    size = 18.dp,
                                    tint = null
                                )
                            }
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

                    item {
                        HorizontalDivider(color = talosBorder, modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp))
                        Text(
                            text = "EXTENSIBLE MODULES",
                            color = talosTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }

                    items(FeatureCatalog.slots.filter { it.id != "chambers" }) { slot ->
                        val isSlotSelected = slot.id == activeSlotId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSlotSelected) PrimaryAccent.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable {
                                    activeSlotId = slot.id
                                    scope.launch { drawerState.close() }
                                }
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = slot.icon,
                                contentDescription = slot.title,
                                tint = if (isSlotSelected) PrimaryAccent else talosTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = slot.title,
                                    color = if (isSlotSelected) PrimaryAccent else talosTextPrimary,
                                    fontWeight = if (isSlotSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = slot.summary,
                                    color = talosTextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    item {
                        HorizontalDivider(color = talosBorder, modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    scope.launch { drawerState.close() }
                                    onExitToLanding()
                                }
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = PrimaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Return to Landing",
                                    color = PrimaryAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "View overview and setup wizard",
                                    color = talosTextMuted,
                                    fontSize = 11.sp
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // Floating M3 Glassmorphic Chamber Header
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.2.dp, metallicBorderBrush())
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Chamber Drawer Toggle & Machined Emblem Socket
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Chambers Menu",
                                        tint = talosTextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                val roomEmblem = activeRoom?.let { room ->
                                    when {
                                        room.id == "core-dm" || room.mode == "dm" -> emblemByKey("hermes")
                                        room.id == "the-council" -> emblemByKey("omnirush")
                                        else -> emblemBySender(room.name)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(EmblemSocket)
                                        .border(1.dp, PrimaryAccent.copy(alpha = 0.50f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (roomEmblem != null) {
                                        AgentEmblemIcon(emblem = roomEmblem, size = 20.dp, tint = null)
                                    } else {
                                        Icon(Icons.Default.Hub, contentDescription = null, tint = PrimaryAccent, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = roomTitle,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = talosTextPrimary,
                                        maxLines = 1
                                    )
                                    // Real-Time Telemetry Capsule
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isConnected) StatusGreen else MaterialTheme.colorScheme.error)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (isConnected) "MESH LINKED · 12ms" else "OFFLINE",
                                            color = if (isConnected) StatusGreen else MaterialTheme.colorScheme.error,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }

                            // Right: Action Capsule Controls (40dp Accessible Touch Targets)
                            Row(verticalAlignment = Alignment.CenterVertically) {

                                val themeTransition = updateTransition(targetState = isDark, label = "m_theme_transition")
                                val themeRot by themeTransition.animateFloat(
                                    transitionSpec = { M3EMotion.fastSpatial() },
                                    label = "m_theme_rot"
                                ) { dark -> if (dark) 0f else 180f }
                                val themeScale by themeTransition.animateFloat(
                                    transitionSpec = { M3EMotion.fastSpatial() },
                                    label = "m_theme_scale"
                                ) { dark -> if (dark) 1.0f else 1.15f }
                                IconButton(
                                    onClick = onToggleTheme,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode",
                                        tint = PrimaryAccent,
                                        modifier = Modifier
                                            .size(19.dp)
                                            .graphicsLayer {
                                                rotationZ = themeRot
                                                scaleX = themeScale
                                                scaleY = themeScale
                                            }
                                    )
                                }

                                IconButton(
                                    onClick = { showSettingsDialog = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = talosTextMuted,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                IconButton(
                                    onClick = onExitToLanding,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = "Return to Landing",
                                        tint = talosTextMuted,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Single Unified Contextual Navigation Rail (Anti-Slop: Zero Stacked Rows)
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (activeSlotId == "chambers") {
                            // Primary: Active Council Chambers
                            items(rooms, key = { "room_${it.id}" }) { room: Room ->
                                val isSelected = room.id == currentRoomId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { client.switchRoom(room.id) },
                                    leadingIcon = {
                                        val quickEmblem = when {
                                            room.id == "core-dm" || room.mode == "dm" -> emblemByKey("hermes")
                                            room.id == "the-council" -> emblemByKey("omnirush")
                                            else -> emblemBySender(room.name)
                                        }
                                        AgentEmblemIcon(emblem = quickEmblem, size = 14.dp, tint = null)
                                    },
                                    label = {
                                        Text(
                                            text = room.name,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    shape = ShapeLargeIncreased,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryAccent,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        labelColor = talosTextPrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                            }
                        }

                        // Module Perspective Switches
                        items(FeatureCatalog.slots.filter { activeSlotId != "chambers" || it.id != "chambers" }, key = { "slot_${it.id}" }) { slot ->
                            val isSlotActive = slot.id == activeSlotId
                            FilterChip(
                                selected = isSlotActive,
                                onClick = { activeSlotId = slot.id },
                                leadingIcon = {
                                    Icon(
                                        imageVector = slot.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = slot.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSlotActive) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                shape = ShapeLargeIncreased,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryAccent,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    labelColor = talosTextPrimary,
                                    iconColor = talosTextMuted
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSlotActive,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (activeSlotId == "chambers") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                    if (isTyping) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(bottom = 6.dp, start = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PrimaryAccent.copy(alpha = 0.12f))
                                .border(1.dp, PrimaryAccent.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            ThreeDotTypingIndicator(dotSize = 5.dp, dotSpacing = 3.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Agent fleet is deliberating...",
                                color = PrimaryAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Quick Command / Suggestion Strip (Apple HIG Proximity)
                    val showQuickChips = inputText.isEmpty() || inputText.startsWith("/")
                    if (showQuickChips && !isTyping) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            val suggestions = listOf("/consensus", "/mesh", "/clear", "/benchmark", "/status")
                            items(suggestions) { cmd ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                                    border = BorderStroke(1.dp, metallicBorderBrush()),
                                    modifier = Modifier.clickable {
                                        inputText = cmd
                                    }
                                ) {
                                    Text(
                                        text = cmd,
                                        color = PrimaryAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Floating Benchmark Metallic Command Dock (Apple Design + M3E)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // (A) Input Capsule with Left Tool and Right Inline Actions
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.2.dp, metallicBorderBrush()),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        inputText = if (inputText.startsWith("/")) "" else "/"
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SentimentSatisfied,
                                        contentDescription = "Agent & Tool Mode",
                                        tint = if (inputText.startsWith("/")) PrimaryAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    placeholder = {
                                        Text(
                                            text = "Message $roomTitle...",
                                            color = talosTextMuted,
                                            fontSize = 14.sp
                                        )
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedTextColor = talosTextPrimary,
                                        unfocusedTextColor = talosTextPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics {
                                            contentDescription = "Message composition input field for $roomTitle"
                                        }
                                )

                                IconButton(
                                    onClick = { /* Attachment action */ },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Attach Context",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // (B) Standalone Floating Action Dial (Morphs between Mic & Send)
                        val isSendMode = inputText.isNotBlank()
                        val sendInteraction = remember { MutableInteractionSource() }
                        val isSendPressed by sendInteraction.collectIsPressedAsState()
                        val sendScale by animateFloatAsState(
                            targetValue = if (isSendPressed) 0.96f else 1.0f,
                            animationSpec = M3EMotion.fastSpatial(),
                            label = "sendScale"
                        )
                        val sendMorphProgress by animateFloatAsState(
                            targetValue = if (isSendPressed) 1f else 0f,
                            animationSpec = M3EMotion.defaultSpatial(),
                            label = "sendMorph"
                        )
                        val sendMorph = remember { Morph(M3EShapes.circle(), M3EShapes.scallop12()) }

                        Surface(
                            shape = if (sendMorphProgress > 0.01f) M3EMorphShape(sendMorph, sendMorphProgress) else CircleShape,
                            color = PrimaryAccent,
                            border = BorderStroke(1.dp, metallicBorderBrush()),
                            modifier = Modifier
                                .size(48.dp)
                                .scale(sendScale)
                                .semantics {
                                    contentDescription = if (isSendMode) "Send message" else "Voice input"
                                }
                                .clickable(
                                    enabled = !isTyping,
                                    interactionSource = sendInteraction,
                                    indication = null
                                ) {
                                    if (isSendMode) {
                                        client.sendMessage(inputText)
                                        inputText = ""
                                    } else {
                                        // Voice / Mic tap feedback
                                        client.sendMessage("/consensus")
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Crossfade(
                                    targetState = isSendMode,
                                    animationSpec = M3EMotion.fastEffects(),
                                    label = "ActionIconCrossfade"
                                ) { sendState ->
                                    if (sendState) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send Command",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Voice Prompt",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            if (activeSlotId == "chambers") {
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
                        items(messages, key = { it.id }) { msg: Message ->
                            Box(modifier = Modifier.animateItem()) {
                                MessageBubble(msg = msg)
                            }
                        }
                    }
                }
            } else {
                val slot = FeatureCatalog.slots.find { it.id == activeSlotId }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    slot?.Content(featureScope)
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
    var isVerifying by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = talosSurface,
        title = {
            Column {
                Text(
                    text = "Tailscale Host & Connection Settings",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Update your host Tailscale IP or API key whenever your network changes.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Core Tailscale Address:",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { 
                        urlText = it
                        statusMessage = null
                        isSuccess = false
                    },
                    placeholder = { Text("http://100.x.y.z:8010", fontSize = 12.sp) },
                    singleLine = true,
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
                    text = "Host API Key (talos_sk_...):",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = tokenText,
                    onValueChange = { 
                        tokenText = it
                        statusMessage = null
                        isSuccess = false
                    },
                    placeholder = { Text("Paste key from laptop", fontSize = 12.sp) },
                    singleLine = true,
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

                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = statusMessage ?: "",
                        color = if (isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                M3EButton(
                    text = if (isVerifying) "Verifying Handshake..." else "Verify & Update Connection",
                    onClick = {
                        scope.launch {
                            isVerifying = true
                            statusMessage = null
                            val cleanUrl = urlText.trim().trimEnd('/')
                            val cleanToken = tokenText.trim()
                            val (ok, msg) = client.verifyAndConnect(cleanUrl, cleanToken)
                            isVerifying = false
                            if (ok) {
                                isSuccess = true
                                statusMessage = "Verified & Connected (200 OK)"
                                onSave(cleanUrl, cleanToken)
                            } else {
                                isSuccess = false
                                statusMessage = msg
                            }
                        }
                    },
                    enabled = !isVerifying,
                    loading = isVerifying,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Close", color = TextMuted)
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

    val infiniteCorePulse = rememberInfiniteTransition(label = "hero_core_pulse")
    val coreScale by infiniteCorePulse.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_scale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // M3E signature scallop badge: 12-lobed expressive flower portal
        Surface(
            shape = M3EPolygonShape(M3EShapes.scallop12()),
            color = if (isDark) EmblemSocket else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.5.dp, metallicBorderBrush()),
            modifier = Modifier
                .size(76.dp)
                .scale(coreScale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                val heroEmblem = room?.let { r ->
                    when {
                        r.id == "core-dm" || r.mode == "dm" -> emblemByKey("hermes")
                        r.id == "the-council" -> emblemByKey("omnirush")
                        else -> emblemBySender(r.name)
                    }
                }
                if (heroEmblem != null) {
                    AgentEmblemIcon(emblem = heroEmblem, size = 42.dp, tint = null)
                } else {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = PrimaryAccent,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
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

        // Participating Agent Roster Dock with Machined Obsidian Sockets
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.2.dp, metallicBorderBrush()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Participating Agent Fleet",
                        color = talosTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = CircleShape,
                        color = StatusGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "${AgentEmblems.size} Agents Online",
                            color = StatusGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AgentEmblems, key = { it.key }) { emblem ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(EmblemSocket)
                                .border(1.dp, emblem.accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AgentEmblemIcon(emblem = emblem, size = 20.dp, tint = null)
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
            Triple("/mesh", "Scan peers on your private Tailscale overlay", Icons.Default.Hub)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for ((cmd, desc, icon) in commands) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.2.dp, metallicBorderBrush()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPromptClick(cmd) }
                        .semantics { contentDescription = "Execute command $cmd: $desc" }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(EmblemSocket)
                                .border(1.dp, PrimaryAccent.copy(alpha = 0.50f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = PrimaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cmd,
                                color = PrimaryAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Text(
                                text = desc,
                                color = talosTextMuted,
                                fontSize = 12.sp,
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

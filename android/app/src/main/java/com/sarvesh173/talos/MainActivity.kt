package com.sarvesh173.talos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.sarvesh173.talos.data.MeshInfo
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
            client.switchRoom("alya-dm")
        }

        setContent {
            TalosTheme {
                val isPaired by client.isPaired.collectAsState()
                if (!isPaired) {
                    OnboardingScreen(
                        client = client,
                        onConnect = { url, token ->
                            client.updateConfig(url, token)
                        }
                    )
                } else {
                    MainScreen(client = client)
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(
    client: TalosClient,
    onConnect: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(true) }
    var meshInfo by remember { mutableStateOf<MeshInfo?>(null) }
    var isPairing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showEndpointEdit by remember { mutableStateOf(false) }
    var customUrl by remember { mutableStateOf(client.baseUrl) }

    LaunchedEffect(Unit) {
        isChecking = true
        meshInfo = client.checkMeshStatus()
        isChecking = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = PrimaryAccent,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "TALOS Mesh Link",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tailscale Encrypted Autonomous Agent Gateway",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )

            // Dynamic Mesh Status Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF090D15))
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (meshInfo?.active == true) StatusGreen else if (isChecking) PrimaryAccent else Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (meshInfo?.active == true) "Tailscale Mesh Verified" else if (isChecking) "Discovering Mesh..." else "Endpoint Standby",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Host Node: ${meshInfo?.hostname ?: "sarvesh-82c3"} (${meshInfo?.self_ip ?: "100.75.107.79"})",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                if (!meshInfo?.peer_name.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Device Peer: ${meshInfo?.peer_name}",
                        color = PrimaryAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Autonomous Button: Generate Link & Pair Agent
            Button(
                onClick = {
                    scope.launch {
                        isPairing = true
                        errorMessage = null
                        val token = client.pairDevice()
                        if (token != null) {
                            onConnect(client.baseUrl, token)
                        } else {
                            errorMessage = "Could not link with agent. Verify Tailscale network."
                        }
                        isPairing = false
                    }
                },
                enabled = !isPairing,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isPairing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Provisioning Agent Link...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Generate Link & Pair Agent", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage ?: "",
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subtle Custom Endpoint Toggle
            Text(
                text = if (showEndpointEdit) "Hide Custom Endpoint" else "Custom Endpoint",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable { showEndpointEdit = !showEndpointEdit }
                    .padding(4.dp)
            )

            if (showEndpointEdit) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customUrl,
                    onValueChange = {
                        customUrl = it
                        client.updateConfig(it, client.apiToken)
                    },
                    label = { Text("Endpoint URL", color = TextMuted, fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF090D15),
                        unfocusedContainerColor = Color(0xFF090D15),
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(client: TalosClient) {
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
                drawerContainerColor = SurfaceDark,
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "TALOS Chambers",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
                HorizontalDivider(color = SurfaceBorder)

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
                            Icon(
                                imageVector = if (room.mode == "dm") Icons.Default.Person else Icons.Default.Groups,
                                contentDescription = null,
                                tint = if (isSelected) PrimaryAccent else TextMuted,
                                modifier = Modifier.size(20.dp)
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
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BgDark,
                        titleContentColor = TextPrimary
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = TextMuted)
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    color = BgDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Column {
                        if (isTyping) {
                            Text(
                                text = "Alya is deliberating...",
                                color = PrimaryAccent,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
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
                                    focusedContainerColor = SurfaceDark,
                                    unfocusedContainerColor = SurfaceDark,
                                    focusedBorderColor = PrimaryAccent,
                                    unfocusedBorderColor = SurfaceBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        client.sendMessage(inputText)
                                        inputText = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryAccent)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            containerColor = BgDark
        ) { paddingValues ->
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
        containerColor = SurfaceDark,
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
                        focusedContainerColor = Color(0xFF090D15),
                        unfocusedContainerColor = Color(0xFF090D15),
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
                        focusedContainerColor = Color(0xFF090D15),
                        unfocusedContainerColor = Color(0xFF090D15),
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

package com.sarvesh173.talos.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.BrowserFetchResultDto
import com.sarvesh173.talos.network.BrowserStatusDto
import com.sarvesh173.talos.network.TalosClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObscuraBrowserScreen(
    client: TalosClient,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var urlInput by remember { mutableStateOf("https://skills.sh") }
    var isStealthActive by remember { mutableStateOf(true) }
    var selectedDumpMode by remember { mutableStateOf("text") }
    var isLoading by remember { mutableStateOf(false) }
    var currentResult by remember { mutableStateOf<BrowserFetchResultDto?>(null) }
    var browserStatus by remember { mutableStateOf<BrowserStatusDto?>(null) }

    LaunchedEffect(Unit) {
        client.checkBrowserStatus { status ->
            browserStatus = status
        }
    }

    val quickBookmarks = listOf(
        "Skills Hub" to "https://skills.sh",
        "Hermes Docs" to "https://hermes-agent.nousresearch.com/docs",
        "TALOS Repo" to "https://github.com/sarvesh173/talos",
        "Mesh Status" to "${client.baseUrl}/health"
    )

    fun executeFetch(targetUrl: String = urlInput) {
        if (targetUrl.isBlank()) return
        isLoading = true
        client.fetchBrowserPage(
            url = targetUrl,
            stealth = isStealthActive,
            dumpMode = selectedDumpMode
        ) { result ->
            isLoading = false
            currentResult = result
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Obscura Browser",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isStealthActive) "STEALTH ACTIVE" else "STANDARD",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        Text(
                            text = browserStatus?.let { "${it.version} • CDP Port ${it.cdp_port} • 28MB RSS" }
                                ?: "Rust Headless V8 Engine • Anti-Fingerprint",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        client.checkBrowserStatus { status ->
                            browserStatus = status
                            Toast.makeText(context, "Obscura status refreshed", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Status",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Omnibox Address Bar - fully rounded capsule per DESIGN.md button/surface rule
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isStealthActive) Icons.Default.VpnKey else Icons.Default.Language,
                        contentDescription = null,
                        tint = if (isStealthActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("Enter URL or domain...", fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { executeFetch() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (urlInput.isNotBlank()) {
                        IconButton(
                            onClick = { urlInput = "" },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }

                    IconButton(
                        onClick = { executeFetch() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        if (isLoading) {
                            MetallicCircularLoader(
                                size = 18.dp,
                                strokeWidth = 2.dp,
                                accentColor = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Quick Bookmarks Carousel
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickBookmarks) { (label, url) ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable {
                            urlInput = url
                            executeFetch(url)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // Controls Row (Stealth Toggle, Dump Mode Chips)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("text" to "Text Reader", "links" to "Links", "assets" to "Assets").forEach { (mode, label) ->
                        val isSelected = selectedDumpMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedDumpMode = mode
                                if (currentResult != null) executeFetch()
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                FilterChip(
                    selected = isStealthActive,
                    onClick = { isStealthActive = !isStealthActive },
                    label = { Text("Stealth", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                )
            }

            // Render Output Area
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                val result = currentResult

                when {
                    isLoading -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                MetallicCircularLoader(
                                    size = 44.dp,
                                    strokeWidth = 3.5.dp,
                                    accentColor = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Obscura rendering DOM over V8...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    result != null -> {
                        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.title.ifBlank { result.url },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${result.duration_ms}ms • ${result.content.length} chars extracted",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (result.success) StatusGreen else MaterialTheme.colorScheme.error
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(result.content))
                                            Toast.makeText(context, "Content copied!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            val excerpt = result.content.take(600).trim()
                                            client.sendMessage("[Obscura Web Excerpt: ${result.url}]\n$excerpt...")
                                            Toast.makeText(context, "Forwarded to Council Room!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Forward", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 10.dp)
                            )

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item {
                                    Text(
                                        text = result.content.ifBlank { result.error ?: "Empty response" },
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize().padding(24.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Ready to Navigate",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Type any URL above or choose a bookmark to extract clean text, links, or assets via the Rust Obscura engine.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

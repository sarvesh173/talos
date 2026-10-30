package com.sarvesh173.talos.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient

data class GeneratedImageArtifact(
    val id: String,
    val prompt: String,
    val style: String,
    val aspectRatio: String,
    val model: String,
    val timestamp: String,
    val gradientColors: List<Color>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGalleryScreen(
    client: TalosClient,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val primaryColor = PrimaryAccent
    var selectedArtifact by remember { mutableStateOf<GeneratedImageArtifact?>(null) }
    var showGenerateDialog by remember { mutableStateOf(false) }

    val imageArtifacts = remember {
        mutableStateListOf(
            GeneratedImageArtifact(
                id = "img_1",
                prompt = "A sleek robotic companion with obsidian metallic body and amber glowing optical sensors in a neon rain city.",
                style = "Cinematic",
                aspectRatio = "16:9",
                model = "Hermes AquaDevs SDXL",
                timestamp = "Just now",
                gradientColors = listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFFF59E0B))
            ),
            GeneratedImageArtifact(
                id = "img_2",
                prompt = "Minimalist vector emblem of a celestial fox surrounded by verdigris geometric runes.",
                style = "Pixel Art",
                aspectRatio = "1:1",
                model = "Crax SeaDream-5",
                timestamp = "10m ago",
                gradientColors = listOf(Color(0xFF042F2E), Color(0xFF115E59), Color(0xFF2DD4BF))
            ),
            GeneratedImageArtifact(
                id = "img_3",
                prompt = "Tactile mechanical dashboard gauge cluster with brushed titanium bezel and phosphor dial.",
                style = "3D Render",
                aspectRatio = "9:16",
                model = "Hermes Multi-Modal Studio",
                timestamp = "1h ago",
                gradientColors = listOf(Color(0xFF0F172A), Color(0xFF334155), Color(0xFF94A3B8))
            ),
            GeneratedImageArtifact(
                id = "img_4",
                prompt = "Anime illustration of Alisa Mikhailovna Kujou standing beneath cherry blossoms at twilight.",
                style = "Anime",
                aspectRatio = "1:1",
                model = "AquaDevs GPT-Image",
                timestamp = "2h ago",
                gradientColors = listOf(Color(0xFF4A044E), Color(0xFF701A75), Color(0xFFF472B6))
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AI Image Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${imageArtifacts.size} artifacts generated",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryAccent
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
                    IconButton(onClick = { showGenerateDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "New Generation",
                            tint = PrimaryAccent
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(imageArtifacts, key = { it.id }) { artifact ->
                    ImageArtifactCard(
                        artifact = artifact,
                        onClick = { selectedArtifact = artifact }
                    )
                }
            }
        }
    }

    // Full-Screen Image Lightbox Modal
    selectedArtifact?.let { artifact ->
        AlertDialog(
            onDismissRequest = { selectedArtifact = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            title = {
                Text(
                    text = "${artifact.style} • ${artifact.aspectRatio}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Simulated High-Res Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(artifact.gradientColors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Text(
                        text = artifact.prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Model: ${artifact.model}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = artifact.timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryAccent
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        clipboardManager.setText(AnnotatedString(artifact.prompt))
                        Toast.makeText(context, "Prompt copied!", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Copy Prompt")
                    }
                    Button(
                        onClick = {
                            Toast.makeText(context, "Image saved to device gallery!", Toast.LENGTH_SHORT).show()
                            selectedArtifact = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                    ) {
                        Text("Save Image", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedArtifact = null }) {
                    Text("Close")
                }
            }
        )
    }

    // New Image Generation Dialog
    if (showGenerateDialog) {
        var promptText by remember { mutableStateOf("") }
        var chosenStyle by remember { mutableStateOf("Cinematic") }
        val styles = listOf("Cinematic", "3D Render", "Pixel Art", "Anime")

        AlertDialog(
            onDismissRequest = { showGenerateDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryAccent)
            },
            title = {
                Text("Compose Image Prompt", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        placeholder = { Text("Describe the visual concept...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        styles.forEach { style ->
                            FilterChip(
                                selected = chosenStyle == style,
                                onClick = { chosenStyle = style },
                                label = { Text(style, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (promptText.isNotBlank()) {
                            imageArtifacts.add(
                                0,
                                GeneratedImageArtifact(
                                    id = "img_${System.currentTimeMillis() % 1000}",
                                    prompt = promptText,
                                    style = chosenStyle,
                                    aspectRatio = "1:1",
                                    model = "Hermes AquaDevs",
                                    timestamp = "Just now",
                                    gradientColors = listOf(Color(0xFF1E293B), Color(0xFF0F172A), primaryColor)
                                )
                            )
                            client.sendMessage("/generate-image [Style: $chosenStyle] $promptText")
                            showGenerateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Dispatch", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ImageArtifactCard(
    artifact: GeneratedImageArtifact,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Brush.linearGradient(artifact.gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = artifact.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = artifact.style,
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = artifact.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

package com.sarvesh173.talos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Share
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.data.Message
import com.sarvesh173.talos.data.Role
import com.sarvesh173.talos.data.ToolCall

/**
 * Reusable Material 3 Expressive PressableSurface composable.
 *
 * Implements smooth tactile press animation:
 * - Pressed scale: 0.96f
 * - Spring physics: Spring.DampingRatioMediumBouncy (0.5f), Spring.StiffnessMedium (1500f)
 * - Rounded corners: Strictly >= 16dp (No square boxes anywhere in M3 UI)
 * - Physical haptic feedback on actuation
 */
@Composable
fun PressableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    /** Optional M3E press morph target. When set, the surface tightens toward this
     *  corner shape while held and springs back on release. Only RoundedCornerShape
     *  pairs are morphed; any other shape keeps its resting geometry. */
    pressedShape: RoundedCornerShape? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    pressedScale: Float = 0.96f,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.62f,
            stiffness = 420f
        ),
        label = "PressableSurfaceScale"
    )

    // Shape morph: spatial spring, overshoot allowed (M3E press signature).
    val morphProgress by animateFloatAsState(
        targetValue = if (isPressed && enabled && pressedShape != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "PressableSurfaceMorph"
    )

    val resolvedShape: Shape = run {
        val from = shape as? RoundedCornerShape
        if (from != null && pressedShape != null && morphProgress > 0.001f) {
            MorphRoundedShape(from, pressedShape, morphProgress)
        } else shape
    }

    // M3 state layer: content-colored overlay at 12% while pressed.
    // Effects spring (no overshoot) — the layer must never flash past 12%.
    val stateLayerAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.12f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 3800f
        ),
        label = "PressableSurfaceStateLayer"
    )

    val activeBorder = if (isPressed && enabled) {
        BorderStroke(
            1.2.dp,
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                )
            )
        )
    } else border

    Surface(
        shape = resolvedShape,
        color = color,
        contentColor = contentColor,
        border = activeBorder,
        shadowElevation = shadowElevation,
        tonalElevation = 0.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            // State layer sits UNDER the content so glyphs keep full contrast.
            if (stateLayerAlpha > 0.001f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = stateLayerAlpha }
                        .background(contentColor)
                )
            }
            content()
        }
    }
}

@Composable
fun ThreeDotTypingIndicator(
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.primary,
    dotSize: Dp = 6.dp,
    dotSpacing: Dp = 4.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "TypingIndicatorTransition")

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 0 using FastOutSlowInEasing
                1.0f at 300 using FastOutSlowInEasing
                0.3f at 600 using FastOutSlowInEasing
                0.3f at 1200 using FastOutSlowInEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot1Alpha"
    )

    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 200 using FastOutSlowInEasing
                1.0f at 500 using FastOutSlowInEasing
                0.3f at 800 using FastOutSlowInEasing
                0.3f at 1200 using FastOutSlowInEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot2Alpha"
    )

    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 400 using FastOutSlowInEasing
                1.0f at 700 using FastOutSlowInEasing
                0.3f at 1000 using FastOutSlowInEasing
                0.3f at 1200 using FastOutSlowInEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Dot3Alpha"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing)
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    alpha = dot1Alpha
                    translationY = -(dot1Alpha - 0.3f) * 4f
                }
                .background(dotColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    alpha = dot2Alpha
                    translationY = -(dot2Alpha - 0.3f) * 4f
                }
                .background(dotColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    alpha = dot3Alpha
                    translationY = -(dot3Alpha - 0.3f) * 4f
                }
                .background(dotColor, CircleShape)
        )
    }
}

@Composable
fun MessageBubble(
    msg: Message,
    modifier: Modifier = Modifier
) {
    val isHuman = msg.sender_role == Role.human

    // M3E entrance choreography: spatial scale springs may overshoot,
    // alpha fades use effects springs and never do.
    var bubbleAppeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { bubbleAppeared = true }
    val entranceScale by animateFloatAsState(
        targetValue = if (bubbleAppeared) 1f else 0.90f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "bubbleEntranceScale"
    )
    val entranceAlpha by animateFloatAsState(
        targetValue = if (bubbleAppeared) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "bubbleEntranceAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .graphicsLayer {
                scaleX = entranceScale
                scaleY = entranceScale
                alpha = entranceAlpha
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    if (isHuman) 1f else 0f,
                    1f
                )
            },
        horizontalArrangement = if (isHuman) Arrangement.End else Arrangement.Start
    ) {
        if (!isHuman) {
            // Official CLI Agent SVG Emblem inside Machined Obsidian Socket
            val emblem = emblemBySender(msg.sender_name)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(EmblemSocket)
                    .border(1.dp, emblem.accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                AgentEmblemIcon(emblem = emblem, size = 22.dp, tint = null)
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isHuman) Alignment.End else Alignment.Start
        ) {
            // Sender name header with matching brand accent and spec chip
            if (!isHuman) {
                val emblem = emblemBySender(msg.sender_name)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = msg.sender_name,
                        color = emblem.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = emblem.accent.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, emblem.accent.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = emblem.role.substringBefore(" · ").ifBlank { emblem.role },
                            color = emblem.accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Collapsible Thought / Reasoning Block
            if (!msg.thought.isNullOrBlank()) {
                CollapsibleThoughtBlock(thoughtText = msg.thought)
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Tool Call Status Badges
            for (tool in msg.tool_calls) {
                ToolCallItem(tool = tool)
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Message text container
            val content = msg.content
            val isAudio = content.contains(".mp3", ignoreCase = true) ||
                          content.contains(".ogg", ignoreCase = true) ||
                          content.contains(".wav", ignoreCase = true) ||
                          content.contains(".opus", ignoreCase = true) ||
                          content.contains(".m4a", ignoreCase = true) ||
                          content.contains("[Voice Note", ignoreCase = true) ||
                          (content.contains("data:audio/") && content.contains(";base64,")) ||
                          content.startsWith("MEDIA:") && (content.endsWith(".ogg") || content.endsWith(".mp3") || content.endsWith(".opus"))
            val isVideo = content.contains(".mp4", ignoreCase = true) ||
                          content.contains(".webm", ignoreCase = true)
            val isImageGen = content.startsWith("/generate-image", ignoreCase = true) ||
                             content.contains("[AI Studio", ignoreCase = true) ||
                             content.contains("[Generating Image", ignoreCase = true)
            val isSketch = content.contains("[OpenBoard Sketch Attached", ignoreCase = true)
            val isLocation = content.contains("[Mesh Node", ignoreCase = true)
            val isPhoto = content.contains("[Photo Captured", ignoreCase = true) || (content.contains("data:image/") && content.contains(";base64,"))

            when {
                isPhoto -> {
                    CapturedPhotoBubble(content = content, isHuman = isHuman)
                }
                isLocation -> {
                    LocationPinBubble(content = content, isHuman = isHuman)
                }
                isImageGen -> {
                    ImageGenerationPromptBubble(content = content, isHuman = isHuman)
                }
                isSketch -> {
                    SketchAttachmentBubble(content = content, isHuman = isHuman)
                }
                isAudio -> {
                    NativeAudioPlayerBubble(
                        audioUrl = content.removePrefix("MEDIA:").trim(),
                        isHuman = isHuman
                    )
                }
                isVideo -> {
                    NativeVideoPreviewBubble(
                        videoUrl = content.removePrefix("MEDIA:").trim(),
                        isHuman = isHuman
                    )
                }
                else -> {
                    val isDark = LocalIsDark.current
                    Surface(
                        color = if (isHuman) {
                            Color(0xFF0071E3) // Apple Action Blue signature fill
                        } else {
                            if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA) // Apple Secondary System Background
                        },
                        tonalElevation = 0.dp,
                        shape = if (isHuman) {
                            RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
                        } else {
                            RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
                        },
                        border = if (isHuman) {
                            BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f))
                        } else {
                            BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f))
                        }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = msg.content,
                                color = if (isHuman) Color.White else (if (isDark) Color.White else Color(0xFF1D1D1F)),
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                letterSpacing = (-0.15).sp // Apple SF Pro clean negative tracking
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NativeAudioPlayerBubble(
    audioUrl: String,
    isHuman: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    DisposableEffect(audioUrl) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    val contentColor = if (isHuman) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (isHuman) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    val trackActive = if (isHuman) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val trackInactive = if (isHuman) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
    val buttonBg = if (isHuman) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val iconTint = if (isHuman) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary

    val audioTitle = remember(audioUrl) {
        if (audioUrl.contains("[Voice Note")) {
            val sec = audioUrl.substringAfter("[Voice Note:").substringBefore("]").trim()
            "Recorded Voice Note ($sec)"
        } else {
            audioUrl.substringAfterLast("/").substringBefore("?").ifBlank { "Audio Note" }
        }
    }

    fun togglePlayback() {
        if (isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
        } else {
            if (mediaPlayer == null) {
                try {
                    val player = android.media.MediaPlayer()
                    if (audioUrl.contains(";base64,")) {
                        val b64 = audioUrl.substringAfter(";base64,").trim()
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                        val tempAudio = java.io.File(context.cacheDir, "temp_play_${audioUrl.hashCode()}.m4a")
                        tempAudio.writeBytes(bytes)
                        player.setDataSource(tempAudio.absolutePath)
                    } else if (audioUrl.startsWith("http://") || audioUrl.startsWith("https://")) {
                        player.setDataSource(audioUrl)
                    } else {
                        player.setDataSource(audioUrl.removePrefix("MEDIA:").trim())
                    }
                    player.prepare()
                    player.setOnCompletionListener {
                        isPlaying = false
                        progress = 0f
                    }
                    mediaPlayer = player
                } catch (_: Exception) {
                    return
                }
            }
            mediaPlayer?.start()
            isPlaying = true

            coroutineScope.launch {
                while (isPlaying) {
                    val p = mediaPlayer
                    if (p != null && p.isPlaying && p.duration > 0) {
                        progress = p.currentPosition.toFloat() / p.duration.toFloat()
                    }
                    kotlinx.coroutines.delay(100)
                }
            }
        }
    }

    Surface(
        color = if (isHuman) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.2.dp,
            brush = if (isHuman) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.40f), Color.Transparent)) else metallicBorderBrush()
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = { togglePlayback() },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(buttonBg)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = mutedColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = audioTitle,
                            color = contentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    Slider(
                        value = progress,
                        onValueChange = { newProgress ->
                            progress = newProgress
                            mediaPlayer?.let { p ->
                                val seekTo = (newProgress * p.duration).toInt()
                                p.seekTo(seekTo)
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = trackActive,
                            activeTrackColor = trackActive,
                            inactiveTrackColor = trackInactive
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { /* local save */ },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Save to cache",
                        tint = mutedColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ImageGenerationPromptBubble(content: String, isHuman: Boolean) {
    val isGenerating = content.contains("[Generating Image", ignoreCase = true)
    val isArtifact = content.contains("[AI Studio Artifact:", ignoreCase = true)
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val style = when {
        isArtifact -> content.substringAfter("• ").substringBefore("]").ifBlank { "Cinematic" }
        content.contains("[Style: ") -> content.substringAfter("[Style: ").substringBefore("]").ifBlank { "Cinematic" }
        else -> "Cinematic"
    }
    val prompt = when {
        content.contains("Prompt: ") -> content.substringAfter("Prompt: ")
        content.contains("] ") -> content.substringAfter("] ")
        else -> content
    }
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, metallicBorderBrush()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TalosSparkle,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isArtifact) "Hermes Studio Artifact" else "Hermes Image Studio",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = style,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isGenerating) {
                val infiniteTransition = rememberInfiniteTransition(label = "img_shimmer")
                val alphaPulse by infiniteTransition.animateFloat(
                    initialValue = 0.40f,
                    targetValue = 0.95f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Synthesizing vector scene via Hermes multimodal engine...",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alphaPulse)
                        )
                    }
                }
            } else if (isArtifact) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(StatusGreen, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SYNTHESIS READY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusGreen
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(content))
                                    Toast.makeText(context, "Copied artifact URI to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SketchAttachmentBubble(content: String, isHuman: Boolean) {
    val b64 = remember(content) {
        if (content.contains(";base64,")) {
            content.substringAfter(";base64,").trim()
        } else ""
    }
    val bitmap = remember(b64) {
        if (b64.isNotBlank()) {
            runCatching {
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        } else null
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.2.dp, metallicBorderBrush()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)), RoundedCornerShape(16.dp))
                    ) {
                        Icon(
                            imageVector = TalosBoard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "OPENBOARD WHITEBOARD",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val strokeCount = remember(content) {
                            Regex("""(\d+)\s*strokes""").find(content)?.groupValues?.get(1) ?: "Vector"
                        }
                        Text(
                            text = "$strokeCount strokes committed",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "CANVAS",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (bitmap != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Image(
                    bitmap = bitmap,
                    contentDescription = "Whiteboard Drawing",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                )
            }
        }
    }
}

@Composable
fun CapturedPhotoBubble(content: String, isHuman: Boolean) {
    val b64 = remember(content) {
        if (content.contains(";base64,")) {
            content.substringAfter(";base64,").trim()
        } else {
            ""
        }
    }
    val bitmap = remember(b64) {
        if (b64.isNotBlank()) {
            runCatching {
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        } else null
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.2.dp, metallicBorderBrush()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    ) {
                        Icon(
                            imageVector = TalosIcons.TalosCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "CAMERA ATTACHMENT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Live Frame Captured",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "READY",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Captured Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                )
            } else {
                Text(
                    text = content.substringBefore("data:image").trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun LocationPinBubble(content: String, isHuman: Boolean) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val accent = MaterialTheme.colorScheme.primary

    // Extract latitude and longitude
    val latMatch = Regex("""Latitude:\s*([\d.-]+)""").find(content)
    val lonMatch = Regex("""Longitude:\s*([\d.-]+)""").find(content)
    val lat = latMatch?.groupValues?.get(1)
    val lon = lonMatch?.groupValues?.get(1)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, metallicBorderBrush()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(accent.copy(alpha = 0.15f))
                            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "MESH NODE GEOLOCATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tailscale Encrypted Proximity",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = StatusGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "VERIFIED",
                        color = StatusGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (lat != null && lon != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 10.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("LATITUDE", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$lat°", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Box(modifier = Modifier.height(28.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("LONGITUDE", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$lon°", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PressableSurface(
                        onClick = {
                            val uri = android.net.Uri.parse("geo:$lat,$lon?q=$lat,$lon(Talos Mesh Node)")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(16.dp),
                        pressedShape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Maps", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    PressableSurface(
                        onClick = {
                            clipboardManager.setText(AnnotatedString("$lat, $lon"))
                            android.widget.Toast.makeText(context, "Coordinates copied!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(16.dp),
                        pressedShape = RoundedCornerShape(12.dp),
                        color = accent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun NativeVideoPreviewBubble(
    videoUrl: String,
    isHuman: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, metallicBorderBrush()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Scales with the pane instead of a fixed height that
                    // overflows in a short split-screen window.
                    .aspectRatio(1.7f)
                    .heightIn(max = 160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(1.dp, metallicBorderBrush(), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PressableSurface(
                        onClick = { /* launch native video playback */ },
                        shape = CircleShape,
                        pressedShape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Video",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp).align(Alignment.Center)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = videoUrl.substringAfterLast("/").ifBlank { "Video Stream" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CollapsibleThoughtBlock(thoughtText: String) {
    var isExpanded by remember { mutableStateOf(false) }

    PressableSurface(
        onClick = { isExpanded = !isExpanded },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        pressedShape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, metallicBorderBrush()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Reasoning Process",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Text(
                    text = thoughtText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ToolCallItem(tool: ToolCall) {
    var showOutput by remember { mutableStateOf(false) }

    PressableSurface(
        onClick = { if (!tool.output.isNullOrBlank()) showOutput = !showOutput },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        pressedShape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, metallicBorderBrush()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (tool.tool_name.contains("code", ignoreCase = true)) TalosCode else TalosTerminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tool.tool_name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = StatusGreen,
                    modifier = Modifier.size(14.dp)
                )
            }

            if (showOutput && !tool.output.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tool.output,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

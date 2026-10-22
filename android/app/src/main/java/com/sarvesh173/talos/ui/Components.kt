package com.sarvesh173.talos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.data.Message
import com.sarvesh173.talos.data.Role
import com.sarvesh173.talos.data.ToolCall

@Composable
fun MessageBubble(msg: Message) {
    val isHuman = msg.sender_role == Role.human

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = if (isHuman) Arrangement.End else Arrangement.Start
    ) {
        if (!isHuman) {
            // Agent Avatar Badge
            val avatarColor = when {
                msg.sender_name.contains("Omni", ignoreCase = true) -> Color(0xFFA855F7)
                msg.sender_name.contains("Claude", ignoreCase = true) -> Color(0xFFD97757)
                msg.sender_name.contains("Hermes", ignoreCase = true) -> Color(0xFF22C55E)
                msg.sender_name.contains("OpenClaw", ignoreCase = true) -> Color(0xFFF97316)
                else -> Color(0xFF38BDF8)
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.2f))
                    .border(1.dp, avatarColor.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = msg.sender_name.take(1).uppercase(),
                    color = avatarColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isHuman) Alignment.End else Alignment.Start
        ) {
            // Sender name header for multi-agent clarity
            if (!isHuman) {
                Text(
                    text = msg.sender_name,
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
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
                          content.startsWith("MEDIA:") && (content.endsWith(".ogg") || content.endsWith(".mp3"))
            val isVideo = content.contains(".mp4", ignoreCase = true) ||
                          content.contains(".webm", ignoreCase = true)

            when {
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isHuman) PrimaryAccent else talosSurface)
                            .border(1.dp, if (isHuman) PrimaryAccent else SurfaceBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = msg.content,
                            color = if (isHuman) Color.White else TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )
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
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0.35f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isHuman) PrimaryAccent else talosSurface)
            .border(1.dp, if (isHuman) PrimaryAccent else SurfaceBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = audioUrl.substringAfterLast("/").ifBlank { "Audio Note" },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
                Slider(
                    value = progress,
                    onValueChange = { progress = it },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
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
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(talosSurface)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(talosBg),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { /* launch native video playback */ },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(PrimaryAccent)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = videoUrl.substringAfterLast("/").ifBlank { "Video Stream" },
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CollapsibleThoughtBlock(thoughtText: String) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF131722))
            .border(1.dp, Color(0xFF283145), RoundedCornerShape(10.dp))
            .clickable { isExpanded = !isExpanded }
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = Color(0xFFA855F7),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Reasoning Process",
                color = Color(0xFFA855F7),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Text(
                text = thoughtText,
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
fun ToolCallItem(tool: ToolCall) {
    var showOutput by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F1523))
            .border(1.dp, Color(0xFF222B3D), RoundedCornerShape(8.dp))
            .clickable { if (!tool.output.isNullOrBlank()) showOutput = !showOutput }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (tool.tool_name.contains("code", ignoreCase = true)) Icons.Default.Code else Icons.Default.Terminal,
                contentDescription = null,
                tint = PrimaryAccent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = tool.tool_name,
                color = TextPrimary,
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tool.output,
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF090D15))
                    .padding(6.dp)
                    .fillMaxWidth()
            )
        }
    }
}

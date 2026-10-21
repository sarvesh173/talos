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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
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
            val avatarColor = if (msg.sender_name.contains("Alya", ignoreCase = true)) AlyaPink else OmniBlue
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
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isHuman) PrimaryAccent else SurfaceDark)
                    .border(1.dp, if (isHuman) PrimaryAccent else SurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = msg.content,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
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

package com.sarvesh173.talos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The model's reasoning, folded.
 *
 * Ported from dsh-Go's `thinking_pill.dart`: one quiet row, a glyph, a
 * chevron, and a hairline up the glyph column tying the body to the row.
 *
 * The anatomy is `ProcessRow` — lead 24, gap 8, text at 32 — and the rail sits
 * at x = 12 beside the body, not above it. That is the whole fix: the previous
 * version put the rail in the Column *under* the header, so it stacked above the
 * text and read as three unrelated paragraphs instead of one list.
 *
 * While streaming the body is a live window that tracks the newest text rather
 * than the first line, so the row does not change height on every token.
 *
 * @param streaming true while the thought is still arriving.
 */
@Composable
fun ThinkingPill(
    thought: String,
    streaming: Boolean = false,
    isDark: Boolean = false,
    brandColor: Color,
    modifier: Modifier = Modifier
) {
    if (thought.isBlank()) return

    var expanded by remember(thought.isEmpty()) { mutableStateOf(false) }
    val clean = remember(thought) { thought.trim().replace(Regex("\\*\\*"), "").replace(Regex("`"), "") }
    // Collapsed: the first line. Streaming: the tail, where the model is now.
    val preview = remember(clean, streaming) {
        if (streaming) clean.takeLast(160).trim()
        else clean.lineSequence().firstOrNull()?.trim().orEmpty()
    }

    val label = if (streaming) "Thinking" else "Thought for a moment"
    val accent = if (isDark) brandColor.copy(alpha = 0.9f) else brandColor
    val secondary = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.45f)
    val rail = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)

    // One slot for both bodies: the live window and the full text share it, so
    // only one is ever on screen and they cannot cross-fade into each other.
    val open = streaming || expanded

    Column(modifier = modifier.fillMaxWidth()) {
        ProcessRow.Row(
            onClick = { expanded = !expanded },
            lead = {
                Box(
                    modifier = Modifier
                        .size(ProcessRow.LEAD)
                        .clip(RoundedCornerShape(7.dp))
                        .background(accent.copy(alpha = if (isDark) 0.18f else 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    ThinkGlyph(size = 15.dp, color = accent, active = streaming)
                }
            }
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                lineHeight = 18.2.sp,
                fontWeight = FontWeight.Medium,
                color = accent,
                maxLines = 1
            )
            if (preview.isNotBlank() && !open) {
                ProcessRow.Dot(color = secondary)
                Text(
                    text = preview,
                    style = ProcessRow.detail(),
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            ProcessRow.Chevron(open = expanded, color = secondary)
        }

        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(200)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
        ) {
            // The rail is a sibling of the text, in the same Box, so it runs up
            // the glyph column and the text indents to the text column.
            ProcessRow.Thread(color = rail) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Spacer reserves the glyph column; the rail is painted over it.
                    Spacer(modifier = Modifier.width(ProcessRow.INDENT))
                    Text(
                        text = clean,
                        style = ProcessRow.detail(),
                        color = secondary,
                        lineHeight = 16.9.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 4.dp, end = 4.dp, bottom = 8.dp)
                    )
                }
            }
        }
    }
}
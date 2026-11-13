package com.sarvesh173.talos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.minimumInteractiveComponentSize
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
 * Ported from dsh-Go's `thinking_pill.dart`, which is the shape people already
 * recognise from ChatGPT: one quiet row, a chevron, and the first line of the
 * thought as a preview. Tapping opens the whole thing; tapping again closes it.
 *
 * Why folded by default: reasoning is the agent's scratchpad, not the answer.
 * Shown in full it buries the reply under a wall of "let me check…" text, and a
 * long turn can be thousands of characters. One row costs 24dp and answers the
 * only question a reader has — did it think, and about what.
 *
 * @param streaming true while the thought is still arriving; the preview then
 *   tracks the newest text rather than freezing on the first line.
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
    // While streaming, show the tail: that is where the model is now.
    val preview = remember(thought, streaming) {
        if (streaming) thought.trim().takeLast(160).trim()
        else thought.trim().lineSequence().firstOrNull()?.trim().orEmpty()
    }.replace(Regex("\\*\\*"), "").replace(Regex("`"), "")

    val label = if (streaming) "Thinking" else "Thought for a moment"
    val accent = if (isDark) brandColor.copy(alpha = 0.9f) else brandColor
    val secondary = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.45f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .heightIn(min = 32.dp)
                .minimumInteractiveComponentSize()
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 24dp lead column, same as the tool rows: the glyph, the tool
            // icon and a status dot all sit in one vertical line.
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                ThinkGlyph(
                    size = 15.dp,
                    color = accent,
                    active = streaming
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = accent,
                maxLines = 1
            )
            if (preview.isNotBlank() && !expanded) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = preview,
                    fontSize = 12.5.sp,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (expanded) "⌃" else "⌄",
                fontSize = 13.sp,
                color = secondary
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 2.dp, bottom = 6.dp, end = 4.dp)
            ) {
                // A hairline ties the body to the row above it, so it reads as
                // belonging to this turn rather than a stray paragraph.
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .heightIn(min = 12.dp)
                        .background(accent.copy(alpha = 0.25f))
                ) {}
                Spacer(modifier = Modifier.width(0.dp))
                Text(
                    text = thought.trim(),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = secondary,
                    modifier = Modifier.padding(start = 10.dp, top = 4.dp)
                )
            }
        }
    }
}
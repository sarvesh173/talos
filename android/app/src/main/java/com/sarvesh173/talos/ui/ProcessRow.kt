package com.sarvesh173.talos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one anatomy every process row of a turn shares: thinking, tool calls,
 * tool groups, slash commands, notices.
 *
 * Ported from dsh-Go's `stream_row.dart`, which is the shape people already
 * recognise. The geometry is the whole point:
 *
 * ```
 * | glyph | label · preview            ⌄ |   lead 24 + gap 8 + text
 * |       | body, indented to the text   |   the rail sits at x = 12
 * ```
 *
 * Every row puts its glyph in one 24dp column, its text in the next, and an
 * expanded body draws a hairline up the middle of that column — so the glyph,
 * the rail and the body all read as one list rather than three unrelated
 * paragraphs.
 */
object ProcessRow {
    /** Glyph column: the tool icon, the thinking glyph and a status dot all centre here. */
    val LEAD: Dp = 24.dp

    /** Between the glyph column and the text. */
    val GAP: Dp = 8.dp

    /** Where the text column starts; expanded content indents to here. */
    val INDENT: Dp = LEAD + GAP

    /** The rail's x inside the row — the centre of the glyph column. */
    private val RAIL_X: Dp = LEAD / 2

    /** A single row's floor: the 24dp glyph plus 4 above and below. */
    val MIN_HEIGHT: Dp = 32.dp

    /** Label: verbs and summaries, a shade quieter than the answer. */
    val label: @Composable () -> TextStyle = {
        TextStyle(fontSize = 14.sp, lineHeight = 18.2.sp, fontWeight = FontWeight.Normal)
    }

    /** Detail: commands, paths, previews — quieter still. */
    val detail: @Composable () -> TextStyle = {
        TextStyle(fontSize = 13.sp, lineHeight = 16.9.sp, fontWeight = FontWeight.Normal)
    }

    /**
     * The glyph column. Give it a 24dp box and it centres whatever you put in
     * it, so every row's mark lands on the same vertical line.
     */
    @Composable
    fun Lead(content: @Composable () -> Unit) {
        Box(modifier = Modifier.size(LEAD), contentAlignment = Alignment.Center) { content() }
    }

    /** A hairline up the glyph column, tying a body to the row above it. */
    @Composable
    fun Thread(
        color: Color,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        Box(modifier = modifier) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val x = RAIL_X.toPx()
                // Inset 4 at each end so the rail reads as belonging to this row
                // and not as a stray divider spanning the whole list.
                if (size.height <= 8.dp.toPx()) return@Canvas
                drawLine(
                    color = color,
                    start = Offset(x, 4.dp.toPx()),
                    end = Offset(x, size.height - 4.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            content()
        }
    }

    /** The gap between a verb and its summary. */
    @Composable
    fun Dot(color: Color) {
        Spacer(modifier = Modifier.width(6.dp))
        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
    }

    /** The chevron at the end of a row: down when closed, up when open. */
    @Composable
    fun Chevron(open: Boolean, color: Color) {
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (open) "⌃" else "⌄",
            fontSize = 13.sp,
            color = color,
            maxLines = 1
        )
    }

    /** A one-line row: glyph, then label, then anything the caller appends. */
    @Composable
    fun Row(
        modifier: Modifier = Modifier,
        minHeight: Dp = MIN_HEIGHT,
        onClick: (() -> Unit)? = null,
        lead: @Composable () -> Unit,
        content: @Composable () -> Unit
    ) {
        val click = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(click)
                .defaultMinSize(minHeight = minHeight)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            lead()
            Spacer(modifier = Modifier.width(GAP))
            content()
        }
    }
}

/** Padding a rail's body sits behind — the text column, nothing more. */
internal val ProcessBodyPadding: PaddingValues =
    PaddingValues(start = ProcessRow.INDENT, top = 2.dp, bottom = 6.dp)
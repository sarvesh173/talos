package com.sarvesh173.talos.ui.avatar

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * WhatsApp-style composed group avatar.
 *
 * Mental model: the room / CLI tool is the group profile picture, and every
 * agent inside it keeps its own picture. This file composes the group picture
 * out of the members' own identities — it never invents a single unrelated
 * image and never drops a member's identity.
 *
 * Layout (fractional cells inside the square, clipped to a circle; hairline
 * separators; no gradients; no emoji; geometry snapped to a 4dp grid):
 * - 0 members: honest empty state — a dashed hollow ring plus a centre dot.
 *   Never a blank filled circle, never a broken-image icon.
 * - 1 member: one full cell; the member itself, not a broken grid.
 * - 2 members: a split — left half / right half.
 * - 3 members: a triad — full-width top half, two bottom quarters.
 * - 4 members: a quad — 2 x 2 quarters.
 * - 5+ members: a quad whose first three cells are members and whose fourth
 *   cell is a "+N" count (N = memberCount - 3). The count cell is the
 *   overflow indicator; tapping it opens the group.
 *
 * Layering rule: the group ring is drawn in the group's own (monochrome ink)
 * palette. Member cells use member palettes. [groupAccentPaletteId]
 * guarantees the group accent never equals a member accent, which is what
 * makes the composition readable at a glance.
 *
 * Delegate marking: a delegate cell (see [isDelegateIdentity]) carries a
 * notch square at its top edge plus a heavier ring, so a spawned helper is
 * always visually distinct from a main agent.
 *
 * Photo fallback: a Photo member whose image fails to load renders its
 * generated identity (Character) instead — never a broken-image icon and
 * never a null cell (see [resolveCellForm]).
 */

/** Fractional rect of one cell inside the group square, 0..1 on each axis. */
data class CellRect(
    val fx: Float,
    val fy: Float,
    val fw: Float,
    val fh: Float
)

/** Max member cells ever drawn; beyond this the 4th cell becomes "+N". */
const val GROUP_MAX_CELLS: Int = 4

/** Member cells kept in the 5+ quad (the 4th cell is the "+N" count). */
const val GROUP_COUNT_THRESHOLD: Int = 5

/** Max avatars in the overlapping strip before a "+N" chip appears. */
const val STRIP_MAX_CELLS: Int = 4

/** Strip step as a fraction of one avatar: 1 - 0.375 overlap. */
const val STRIP_STEP_FRACTION: Float = 0.625f

/** 4dp grid unit used for glyph insets. */
const val GRID_UNIT_DP: Float = 4f

/**
 * Cell rects for [memberCount] members, in draw order. Pure arithmetic so it
 * is unit-testable without a device and mirrored in
 * tests/test_group_member_avatar.py.
 */
fun groupCellRects(memberCount: Int): List<CellRect> {
    return when {
        memberCount <= 0 -> listOf(CellRect(0f, 0f, 1f, 1f)) // empty state
        memberCount == 1 -> listOf(CellRect(0f, 0f, 1f, 1f))
        memberCount == 2 -> listOf(
            CellRect(0f, 0f, 0.5f, 1f),
            CellRect(0.5f, 0f, 0.5f, 1f)
        )
        memberCount == 3 -> listOf(
            CellRect(0f, 0f, 1f, 0.5f),
            CellRect(0f, 0.5f, 0.5f, 0.5f),
            CellRect(0.5f, 0.5f, 0.5f, 0.5f)
        )
        else -> listOf( // 4: quad; 5+: quad with 4th cell as "+N"
            CellRect(0f, 0f, 0.5f, 0.5f),
            CellRect(0.5f, 0f, 0.5f, 0.5f),
            CellRect(0f, 0.5f, 0.5f, 0.5f),
            CellRect(0.5f, 0.5f, 0.5f, 0.5f)
        )
    }
}

/** Number of member cells actually drawn (the rest is the "+N" count cell). */
fun groupVisibleMemberCount(memberCount: Int): Int {
    if (memberCount <= 0) return 0
    return if (memberCount >= GROUP_COUNT_THRESHOLD) {
        GROUP_MAX_CELLS - 1 // 3 members + count cell
    } else {
        min(memberCount, GROUP_MAX_CELLS)
    }
}

/** Overflow shown in the "+N" cell; 0 means no count cell. */
fun groupOverflowCount(memberCount: Int): Int {
    return if (memberCount >= GROUP_COUNT_THRESHOLD) memberCount - (GROUP_MAX_CELLS - 1) else 0
}

/** Avatars drawn in the strip (members only; the count chip is separate). */
fun stripVisibleMemberCount(memberCount: Int): Int {
    if (memberCount <= 0) return 0
    return if (memberCount > STRIP_MAX_CELLS) STRIP_MAX_CELLS - 1 else memberCount
}

/** Overflow shown in the strip's "+N" chip; 0 means no chip. */
fun stripOverflowCount(memberCount: Int): Int {
    return if (memberCount > STRIP_MAX_CELLS) memberCount - (STRIP_MAX_CELLS - 1) else 0
}

/** X offset (dp) of strip avatar [index] for avatars of [avatarSizeDp] dp. */
fun stripOffsetDp(index: Int, avatarSizeDp: Float): Float {
    return index * avatarSizeDp * STRIP_STEP_FRACTION
}

/** Total strip width (dp), including the "+N" chip when present. */
fun stripWidthDp(memberCount: Int, avatarSizeDp: Float): Float {
    if (memberCount <= 0) return avatarSizeDp
    val cells = stripVisibleMemberCount(memberCount) +
        (if (stripOverflowCount(memberCount) > 0) 1 else 0)
    return avatarSizeDp + (cells - 1) * avatarSizeDp * STRIP_STEP_FRACTION
}

/**
 * A delegate is a spawned helper: a non-group identity that belongs to a
 * group ([AgentIdentity.groupId] set) or whose id carries the delegate
 * prefix. Main agents are the rest. Kept as a pure predicate so tests and
 * any future roster source share one definition.
 */
fun isDelegateIdentity(member: AgentIdentity): Boolean {
    if (member.isGroup) return false
    return member.groupId != null ||
        member.agentId.startsWith("delegate-") ||
        member.agentId.startsWith("delegate_")
}

/**
 * The form a member cell actually renders. A Photo member with no loadable
 * image falls back to its generated Character identity — never a broken
 * image, never a null cell.
 */
fun resolveCellForm(member: AgentIdentity, photoAvailable: Boolean): IdentityForm {
    return if (member.form == IdentityForm.Photo && !photoAvailable) {
        IdentityForm.Character
    } else {
        member.form
    }
}

/**
 * Group accent palette id, guaranteed distinct from every member palette.
 * Mirrors [AgentIdentityResolver.paletteFor] for groups: advance cyclically
 * past any member-taken palette; if every palette is taken, suffix "-g",
 * which no member can ever hold. A null [groupPaletteId] resolves against
 * the first palette. Pure arithmetic; mirrored in the Python tests.
 */
fun groupAccentPaletteId(
    memberPaletteIds: List<String>,
    groupPaletteId: String? = null
): String {
    val ids = AgentIdentityResolver.PALETTE_IDS
    val base = if (groupPaletteId == null) {
        0
    } else {
        val bare = groupPaletteId.removeSuffix("-g")
        val idx = ids.indexOf(bare)
        if (idx < 0) 0 else idx
    }
    for (i in ids.indices) {
        val candidate = ids[(base + i) % ids.size]
        if (candidate !in memberPaletteIds) return candidate
    }
    return ids[base] + "-g"
}

/** Muted member accent for a palette id. Monochrome-first, one accent max. */
fun paletteColor(paletteId: String, isDark: Boolean): Color {
    val bare = paletteId.removeSuffix("-g")
    val value = if (isDark) 0.88f else 0.62f
    return when (bare) {
        "amber" -> Color(0.85f, 0.62f, 0.18f, 1f).let { tinted(it, value) }
        "teal" -> tinted(Color(0.13f, 0.65f, 0.62f, 1f), value)
        "coral" -> tinted(Color(0.94f, 0.45f, 0.36f, 1f), value)
        "violet" -> tinted(Color(0.55f, 0.45f, 0.92f, 1f), value)
        "mint" -> tinted(Color(0.35f, 0.78f, 0.55f, 1f), value)
        "rose" -> tinted(Color(0.90f, 0.45f, 0.60f, 1f), value)
        "sky" -> tinted(Color(0.30f, 0.58f, 0.92f, 1f), value)
        else -> tinted(Color(0.50f, 0.55f, 0.62f, 1f), value) // slate / fallback
    }
}

private fun tinted(base: Color, value: Float): Color {
    // Cheap monochrome-first desaturation toward mid grey.
    val lum = 0.299f * base.red + 0.587f * base.green + 0.114f * base.blue
    val k = 0.55f
    return Color(
        red = (base.red * k + lum * (1f - k)) * value + (1f - value) * 0.25f,
        green = (base.green * k + lum * (1f - k)) * value + (1f - value) * 0.25f,
        blue = (base.blue * k + lum * (1f - k)) * value + (1f - value) * 0.25f
    )
}

/**
 * Composed group avatar for the chat header.
 *
 * @param members ordered members; index 0 is the primary (main) agent.
 * @param size outer diameter; snapped by callers to the 4dp grid.
 * @param onOpenMember called with the member's agentId when its cell is tapped.
 * @param onOpenGroup called when the group ring / count cell / empty state is tapped.
 * @param groupPaletteId the group's own palette (resolved distinct from every
 *   member by [groupAccentPaletteId]); null renders the ring in neutral ink.
 */
@Composable
fun GroupAvatar(
    members: List<AgentIdentity>,
    size: Dp = 56.dp,
    onOpenMember: (String) -> Unit,
    onOpenGroup: () -> Unit = {},
    groupPaletteId: String? = null,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val memberPalettes = remember(members) { members.map { it.paletteId } }
    val resolvedGroupPalette = remember(memberPalettes, groupPaletteId) {
        groupAccentPaletteId(memberPalettes, groupPaletteId)
    }
    // Group ring: the group's own palette (resolved distinct from every
    // member by groupAccentPaletteId), never a member hue (layering rule).
    val groupInk = scheme.onSurface
    val groupRingColor = paletteColor(resolvedGroupPalette, isDark)
    val cellBg = scheme.surfaceContainer
    val hairline = scheme.outline.copy(alpha = 0.45f)

    val rects = groupCellRects(members.size)
    val visibleMembers = groupVisibleMemberCount(members.size)
    val overflow = groupOverflowCount(members.size)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(cellBg)
            .clickable(onClick = onOpenGroup)
            .semantics {
                contentDescription = if (members.isEmpty()) {
                    "Group, no members"
                } else {
                    val names = members.take(visibleMembers).joinToString(", ") { it.displayName }
                    "Group with ${members.size} members: $names" +
                        (if (overflow > 0) ", and $overflow more" else "")
                }
            }
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val canvasW = this.size.width
            val canvasH = this.size.height
            val cx = canvasW / 2f
            val cy = canvasH / 2f
            val outerR = min(canvasW, canvasH) / 2f

            // Hairline separators between cells, then glyphs, then the ring.
            for (rect in rects) {
                val left = rect.fx * canvasW
                val top = rect.fy * canvasH
                val w = rect.fw * canvasW
                val h = rect.fh * canvasH
                drawRect(
                    color = hairline,
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                )
            }

            if (members.isEmpty()) {
                drawDashedCircle(cx, cy, outerR * 0.82f, groupInk.copy(alpha = 0.6f))
                drawCircle(
                    color = groupInk.copy(alpha = 0.5f),
                    radius = outerR * 0.10f,
                    center = Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                )
            } else {
                for (i in 0 until visibleMembers) {
                    val member = members[i]
                    val rect = rects[i]
                    val ccx = (rect.fx + rect.fw / 2f) * canvasW
                    val ccy = (rect.fy + rect.fh / 2f) * canvasH
                    val cellR = min(rect.fw * canvasW, rect.fh * canvasH) / 2f
                    val accent = paletteColor(member.paletteId, isDark)
                    drawMemberGlyph(member, ccx, ccy, cellR * 0.86f, accent, groupInk, isDark)
                    if (isDelegateIdentity(member)) {
                        drawDelegateNotch(ccx, ccy - cellR * 0.86f, groupInk)
                    }
                }
            }

            // Group ring on top: group's own palette, never a member accent.
            drawCircle(
                color = groupRingColor.copy(alpha = 0.85f),
                radius = outerR - 1f,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }

        // Per-member tap targets (transparent, above the canvas).
        if (members.isNotEmpty()) {
            for (i in 0 until visibleMembers) {
                val rect = rects[i]
                val member = members[i]
                Box(
                    modifier = Modifier
                        .offset(
                            x = size * rect.fx,
                            y = size * rect.fy
                        )
                        .size(
                            width = size * rect.fw,
                            height = size * rect.fh
                        )
                        .semantics {
                            contentDescription =
                                "${member.displayName}" +
                                    (if (isDelegateIdentity(member)) ", delegate" else "")
                        }
                        .clickable { onOpenMember(member.agentId) }
                )
            }
            if (overflow > 0) {
                val countRect = rects[GROUP_MAX_CELLS - 1]
                Box(
                    modifier = Modifier
                        .offset(
                            x = size * countRect.fx,
                            y = size * countRect.fy
                        )
                        .size(
                            width = size * countRect.fw,
                            height = size * countRect.fh
                        )
                        .semantics {
                            contentDescription = "$overflow more members"
                        }
                        .clickable(onClick = onOpenGroup),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+$overflow",
                        color = groupInk,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Small overlapping row of member avatars next to a group name, like
 * WhatsApp's participant hint. Up to [STRIP_MAX_CELLS] cells are drawn; beyond
 * that the last cell is an "+N" chip. Empty input renders an honest
 * "No members" label instead of an empty row.
 */
@Composable
fun MemberAvatarStrip(
    members: List<AgentIdentity>,
    size: Dp = 24.dp,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    if (members.isEmpty()) {
        Text(
            text = "No members",
            color = scheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = modifier.semantics {
                contentDescription = "No members"
            }
        )
        return
    }

    val visible = stripVisibleMemberCount(members.size)
    val overflow = stripOverflowCount(members.size)
    val cells = visible + (if (overflow > 0) 1 else 0)
    val stepDp = size * STRIP_STEP_FRACTION
    val totalWidth = size + stepDp * (cells - 1)

    Box(
        modifier = modifier
            .size(width = totalWidth, height = size)
            .semantics {
                val names = members.take(visible).joinToString(", ") { it.displayName }
                contentDescription =
                    "Members: $names" + (if (overflow > 0) ", and $overflow more" else "")
            }
    ) {
        for (i in 0 until visible) {
            val member = members[i]
            val isDel = isDelegateIdentity(member)
            val bitmap = remember(member.photoPath) {
                loadPhotoBitmap(member.photoPath)
            }
            val effectiveForm = resolveCellForm(member, bitmap != null)
            Box(
                modifier = Modifier
                    .offset(x = stepDp * i)
                    .size(size)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainer)
                    .border(
                        width = if (isDel) 2.dp else 1.dp,
                        color = if (isDel) {
                            scheme.onSurface
                        } else {
                            scheme.outline.copy(alpha = 0.5f)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(size)) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height
                    val ccx = canvasW / 2f
                    val ccy = canvasH / 2f
                    val cellR = min(canvasW, canvasH) / 2f
                    val accent = paletteColor(member.paletteId, isDark)
                    if (bitmap != null && effectiveForm == IdentityForm.Photo) {
                        drawImage(
                            image = bitmap,
                            topLeft = Offset(ccx - cellR, ccy - cellR),
                            size = Size(cellR * 2f, cellR * 2f)
                        )
                    } else {
                        drawMemberGlyph(
                            member.copy(form = effectiveForm),
                            ccx, ccy, cellR * 0.86f, accent, scheme.onSurface, isDark
                        )
                    }
                    if (isDel) {
                        drawDelegateNotch(ccx, ccy - cellR * 0.86f, scheme.onSurface)
                    }
                }
            }
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .offset(x = stepDp * visible)
                    .size(size)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHighest)
                    .border(1.dp, scheme.outline.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$overflow",
                    color = scheme.onSurface,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Loads a Photo member image; null on any failure (caller falls back). */
private fun loadPhotoBitmap(photoPath: String?): ImageBitmap? {
    if (photoPath.isNullOrBlank()) return null
    return try {
        BitmapFactory.decodeFile(photoPath)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

private fun DrawScope.drawDashedCircle(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color
) {
    val dashes = 12
    val dashAngle = 360f / dashes
    for (i in 0 until dashes) {
        drawArc(
            color = color,
            startAngle = i * dashAngle,
            sweepAngle = dashAngle * 0.55f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2f, radius * 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        )
    }
}

/** Delegate notch: a small filled square straddling the cell's top edge. */
private fun DrawScope.drawDelegateNotch(cx: Float, topY: Float, color: Color) {
    val half = 3f
    drawRect(
        color = color,
        topLeft = Offset(cx - half, topY - half),
        size = Size(half * 2f, half * 2f)
    )
}

/** Draws one member's own identity (blob / orb / character / photo glyph). */
private fun DrawScope.drawMemberGlyph(
    member: AgentIdentity,
    cx: Float,
    cy: Float,
    radius: Float,
    accent: Color,
    ink: Color,
    isDark: Boolean
) {
    when (member.form) {
        IdentityForm.Blob -> drawBlobGlyph(member, cx, cy, radius, accent, ink)
        IdentityForm.Orb -> drawOrbGlyph(member, cx, cy, radius, accent, ink)
        IdentityForm.Character -> drawCharacterGlyph(member, cx, cy, radius, accent, ink)
        IdentityForm.Photo -> {
            // Photo bytes are drawn by the caller (Strip) or fall back via
            // resolveCellForm; a Photo glyph here is its generated identity.
            drawCharacterGlyph(
                member.copy(form = IdentityForm.Character),
                cx, cy, radius, accent, ink
            )
        }
    }
}

private fun DrawScope.drawBlobGlyph(
    member: AgentIdentity,
    cx: Float,
    cy: Float,
    radius: Float,
    accent: Color,
    ink: Color
) {
    val lobes = member.blobParams.lobeCount.coerceIn(3, 8)
    val asym = member.blobParams.lobeAsymmetry.coerceIn(0f, 1f)
    val steps = 48
    val path = androidx.compose.ui.graphics.Path()
    for (i in 0..steps) {
        val a = (i.toFloat() / steps) * 2f * PI.toFloat()
        val wobble = 1f + asym * 0.18f * sin(a * lobes.toFloat() + member.seed.toFloat())
        val r = radius * wobble
        val px = cx + cos(a) * r
        val py = cy + sin(a) * r
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color = accent.copy(alpha = 0.28f))
    drawPath(
        path,
        color = accent.copy(alpha = 0.85f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = radius * 0.07f)
    )
    drawCircle(
        color = ink.copy(alpha = 0.75f),
        radius = radius * 0.10f,
        center = Offset(cx - radius * 0.22f, cy - radius * 0.10f)
    )
    drawCircle(
        color = ink.copy(alpha = 0.75f),
        radius = radius * 0.10f,
        center = Offset(cx + radius * 0.22f, cy - radius * 0.10f)
    )
}

private fun DrawScope.drawOrbGlyph(
    member: AgentIdentity,
    cx: Float,
    cy: Float,
    radius: Float,
    accent: Color,
    ink: Color
) {
    drawCircle(
        color = ink.copy(alpha = 0.12f),
        radius = radius,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = ink.copy(alpha = 0.5f),
        radius = radius,
        center = Offset(cx, cy),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = radius * 0.05f)
    )
    val dots = member.orbParams.dotCount.coerceIn(4, 64)
    val shown = min(dots, 16)
    for (i in 0 until shown) {
        val a = i * 2.399963f // golden angle: even cover, no clumping
        val r = radius * (0.35f + 0.55f * ((i % 5) / 4f))
        drawCircle(
            color = accent.copy(alpha = 0.8f),
            radius = radius * 0.09f,
            center = Offset(cx + cos(a) * r, cy + sin(a) * r)
        )
    }
}

private fun DrawScope.drawCharacterGlyph(
    member: AgentIdentity,
    cx: Float,
    cy: Float,
    radius: Float,
    accent: Color,
    ink: Color
) {
    val params = member.characterParams
    val bodyH = radius * params.bodyProportion.coerceIn(0.5f, 1.5f)
    // Body: rounded primitive.
    drawCircle(
        color = accent.copy(alpha = 0.25f),
        radius = radius * 0.92f,
        center = Offset(cx, cy + radius * 0.08f)
    )
    drawCircle(
        color = accent.copy(alpha = 0.8f),
        radius = radius * 0.92f,
        center = Offset(cx, cy + radius * 0.08f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = radius * 0.06f)
    )
    if (params.hasAntenna) {
        drawLine(
            color = ink.copy(alpha = 0.7f),
            start = Offset(cx, cy - bodyH * 0.55f),
            end = Offset(cx, cy - radius * 0.95f),
            strokeWidth = radius * 0.06f
        )
        drawCircle(
            color = accent,
            radius = radius * 0.09f,
            center = Offset(cx, cy - radius * 0.95f)
        )
    }
    // Eyes.
    val eyeSep = radius * 0.34f * params.eyeSpacing.coerceIn(0.6f, 1.6f)
    val eyeR = radius * 0.13f * params.eyeSize.coerceIn(0.5f, 1.8f)
    drawCircle(
        color = ink,
        radius = eyeR,
        center = Offset(cx - eyeSep, cy - radius * 0.05f)
    )
    drawCircle(
        color = ink,
        radius = eyeR,
        center = Offset(cx + eyeSep, cy - radius * 0.05f)
    )
    // Mouth curve as a short hairline arc.
    val mouthR = radius * 0.30f
    drawArc(
        color = ink.copy(alpha = 0.8f),
        startAngle = 20f + params.mouthCurve * 30f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(cx - mouthR, cy + radius * 0.18f - mouthR / 2f),
        size = Size(mouthR * 2f, mouthR),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = radius * 0.05f)
    )
}

package com.sarvesh173.talos.ui.avatar
import kotlin.math.abs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Identity gallery: palettes plus a horizontally scrollable row of form
 * cards (Character, Blob, Orb, Photo) each showing a live mini render of
 * the current identity with that form. Selecting a card switches the form
 * for the current agent or group.
 *
 * Design: monochrome-first instrument panel. Canvas #000000, cards
 * #1C1C1E, white / #8E8E93 text, one accent per palette used only for
 * the active control. Sentence case throughout.
 */

private val IdentityCanvas = Color(0xFF000000)
private val IdentityCard = Color(0xFF1C1C1E)
private val IdentityPrimaryText = Color(0xFFFFFFFF)
private val IdentitySecondaryText = Color(0xFF8E8E93)
private val IdentityHairline = Color(0xFF2C2C2E)

/** A monochrome palette: a small neutral set plus exactly one accent. */
data class IdentityPalette(
    val id: String,
    val displayName: String,
    /** The single accent. Used only for the active control. */
    val accent: Color
)

/**
 * The eight named palettes, keyed to
 * [AgentIdentityResolver.PALETTE_IDS]. Neutrals are shared (white,
 * #8E8E93, #1C1C1E on a #000000 canvas); only the accent differs.
 */
val IdentityPalettes: List<IdentityPalette> = listOf(
    IdentityPalette("amber", "Amber", Color(0xFFFF9F0A)),
    IdentityPalette("teal", "Teal", Color(0xFF40C8E0)),
    IdentityPalette("coral", "Coral", Color(0xFFFF6B6B)),
    IdentityPalette("violet", "Violet", Color(0xFFBF5AF2)),
    IdentityPalette("mint", "Mint", Color(0xFF30D158)),
    IdentityPalette("rose", "Rose", Color(0xFFFF375F)),
    IdentityPalette("sky", "Sky", Color(0xFF5AC8D8)),
    IdentityPalette("slate", "Slate", Color(0xFF8E8E93))
)

/** Prefix used when the user enters a custom accent hex. It rides inside
 *  [AgentIdentity.paletteId] so the override persists with the identity. */
const val CUSTOM_PALETTE_PREFIX = "custom:"

/**
 * Resolve the palette for a [paletteId]. Ids written with the [CUSTOM_PALETTE_PREFIX]
 * carry a custom `#RRGGBB` accent hex. Anything unknown falls back to the
 * first palette rather than failing the render.
 */
fun paletteById(paletteId: String): IdentityPalette {
    if (paletteId.startsWith(CUSTOM_PALETTE_PREFIX)) {
        val hex = paletteId.removePrefix(CUSTOM_PALETTE_PREFIX)
        val accent = parseIdentityHex(hex)
        if (accent != null) {
            return IdentityPalette(paletteId, "Custom", accent)
        }
        return IdentityPalettes.first()
    }
    return IdentityPalettes.firstOrNull { it.id == paletteId } ?: IdentityPalettes.first()
}

/** Parse a `#RRGGBB` (or bare `RRGGBB`) hex to a [Color], or null when invalid. */
internal fun parseIdentityHex(raw: String): Color? {
    val h = raw.trim().removePrefix("#")
    if (h.length != 6 || h.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    val value = h.toIntOrNull(16) ?: return null
    return Color(0xFF000000L or (value.toLong() and 0xFFFFFFL))
}

/** Custom hex carried by a custom [paletteId], e.g. "#FFFFFF", or null. */
internal fun customHexOf(paletteId: String): String? {
    if (!paletteId.startsWith(CUSTOM_PALETTE_PREFIX)) return null
    val hex = paletteId.removePrefix(CUSTOM_PALETTE_PREFIX)
    return if (parseIdentityHex(hex) != null) hex else null
}

/**
 * Horizontally scrollable row of identity form cards. Each card shows a
 * live mini render of [identity] in that form, so the four options can be
 * compared side by side before choosing. The selected card carries the
 * palette accent border; selection is also announced in the content
 * description, so the row works without colour perception.
 */
@Composable
fun IdentityGallery(
    identity: AgentIdentity,
    onFormSelected: (IdentityForm) -> Unit,
    modifier: Modifier = Modifier,
    borderTint: Color = IdentityHairline
) {
    val palette = paletteById(identity.paletteId)
    val scroll = rememberScrollState()
    val forms = IdentityForm.entries
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .semantics { contentDescription = "Identity forms, ${forms.size} options" }
        ) {
            for (form in forms) {
                val isSelected = identity.form == form
                val label = formLabel(form)
                Column(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(112.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(IdentityCard)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) palette.accent else borderTint,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onFormSelected(form) }
                        .semantics {
                            contentDescription = "Form $label${if (isSelected) ", selected" else ""}"
                            selected = isSelected
                        }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IdentityMiniRender(
                        identity = identity.copy(form = form),
                        size = 72.dp,
                        animated = true
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        color = if (isSelected) IdentityPrimaryText else IdentitySecondaryText,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** Sentence-case label for a form name. */
internal fun formLabel(form: IdentityForm): String = when (form) {
    IdentityForm.Character -> "Character"
    IdentityForm.Blob -> "Blob"
    IdentityForm.Orb -> "Orb"
    IdentityForm.Photo -> "Photo"
}

/**
 * Live mini render of [identity]. Canvas draws react to every parameter
 * in the model (sizes, counts, speeds, densities, palette accent), and
 * the clock advances unless reduced motion is on or [animated] is false.
 */
@Composable
fun IdentityMiniRender(
    identity: AgentIdentity,
    size: Dp,
    animated: Boolean = true,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val live = animated && !reducedMotion
    var tSeconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(identity.agentId, identity.seed, live) {
        if (!live) {
            tSeconds = 0f
            return@LaunchedEffect
        }
        var startNanos = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val elapsed = (now - startNanos) / 1_000_000_000f
            if (elapsed > 3600f) startNanos = now
            tSeconds = elapsed
        }
    }
    val t = if (live) tSeconds else 0f
    val palette = paletteById(identity.paletteId)
    when (identity.form) {
        IdentityForm.Photo -> IdentityPhotoPreview(
            photoPath = identity.photoPath,
            size = size,
            modifier = modifier,
            accent = palette.accent
        )
        IdentityForm.Character -> Box(
            modifier = modifier
                .size(size)
                .semantics {
                    contentDescription = "Character preview, body proportion " +
                        "%.2f".format(identity.characterParams.bodyProportion)
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size)) {
                drawCharacter(this, identity.characterParams, t, palette)
            }
        }
        IdentityForm.Blob -> Box(
            modifier = modifier
                .size(size)
                .semantics {
                    contentDescription = "Blob preview, ${identity.blobParams.lobeCount} lobes"
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size)) {
                drawBlob(this, identity, t, palette)
            }
        }
        IdentityForm.Orb -> Box(
            modifier = modifier
                .size(size)
                .semantics {
                    contentDescription = "Orb preview, ${identity.orbParams.dotCount} dots"
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size)) {
                drawOrb(this, identity.orbParams, t, palette)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Photo preview (no Coil in this app: BitmapFactory + asImageBitmap only)
// ---------------------------------------------------------------------------

/** Read + downsample an image URI into a bitmap on a background thread. */
internal fun decodeIdentityPhoto(context: Context, uriString: String, maxPx: Int = 512): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val resolver = context.contentResolver
    try {
        resolver.openInputStream(android.net.Uri.parse(uriString))?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    } catch (_: Exception) {
        return null
    }
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    var sample = 1
    while (options.outWidth / sample > maxPx || options.outHeight / sample > maxPx) sample *= 2
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
    return try {
        resolver.openInputStream(android.net.Uri.parse(uriString))?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        }
    } catch (_: Exception) {
        null
    }
}

private fun canOpenIdentityPhoto(context: Context, uriString: String): Boolean = try {
    context.contentResolver.openInputStream(android.net.Uri.parse(uriString))?.use { true } ?: false
} catch (_: Exception) {
    false
}

@Composable
private fun IdentityPhotoPreview(
    photoPath: String?,
    size: Dp,
    modifier: Modifier,
    accent: Color
) {
    val context = LocalContext.current
    var bitmap by remember(photoPath) { mutableStateOf<Bitmap?>(null) }
    var error by remember(photoPath) { mutableStateOf<String?>(null) }
    LaunchedEffect(photoPath) {
        if (photoPath == null) return@LaunchedEffect
        if (!canOpenIdentityPhoto(context, photoPath)) {
            error = "Could not open the selected image"
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) { decodeIdentityPhoto(context, photoPath) }
        if (bitmap == null) error = "Could not open the selected image"
    }
    val bmp = bitmap
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(IdentityCard)
            .semantics {
                contentDescription = when {
                    photoPath == null -> "Photo preview, no image selected"
                    error != null -> "Photo preview, image unavailable"
                    else -> "Photo preview"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (photoPath == null) "Select image" else "Image unavailable",
                    color = if (photoPath == null) IdentitySecondaryText else accent,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Live canvas draws (monochrome: white neutrals + one palette accent)
// ---------------------------------------------------------------------------

private fun drawCharacter(
    scope: DrawScope,
    params: CharacterParams,
    tSeconds: Float,
    palette: IdentityPalette
) {
    val w = scope.size.width
    val h = scope.size.height
    val cx = w / 2f
    val cy = h / 2f
    val ink = Color.White
    val accent = palette.accent

    // Body proportion: 0.5 squat .. 1.5 lanky, applied to head height.
    val bodyScale = params.bodyProportion
    val headW = w * 0.52f
    val headH = h * 0.52f * bodyScale
    val headTop = cy - headH / 2f

    // Eyes: blink oscillates closed at blinkRate blinks per second;
    // 0 means never blinks.
    val open = if (params.blinkRate > 0f) {
        val phase = tSeconds * params.blinkRate
        val frac = phase - kotlin.math.floor(phase)
        if (frac < 0.12f) 0.1f else 1f
    } else {
        1f
    }
    val eyeR = headW * 0.10f * params.eyeSize
    val eyeSep = headW * 0.26f * params.eyeSpacing
    val eyeY = headTop + headH * 0.42f

    // Ears (behind head).
    if (params.hasEars) {
        for (side in listOf(-1f, 1f)) {
            val ex = cx + side * (headW / 2f) * 0.92f
            val ey = headTop + headH * 0.30f
            scope.drawCircle(
                color = ink.copy(alpha = 0.55f),
                radius = headW * 0.14f,
                center = androidx.compose.ui.geometry.Offset(ex, ey)
            )
        }
    }

    // Head: superellipse so it reads as a character, not a bare dot.
    val headPath = Path().apply {
        val rx = headW / 2f
        val ry = headH / 2f
        val n = 2.6f
        val steps = 48
        for (i in 0..steps) {
            val a = (i.toFloat() / steps) * 2f * PI.toFloat()
            val ca = cos(a)
            val sa = sin(a)
            val px = rx * signPow(ca, 2f / n)
            val py = ry * signPow(sa, 2f / n)
            if (i == 0) moveTo(cx + px, cy + py) else lineTo(cx + px, cy + py)
        }
        close()
    }
    scope.drawPath(headPath, color = ink.copy(alpha = 0.14f))
    scope.drawPath(
        headPath,
        color = ink.copy(alpha = 0.55f),
        style = Stroke(width = w * 0.02f)
    )

    // Antenna.
    if (params.hasAntenna) {
        val tipX = cx + headW * 0.10f
        val tipY = headTop - headH * 0.18f
        scope.drawLine(
            color = ink.copy(alpha = 0.7f),
            start = androidx.compose.ui.geometry.Offset(cx, headTop),
            end = androidx.compose.ui.geometry.Offset(tipX, tipY),
            strokeWidth = w * 0.016f
        )
        scope.drawCircle(
            color = accent,
            radius = headW * 0.045f,
            center = androidx.compose.ui.geometry.Offset(tipX, tipY)
        )
    }

    // Eyes as ellipses; shrunk vertically when blinking.
    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * eyeSep
        val rx = eyeR
        val ry = (eyeR * 1.4f * open).coerceAtLeast(w * 0.004f)
        scope.drawOval(
            color = ink,
            topLeft = androidx.compose.ui.geometry.Offset(ex - rx, eyeY - ry),
            size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f)
        )
    }

    // Mouth: arc, curvature driven by mouthCurve (-1 frown .. 1 smile).
    val mouthW = headW * 0.34f
    val mouthY = headTop + headH * 0.70f
    val sag = params.mouthCurve * headH * 0.10f
    val mouth = Path().apply {
        moveTo(cx - mouthW / 2f, mouthY)
        quadraticBezierTo(cx, mouthY + sag * 2f, cx + mouthW / 2f, mouthY)
    }
    scope.drawPath(
        mouth,
        color = ink.copy(alpha = 0.8f),
        style = Stroke(width = w * 0.016f)
    )

    // Particle aura: orbiting sparkles, count scales with density.
    val count = (params.particleAuraDensity * 12f).roundToInt()
    if (count > 0) {
        val orbit = headW * 0.75f
        for (i in 0 until count) {
            val ang = tSeconds * 0.6f + (i.toFloat() / count) * 2f * PI.toFloat()
            scope.drawCircle(
                color = accent.copy(alpha = 0.5f),
                radius = w * 0.012f,
                center = androidx.compose.ui.geometry.Offset(
                    cx + orbit * cos(ang),
                    cy + orbit * sin(ang) * 0.7f
                )
            )
        }
    }
}

private fun drawBlob(
    scope: DrawScope,
    identity: AgentIdentity,
    tSeconds: Float,
    palette: IdentityPalette
) {
    val params = identity.blobParams
    val w = scope.size.width
    val h = scope.size.height
    val cx = w / 2f
    val cy = h / 2f
    val ink = Color.White
    val accent = palette.accent

    // Wobble: viscousness slows and softens the deformation; frequency
    // sets its tempo; amplitude sets how far the silhouette travels.
    val energy = params.wobbleAmplitude * (1f - params.viscousness * 0.7f)
    val phase = tSeconds * params.wobbleFrequency * 2f * PI.toFloat() * (0.4f + 0.6f * (1f - params.viscousness))
    val squash = params.squashStretch * sin(phase) * 0.10f

    val steps = 96
    val path = Path().apply {
        for (i in 0..steps) {
            val theta = (i.toFloat() / steps) * 2f * PI.toFloat()
            var r = 1f
            // Primary lobes.
            r += energy * cos(params.lobeCount * theta + phase) * 0.5f
            // Asymmetry: one lobe leans, so the silhouette is not a star.
            r += params.lobeAsymmetry * 0.18f * cos(theta - 0.6f + phase * 0.2f)
            // Roughness: high harmonics lumpy the edge.
            r += params.surfaceRoughness * 0.10f * (sin(3f * theta + phase) * 0.5f + sin(5f * theta - phase * 0.7f) * 0.5f)
            r = r.coerceIn(0.55f, 1.55f)
            val base = w * 0.34f
            val px = cx + r * base * cos(theta)
            val py = cy + r * base * (1f + squash) * sin(theta)
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    scope.drawPath(path, color = ink.copy(alpha = 0.14f))
    scope.drawPath(path, color = ink.copy(alpha = 0.55f), style = Stroke(width = w * 0.02f))

    // Rim light: a soft arc on the upper edge, intensity scaled.
    if (params.rimLightStrength > 0.02f) {
        val rim = Path().apply {
            val arcStart = -2.4f
            val arcEnd = -0.6f
            val n = 24
            for (i in 0..n) {
                val a = arcStart + (arcEnd - arcStart) * (i.toFloat() / n)
                val base = w * 0.34f
                val px = cx + base * 1.05f * cos(a)
                val py = cy + base * 1.05f * (1f + squash) * sin(a)
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
        }
        scope.drawPath(
            rim,
            color = accent.copy(alpha = params.rimLightStrength * 0.7f),
            style = Stroke(width = w * 0.03f)
        )
    }

    // Inner shadow: a soft sweep along the lower edge, intensity scaled.
    if (params.innerShadowStrength > 0.02f) {
        val sweep = Path().apply {
            val arcStart = 0.4f
            val arcEnd = 2.6f
            val n = 24
            for (i in 0..n) {
                val a = arcStart + (arcEnd - arcStart) * (i.toFloat() / n)
                val base = w * 0.34f
                val px = cx + base * 0.92f * cos(a)
                val py = cy + base * 0.92f * (1f + squash) * sin(a)
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
        }
        scope.drawPath(
            sweep,
            color = ink.copy(alpha = params.innerShadowStrength * 0.5f),
            style = Stroke(width = w * 0.06f)
        )
    }
}

private fun drawOrb(
    scope: DrawScope,
    params: OrbParams,
    tSeconds: Float,
    palette: IdentityPalette
) {
    val w = scope.size.width
    val h = scope.size.height
    val cx = w / 2f
    val cy = h / 2f
    val ink = Color.White
    val accent = palette.accent
    val orbR = h * 0.40f

    // Shell outline.
    scope.drawCircle(
        color = ink.copy(alpha = 0.10f),
        radius = orbR,
        center = androidx.compose.ui.geometry.Offset(cx, cy)
    )
    scope.drawCircle(
        color = ink.copy(alpha = 0.45f),
        radius = orbR,
        center = androidx.compose.ui.geometry.Offset(cx, cy),
        style = Stroke(width = w * 0.015f)
    )

    // Meridian rings.
    for (m in 0 until params.meridianDensity) {
        val t = if (params.meridianDensity <= 1) 0.5f else m.toFloat() / (params.meridianDensity - 1f)
        val rx = orbR * (0.15f + 0.85f * t * t)
        scope.drawOval(
            color = ink.copy(alpha = 0.25f),
            topLeft = androidx.compose.ui.geometry.Offset(cx - rx, cy - orbR * (0.35f + 0.65f * t)),
            size = androidx.compose.ui.geometry.Size(rx * 2f, orbR * (0.35f + 0.65f * t) * 2f),
            style = Stroke(width = w * 0.010f)
        )
        
    }

    // Surface dots: fibonacci sphere, projected. shellDepth pulls them
    // toward the limb (flat disc) or keeps them on the full sphere.
    val count = params.dotCount.coerceIn(1, 64)
    val golden = 2.399963f
    val innerTurn = tSeconds * params.innerRotationSpeed
    for (i in 0 until count) {
        val u = if (count <= 1) 0.5f else i.toFloat() / (count - 1f)
        val y = 1f - 2f * u
        val rr = sqrt((1f - y * y).coerceAtLeast(0f))
        val theta = i * golden + innerTurn
        val limbFactor = (1f - params.shellDepth) * 0.55f
        val px = cx + rr * cos(theta) * orbR * (1f - limbFactor * abs(y))
        val py = cy + y * orbR * (1f - limbFactor * 0.3f)
        // Back hemisphere dimmer: the depth cue.
        val facing = ((cos(theta) + 1f) / 2f).coerceIn(0f, 1f)
        val alpha = (0.30f + 0.60f * facing) .coerceIn(0f, 1f)
        val dotR = (orbR * params.dotRadius).coerceAtLeast(1f)
        scope.drawCircle(
            color = ink.copy(alpha = alpha),
            radius = dotR,
            center = androidx.compose.ui.geometry.Offset(px, py)
        )
        
    }

    // Scan highlight: latitude sweep, speed from scanSpeed.
    if (params.scanSpeed > 0.01f) {
        val sweep = (tSeconds * params.scanSpeed) % (2f * PI.toFloat()) / (2f * PI.toFloat())
        val scanY = cy + (sweep * 2f - 1f) * orbR * 0.95f
        scope.drawLine(
            color = accent.copy(alpha = 0.55f),
            start = androidx.compose.ui.geometry.Offset(cx - orbR * 0.95f, scanY),
            end = androidx.compose.ui.geometry.Offset(cx + orbR * 0.95f, scanY),
            strokeWidth = w * 0.012f
        )
    }
}

private fun signPow(x: Float, n: Float): Float =
    if (x < 0f) -(-x).pow(n.coerceAtLeast(0.01f)) else x.pow(n.coerceAtLeast(0.01f))

package com.sarvesh173.talos.ui

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.platform.LocalDensity
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
enum class EditTool { CROP, ROTATE, FLIP, ADJUST, DRAW }

/**
 * Telegram-style edit-before-send screen for a picked photo, styled in TALOS's own
 * design language (rounded surfaces, the app's dark/light colour pair, brand accent).
 *
 * All edits are non-destructive until Done: the preview always shows the exact pixels
 * that will be exported, and export runs through the same matrix math so preview == result.
 *
 * Deliberately out of scope (kept for a follow-up): captions here (the confirm sheet owns
 * captions), stickers, filters, undo/redo, and zoom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    image: PendingImage,
    isDark: Boolean,
    onDone: (PendingImage) -> Unit,
    onCancel: () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val brandColor = MaterialTheme.colorScheme.primary
    val previewBg = if (isDark) ComposeColor(0xFF141416) else ComposeColor(0xFFF2F2F7)

    val source = remember(image) {
        runCatching {
            BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
        }.getOrNull()
    }
    val sw = source?.width ?: 0
    val sh = source?.height ?: 0
    val strokeWidthPx = 5f * density.density

    var tool by remember(image) { mutableStateOf(EditTool.CROP) }
    var cropRect by remember(image) {
        mutableStateOf(
            RectF(0f, 0f, sw.coerceAtLeast(1).toFloat(), sh.coerceAtLeast(1).toFloat())
        )
    }
    var aspect by remember(image) { mutableStateOf<Float?>(null) }
    var rotation by remember(image) { mutableIntStateOf(0) }
    var flipX by remember(image) { mutableStateOf(false) }
    var flipY by remember(image) { mutableStateOf(false) }
    var brightness by remember(image) { mutableFloatStateOf(1f) }
    var contrast by remember(image) { mutableFloatStateOf(1f) }
    var saturation by remember(image) { mutableFloatStateOf(1f) }
    var strokes = remember(image) { mutableStateListOf<StrokePath>() }
    var currentPoints = remember { mutableStateListOf<Offset>() }
    var dragTemp by remember { mutableStateOf<RectF?>(null) }
    var dragStart by remember { mutableStateOf<PointF?>(null) }
    var strokeColor by remember { mutableStateOf(brandColor) }

    val drawColors = listOf(
        ComposeColor(0xFFFF3B30), ComposeColor(0xFFFF9500), ComposeColor(0xFFFFCC00),
        ComposeColor(0xFF34C759), brandColor, ComposeColor(0xFFAF52DE)
    )

    val layout = remember(sw, sh, cropRect, rotation, flipX, flipY) {
        editLayout(sw, sh, cropRect, rotation, flipX, flipY)
    }
    val outW = layout.outW
    val outH = layout.outH
    val invMatrix = Matrix().also { layout.matrix.invert(it) }
    val full = RectF(0f, 0f, sw.coerceAtLeast(1).toFloat(), sh.coerceAtLeast(1).toFloat())
    val changed = source != null && (
        rotation != 0 || flipX || flipY ||
        cropRect.left != full.left || cropRect.top != full.top ||
        cropRect.right != full.right || cropRect.bottom != full.bottom ||
        brightness != 1f || contrast != 1f || saturation != 1f || strokes.isNotEmpty()
    )

    var viewSize by remember { mutableStateOf(Size.Unspecified) }
    val placement = remember(viewSize, outW, outH) {
        placementMatrix(viewSize.width, viewSize.height, outW, outH)
    }

    fun finish() {
        source?.let { src ->
            scope.launch {
                val bytes = exportImage(src, layout, strokes.toList(), saturation, contrast, brightness)
                withContext(Dispatchers.Main) {
                    onDone(PendingImage(bytes, "Edited ${image.label}", layout.outW, layout.outH))
                }
            }
        }
    }

    fun applyAspect(ratio: Float) {
        var w = cropRect.width().coerceAtMost(sw.toFloat().coerceAtLeast(1f))
        var h = w / ratio
        if (h > sh.toFloat()) {
            h = sh.toFloat()
            w = h * ratio
        }
        if (w > sw.toFloat()) {
            w = sw.toFloat()
            h = w / ratio
        }
        var left = cropRect.centerX() - w / 2f
        var top = cropRect.centerY() - h / 2f
        if (left < 0f) left = 0f
        if (left + w > sw.toFloat()) left = sw.toFloat() - w
        if (top < 0f) top = 0f
        if (top + h > sh.toFloat()) top = sh.toFloat() - h
        cropRect = RectF(
            left.coerceAtLeast(0f), top.coerceAtLeast(0f),
            (left + w).coerceAtMost(sw.toFloat()), (top + h).coerceAtMost(sh.toFloat())
        )
    }

    /** View (preview) pixel -> source bitmap pixel, clamped into source bounds. */
    fun viewToSource(pos: Offset): Offset {
        val scale = placementScale(viewSize.width, viewSize.height, outW, outH)
        val dx = (viewSize.width - outW * scale) / 2f
        val dy = (viewSize.height - outH * scale) / 2f
        val ox = (pos.x - dx) / scale
        val oy = (pos.y - dy) / scale
        val dst = FloatArray(2)
        invMatrix.mapPoints(floatArrayOf(ox, oy), dst)
        return Offset(
            dst[0].coerceIn(0f, sw.coerceAtLeast(1).toFloat()),
            dst[1].coerceIn(0f, sh.coerceAtLeast(1).toFloat())
        )
    }

    fun onDragStart(pos: Offset) {
        val p = viewToSource(pos)
        when (tool) {
            EditTool.CROP -> {
                dragStart = PointF(p.x, p.y)
                dragTemp = RectF(p.x, p.y, p.x, p.y)
                currentPoints.clear()
                currentPoints.add(p)
            }
            EditTool.DRAW -> { currentPoints.clear(); currentPoints.add(p) }
            else -> Unit
        }
    }

    fun onDragMove(pos: Offset) {
        val p = viewToSource(pos)
        when (tool) {
            EditTool.CROP -> {
                val start = dragStart ?: PointF(p.x, p.y)
                val ratio = aspect
                val free = if (ratio != null) {
                    constrainedRect(start, PointF(p.x, p.y), ratio)
                } else {
                    rectBetween(start, PointF(p.x, p.y))
                }
                dragTemp = clampRect(free, sw, sh)
            }
            EditTool.DRAW -> currentPoints.add(p)
            else -> Unit
        }
    }

    fun onDragEnd() {
        when (tool) {
            EditTool.CROP -> {
                dragTemp?.let { temp ->
                    if (temp.width() >= 20f && temp.height() >= 20f) cropRect = temp
                    dragTemp = null
                }
            }
            EditTool.DRAW -> {
                if (currentPoints.size >= 2) {
                    strokes.add(
                        StrokePath(currentPoints.toList(), strokeColor, strokeWidthPx, ShapeType.FREEHAND)
                    )
                }
                currentPoints.clear()
            }
            else -> Unit
        }
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = 40.dp)
            ) {
                Text(
                    text = "Cancel",
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) ComposeColor.White else ComposeColor(0xFF1D1D1F)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Edit photo",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) ComposeColor.White else ComposeColor(0xFF1D1D1F),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(110.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { finish() },
                enabled = changed && source != null,
                modifier = Modifier.heightIn(min = 40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = brandColor)
            ) {
                Text(text = "Done", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (source == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Preview unavailable",
                    fontSize = 12.sp,
                    color = if (isDark) ComposeColor.White.copy(alpha = 0.5f) else ComposeColor.Black.copy(alpha = 0.45f)
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(previewBg)
                    .onSizeChanged { viewSize = it.size }
                    .pointerInput(placement) {
                        detectDragGestures(
                            onDragStart = ::onDragStart,
                            onDrag = { change, _ -> onDragMove(change.position) },
                            onDragEnd = ::onDragEnd
                        )
                    }
            ) {
                drawIntoCanvas { g ->
                    g.concat(placement)

                    val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    if (brightness != 1f || contrast != 1f || saturation != 1f) {
                        imgPaint.colorFilter = ColorMatrixColorFilter(adjustMatrix(saturation, contrast, brightness))
                    }
                    g.drawBitmap(source, layout.matrix, imgPaint)

                    val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    strokes.forEach { s ->
                        sp.color = Color.argb((s.color.alpha * 255).toInt(), s.color.red, s.color.green, s.color.blue)
                        sp.strokeWidth = s.strokeWidth
                        val path = Path().apply {
                            moveTo(s.points.first().x, s.points.first().y)
                            s.points.drop(1).forEach { pt -> lineTo(pt.x, pt.y) }
                        }
                        path.transform(layout.matrix)
                        g.drawPath(path, sp)
                    }
                    if (currentPoints.size >= 2) {
                        sp.color = Color.argb(
                            (strokeColor.alpha * 255).toInt(), strokeColor.red, strokeColor.green, strokeColor.blue
                        )
                        sp.strokeWidth = strokeWidthPx
                        val path = Path().apply {
                            moveTo(currentPoints.first().x, currentPoints.first().y)
                            currentPoints.drop(1).forEach { pt -> lineTo(pt.x, pt.y) }
                        }
                        path.transform(layout.matrix)
                        g.drawPath(path, sp)
                    }

                    if (tool == EditTool.CROP) {
                        val frame = Paint().apply {
                            color = if (isDark) Color.White else Color.Black
                            alpha = 0.18f
                            style = Paint.Style.STROKE
                            strokeWidth = 2f
                        }
                        g.drawRect(
                            2f, 2f,
                            outW.coerceAtLeast(4).toFloat() - 2f,
                            outH.coerceAtLeast(4).toFloat() - 2f,
                            frame
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EditToolButton(EditTool.CROP, "Crop", tool == EditTool.CROP, isDark) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                tool = EditTool.CROP
            }
            EditToolButton(EditTool.ROTATE, "Rotate", tool == EditTool.ROTATE, isDark) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                tool = EditTool.ROTATE
            }
            EditToolButton(EditTool.FLIP, "Flip", tool == EditTool.FLIP, isDark) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                tool = EditTool.FLIP
            }
            EditToolButton(EditTool.ADJUST, "Adjust", tool == EditTool.ADJUST, isDark) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                tool = EditTool.ADJUST
            }
            EditToolButton(EditTool.DRAW, "Draw", tool == EditTool.DRAW, isDark) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                tool = EditTool.DRAW
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (tool) {
            EditTool.CROP -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        null to "Free", 1f to "1:1", 4f / 3f to "4:3", 16f / 9f to "16:9"
                    ).forEach { (ratio, label) ->
                        val selected = aspect == ratio
                        FilterChip(
                            selected = selected,
                            onClick = {
                                aspect = ratio
                                if (ratio != null) applyAspect(ratio)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            label = { Text(text = label, fontSize = 11.sp) }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            cropRect = RectF(0f, 0f, sw.toFloat(), sh.toFloat())
                            aspect = null
                        }
                    ) {
                        Text(text = "Reset", fontSize = 12.sp)
                    }
                }
            }
            EditTool.ROTATE -> {
                Button(
                    onClick = {
                        rotation = (rotation + 90) % 360
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    modifier = Modifier.heightIn(min = 40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                ) {
                    Text(text = "Rotate 90°", fontWeight = FontWeight.Medium)
                }
            }
            EditTool.FLIP -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { flipX = !flipX },
                        modifier = Modifier.heightIn(min = 40.dp).weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                    ) {
                        Text(text = "Flip horizontal", fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = { flipY = !flipY },
                        modifier = Modifier.heightIn(min = 40.dp).weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                    ) {
                        Text(text = "Flip vertical", fontWeight = FontWeight.Medium)
                    }
                }
            }
            EditTool.ADJUST -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    adjustRow("Brightness", brightness, { brightness = it })
                    adjustRow("Contrast", contrast, { contrast = it })
                    adjustRow("Saturation", saturation, { saturation = it })
                }
            }
            EditTool.DRAW -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    drawColors.forEach { c ->
                        val selected = strokeColor == c
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    if (selected) 2.5.dp else 0.8.dp,
                                    if (selected) ComposeColor.White else ComposeColor.Transparent,
                                    CircleShape
                                )
                                .clickable {
                                    strokeColor = c
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                            contentAlignment = Alignment.Center
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { strokes.clear(); currentPoints.clear() }
                    ) {
                        Text(text = "Clear strokes", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

/** One icon button in the editor's tool row, drawn in TALOS style instead of copied icons. */
@Composable
private fun EditToolButton(tool: EditTool, label: String, active: Boolean, isDark: Boolean, onClick: () -> Unit) {
    val brandColor = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (active) brandColor.copy(alpha = 0.12f) else if (isDark) ComposeColor.White.copy(alpha = 0.07f) else ComposeColor.Black.copy(alpha = 0.04f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            EditIcon(tool, active)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (active) brandColor else if (isDark) ComposeColor.White.copy(alpha = 0.65f) else ComposeColor.Black.copy(alpha = 0.60f)
        )
    }
}

/** Small monochrome tool icons drawn with Compose Canvas (no asset library needed). */
@Composable
private fun EditIcon(tool: EditTool, active: Boolean) {
    val tint = if (active) MaterialTheme.colorScheme.primary else ComposeColor(0xFF1D1D1F)
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val s = minOf(w, h)
        val pad = 0.25f * s
        val c = s / 2f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, sw: Float = 2.2f) {
            drawLine(
                color = tint, start = Offset(x1, y1), end = Offset(x2, y2),
                strokeWidth = sw, cap = StrokeCap.Round
            )
        }
        when (tool) {
            EditTool.CROP -> {
                val seg = 0.30f * s
                // four corner brackets
                line(pad, pad, pad + seg, pad)
                line(pad, pad, pad, pad + seg)
                line(w - pad, pad, w - pad - seg, pad)
                line(w - pad, pad, w - pad, pad + seg)
                line(pad, h - pad, pad + seg, h - pad)
                line(pad, h - pad, pad, h - pad - seg)
                line(w - pad, h - pad, w - pad - seg, h - pad)
                line(w - pad, h - pad, w - pad, h - pad - seg)
            }
            EditTool.ROTATE -> {
                val r = 0.32f * s
                drawArc(
                    color = tint,
                    startDegrees = -135f,
                    sweepDegrees = 270f,
                    useCenter = false,
                    style = Stroke(width = 2.2f),
                    topLeft = Offset(c - r, c - r),
                    size = Size(2 * r, 2 * r)
                )
                // arrowhead at end of the arc (angle -135 + 270 = 135 deg)
                val a = 135f * Math.PI.toFloat() / 180f
                val ex = c + r * cos(a)
                val ey = c + r * sin(a)
                val l = 0.18f * s
                line(ex, ey, ex - l * cos(a - 0.5f), ey - l * sin(a - 0.5f))
                line(ex, ey, ex - l * cos(a + 0.5f), ey - l * sin(a + 0.5f))
            }
            EditTool.FLIP -> {
                val y1 = 0.40f * h
                val y2 = 0.60f * h
                val x1 = 0.22f * w
                val x2 = 0.50f * w
                val x3 = 0.78f * w
                val head = 0.10f * s
                line(x1, y1, x2 - head, y1)
                line(x2, y1, x2 - head, y1 - head * 0.6f)
                line(x2, y1, x2 - head, y1 + head * 0.6f)
                line(x3, y2, x2 + head, y2)
                line(x2, y2, x2 + head, y2 - head * 0.6f)
                line(x2, y2, x2 + head, y2 + head * 0.6f)
            }
            EditTool.ADJUST -> {
                val r = 0.22f * s
                drawCircle(color = tint, offset = Offset(c, c), radius = r, style = Stroke(width = 2.2f))
                val ray = 0.26f * s
                val shortRay = 0.16f * s
                repeat(8) { i ->
                    val a = i * Math.PI.toFloat() / 4f
                    val (rOut, rIn) = if (i % 2 == 0) Pair(ray, shortRay) else Pair(shortRay, ray)
                    line(
                        c + rIn * cos(a), c + rIn * sin(a),
                        c + rOut * cos(a), c + rOut * sin(a)
                    )
                }
            }
            EditTool.DRAW -> {
                line(pad, h - pad, w - pad, pad, sw = 3.4f)
            }
        }
    }
}

/** Row for one adjustment slider. */
@Composable
private fun adjustRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, modifier = Modifier.width(56.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0.5f..2f,
            steps = 60,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics { contentDescription = "$label ${(value * 100).roundToInt()}%" }
        )
        Text(text = "${(value * 100).roundToInt()}%", fontSize = 12.sp, modifier = Modifier.width(34.dp))
    }
}

/** Rect from two points (free-form crop). */
private fun rectBetween(a: PointF, b: PointF): RectF =
    RectF(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))

/** Rect from anchor point p1 toward p2, locked to aspect ratio w/h. */
private fun constrainedRect(p1: PointF, p2: PointF, ratio: Float): RectF {
    val dx = p2.x - p1.x
    val dy = p2.y - p1.y
    var w = abs(dx)
    var h = w / ratio
    if (h <= 0f) return rectBetween(p1, p2)
    val left = if (dx >= 0f) p1.x else p1.x - w
    val top = if (dy >= 0f) p1.y else p1.y - h
    return RectF(left, top, left + w, top + h)
}

/** Clamp a rect into [0, maxW] x [0, maxH], keeping it non-empty where possible. */
private fun clampRect(r: RectF, maxW: Int, maxH: Int): RectF {
    val mw = maxW.coerceAtLeast(1).toFloat()
    val mh = maxH.coerceAtLeast(1).toFloat()
    var left = r.left.coerceIn(0f, mw)
    var top = r.top.coerceIn(0f, mh)
    var right = r.right.coerceIn(left, mw)
    var bottom = r.bottom.coerceIn(top, mh)
    return RectF(left, top, right, bottom)
}

/** Scale + centre placement that fits the output rect into the view (never upscales). */
private fun placementMatrix(viewW: Float, viewH: Float, outW: Int, outH: Int): Matrix {
    if (!viewW.isFinite || !viewH.isFinite || outW <= 0 || outH <= 0) return Matrix()
    val scale = minOf(viewW / outW, viewH / outH, 1f)
    return Matrix().apply {
        postScale(scale, scale)
        postTranslate((viewW - outW * scale) / 2f, (viewH - outH * scale) / 2f)
    }
}

private fun placementScale(viewW: Float, viewH: Float, outW: Int, outH: Int): Float =
    if (!viewW.isFinite || !viewH.isFinite || outW <= 0 || outH <= 0) 1f else minOf(viewW / outW, viewH / outH, 1f)

/**
 * Builds the source->output matrix for the current crop/rotate/fill state.
 *
 * Crop rect lives in source pixels; output is exactly that rect, rotated/flipped about
 * its own centre, then shifted to the origin. Everything is rigid (no scale), so the
 * inverse transform is exact and strokes stay pixel-aligned with the image.
 */
private fun editLayout(sw: Int, sh: Int, crop: RectF, rotation: Int, flipX: Boolean, flipY: Boolean): EditLayout {
    val cw = crop.width().coerceAtLeast(1f)
    val ch = crop.height().coerceAtLeast(1f)
    val m = Matrix()
    // Operations are pre-concatenated so they apply to the point in this physical order:
    // 1) shift crop top-left to origin, 2) centre on origin, 3) flip, 4) rotate, 5) un-centre,
    // 6) shift the transformed rect's bounding box to the origin.
    m.preConcat(Matrix().setTranslate(-crop.left, -crop.top))
    m.preConcat(Matrix().setTranslate(-cw / 2f, -ch / 2f))
    if (flipX) m.preConcat(Matrix().setScale(-1f, 1f))
    if (flipY) m.preConcat(Matrix().setScale(1f, -1f))
    m.preConcat(Matrix().setRotate(rotation.toFloat()))
    m.preConcat(Matrix().setTranslate(cw / 2f, ch / 2f))
    val bounds = RectF()
    m.mapRect(bounds, RectF(0f, 0f, cw, ch))
    m.preConcat(Matrix().setTranslate(-bounds.left, -bounds.top))
    return EditLayout(m, bounds.width().roundToInt(), bounds.height().roundToInt())
}

/** Result of [editLayout]: the transform and the output bitmap size in pixels. */
private data class EditLayout(val matrix: Matrix, val outW: Int, val outH: Int)

/** Brightness/contrast/saturation as a single 5x5 colour matrix (row-vector convention). */
private fun adjustMatrix(saturation: Float, contrast: Float, brightness: Float): FloatArray {
    val s = floatArrayOf(
        0.213f + 0.787f * saturation, 0.715f - 0.715f * saturation, 0.072f - 0.072f * saturation, 0f, 0f,
        0.213f - 0.213f * saturation, 0.715f + 0.285f * saturation, 0.072f - 0.072f * saturation, 0f, 0f,
        0.213f - 0.213f * saturation, 0.715f - 0.715f * saturation, 0.072f + 0.928f * saturation, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
        0f, 0f, 0f, 0f, 1f
    )
    val t = (1f - contrast) * 0.5f
    val c = floatArrayOf(
        contrast, 0f, 0f, 0f, t,
        0f, contrast, 0f, 0f, t,
        0f, 0f, contrast, 0f, t,
        0f, 0f, 0f, 1f, 0f,
        0f, 0f, 0f, 0f, 1f
    )
    val br = brightness - 1f
    val b = floatArrayOf(
        1f, 0f, 0f, 0f, br,
        0f, 1f, 0f, 0f, br,
        0f, 0f, 1f, 0f, br,
        0f, 0f, 0f, 1f, 0f,
        0f, 0f, 0f, 0f, 1f
    )
    val sc = FloatArray(25)
    matMul(sc, s, c)
    val m = FloatArray(25)
    matMul(m, sc, b)
    return m
}

private fun matMul(out: FloatArray, a: FloatArray, b: FloatArray) {
    for (i in 0 until 5) {
        for (j in 0 until 5) {
            var sum = 0f
            for (k in 0 until 5) sum += a[i * 5 + k] * b[k * 5 + j]
            out[i * 5 + j] = sum
        }
    }
}

/**
 * Renders the edited image (and strokes) exactly as shown in the preview, then compresses
 * to JPEG. Runs on the caller's background context.
 */
private suspend fun exportImage(
    source: Bitmap,
    layout: EditLayout,
    strokes: List<StrokePath>,
    saturation: Float,
    contrast: Float,
    brightness: Float
): ByteArray = withContext(Dispatchers.IO) {
    val bitmap = Bitmap.createBitmap(layout.outW, layout.outH, Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(adjustMatrix(saturation, contrast, brightness))
    }
    canvas.drawBitmap(source, layout.matrix, paint)

    val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { s ->
        sp.color = Color.argb((s.color.alpha * 255).toInt(), s.color.red, s.color.green, s.color.blue)
        sp.strokeWidth = s.strokeWidth
        val path = Path().apply {
            moveTo(s.points.first().x, s.points.first().y)
            s.points.drop(1).forEach { pt -> lineTo(pt.x, pt.y) }
        }
        path.transform(layout.matrix)
        canvas.drawPath(path, sp)
    }
    ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        out.toByteArray()
    }
}

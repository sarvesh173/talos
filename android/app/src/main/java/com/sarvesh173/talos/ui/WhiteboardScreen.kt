package com.sarvesh173.talos.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.*
import kotlin.math.abs
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient

enum class OpenBoardTool {
    PEN, HIGHLIGHTER, LINE, RECTANGLE, CIRCLE, ERASER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteboardScreen(
    client: TalosClient,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val pages = remember { mutableStateListOf(mutableStateListOf<StrokePath>()) }
    val redoPages = remember { mutableStateListOf(mutableStateListOf<StrokePath>()) }
    var currentPageIndex by remember { mutableIntStateOf(0) }

    if (pages.isEmpty()) {
        pages.add(mutableStateListOf())
        redoPages.add(mutableStateListOf())
    }
    val safeIndex = currentPageIndex.coerceIn(0, pages.size - 1)
    val paths = pages[safeIndex]
    val redoPaths = redoPages[safeIndex]
    var currentPoints = remember { mutableStateListOf<Offset>() }
    val defaultColor = MaterialTheme.colorScheme.primary
    var currentColor by remember { mutableStateOf(defaultColor) }
    var strokeWidth by remember { mutableFloatStateOf(6f) }
    var activeTool by remember { mutableStateOf(OpenBoardTool.PEN) }
    var canvasBackground by remember { mutableStateOf("DotGrid") }

    val colorPalette = listOf(
        MaterialTheme.colorScheme.primary, // Signature Iris Purple
        Color(0xFF60A5FA), // Azure Blue
        Color(0xFF34D399), // Mint Emerald
        Color(0xFFF87171), // Coral Rose
        Color(0xFFFFFFFF)  // Chalk White
    )

    val effectiveColor = when (activeTool) {
        OpenBoardTool.PEN, OpenBoardTool.LINE, OpenBoardTool.RECTANGLE, OpenBoardTool.CIRCLE -> currentColor
        OpenBoardTool.HIGHLIGHTER -> currentColor.copy(alpha = 0.35f)
        OpenBoardTool.ERASER -> when (canvasBackground) {
            "DarkSlate" -> Color(0xFF1E293B)
            else -> Color(0xFF0A0C10)
        }
    }
    val effectiveWidth = when (activeTool) {
        OpenBoardTool.PEN, OpenBoardTool.LINE, OpenBoardTool.RECTANGLE, OpenBoardTool.CIRCLE -> strokeWidth
        OpenBoardTool.HIGHLIGHTER -> 24f
        OpenBoardTool.ERASER -> 32f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "OpenBoard Whiteboard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Page ${safeIndex + 1}/${pages.size} • ${paths.size} strokes • ${activeTool.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
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
                    // Previous Page
                    IconButton(
                        onClick = {
                            if (currentPageIndex > 0) {
                                currentPageIndex--
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        enabled = currentPageIndex > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev Page", modifier = Modifier.size(18.dp))
                    }
                    // Next Page
                    IconButton(
                        onClick = {
                            if (currentPageIndex < pages.size - 1) {
                                currentPageIndex++
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        enabled = currentPageIndex < pages.size - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page", modifier = Modifier.size(18.dp))
                    }
                    // Add Page
                    IconButton(
                        onClick = {
                            pages.add(mutableStateListOf())
                            redoPages.add(mutableStateListOf())
                            currentPageIndex = pages.size - 1
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            Toast.makeText(context, "Page ${pages.size} added!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.PostAdd, contentDescription = "Add Page", tint = MaterialTheme.colorScheme.primary)
                    }
                    // Undo
                    IconButton(
                        onClick = {
                            if (paths.isNotEmpty()) {
                                val last = paths.removeAt(paths.size - 1)
                                redoPaths.add(last)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        enabled = paths.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    // Redo
                    IconButton(
                        onClick = {
                            if (redoPaths.isNotEmpty()) {
                                val last = redoPaths.removeAt(redoPaths.size - 1)
                                paths.add(last)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        enabled = redoPaths.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    // Clear
                    IconButton(
                        onClick = {
                            redoPaths.clear()
                            paths.clear()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        enabled = paths.isNotEmpty()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = MaterialTheme.colorScheme.error)
                    }
                    // Send to Council / Chamber with rendered bitmap
                    IconButton(
                        onClick = {
                            if (paths.isNotEmpty()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val b64 = exportStrokesToPngBase64(paths, background = canvasBackground)
                                if (b64 != null) {
                                    client.sendMessage("[OpenBoard Sketch: ${paths.size} strokes]\ndata:image/png;base64,$b64")
                                } else {
                                    client.sendMessage("[OpenBoard Sketch Attached: ${paths.size} strokes]")
                                }
                                Toast.makeText(context, "Sketch attached to Council Room!", Toast.LENGTH_SHORT).show()
                                onBack()
                            } else {
                                Toast.makeText(context, "Draw something first!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Tool Selection & Grid Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = activeTool == OpenBoardTool.PEN,
                                onClick = {
                                    activeTool = OpenBoardTool.PEN
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                label = { Text("Pen", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = activeTool == OpenBoardTool.HIGHLIGHTER,
                                onClick = {
                                    activeTool = OpenBoardTool.HIGHLIGHTER
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                label = { Text("High", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = activeTool == OpenBoardTool.LINE,
                                onClick = {
                                    activeTool = OpenBoardTool.LINE
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                label = { Text("Line", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = activeTool == OpenBoardTool.RECTANGLE,
                                onClick = {
                                    activeTool = OpenBoardTool.RECTANGLE
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                label = { Text("Rect", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = activeTool == OpenBoardTool.CIRCLE,
                                onClick = {
                                    activeTool = OpenBoardTool.CIRCLE
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                label = { Text("Oval", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = activeTool == OpenBoardTool.ERASER,
                                onClick = {
                                    activeTool = OpenBoardTool.ERASER
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                label = { Text("Eraser", fontSize = 10.sp) }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = strokeWidth == 4f,
                                onClick = { strokeWidth = 4f },
                                label = { Text("Thin", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = strokeWidth == 8f,
                                onClick = { strokeWidth = 8f },
                                label = { Text("Bold", fontSize = 10.sp) }
                            )
                            val backgrounds = listOf("DotGrid", "GraphGrid", "Ruled", "Obsidian")
                            val nextBg = backgrounds[(backgrounds.indexOf(canvasBackground) + 1).coerceAtLeast(0) % backgrounds.size]
                            FilterChip(
                                selected = canvasBackground != "Obsidian",
                                onClick = {
                                    canvasBackground = nextBg
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                label = { Text(canvasBackground, fontSize = 10.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Color Palette with 48dp minimum touch bounds
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorPalette.forEach { color ->
                            val isSelected = currentColor == color
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { currentColor = color }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            if (isSelected) 2.5.dp else 1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = when (canvasBackground) {
                "DarkSlate" -> Color(0xFF1E293B)
                else -> Color(0xFF0A0C10)
            },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(activeTool, currentColor, strokeWidth, canvasBackground) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPoints = mutableStateListOf(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentPoints.add(change.position)
                            },
                            onDragEnd = {
                                if (currentPoints.isNotEmpty()) {
                                    val shapeType = when (activeTool) {
                                        OpenBoardTool.LINE -> ShapeType.LINE
                                        OpenBoardTool.RECTANGLE -> ShapeType.RECTANGLE
                                        OpenBoardTool.CIRCLE -> ShapeType.CIRCLE
                                        else -> ShapeType.FREEHAND
                                    }
                                    val committedPoints = if (shapeType == ShapeType.FREEHAND) {
                                        currentPoints.toList()
                                    } else {
                                        listOf(currentPoints.first(), currentPoints.last())
                                    }
                                    paths.add(
                                        StrokePath(
                                            points = committedPoints,
                                            color = effectiveColor,
                                            strokeWidth = effectiveWidth,
                                            shapeType = shapeType
                                        )
                                    )
                                    redoPaths.clear()
                                    currentPoints = mutableStateListOf()
                                }
                            }
                        )
                    }
            ) {
                // OpenBoard canvas background styles
                when (canvasBackground) {
                    "DotGrid" -> {
                        val spacing = 28.dp.toPx()
                        var x = spacing
                        while (x < size.width) {
                            var y = spacing
                            while (y < size.height) {
                                drawCircle(
                                    color = Color(0xFF334155),
                                    radius = 1.5f,
                                    center = Offset(x, y)
                                )
                                y += spacing
                            }
                            x += spacing
                        }
                    }
                    "GraphGrid" -> {
                        val spacing = 24.dp.toPx()
                        var x = spacing
                        while (x < size.width) {
                            drawLine(
                                color = Color(0xFF263045),
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1f
                            )
                            x += spacing
                        }
                        var y = spacing
                        while (y < size.height) {
                            drawLine(
                                color = Color(0xFF263045),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f
                            )
                            y += spacing
                        }
                    }
                    "Ruled" -> {
                        val spacing = 32.dp.toPx()
                        var y = spacing
                        while (y < size.height) {
                            drawLine(
                                color = Color(0xFF2B3648),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f
                            )
                            y += spacing
                        }
                    }
                }

                // Render committed paths
                paths.forEach { strokePath ->
                    when (strokePath.shapeType) {
                        ShapeType.FREEHAND -> {
                            if (strokePath.points.size > 1) {
                                val path = Path().apply {
                                    moveTo(strokePath.points.first().x, strokePath.points.first().y)
                                    for (i in 1 until strokePath.points.size) {
                                        lineTo(strokePath.points[i].x, strokePath.points[i].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = strokePath.color,
                                    style = Stroke(
                                        width = strokePath.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                        ShapeType.LINE -> {
                            if (strokePath.points.size >= 2) {
                                drawLine(
                                    color = strokePath.color,
                                    start = strokePath.points.first(),
                                    end = strokePath.points.last(),
                                    strokeWidth = strokePath.strokeWidth,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                        ShapeType.RECTANGLE -> {
                            if (strokePath.points.size >= 2) {
                                val start = strokePath.points.first()
                                val end = strokePath.points.last()
                                val topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y))
                                val rectSize = androidx.compose.ui.geometry.Size(
                                    abs(start.x - end.x),
                                    abs(start.y - end.y)
                                )
                                drawRect(
                                    color = strokePath.color,
                                    topLeft = topLeft,
                                    size = rectSize,
                                    style = Stroke(width = strokePath.strokeWidth)
                                )
                            }
                        }
                        ShapeType.CIRCLE -> {
                            if (strokePath.points.size >= 2) {
                                val start = strokePath.points.first()
                                val end = strokePath.points.last()
                                val topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y))
                                val ovalSize = androidx.compose.ui.geometry.Size(
                                    abs(start.x - end.x),
                                    abs(start.y - end.y)
                                )
                                drawOval(
                                    color = strokePath.color,
                                    topLeft = topLeft,
                                    size = ovalSize,
                                    style = Stroke(width = strokePath.strokeWidth)
                                )
                            }
                        }
                    }
                }

                // Render active in-progress stroke
                if (currentPoints.size > 1) {
                    when (activeTool) {
                        OpenBoardTool.PEN, OpenBoardTool.HIGHLIGHTER, OpenBoardTool.ERASER -> {
                            val activePath = Path().apply {
                                moveTo(currentPoints.first().x, currentPoints.first().y)
                                for (i in 1 until currentPoints.size) {
                                    lineTo(currentPoints[i].x, currentPoints[i].y)
                                }
                            }
                            drawPath(
                                path = activePath,
                                color = effectiveColor,
                                style = Stroke(
                                    width = effectiveWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        OpenBoardTool.LINE -> {
                            drawLine(
                                color = effectiveColor,
                                start = currentPoints.first(),
                                end = currentPoints.last(),
                                strokeWidth = effectiveWidth,
                                cap = StrokeCap.Round
                            )
                        }
                        OpenBoardTool.RECTANGLE -> {
                            val start = currentPoints.first()
                            val end = currentPoints.last()
                            val topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y))
                            val rectSize = androidx.compose.ui.geometry.Size(
                                abs(start.x - end.x),
                                abs(start.y - end.y)
                            )
                            drawRect(
                                color = effectiveColor,
                                topLeft = topLeft,
                                size = rectSize,
                                style = Stroke(width = effectiveWidth)
                            )
                        }
                        OpenBoardTool.CIRCLE -> {
                            val start = currentPoints.first()
                            val end = currentPoints.last()
                            val topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y))
                            val ovalSize = androidx.compose.ui.geometry.Size(
                                abs(start.x - end.x),
                                abs(start.y - end.y)
                            )
                            drawOval(
                                color = effectiveColor,
                                topLeft = topLeft,
                                size = ovalSize,
                                style = Stroke(width = effectiveWidth)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * OpenBoard Canvas Exporter: renders committed vector strokes to PNG Base64.
 */
fun exportStrokesToPngBase64(paths: List<StrokePath>, width: Int = 720, height: Int = 480, background: String = "DotGrid"): String? {
    if (paths.isEmpty()) return null
    return try {
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(0xFF0F1117.toInt())

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }

        // Draw canvas background grid
        val gridPaint = android.graphics.Paint().apply {
            color = 0xFF2B3245.toInt()
            isAntiAlias = true
            strokeWidth = 1f
        }

        when (background) {
            "DotGrid" -> {
                val spacing = 28f
                var x = spacing
                while (x < width) {
                    var y = spacing
                    while (y < height) {
                        canvas.drawCircle(x, y, 1.5f, gridPaint)
                        y += spacing
                    }
                    x += spacing
                }
            }
            "GraphGrid" -> {
                val spacing = 24f
                var x = spacing
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
                    x += spacing
                }
                var y = spacing
                while (y < height) {
                    canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
                    y += spacing
                }
            }
            "Ruled" -> {
                val spacing = 32f
                var y = spacing
                while (y < height) {
                    canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
                    y += spacing
                }
            }
        }

        paths.forEach { stroke ->
            if (stroke.points.size > 1) {
                val c = stroke.color
                val a = (c.alpha * 255).toInt()
                val r = (c.red * 255).toInt()
                val g = (c.green * 255).toInt()
                val b = (c.blue * 255).toInt()
                paint.color = android.graphics.Color.argb(a, r, g, b)
                paint.strokeWidth = stroke.strokeWidth

                when (stroke.shapeType) {
                    ShapeType.FREEHAND -> {
                        val path = android.graphics.Path().apply {
                            moveTo(stroke.points[0].x, stroke.points[0].y)
                            for (i in 1 until stroke.points.size) {
                                lineTo(stroke.points[i].x, stroke.points[i].y)
                            }
                        }
                        canvas.drawPath(path, paint)
                    }
                    ShapeType.LINE -> {
                        canvas.drawLine(stroke.points.first().x, stroke.points.first().y, stroke.points.last().x, stroke.points.last().y, paint)
                    }
                    ShapeType.RECTANGLE -> {
                        val start = stroke.points.first()
                        val end = stroke.points.last()
                        canvas.drawRect(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y), paint)
                    }
                    ShapeType.CIRCLE -> {
                        val start = stroke.points.first()
                        val end = stroke.points.last()
                        canvas.drawOval(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y), paint)
                    }
                }
            }
        }
        val bytes = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, bytes)
        android.util.Base64.encodeToString(bytes.toByteArray(), android.util.Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }
}

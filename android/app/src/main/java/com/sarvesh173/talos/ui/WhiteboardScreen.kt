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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteboardScreen(
    client: TalosClient,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val paths = remember { mutableStateListOf<StrokePath>() }
    var currentPoints = remember { mutableStateListOf<Offset>() }
    var currentColor by remember { mutableStateOf(Color(0xFFF59E0B)) }
    var strokeWidth by remember { mutableFloatStateOf(6f) }
    var canvasBackground by remember { mutableStateOf("DotGrid") }

    val colorPalette = listOf(
        Color(0xFFF59E0B), // Molten Amber
        Color(0xFF2DD4BF), // Electric Verdigris
        Color(0xFF60A5FA), // Azure Blue
        Color(0xFFF87171), // Laser Red
        Color(0xFFFFFFFF)  // Chalk White
    )

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
                            text = "${paths.size} strokes committed",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryAccent
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
                    // Undo
                    IconButton(
                        onClick = { if (paths.isNotEmpty()) paths.removeAt(paths.size - 1) },
                        enabled = paths.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    // Clear
                    IconButton(
                        onClick = { paths.clear() },
                        enabled = paths.isNotEmpty()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = MaterialTheme.colorScheme.error)
                    }
                    // Send to Council
                    IconButton(
                        onClick = {
                            if (paths.isNotEmpty()) {
                                client.sendMessage("📐 [OpenBoard Sketch Attached: ${paths.size} strokes]")
                                Toast.makeText(context, "Sketch attached to Council Room!", Toast.LENGTH_SHORT).show()
                                onBack()
                            } else {
                                Toast.makeText(context, "Draw something first!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = PrimaryAccent)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Color Palette with 48dp minimum touch bounds
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        colorPalette.forEach { color ->
                            val isSelected = currentColor == color
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable { currentColor = color }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
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

                    // Stroke & Background controls
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
                        FilterChip(
                            selected = canvasBackground == "DotGrid",
                            onClick = { canvasBackground = if (canvasBackground == "DotGrid") "DarkSlate" else "DotGrid" },
                            label = { Text(if (canvasBackground == "DotGrid") "Grid" else "Slate", fontSize = 10.sp) }
                        )
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
                    .pointerInput(currentColor, strokeWidth) {
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
                                    paths.add(
                                        StrokePath(
                                            points = currentPoints.toList(),
                                            color = currentColor,
                                            strokeWidth = strokeWidth
                                        )
                                    )
                                    currentPoints = mutableStateListOf()
                                }
                            }
                        )
                    }
            ) {
                // OpenBoard dot grid
                if (canvasBackground == "DotGrid") {
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

                // Render committed paths
                paths.forEach { strokePath ->
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

                // Render active in-progress stroke
                if (currentPoints.size > 1) {
                    val activePath = Path().apply {
                        moveTo(currentPoints.first().x, currentPoints.first().y)
                        for (i in 1 until currentPoints.size) {
                            lineTo(currentPoints[i].x, currentPoints[i].y)
                        }
                    }
                    drawPath(
                        path = activePath,
                        color = currentColor,
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}

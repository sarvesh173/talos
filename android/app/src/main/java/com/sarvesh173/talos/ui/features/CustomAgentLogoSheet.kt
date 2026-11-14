package com.sarvesh173.talos.ui.features
import kotlin.math.min
import kotlin.math.max

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.agents.AgentRoster
import com.sarvesh173.talos.ui.bot.CustomLogoStore
import com.sarvesh173.talos.ui.bot.Pt
import com.sarvesh173.talos.ui.bot.SvgPathParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Lets the user attach their own SVG mark to [agentId] (e.g. "opencode") and
 * persist it via [CustomLogoStore] under `filesDir/logos/<agentId>.svg`, so it
 * survives restarts and app updates. Previews the parsed mark via Compose
 * [Path] before anything is written.
 *
 * Apple HIG: #000000 preview canvas, #1C1C1E sheet, 20dp sheet radius,
 * one accent #0A84FF, 4dp grid, hairlines at 8% white, no emoji.
 *
 * DEPENDENCY: `ui/bot/SvgPathParser.kt` (parallel work) with
 * `parseSvg(source: String): SvgParseResult` exposing
 * `contours: List<List<Pt>>` (viewport coords, y-down).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomAgentLogoSheet(
    agentId: String,
    onDismiss: () -> Unit,
    onSaved: ((agentId: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val accent = MaterialTheme.colorScheme.primary
    val hairline = Color.White.copy(alpha = 0.08f)

    val title = remember(agentId) { AgentRoster.byId(agentId)?.name ?: agentId }
    val existing = remember(agentId) { CustomLogoStore.load(context, agentId) ?: "" }
    val existingColor = remember(agentId) { CustomLogoStore.loadColor(context, agentId) }

    var svgText by remember(agentId) { mutableStateOf(existing) }
    var errorText by remember(agentId) { mutableStateOf<String?>(null) }
    var selectedHex by remember(agentId) { mutableStateOf<String?>(existingColor) }

    // Parse on every edit so the preview (and any error) is live.
    val parsed = remember(svgText) {
        if (svgText.isBlank()) {
            null to null
        } else {
            try {
                val result = SvgPathParser.parseSvg(svgText)
                val contours = result.contours.filter { it.size >= 3 }
                if (contours.isEmpty()) {
                    null to "Could not parse this SVG: no usable path contours found. Check for <path d=\"...\"> geometry."
                } else {
                    contours to null
                }
            } catch (e: Exception) {
                null to "Could not parse this SVG: ${e.message ?: e::class.simpleName}"
            }
        }
    }
    val previewContours: List<List<Pt>>? = parsed.first
    val liveError: String? = parsed.second
    val shownError = errorText ?: liveError

    // File picker: .svg / .txt picked through the system document picker.
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw IllegalArgumentException("Could not read the selected file")
                if (bytes.size > CustomLogoStore.MAX_SVG_BYTES) {
                    errorText = "File is ${bytes.size} bytes, exceeds the 512 KB size cap"
                    return@launch
                }
                val text = String(bytes, Charsets.UTF_8)
                if (text.isBlank()) {
                    errorText = "Selected file is empty"
                    return@launch
                }
                errorText = null
                svgText = text
            } catch (e: Exception) {
                errorText = "Could not read file: ${e.message ?: e::class.simpleName}"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = "Custom mark for $agentId",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(16.dp))

            // Live preview on the #000000 canvas.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF000000))
                    .border(BorderStroke(0.6.dp, hairline), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (previewContours != null) {
                    SvgPreview(
                        contours = previewContours,
                        color = parseHex(selectedHex) ?: accent
                    )
                } else {
                    Text(
                        text = if (shownError != null) "No preview" else "Paste or pick an SVG to preview it here",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // Real parse / save errors — never silent.
            if (shownError != null) {
                Text(
                    text = shownError,
                    fontSize = 13.sp,
                    color = Color(0xFFFF453A),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            OutlinedTextField(
                value = svgText,
                onValueChange = { svgText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                maxLines = 10,
                label = { Text("SVG markup") },
                placeholder = { Text("<svg viewBox=\"0 0 24 24\">...</svg>") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = accent,
                    unfocusedBorderColor = hairline,
                    cursorColor = accent
                )
            )
            Text(
                text = "${svgText.toByteArray(Charsets.UTF_8).size} bytes / 512 KB cap",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    picker.launch(arrayOf("image/svg+xml", "text/*", "application/xml"))
                },
                border = BorderStroke(0.6.dp, hairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pick .svg / .txt file", color = Color.White)
            }
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Mark colour",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            ColourOverrideRow(selectedHex = selectedHex) { hex ->
                selectedHex = hex
            }
            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        if (svgText.isBlank()) {
                            errorText = "Nothing to save: paste or pick an SVG first"
                            return@Button
                        }
                        if (liveError != null) {
                            errorText = liveError
                            return@Button
                        }
                        val baked = CustomLogoStore.bakeCustom(context, agentId, svgText)
                        val bakeError = baked.exceptionOrNull()
                        if (bakeError != null) {
                            errorText = bakeError.message ?: "SVG failed to parse"
                            return@Button
                        }
                        val saved = CustomLogoStore.save(context, agentId, svgText)
                        val saveError = saved.exceptionOrNull()
                        if (saveError != null) {
                            errorText = saveError.message ?: "Could not save the SVG"
                            return@Button
                        }
                        if (selectedHex != null) {
                            CustomLogoStore.saveColor(context, agentId, selectedHex!!)
                        } else {
                            CustomLogoStore.clearColor(context, agentId)
                        }
                        onSaved?.invoke(agentId)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
                OutlinedButton(
                    onClick = {
                        CustomLogoStore.remove(context, agentId)
                        svgText = ""
                        selectedHex = null
                        errorText = null
                        onDismiss()
                    },
                    border = BorderStroke(0.6.dp, hairline),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset to default", color = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = accent)
            }
        }
    }
}

/** Renders [contours] as filled Compose [Path]s, fitted into the canvas. */
@Composable
private fun SvgPreview(contours: List<List<Pt>>, color: Color) {
    Canvas(modifier = Modifier.size(96.dp)) {
        val all = contours.flatten()
        if (all.isEmpty()) return@Canvas
        val minX = all.minOf { it.x }
        val maxX = all.maxOf { it.x }
        val minY = all.minOf { it.y }
        val maxY = all.maxOf { it.y }
        val w = (maxX - minX).coerceAtLeast(1e-6f)
        val h = (maxY - minY).coerceAtLeast(1e-6f)
        val scale = min(size.width, size.height) / max(w, h) * 0.9f
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f

        fun toOffset(p: Pt) = Offset(
            size.width / 2f + (p.x - cx) * scale,
            size.height / 2f + (p.y - cy) * scale
        )

        for (contour in contours) {
            if (contour.isEmpty()) continue
            val path = Path().apply {
                val first = toOffset(contour[0])
                moveTo(first.x, first.y)
                for (i in 1 until contour.size) {
                    val o = toOffset(contour[i])
                    lineTo(o.x, o.y)
                }
                close()
            }
            drawPath(path, color = color)
            drawPath(path, color = color, style = Stroke(width = 1f))
        }
    }
}

private val overridePalette = listOf("#0A84FF", "#FFFFFF", "#FFBF00", "#FF453A", "#30D158", "#BF5AF2")

@Composable
private fun ColourOverrideRow(selectedHex: String?, onPick: (String?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // "Default" chip clears the override.
        SwatchChip(
            label = "Default",
            selected = selectedHex == null,
            onClick = { onPick(null) }
        )
        for (hex in overridePalette) {
            val c = parseHex(hex) ?: Color.White
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                        BorderStroke(
                            if (selectedHex.equals(hex, ignoreCase = true)) 2.dp else 0.6.dp,
                            if (selectedHex.equals(hex, ignoreCase = true)) Color.White
                            else Color.White.copy(alpha = 0.08f)
                        ),
                        CircleShape
                    )
                    .clickable { onPick(hex) }
            )
        }
    }
}

@Composable
private fun SwatchChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(
                    if (selected) 2.dp else 0.6.dp,
                    if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
                ),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, color = Color.White)
    }
}

private fun parseHex(hex: String?): Color? {
    if (hex == null) return null
    val h = hex.trim().removePrefix("#")
    if (h.length != 6) return null
    val rgb = h.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}

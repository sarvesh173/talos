@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sarvesh173.talos.ui.onboarding

import com.sarvesh173.talos.ui.*

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.R
import com.sarvesh173.talos.data.MeshInfo
import com.sarvesh173.talos.network.MeshDiscovery
import com.sarvesh173.talos.network.TalosClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

// ==========================================
// PALETTE (Grok-style dark onboarding)
// ==========================================

private val WizardBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val WizardSurface: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
private val WizardSurfaceAlt: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
private val WizardBorder: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val WizardText: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val WizardMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val WizardDanger: Color @Composable get() = MaterialTheme.colorScheme.error

// Illustration badge accents - scheme-driven so both modes stay consistent.
private val CyberGreen: Color @Composable get() = MaterialTheme.colorScheme.tertiary
private val CyberViolet: Color @Composable get() = MaterialTheme.colorScheme.primary
private val CyberOrange: Color @Composable get() = MaterialTheme.colorScheme.tertiary


// ==========================================
// MAIN WIZARD
// ==========================================

@OptIn(ExperimentalLayoutApi::class)


@Composable
internal fun ShieldLanBadge(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        // Shield body
        val shield = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.88f, h * 0.24f)
            lineTo(w * 0.88f, h * 0.52f)
            cubicTo(w * 0.88f, h * 0.76f, w * 0.70f, h * 0.90f, w * 0.5f, h * 0.97f)
            cubicTo(w * 0.30f, h * 0.90f, w * 0.12f, h * 0.76f, w * 0.12f, h * 0.52f)
            lineTo(w * 0.12f, h * 0.24f)
            close()
        }
        drawPath(shield, tint)
        // LAN nodes punched out of the shield
        drawCircle(Color.White, radius = w * 0.08f, center = Offset(w * 0.5f, h * 0.42f))
        drawCircle(Color.White, radius = w * 0.08f, center = Offset(w * 0.30f, h * 0.68f))
        drawCircle(Color.White, radius = w * 0.08f, center = Offset(w * 0.70f, h * 0.68f))
        // Links
        drawLine(
            color = Color.White,
            start = Offset(w * 0.5f, h * 0.42f),
            end = Offset(w * 0.30f, h * 0.68f),
            strokeWidth = w * 0.045f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(w * 0.5f, h * 0.42f),
            end = Offset(w * 0.70f, h * 0.68f),
            strokeWidth = w * 0.045f,
            cap = StrokeCap.Round
        )
    }
}

/** Step 1 badge 2 — agent fleet marks (three pill agents). */
@Composable
internal fun AgentFleetBadge(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        // Centre agent (larger)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.30f, h * 0.14f),
            size = Size(w * 0.40f, h * 0.46f),
            cornerRadius = CornerRadius(w * 0.20f, w * 0.20f)
        )
        // Side agents (smaller)
        drawRoundRect(
            color = tint.copy(alpha = 0.62f),
            topLeft = Offset(w * 0.02f, h * 0.34f),
            size = Size(w * 0.24f, h * 0.30f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
        )
        drawRoundRect(
            color = tint.copy(alpha = 0.62f),
            topLeft = Offset(w * 0.74f, h * 0.34f),
            size = Size(w * 0.24f, h * 0.30f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
        )
        // Connector rail
        drawLine(
            color = tint.copy(alpha = 0.45f),
            start = Offset(w * 0.14f, h * 0.49f),
            end = Offset(w * 0.86f, h * 0.49f),
            strokeWidth = w * 0.05f,
            cap = StrokeCap.Round
        )
        // Eyes on the centre agent
        drawCircle(Color.White, radius = w * 0.045f, center = Offset(w * 0.43f, h * 0.30f))
        drawCircle(Color.White, radius = w * 0.045f, center = Offset(w * 0.57f, h * 0.30f))
    }
}

/** Step 1 badge 3 — terminal window over a bus line. */
@Composable
internal fun TerminalBusBadge(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        // Terminal frame
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.04f, h * 0.10f),
            size = Size(w * 0.92f, h * 0.66f),
            cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
        )
        // Cut the inner screen
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.12f, h * 0.24f),
            size = Size(w * 0.76f, h * 0.44f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )
        // Prompt glyph
        drawLine(
            color = tint,
            start = Offset(w * 0.20f, h * 0.34f),
            end = Offset(w * 0.30f, h * 0.42f),
            strokeWidth = w * 0.05f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.30f, h * 0.42f),
            end = Offset(w * 0.20f, h * 0.50f),
            strokeWidth = w * 0.05f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.36f, h * 0.52f),
            end = Offset(w * 0.56f, h * 0.52f),
            strokeWidth = w * 0.05f,
            cap = StrokeCap.Round
        )
        // Bus rail with two nodes
        drawLine(
            color = tint.copy(alpha = 0.55f),
            start = Offset(w * 0.12f, h * 0.87f),
            end = Offset(w * 0.88f, h * 0.87f),
            strokeWidth = w * 0.06f,
            cap = StrokeCap.Round
        )
        drawCircle(tint, radius = w * 0.09f, center = Offset(w * 0.20f, h * 0.87f))
        drawCircle(tint, radius = w * 0.80f, center = Offset(w * 0.80f, h * 0.87f))
    }
}

/** Step 3 badge — key glyph. */
@Composable
internal fun KeyBadge(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = tint,
            radius = w * 0.26f,
            center = Offset(w * 0.32f, h * 0.50f),
            style = Stroke(width = w * 0.13f)
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.52f, h * 0.50f),
            end = Offset(w * 0.94f, h * 0.50f),
            strokeWidth = w * 0.13f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.78f, h * 0.50f),
            end = Offset(w * 0.78f, h * 0.74f),
            strokeWidth = w * 0.13f,
            cap = StrokeCap.Round
        )
    }
}

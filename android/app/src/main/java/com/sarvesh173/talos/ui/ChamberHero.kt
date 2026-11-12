package com.sarvesh173.talos

package com.sarvesh173.talos

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import com.sarvesh173.talos.ui.talosSpecularGlassBorder
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.lifecycleScope
import com.sarvesh173.talos.data.Message
import com.sarvesh173.talos.data.Room
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.core.feature.FeatureCatalog
import com.sarvesh173.talos.core.feature.FeatureScope
import com.sarvesh173.talos.core.motion.MotionTokens
import com.sarvesh173.talos.ui.entry.AppLockController
import com.sarvesh173.talos.ui.entry.AppLockPrompt
import com.sarvesh173.talos.ui.entry.AppLockStore
import com.sarvesh173.talos.ui.entry.LockState
import com.sarvesh173.talos.ui.entry.isBiometricAvailable
import com.sarvesh173.talos.ui.entry.shouldLock
import com.sarvesh173.talos.ui.features.SettingsScreen
import com.sarvesh173.talos.ui.settings.DashboardControlScreen
import com.sarvesh173.talos.ui.agents.AgentLifecycleScreen
import com.sarvesh173.talos.ui.kanban.KanbanScreen
import com.sarvesh173.talos.ui.review.ReviewWorkScreen
import com.sarvesh173.talos.ui.cron.JobListScreen
import com.sarvesh173.talos.ui.processes.ProcessSupervisorScreen
import com.sarvesh173.talos.ui.bot.AgentLogoCustomizeScreen
import com.sarvesh173.talos.ui.features.LookAndFeelScreen
import com.sarvesh173.talos.ui.features.ChatPreferencesScreen
import com.sarvesh173.talos.ui.features.SecurityScreen
import com.sarvesh173.talos.ui.features.CachingScreen
import com.sarvesh173.talos.ui.features.NotificationsScreen
import com.sarvesh173.talos.ui.features.ThinkingOrbSettingsScreen
import com.sarvesh173.talos.ui.features.SupportedClisScreen
import com.sarvesh173.talos.ui.*
import com.sarvesh173.talos.ui.TalosHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChamberHero(
    room: Room?,
    onPromptClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDark.current

    val infiniteCorePulse = rememberInfiniteTransition(label = "hero_core_pulse")
    val coreScale by infiniteCorePulse.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_scale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // M3E signature scallop badge: 12-lobed expressive flower portal
        Surface(
            shape = M3EPolygonShape(M3EShapes.scallop12()),
            color = if (isDark) EmblemSocket else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.5.dp, metallicBorderBrush()),
            modifier = Modifier
                .size(76.dp)
                .scale(coreScale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                val heroEmblem = room?.let { r ->
                    when {
                        r.id == "core-dm" || r.mode == "dm" -> emblemByKey("hermes")
                        r.id == "the-council" -> emblemByKey("omnirush")
                        else -> emblemBySender(r.name)
                    }
                }
                if (heroEmblem != null) {
                    AgentEmblemIcon(emblem = heroEmblem, size = 42.dp, tint = null)
                } else {
                    Icon(
                        imageVector = TalosHub,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Active Chamber Title with Live Mesh Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(StatusGreen)
            )
            Text(
                text = room?.name ?: "TALOS Chamber",
                color = talosTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = room?.description ?: "Multi-agent autonomous collaborative bus via Tailscale encrypted subnet.",
            color = talosTextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Participating Agent Roster Dock with Machined Obsidian Sockets
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.2.dp, metallicBorderBrush()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Participating agent fleet",
                        color = talosTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = CircleShape,
                        color = StatusGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "${AgentEmblems.size} agents online",
                            color = StatusGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AgentEmblems, key = { it.key }) { emblem ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(EmblemSocket)
                                .border(1.dp, emblem.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AgentEmblemIcon(emblem = emblem, size = 20.dp, tint = null)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Slash Command Action Grid (Raycast Mobile Style)
        val commands = listOf(
            Triple("/consensus", "Propose democratic vote across registered agents", Icons.Default.Groups),
            Triple("/benchmark", "Ping AI providers and recalculate latency route", Icons.Default.Speed),
            Triple("/mesh", "Scan peers on your private Tailscale overlay", TalosHub)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((cmd, desc, icon) in commands) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.2.dp, metallicBorderBrush()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPromptClick(cmd) }
                        .semantics { contentDescription = "Execute command $cmd: $desc" }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(EmblemSocket)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cmd,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Text(
                                text = desc,
                                color = talosTextMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

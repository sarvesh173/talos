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
fun SettingsDialog(
    client: TalosClient,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onOpenAppearance: () -> Unit = {}
) {
    var urlText by remember { mutableStateOf(client.baseUrl) }
    var tokenText by remember { mutableStateOf(client.apiToken) }
    var isVerifying by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isDark = LocalIsDark.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = talosSurface,
        title = {
            Column {
                Text(
                    text = "Tailscale host & connection settings",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Update your host Tailscale IP or API key whenever your network changes.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Core Tailscale address:",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { 
                        urlText = it
                        statusMessage = null
                        isSuccess = false
                    },
                    placeholder = { Text("http://100.x.y.z:8010", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = talosBg,
                        unfocusedContainerColor = talosBg,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "API:",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = tokenText,
                    onValueChange = { 
                        tokenText = it
                        statusMessage = null
                        isSuccess = false
                    },
                    placeholder = { Text("Paste key from laptop", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = talosBg,
                        unfocusedContainerColor = talosBg,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = statusMessage ?: "",
                        color = if (isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                M3EButton(
                    text = if (isVerifying) "Verifying Handshake..." else "Verify & Update Connection",
                    onClick = {
                        scope.launch {
                            isVerifying = true
                            statusMessage = null
                            val cleanUrl = urlText.trim().trimEnd('/')
                            val cleanToken = tokenText.trim()
                            val (ok, msg) = client.verifyAndConnect(cleanUrl, cleanToken)
                            isVerifying = false
                            if (ok) {
                                isSuccess = true
                                statusMessage = "Verified & Connected (200 OK)"
                                onSave(cleanUrl, cleanToken)
                            } else {
                                isSuccess = false
                                statusMessage = msg
                            }
                        }
                    },
                    enabled = !isVerifying,
                    loading = isVerifying,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Appearance & tone",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "OLED true black, dark space gray, and paper white finishes.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                PressableSurface(
                    onClick = onOpenAppearance,
                    shape = RoundedCornerShape(12.dp),
                    pressedShape = RoundedCornerShape(8.dp),
                    color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                    border = BorderStroke(0.8.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Customize appearance",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = TalosSparkle,
                            contentDescription = null,
                            tint = Color(0xFF2997FF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Close", color = TextMuted)
            }
        }
    )
}

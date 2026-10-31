package com.sarvesh173.talos.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.network.TalosClient

data class ActivityModuleItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val category: String,
    val color: Color,
    val badge: String? = null,
    /** Module needs a live TALOS core link; stays disabled while disconnected. */
    val requiresCore: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleActivityHub(
    client: TalosClient,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val isCoreConnected by client.isConnected.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val modules = remember(primaryColor, secondaryColor, tertiaryColor) {
        listOf(
            ActivityModuleItem(
                id = "canvas",
                title = "Multi-Agent Canvas",
                subtitle = "Spatial mesh of parallel running agents and child subagents",
                icon = Icons.Default.Hub,
                category = "Intelligence & Coding",
                color = primaryColor,
                badge = "LIVE",
                requiresCore = true
            ),
            ActivityModuleItem(
                id = "diff",
                title = "Code & File Changes",
                subtitle = "Unified syntax-colored diffs for git working tree",
                icon = Icons.Default.Code,
                category = "Intelligence & Coding",
                color = tertiaryColor,
                requiresCore = true
            ),
            ActivityModuleItem(
                id = "cli_manager",
                title = "Multi-CLI Governance",
                subtitle = "OmniRush, OpenCode, Codex & command quota sliders",
                icon = Icons.Default.Terminal,
                category = "Intelligence & Coding",
                color = secondaryColor,
                requiresCore = true
            ),
            ActivityModuleItem(
                id = "whiteboard",
                title = "OpenBoard Whiteboard",
                subtitle = "Touch drawing canvas with dot grid and architectural sketch attachments",
                icon = Icons.Default.Brush,
                category = "Creative & Canvas",
                color = primaryColor,
                badge = "NEW"
            ),
            ActivityModuleItem(
                id = "gallery",
                title = "AI Image Studio",
                subtitle = "Bento gallery of generated images with pinch-zoom lightbox",
                icon = Icons.Default.AutoAwesome,
                category = "Creative & Canvas",
                color = tertiaryColor
            ),
            ActivityModuleItem(
                id = "browser",
                title = "Obscura Browser",
                subtitle = "Anti-detection web view with session tabs and script injection",
                icon = Icons.Default.Language,
                category = "System & Gateway",
                color = secondaryColor
            ),
            ActivityModuleItem(
                id = "themes",
                title = "Material UI Themes",
                subtitle = "Switch between Signature Purple, Cobalt Blue, Cyber Mint, and Amber presets",
                icon = Icons.Default.Palette,
                category = "System & Gateway",
                color = primaryColor,
                badge = "NEW"
            )
        )
    }

    val groupedModules = modules.groupBy { it.category }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Activity Hub",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "GCompris-style modular activity dispatching",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            groupedModules.forEach { (category, items) ->
                item {
                    Text(
                        text = category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                items(items, key = { it.id }) { module ->
                    val gated = module.requiresCore && !isCoreConnected
                    ActivityCard(
                        module = module,
                        enabled = !gated,
                        onClick = {
                            if (gated) {
                                TalosHaptics.sendHaptic(context)
                                android.widget.Toast.makeText(
                                    context,
                                    "TALOS core is offline. Connect the gateway first.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onNavigate(module.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(
    module: ActivityModuleItem,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    // M3E disabled state: 38% content opacity, no press scale, muted border.
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outlineVariant
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .m3ePressScale(interactionSource, pressedScale = if (enabled) 0.96f else 1f)
            .clickable(
                interactionSource = interactionSource,
                indication = if (enabled) ripple(bounded = true, radius = 120.dp) else null,
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(module.color.copy(alpha = 0.15f))
                    .border(BorderStroke(1.dp, module.color.copy(alpha = 0.4f)), RoundedCornerShape(16.dp))
            ) {
                Icon(
                    imageVector = module.icon,
                    contentDescription = module.title,
                    tint = module.color,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = module.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (module.badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(module.color.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = module.badge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = module.color
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = module.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

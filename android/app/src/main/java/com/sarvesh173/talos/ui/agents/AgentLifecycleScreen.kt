package com.sarvesh173.talos.ui.agents

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarvesh173.talos.ui.AppleToggleSwitch
import com.sarvesh173.talos.ui.LocalIsDark
import com.sarvesh173.talos.ui.TalosIcons
import com.sarvesh173.talos.ui.appleLiquidGlassOptics
import com.sarvesh173.talos.ui.applePressable

/**
 * SharedPreferences store for per-agent lifecycle.
 *
 * Data-layer lock: Hermes Agent and OpenClaw ([AgentRoster.ALWAYS_ON_IDS])
 * always resolve to [AgentLifecycle.AlwaysOn] regardless of stored values.
 * Writes for those ids are ignored, so the lock cannot be bypassed from the UI
 * or by a stale/forged preference.
 */
object AgentLifecycleStore {

    private const val PREFS = "talos_agent_lifecycle"
    private fun keyFor(agentId: String) = "lifecycle_$agentId"

    /** Effective lifecycle for every roster agent, with the lock enforced. */
    fun load(context: Context): Map<String, AgentLifecycle> {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AgentRoster.agents.associate { entry ->
            entry.id to resolve(appContext, entry.id)
        }
    }

    /** Persist [lifecycle] for [agentId]; no-op for locked ids. */
    fun set(context: Context, agentId: String, lifecycle: AgentLifecycle) {
        if (agentId in AgentRoster.ALWAYS_ON_IDS) return
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(keyFor(agentId), lifecycle.name).apply()
    }

    /** Effective lifecycle for one agent; locked ids always return AlwaysOn. */
    fun resolve(context: Context, agentId: String): AgentLifecycle {
        if (agentId in AgentRoster.ALWAYS_ON_IDS) return AgentLifecycle.AlwaysOn
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(keyFor(agentId), AgentLifecycle.OnDemand.name)
        return runCatching { AgentLifecycle.valueOf(stored ?: AgentLifecycle.OnDemand.name) }
            .getOrDefault(AgentLifecycle.OnDemand)
    }
}

/**
 * Apple HIG inset-grouped agent lifecycle screen.
 *
 * Section 1 "Required" — Hermes Agent and OpenClaw render a lock glyph and no
 * toggle; they cannot be disabled, because if the gateway/automation backbone
 * stops nothing can be restarted. Section 2 "Start when used" — every other
 * agent defaults to OnDemand with an explicit opt-in to stay warm (Pinned).
 */
@Composable
fun AgentLifecycleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var lifecycles by remember { mutableStateOf(AgentLifecycleStore.load(context)) }

    fun toggle(agent: AgentEntry, pinned: Boolean) {
        if (agent.id in AgentRoster.ALWAYS_ON_IDS) return
        if (!agent.isInstalled) return
        val next = if (pinned) AgentLifecycle.Pinned else AgentLifecycle.OnDemand
        AgentLifecycleStore.set(context, agent.id, next)
        lifecycles = lifecycles + (agent.id to next)
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .applePressable(0.94f) { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = TalosIcons.TalosBackArrow,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Agent lifecycle",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.25).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Which agents stay warm around the clock.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    letterSpacing = (-0.1).sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Required")
            LifecycleGroup {
                val required = AgentRoster.agents.filter { it.lifecycle.isLocked }
                required.forEachIndexed { index, agent ->
                    RequiredRow(agent = agent)
                    if (index < required.lastIndex) LifecycleDivider()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(text = "Start when used")
            LifecycleGroup {
                val configurable = AgentRoster.agents.filterNot { it.lifecycle.isLocked }
                configurable.forEachIndexed { index, agent ->
                    val enabled = agent.isInstalled
                    val pinned = lifecycles[agent.id] == AgentLifecycle.Pinned
                    ConfigurableRow(
                        agent = agent,
                        pinned = pinned,
                        onToggle = { toggle(agent, it) }
                    )
                    if (index < configurable.lastIndex) LifecycleDivider()
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Agents kept running hold a Node process and a model context. That is real battery and memory.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    letterSpacing = (-0.1).sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun LifecycleGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val isDark = LocalIsDark.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .appleLiquidGlassOptics(isDark = isDark, chromaticGlow = MaterialTheme.colorScheme.primary, cornerRadius = 20.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun RequiredRow(agent: AgentEntry) {
    val scheme = MaterialTheme.colorScheme
    val reason = when (agent.id) {
        "hermes" -> "Keeps the gateway reachable"
        "openclaw" -> "Required to restart other agents"
        else -> "Required. Keeps the gateway reachable."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = scheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
            Text(
                text = agent.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    letterSpacing = (-0.2).sp
                ),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    letterSpacing = (-0.1).sp
                ),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Always on",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ConfigurableRow(
    agent: AgentEntry,
    pinned: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val enabled = agent.isInstalled
    val subtitle = when {
        !enabled -> "Not installed"
        pinned -> "Keep running"
        else -> "Start when used"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .applePressable(0.97f) { if (enabled) onToggle(!pinned) }
                .alpha(if (enabled) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        letterSpacing = (-0.1).sp
                    ),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .alpha(if (enabled) 1f else 0.4f),
            contentAlignment = Alignment.Center
        ) {
            AppleToggleSwitch(
                checked = enabled && pinned,
                onCheckedChange = { if (enabled) onToggle(it) }
            )
        }
    }
}

@Composable
private fun LifecycleDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 54.dp)
    )
}

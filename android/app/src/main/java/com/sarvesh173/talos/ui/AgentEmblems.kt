package com.sarvesh173.talos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sarvesh173.talos.R

/**
 * Vector emblems for the CLI agents that make up the TALOS mesh.
 *
 * Brand emblems are official SVGs fetched through the dashboard-icons MCP
 * (dashboardicons.com / simple-icons / selfh.st) and converted to normalized
 * Android VectorDrawables: square 24dp viewport, uniform scale, centered,
 * single tintable path. Custom products without an official mark (Hermes,
 * OpenClaw, OmniRush) keep their hand-authored drawables.
 */
data class AgentEmblem(
    val key: String,
    val label: String,
    val role: String,
    val drawableRes: Int,
    val accent: Color
)

val AgentEmblems: List<AgentEmblem> = listOf(
    AgentEmblem("hermes", "Hermes Agent", "Nous Research · Autonomy", R.drawable.ic_hermes, Color(0xFFFFBF00)),
    AgentEmblem("claude", "Claude Code", "Anthropic · Architecture", R.drawable.ic_claude, Color(0xFFD97757)),
    AgentEmblem("codex", "Codex", "OpenAI · Code & Synthesis", R.drawable.ic_codex, Color(0xFF7A9DFF)),
    AgentEmblem("anthropic", "Anthropic", "Anthropic AI", R.drawable.ic_anthropic, Color(0xFFD97757)),
    AgentEmblem("openai", "OpenAI", "OpenAI Family", R.drawable.ic_openai, Color(0xFF10A37F)),
    AgentEmblem("gemini", "Gemini", "Google · Multimodal Reasoning", R.drawable.ic_gemini, Color(0xFF3186FF)),
    AgentEmblem("meta", "Meta AI", "Meta · Llama Family", R.drawable.ic_meta, Color(0xFF1877F2)),
    AgentEmblem("mistral", "Mistral AI", "Mistral AI · Open Weights", R.drawable.ic_mistral, Color(0xFFF24E1E)),
    AgentEmblem("deepseek", "DeepSeek", "DeepSeek · Reasoning", R.drawable.ic_deepseek, Color(0xFF0066FF)),
    AgentEmblem("qwen", "Qwen", "Alibaba · Qwen Series", R.drawable.ic_qwen, Color(0xFF2563EB)),
    AgentEmblem("openclaw", "OpenClaw", "Tools & Automation", R.drawable.ic_openclaw, Color(0xFFF97316)),
    AgentEmblem("omnirush", "OmniRush", "High-Throughput Runner", R.drawable.ic_omnirush, Color(0xFFA855F7)),
    AgentEmblem("opencode", "OpenCode", "Open-Source Terminal CLI", R.drawable.ic_opencode, Color(0xFF3B82F6))
)

fun emblemByKey(key: String): AgentEmblem =
    AgentEmblems.firstOrNull { it.key == key } ?: AgentEmblems.first()

/**
 * Resolve an agent emblem dynamically by sender name, handling agent handles,
 * vendor prefixes, and role descriptors.
 */
fun emblemBySender(name: String): AgentEmblem {
    val clean = name.lowercase().trim()
    return AgentEmblems.firstOrNull { emblem ->
        clean == emblem.key || clean == emblem.label.lowercase()
    } ?: AgentEmblems.firstOrNull { emblem ->
        clean.contains(emblem.key) || clean.contains(emblem.label.lowercase())
    } ?: when {
        clean.contains("omni") || clean.contains("rush") -> emblemByKey("omnirush")
        clean.contains("claude") -> emblemByKey("claude")
        clean.contains("codex") -> emblemByKey("codex")
        clean.contains("anthropic") -> emblemByKey("anthropic")
        clean.contains("openai") || clean.contains("chatgpt") -> emblemByKey("openai")
        clean.contains("hermes") || clean.contains("nous") || clean.contains("core") -> emblemByKey("hermes")
        clean.contains("opencode") -> emblemByKey("opencode")
        clean.contains("claw") || clean.contains("openclaw") -> emblemByKey("openclaw")
        clean.contains("deepseek") -> emblemByKey("deepseek")
        clean.contains("gemini") || clean.contains("google") -> emblemByKey("gemini")
        clean.contains("qwen") || clean.contains("alibaba") -> emblemByKey("qwen")
        clean.contains("mistral") -> emblemByKey("mistral")
        clean.contains("meta") || clean.contains("llama") -> emblemByKey("meta")
        else -> emblemByKey("hermes")
    }
}

/** A single agent emblem rendered straight from its vector drawable. */
@Composable
fun AgentEmblemIcon(
    emblem: AgentEmblem,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color? = null
) {
    val effectiveTint = tint ?: emblem.accent
    Image(
        painter = painterResource(emblem.drawableRes),
        contentDescription = emblem.label,
        modifier = modifier.size(size),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(effectiveTint)
    )
}

/**
 * Small circular badge used to tag a floating agent blob with the identity of
 * the agent it represents. Drawn as a vector icon inside a ring - no text, no
 * emoji.
 */
@Composable
fun AgentEmblemBadge(
    emblem: AgentEmblem,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Box(
        modifier = modifier
            .size(size + 8.dp)
            .clip(CircleShape)
            .background(talosSurface)
            .border(1.dp, emblem.accent.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Accent tint: theme-safe on the surface container and consistent
        // with the M3 color roles. Brand marks stay recognizable by shape.
        AgentEmblemIcon(emblem = emblem, size = size, tint = emblem.accent)
    }
}

/** Horizontal roster of every agent in the mesh, rendered as vector emblems. */
@Composable
fun AgentRoster(
    modifier: Modifier = Modifier,
    emblemSize: Dp = 22.dp,
    gap: Dp = 10.dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AgentEmblems.forEach { emblem ->
            AgentEmblemBadge(emblem = emblem, size = emblemSize)
        }
    }
}

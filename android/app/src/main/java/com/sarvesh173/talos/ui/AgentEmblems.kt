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
 * Every emblem is a hand-authored Android VectorDrawable (no emoji, no raster,
 * no font glyphs). The matching standalone SVGs live in `assets/agent-icons/`
 * and are produced by `assets/agent-icons/generate_icons.py`, which keeps the
 * vector drawables and the web/docs artwork byte-for-byte in sync.
 */
data class AgentEmblem(
    val key: String,
    val label: String,
    val role: String,
    val drawableRes: Int,
    val accent: Color
)

val AgentEmblems: List<AgentEmblem> = listOf(
    AgentEmblem("hermes", "Hermes Agent", "Nous Research · Autonomy", R.drawable.ic_hermes, Color(0xFF22C55E)),
    AgentEmblem("claude", "Claude Code", "Anthropic · Architecture", R.drawable.ic_claude, Color(0xFFD97757)),
    AgentEmblem("codex", "Codex", "OpenAI · Code & Synthesis", R.drawable.ic_codex, Color(0xFF10A37F)),
    AgentEmblem("gemini", "Gemini", "Google · Multimodal Reasoning", R.drawable.ic_gemini, Color(0xFF3186FF)),
    AgentEmblem("meta", "Meta AI", "Meta · Llama Family", R.drawable.ic_meta, Color(0xFF1877F2)),
    AgentEmblem("mistral", "Mistral AI", "Mistral AI · Open Weights", R.drawable.ic_mistral, Color(0xFFF24E1E)),
    AgentEmblem("deepseek", "DeepSeek", "DeepSeek · Reasoning", R.drawable.ic_deepseek, Color(0xFF0066FF)),
    AgentEmblem("qwen", "Qwen", "Alibaba · Qwen Series", R.drawable.ic_qwen, Color(0xFF2563EB)),
    AgentEmblem("openclaw", "OpenClaw", "Tools & Automation", R.drawable.ic_openclaw, Color(0xFFF97316)),
    AgentEmblem("omnirush", "OmniRush", "High-Throughput Runner", R.drawable.ic_omnirush, Color(0xFFA855F7))
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
        clean.contains("claude") || clean.contains("anthropic") -> emblemByKey("claude")
        clean.contains("codex") || clean.contains("openai") -> emblemByKey("codex")
        clean.contains("hermes") || clean.contains("nous") || clean.contains("core") -> emblemByKey("hermes")
        clean.contains("claw") || clean.contains("openclaw") || clean.contains("opencode") -> emblemByKey("openclaw")
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
    Image(
        painter = painterResource(emblem.drawableRes),
        contentDescription = emblem.label,
        modifier = modifier.size(size),
        colorFilter = tint?.let { androidx.compose.ui.graphics.ColorFilter.tint(it) }
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
        AgentEmblemIcon(emblem = emblem, size = size, tint = null)
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

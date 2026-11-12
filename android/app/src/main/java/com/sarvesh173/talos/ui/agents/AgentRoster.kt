package com.sarvesh173.talos.ui.agents

import androidx.compose.runtime.Immutable

/**
 * Agent lifecycle policy.
 *
 * Two agents keep the machine reachable and are permanently warm: Hermes (the
 * gateway that delivers messages) and OpenClaw (the automation backbone). If
 * either of those could be switched off, the user would lose the ability to turn
 * anything else back on — so they are [AlwaysOn] and cannot be disabled.
 *
 * Every other CLI agent is cold by default. Spinning up Node processes, model
 * contexts and MCP servers for six idle tools is how a laptop loses its battery
 * and its RAM for no benefit. They start when the user actually starts a
 * session, and stop when it ends.
 */
enum class AgentLifecycle(val label: String, val detail: String) {
    AlwaysOn(
        label = "Always on",
        detail = "Required. Keeps the gateway reachable.",
    ),
    OnDemand(
        label = "Start when used",
        detail = "Starts when you open a session, stops when it ends.",
    ),
    Pinned(
        label = "Keep running",
        detail = "Stays warm 24/7. Costs memory and battery.",
    );

    /** Locked agents cannot be changed from the UI. */
    val isLocked: Boolean get() = this == AlwaysOn
}

/**
 * One CLI agent as the app actually knows it.
 *
 * [id] matches the backend engine id from `server/cli_engines.py`, so the
 * roster is checkable against real discovery rather than invented.
 */
@Immutable
data class AgentEntry(
    val id: String,
    val name: String,
    val subtitle: String,
    val lifecycle: AgentLifecycle,
    /** False when the binary is not installed on the host. */
    val isInstalled: Boolean,
    /** Real session count from the host, or null when unknown. */
    val sessionCount: Int? = null,
)

/**
 * The default roster.
 *
 * Deliberately short: these are the engines the backend can actually discover.
 * An agent that is not installed stays in the list but reads as unavailable,
 * because "you could install this" is useful information and a fabricated
 * session list is not.
 */
object AgentRoster {

    /** Everything that keeps TALOS reachable. Not user-configurable. */
    val ALWAYS_ON_IDS = setOf("hermes", "openclaw")

    val agents: List<AgentEntry> = listOf(
        AgentEntry(
            id = "hermes",
            name = "Hermes Agent",
            subtitle = "Gateway, messaging, memory",
            lifecycle = AgentLifecycle.AlwaysOn,
            isInstalled = true,
        ),
        AgentEntry(
            id = "openclaw",
            name = "OpenClaw",
            subtitle = "Automation backbone",
            lifecycle = AgentLifecycle.AlwaysOn,
            isInstalled = true,
        ),
        AgentEntry(
            id = "opencode",
            name = "OpenCode",
            subtitle = "Terminal coding agent",
            lifecycle = AgentLifecycle.OnDemand,
            isInstalled = true,
        ),
        AgentEntry(
            id = "claude-code",
            name = "Claude Code",
            subtitle = "Anthropic coding agent",
            lifecycle = AgentLifecycle.OnDemand,
            isInstalled = true,
        ),
        AgentEntry(
            id = "omnirush",
            name = "OmniRush",
            subtitle = "Multi-model runner",
            lifecycle = AgentLifecycle.OnDemand,
            isInstalled = true,
        ),
        AgentEntry(
            id = "zcode",
            name = "ZCode",
            subtitle = "GLM coding agent",
            lifecycle = AgentLifecycle.OnDemand,
            isInstalled = true,
        ),
        AgentEntry(
            id = "codex",
            name = "Codex CLI",
            subtitle = "OpenAI coding agent",
            lifecycle = AgentLifecycle.OnDemand,
            isInstalled = false,
        ),
        AgentEntry(
            id = "antigravity",
            name = "Antigravity",
            subtitle = "Agentic IDE",
            lifecycle = AgentLifecycle.OnDemand,
            isInstalled = false,
        ),
    )

    fun byId(id: String): AgentEntry? = agents.firstOrNull { it.id == id }

    /** Agents that may be edited. Always-on ones are excluded by construction. */
    val configurable: List<AgentEntry> get() = agents.filterNot { it.lifecycle.isLocked }

    /**
     * The roster with [installed] taken from the host instead of from this list.
     *
     * The static entries carry a plausible-looking `isInstalled`, which is the
     * problem: it reads as a fact and is not one. An engine missing from the
     * host's answer is not installed, whatever this file says. Names, subtitles
     * and the lifecycle policy are app policy and stay here; installation is the
     * host's answer and comes from it.
     */
    fun withInstalled(installed: Set<String>): List<AgentEntry> =
        agents.map { entry -> entry.copy(isInstalled = entry.id in installed) }

    /** Count that actually exists on this host, not a hardcoded marketing number. */
    fun installedCount(installedIds: Set<String>? = null): Int {
        val ids = installedIds ?: return agents.count { it.isInstalled }
        return agents.count { it.id in ids }
    }
}

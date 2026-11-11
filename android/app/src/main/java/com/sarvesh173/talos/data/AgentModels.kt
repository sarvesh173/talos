package com.sarvesh173.talos.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AgentInfo(
    val id: String,
    val name: String,
    val description: String = "",
    val installed: Boolean = false,
    val available: Boolean = false,
    @SerialName("current_model") val currentModel: String? = null,
    @SerialName("supported_models") val supportedModels: List<String> = emptyList(),
    @SerialName("cli_type") val cliType: String = "general",
    @SerialName("command_limit") val commandLimit: Int = 25,
    @SerialName("parent_agent_id") val parentAgentId: String? = null,
    @SerialName("child_agent_ids") val childAgentIds: List<String> = emptyList()
)

@Serializable
data class SetCommandLimitRequest(
    @SerialName("command_limit") val commandLimit: Int
)

@Serializable
data class DelegateChildRequest(
    val name: String,
    val description: String? = null,
    val task: String,
    @SerialName("command_limit") val commandLimit: Int? = null,
    val model: String? = null
)

@Serializable
data class AgentHierarchyNode(
    val agent: AgentInfo,
    val children: List<AgentHierarchyNode> = emptyList()
)

@Serializable
data class AgentModelInfo(
    @SerialName("agent_id") val agentId: String,
    @SerialName("current_model") val currentModel: String? = null,
    @SerialName("available_models") val availableModels: List<String> = emptyList()
)

@Serializable
data class AgentModelSwitchRequest(
    val model: String
)

@Serializable
data class PoolStatus(
    val total: Int = 0,
    val running: Int = 0,
    val max_capacity: Int = 0
)

@Serializable
data class DelegateInfo(
    val id: String,
    val name: String,
    val task: String = "",
    @SerialName("parent_id") val parentId: String = "main",
    val model: String = "stealth/crouton-alpha",
    @SerialName("reasoning_effort") val reasoningEffort: String = "high",
    @SerialName("ttl_seconds") val ttlSeconds: Int = 3600,
    @SerialName("room_id") val roomId: String = "",
    @SerialName("started_at") val startedAt: Double = 0.0,
    val status: String = "running",
    val progress: Float = 0f,
    @SerialName("tokens_generated") val tokensGenerated: Int = 0,
    @SerialName("elapsed_seconds") val elapsedSeconds: Int = 0,
    @SerialName("remaining_seconds") val remainingSeconds: Int = 0
)

@Serializable
data class SummonAgentPayload(
    val name: String,
    @SerialName("role_description") val roleDescription: String? = null,
    val model: String = "stealth/crouton-alpha",
    val color: String = "#007AFF",
    @SerialName("system_prompt") val systemPrompt: String? = null,
    val avatar: String? = null,
    @SerialName("is_active") val isActive: Boolean = true
)

/**
 * One CLI engine exactly as the host reports it.
 *
 * [installed] and [binary] come from the host's own `shutil.which` probe, so a
 * row that says installed has been checked, not assumed. [binary] is null when
 * the probe found nothing.
 */
@Serializable
data class CliRosterEntry(
    val id: String,
    val name: String,
    val installed: Boolean = false,
    val binary: String? = null,
    @SerialName("binary_name") val binaryName: String = "",
    val command: String = "",
    @SerialName("session_count") val sessionCount: Int = 0,
    @SerialName("sessions_error") val sessionsError: String = ""
)

/** `/api/agents/roster` envelope. */
@Serializable
data class CliRoster(
    val agents: List<CliRosterEntry> = emptyList(),
    @SerialName("installed_count") val installedCount: Int = 0,
    @SerialName("total_count") val totalCount: Int = 0
)

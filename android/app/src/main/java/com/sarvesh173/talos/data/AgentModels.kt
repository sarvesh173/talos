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

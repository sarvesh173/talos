package com.sarvesh173.talos.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class Role {
    @SerialName("human") human,
    @SerialName("agent") agent,
    @SerialName("system") system,
    @SerialName("orchestrator") orchestrator
}

@Serializable
data class ToolCall(
    val call_id: String = "",
    val tool_name: String,
    val arguments: JsonObject? = null,
    val output: String? = null,
    val status: String = "running",
    val started_at: Double = 0.0,
    val finished_at: Double? = null
)

@Serializable
data class AgentProfile(
    val id: String,
    val name: String,
    val avatar: String? = null,
    val color: String = "#6366f1",
    val model: String? = null,
    val role_description: String? = null,
    val is_active: Boolean = true
)

@Serializable
data class Message(
    val id: String,
    val room_id: String,
    val sender_id: String,
    val sender_name: String,
    val sender_role: Role = Role.human,
    val content: String = "",
    val thought: String? = null,
    val tool_calls: List<ToolCall> = emptyList(),
    val attachments: List<String> = emptyList(),
    val reply_to_id: String? = null,
    val created_at: Double = 0.0
)

@Serializable
data class Room(
    val id: String,
    val name: String,
    val description: String = "",
    val mode: String = "group",
    val participants: List<AgentProfile> = emptyList(),
    val created_at: Double = 0.0,
    val updated_at: Double = 0.0
)

@Serializable
data class BusEvent(
    val event_id: String,
    val event_type: String,
    val room_id: String,
    val payload: JsonObject? = null,
    val timestamp: Double = 0.0
)

@Serializable
data class MeshInfo(
    val active: Boolean = false,
    val hostname: String = "",
    val self_ip: String = "",
    val peers_count: Int = 0,
    val peer_name: String = "",
    /** Live Tailscale tunnel on the core - the app requires this to run. */
    val tailscale_enabled: Boolean = false
)

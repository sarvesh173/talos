package com.sarvesh173.talos.ui.kanban

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTOs for GET /api/kanban/board and GET /api/kanban/stats.
 *
 * Server shape (server/main.py kanban_board, server/tool_ledger.py):
 *   { room_id, stats: { room_id, total_calls, failures, running,
 *       agents: [{agent_id, agent_name, is_delegate, calls, errors, running}],
 *       tools: {tool_name: count} },
 *     lanes: { running: [row], recent: [row], failures: [row] }, server_time }
 *   row = { id, room_id, agent_id, agent_name, tool, arguments,
 *           arguments_preview, status, result_summary, error,
 *           started_at, finished_at, duration_ms, is_delegate, parent_agent_id }
 *
 * Decoding is defensive: the screen's Json uses ignoreUnknownKeys + isLenient,
 * every field has a default, and every field is nullable where the server may
 * legitimately send null (finished_at on a running call, duration_ms, arguments).
 */
@Serializable
data class KanbanBoardDto(
    val room_id: String = "",
    val stats: KanbanStatsDto = KanbanStatsDto(),
    val lanes: KanbanLanesDto = KanbanLanesDto(),
    val server_time: Double = 0.0,
)

@Serializable
data class KanbanStatsDto(
    val room_id: String = "",
    val total_calls: Int = 0,
    val failures: Int = 0,
    val running: Int = 0,
    val agents: List<KanbanAgentStatDto> = emptyList(),
    val tools: Map<String, Int> = emptyMap(),
)

@Serializable
data class KanbanAgentStatDto(
    val agent_id: String = "",
    val agent_name: String = "",
    val is_delegate: Boolean = false,
    val calls: Int = 0,
    val errors: Int = 0,
    val running: Int = 0,
)

@Serializable
data class KanbanLanesDto(
    val running: List<ToolCallDto> = emptyList(),
    val recent: List<ToolCallDto> = emptyList(),
    val failures: List<ToolCallDto> = emptyList(),
)

@Serializable
data class ToolCallDto(
    val id: String = "",
    val room_id: String = "",
    val agent_id: String = "",
    val agent_name: String = "",
    val tool: String = "",
    val arguments: JsonElement? = null,
    val arguments_preview: String = "",
    val status: String = "",
    val result_summary: String = "",
    val error: String = "",
    val started_at: Double = 0.0,
    val finished_at: Double? = null,
    val duration_ms: Long? = null,
    val is_delegate: Boolean = false,
    val parent_agent_id: String = "",
) {
    val isFailure: Boolean get() = status == "error" || status == "denied"
    val isRunning: Boolean get() = status == "running"

    /** Server-computed preview first; fall back to the raw arguments JSON. */
    fun previewText(): String {
        if (arguments_preview.isNotBlank()) return arguments_preview
        val raw = arguments?.toString().orEmpty()
        if (raw.isBlank() || raw == "null") return ""
        val flat = " ".join(raw.split(Regex("\\s+")).filter { it.isNotEmpty() })
        return if (flat.length <= 160) flat else flat.take(159) + "…"
    }
}

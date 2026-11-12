package com.sarvesh173.talos.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.NetworkInterface
import com.sarvesh173.talos.data.BusEvent
import com.sarvesh173.talos.data.Message
import com.sarvesh173.talos.data.MeshInfo
import com.sarvesh173.talos.data.ProviderSnapshotDto
import com.sarvesh173.talos.data.Role
import com.sarvesh173.talos.ui.AgentApprovalRequest
import com.sarvesh173.talos.data.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.File
import java.util.concurrent.TimeUnit

@kotlinx.serialization.Serializable
data class QueuedMessage(
    val roomId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class MeshPeerInfo(
    val hostname: String,
    val ip: String,
    val online: Boolean,
    val os: String,
    val direct: Boolean = true
)

@kotlinx.serialization.Serializable
data class SubagentChildDto(
    val id: String,
    val name: String,
    val parent_delegate_id: String,
    val task_goal: String,
    val state: String,
    val turns_used: Int,
    val created_at: Double = 0.0
)

@kotlinx.serialization.Serializable
data class DelegateLeadDto(
    val id: String,
    val name: String,
    val engine_id: String,
    val assigned_model: String,
    val supported_models: List<String> = emptyList(),
    val reasoning_effort: String,
    val command_turn_limit: Int,
    val turns_consumed: Int,
    val is_paused: Boolean = false,
    val children: List<SubagentChildDto> = emptyList()
)

@kotlinx.serialization.Serializable
data class CliEngineDto(
    val id: String,
    val name: String,
    val command_binary: String,
    val is_available: Boolean,
    val description: String? = null,
    val resolved_path: String? = null,
    val delegates: List<DelegateLeadDto> = emptyList()
)

@kotlinx.serialization.Serializable
data class DelegationHierarchyDto(
    val total_engines: Int = 0,
    val total_available_engines: Int? = null,
    val total_delegates: Int = 0,
    val total_active_children: Int = 0,
    val engines: List<CliEngineDto> = emptyList()
)

@kotlinx.serialization.Serializable
data class UpdateQuotaRequestDto(
    val command_turn_limit: Int? = null,
    val reasoning_effort: String? = null
)

@kotlinx.serialization.Serializable
data class SpawnSubagentRequestDto(
    val name: String,
    val task_goal: String
)

@kotlinx.serialization.Serializable
data class BrowserStatusDto(
    val available: Boolean,
    val version: String,
    val binary_path: String,
    val cdp_port: Int,
    val stealth_capable: Boolean
)

@kotlinx.serialization.Serializable
data class BrowserFetchRequestDto(
    val url: String,
    val stealth: Boolean = true,
    val dump_mode: String = "text",
    val timeout_secs: Long = 15
)

@kotlinx.serialization.Serializable
data class BrowserFetchResultDto(
    val url: String,
    val title: String,
    val content: String,
    val duration_ms: Long,
    val success: Boolean,
    val error: String? = null
)

@kotlinx.serialization.Serializable
data class GeneratedImageDto(
    val id: String,
    val prompt: String,
    val style: String,
    val aspect_ratio: String,
    val image_url: String,
    val created_at: Double
)

@kotlinx.serialization.Serializable
data class UploadDto(
    val url: String? = null,
    val filename: String? = null,
    val size: Long? = null
)

@kotlinx.serialization.Serializable
data class GenerateImageRequestDto(
    val prompt: String,
    val style: String = "cinematic",
    val aspect_ratio: String = "1:1"
)

@kotlinx.serialization.Serializable
data class GitDiffLineDto(
    val line_type: String,
    val old_num: Int? = null,
    val new_num: Int? = null,
    val content: String
)

@kotlinx.serialization.Serializable
data class GitFileDiffDto(
    val filename: String,
    val path: String,
    val additions: Int,
    val deletions: Int,
    val lines: List<GitDiffLineDto> = emptyList()
)

@kotlinx.serialization.Serializable
data class GitDiffSummaryDto(
    val branch: String,
    val total_additions: Int,
    val total_deletions: Int,
    val files: List<GitFileDiffDto> = emptyList()
)

@kotlinx.serialization.Serializable
data class QuestionOptionDto(
    @kotlinx.serialization.SerialName("label") val label: String = "",
    @kotlinx.serialization.SerialName("value") val value: String = "",
    @kotlinx.serialization.SerialName("kind") val kind: String = "neutral",
    @kotlinx.serialization.SerialName("description") val description: String? = null
) {
    val effectiveValue: String get() = value.ifBlank { label }
    val effectiveLabel: String get() = label.ifBlank { value }
}

/**
 * Question or permission prompt payload sent by the orchestrator.
 *
 * Preserves [kind] as an unparsed String so future server-side prompt types
 * do not crash deserialization on running mobile clients.
 */
@kotlinx.serialization.Serializable
data class AgentQuestionUi(
    @kotlinx.serialization.SerialName("id") val id: String = "",
    @kotlinx.serialization.SerialName("prompt") val prompt: String = "",
    @kotlinx.serialization.SerialName("detail") val detail: String = "",
    @kotlinx.serialization.SerialName("command") val command: String = "",
    @kotlinx.serialization.SerialName("agent_name") val agent_name: String = "",
    @kotlinx.serialization.SerialName("kind") val kind: String = "question",
    @kotlinx.serialization.SerialName("options") val options: List<QuestionOptionDto> = emptyList(),
    @kotlinx.serialization.SerialName("allow_custom") val allow_custom: Boolean = true,
    @kotlinx.serialization.SerialName("timeout_seconds") val timeout_seconds: Int? = null,
    @kotlinx.serialization.SerialName("on_timeout") val on_timeout: String? = null,
    @kotlinx.serialization.SerialName("room_id") val room_id: String = ""
) {
    val agentName: String get() = agent_name
    val allowCustom: Boolean get() = allow_custom
    val timeoutSeconds: Int? get() = timeout_seconds
    val onTimeout: String? get() = on_timeout
    val roomId: String get() = room_id
}


@kotlinx.serialization.Serializable
data class SearchHitDto(
    @kotlinx.serialization.SerialName("message_id") val message_id: String,
    @kotlinx.serialization.SerialName("room_id") val room_id: String,
    @kotlinx.serialization.SerialName("room_name") val room_name: String = "",
    @kotlinx.serialization.SerialName("sender_name") val sender_name: String = "",
    @kotlinx.serialization.SerialName("snippet") val snippet: String = "",
    @kotlinx.serialization.SerialName("created_at") val created_at: Double = 0.0,
    @kotlinx.serialization.SerialName("match_offsets") val match_offsets: List<Int> = emptyList()
)

@kotlinx.serialization.Serializable
data class SearchResponseDto(
    @kotlinx.serialization.SerialName("ok") val ok: Boolean = true,
    @kotlinx.serialization.SerialName("query") val query: String = "",
    @kotlinx.serialization.SerialName("total_hits") val total_hits: Int = 0,
    @kotlinx.serialization.SerialName("backend") val backend: String = "fts5",
    @kotlinx.serialization.SerialName("hits") val hits: List<SearchHitDto> = emptyList()
)

@kotlinx.serialization.Serializable
data class ForkResultDto(
    @kotlinx.serialization.SerialName("ok") val ok: Boolean = true,
    @kotlinx.serialization.SerialName("room_id") val room_id: String? = null,
    @kotlinx.serialization.SerialName("error_code") val error_code: String? = null,
    @kotlinx.serialization.SerialName("error") val error: String? = null,
    @kotlinx.serialization.SerialName("copied_message_count") val copied_message_count: Int = 0,
    @kotlinx.serialization.SerialName("created") val created: Boolean = false
)

@kotlinx.serialization.Serializable
data class RoomRefDto(
    @kotlinx.serialization.SerialName("id") val id: String,
    @kotlinx.serialization.SerialName("name") val name: String,
    @kotlinx.serialization.SerialName("parent_room_id") val parent_room_id: String? = null,
    @kotlinx.serialization.SerialName("forked_at_message_id") val forked_at_message_id: String? = null,
    @kotlinx.serialization.SerialName("created_at") val created_at: Double? = null,
    @kotlinx.serialization.SerialName("updated_at") val updated_at: Double? = null
)

@kotlinx.serialization.Serializable
data class PromptDto(
    @kotlinx.serialization.SerialName("id") val id: String,
    @kotlinx.serialization.SerialName("title") val title: String,
    @kotlinx.serialization.SerialName("body") val body: String,
    @kotlinx.serialization.SerialName("category") val category: String? = null,
    @kotlinx.serialization.SerialName("variables") val variables: List<String> = emptyList(),
    @kotlinx.serialization.SerialName("created_at") val created_at: Double? = null,
    @kotlinx.serialization.SerialName("updated_at") val updated_at: Double? = null
)

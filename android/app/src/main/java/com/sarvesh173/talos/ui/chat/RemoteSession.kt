package com.sarvesh173.talos.ui.chat

/**
 * The path to a real agent session, as opposed to a TALOS room.
 *
 * The chat screen has always streamed a room: messages arrive over the core's
 * socket, and `sessionId` was only used as the room key. So tapping an OpenCode
 * session opened that agent's generic room and showed nothing of the session —
 * the conversation existed, the app just never asked for it.
 *
 * These two calls ask for it. They are plain HTTP against the host, which
 * proxies the OpenCode server; the host owns auth, so the app only sends its
 * bearer token like every other call.
 */

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.sarvesh173.talos.data.Message
import com.sarvesh173.talos.data.MessageMetricsDto
import com.sarvesh173.talos.data.ToolCallRecordDto
import com.sarvesh173.talos.data.Role
import com.sarvesh173.talos.network.TalosClient
import com.sarvesh173.talos.ui.SessionSummary
import java.util.concurrent.TimeUnit

/** A session id the host recognises as one of OpenCode's own. */
fun isRemoteSessionId(sessionId: String): Boolean = sessionId.startsWith("ses_")

private val jsonMedia = "application/json; charset=utf-8".toMediaType()

private fun client(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

private fun Request.Builder.auth(token: String): Request.Builder {
    if (token.isNotBlank()) header("Authorization", "Bearer $token")
    return this
}

/**
 * One session's messages, oldest first, in the shape the chat screen renders.
 *
 * The server is inconsistent about where a turn keeps its text — user rows in
 * `text`, the prompt response in `payload.text`, assistants in `content[]` — so
 * every one of them is read rather than the one that was true last week.
 */
suspend fun fetchRemoteMessages(client: TalosClient, sessionId: String): List<Message> =
    withContext(Dispatchers.IO) {
        val http = client()
        // A silent failure here is indistinguishable from an empty conversation.
        android.util.Log.i("TalosChat", "fetch url=${client.baseUrl} session=$sessionId tokenLen=${client.apiToken.length}")
        val builder = Request.Builder()
            .url(client.baseUrl.trimEnd('/') + "/api/opencode/sessions/$sessionId/messages?limit=50")
            .get()
            .auth(client.apiToken)
        val body = http.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP " + resp.code)
            resp.body?.string() ?: error("Empty response")
        }
        val rows = JSONObject(body).optJSONArray("messages") ?: JSONArray()
        val out = ArrayList<Message>(rows.length())
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val roleStr = row.optString("role", "").lowercase()
            if (roleStr == "idle" || roleStr == "system") continue

            val rawText = if (row.isNull("text")) "" else row.optString("text", "")
            val rawError = if (row.isNull("error")) "" else row.optString("error", "")
            val text = if (rawText == "null") "" else rawText.trim()
            val error = if (rawError == "null") "" else rawError.trim()

            if (text.isBlank() && error.isBlank()) continue

            val role = if (roleStr == "user") Role.human else Role.agent
            out.add(
                Message(
                    id = row.optString("id").ifBlank { "remote-$i" },
                    room_id = sessionId,
                    sender_id = if (role == Role.human) "user" else "opencode",
                    sender_name = if (role == Role.human) "You" else "OpenCode",
                    sender_role = role,
                    content = text.ifBlank { error },
                    thought = row.optString("thought").takeIf { it.isNotBlank() },
                    metrics = parseMetrics(row.optJSONObject("metrics")),
                    tool_records = parseToolRecords(row.optJSONArray("tool_records")),
                    created_at = row.optDouble("created_ms", 0.0) / 1000.0,
                    simulated = false,
                )
            )
        }
        out
    }

/**
 * All OpenCode sessions from the host, for the session switcher.
 */
suspend fun fetchRemoteSessions(client: TalosClient): List<SessionSummary> =
    withContext(Dispatchers.IO) {
        val http = client()
        val builder = Request.Builder()
            .url(client.baseUrl.trimEnd('/') + "/api/opencode/sessions")
            .get()
            .auth(client.apiToken)
        val body = runCatching {
            http.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull() ?: return@withContext emptyList()
        val arr = JSONObject(body).optJSONArray("sessions") ?: return@withContext emptyList()
        val out = ArrayList<SessionSummary>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val sid = s.optString("id", "")
            if (sid.isBlank()) continue
            val rawTitle = if (s.isNull("title")) "" else s.optString("title", "")
            val title = if (rawTitle.isBlank() || rawTitle == "null") "Untitled session" else rawTitle
            val rawModel = if (s.isNull("model")) "" else s.optString("model", "")
            val model = if (rawModel.isBlank() || rawModel == "null") "OpenCode" else rawModel
            out.add(
                SessionSummary(
                    id = sid,
                    title = title,
                    relativeTime = model,
                    messageCount = 0
                )
            )
        }
        out
    }

/**
 * Create a new clean session on the host.
 */
suspend fun createRemoteSession(client: TalosClient, title: String = "New Session"): SessionSummary? =
    withContext(Dispatchers.IO) {
        val http = client()
        val builder = Request.Builder()
            .url(client.baseUrl.trimEnd('/') + "/api/opencode/sessions")
            .post("{}".toRequestBody(jsonMedia))
            .auth(client.apiToken)
        val body = runCatching {
            http.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull() ?: return@withContext null
        val sess = JSONObject(body).optJSONObject("session") ?: return@withContext null
        val sid = sess.optString("id", "")
        if (sid.isBlank()) return@withContext null
        SessionSummary(
            id = sid,
            title = sess.optString("title", title).ifBlank { title },
            relativeTime = "Now",
            messageCount = 0
        )
    }

/**
 * Send a message and wait for the reply.
 *
 * The host accepts a prompt immediately and the answer arrives later, so this
 * polls the same endpoint the screen renders from. It returns the assistant's
 * text, or a message to show when the provider refuses.
 */
suspend fun sendRemotePrompt(
    client: TalosClient,
    sessionId: String,
    text: String,
    timeoutMs: Long = 120_000,
): String = withContext(Dispatchers.IO) {
    val http = client()
    val payload = JSONObject().put("text", text).toString().toRequestBody(jsonMedia)
    val builder = Request.Builder()
        .url(client.baseUrl.trimEnd('/') + "/api/opencode/sessions/$sessionId/prompt")
        .post(payload)
        .auth(client.apiToken)
    val refusal = http.newCall(builder.build()).execute().use { resp ->
        if (!resp.isSuccessful) return@use "The host refused the message (HTTP " + resp.code + ")."
        val body = resp.body?.string() ?: return@use "The host accepted the message but sent nothing back."
        if (JSONObject(body).optBoolean("ok", false)) null else "The host refused the message."
    }
    if (refusal != null) return@withContext refusal

    // Poll: the reply is a separate turn. Stop at the first assistant message
    // that is not one we had already rendered, or when the budget runs out.
    val deadline = System.currentTimeMillis() + timeoutMs
    var lastError = "The agent did not answer in time."
    while (System.currentTimeMillis() < deadline) {
        delay(2_000)
        val messages = runCatching { fetchRemoteMessages(client, sessionId) }.getOrNull() ?: continue
        val last = messages.lastOrNull() ?: continue
        val refusal = messages.lastOrNull { it.simulated && it.content.isNotBlank() }
        if (refusal != null && last.id == refusal.id) return@withContext refusal.content
        if (last.sender_role == Role.agent && last.content.isNotBlank()) return@withContext last.content
    }
    lastError
}
/** Bridge telemetry, already in the DTO the rest of the app reads. */
private fun parseMetrics(row: JSONObject?): MessageMetricsDto? {
    if (row == null) return null
    fun num(key: String): Int? = if (row.isNull(key)) null else row.optInt(key)
    fun dbl(key: String): Double? = if (row.isNull(key)) null else row.optDouble(key)
    val latency = num("latency_ms")
    val prompt = num("prompt_tokens")
    val completion = num("completion_tokens")
    val reasoning = num("reasoning_tokens")
    val model = row.optString("model").takeIf { it.isNotBlank() }
    val cost = dbl("cost_usd")
    // All-null is the bridge saying "nothing to show", not a zero-latency turn.
    if (latency == null && prompt == null && completion == null && model == null && cost == null) return null
    return MessageMetricsDto(
        latency_ms = latency,
        prompt_tokens = prompt,
        completion_tokens = completion,
        reasoning_tokens = reasoning,
        cost_usd = cost,
        model = model
    )
}

private fun parseToolRecords(arr: JSONArray?): List<ToolCallRecordDto> {
    if (arr == null) return emptyList()
    val out = ArrayList<ToolCallRecordDto>(arr.length())
    for (i in 0 until arr.length()) {
        val row = arr.optJSONObject(i) ?: continue
        val name = row.optString("name").ifBlank { row.optString("summary") }
        if (name.isBlank()) continue
        out.add(
            ToolCallRecordDto(
                id = row.optString("id").ifBlank { "tool-$i" },
                name = name,
                summary = row.optString("summary").ifBlank { name },
                arguments = row.optString("arguments"),
                status = row.optString("status").ifBlank { "running" },
                started_at = row.optDouble("started_at", 0.0),
                ended_at = if (row.isNull("ended_at")) null else row.optDouble("ended_at"),
                output_preview = row.optString("output_preview")
            )
        )
    }
    return out
}

/** One agent the harness exposes — the one you address, or one it can summon. */
data class HarnessAgent(
    val id: String,
    val name: String,
    val description: String,
    val mode: String,
    val hidden: Boolean,
    val model: String?
)

/** A subagent this session summoned, with its own run. */
data class SubagentRun(
    val id: String,
    val title: String,
    val agent: String?,
    val model: String?,
    val toolCalls: Int,
    val tools: List<String>,
    val reasoningChars: Int,
    val text: String
)

/**
 * The harness's agents. Primary ones are addressable; subagents are summonable.
 *
 * The chat could not show who a turn delegated to: OpenCode runs each summoned
 * subagent in its own child session, so its work never appeared in the parent
 * transcript.
 */
suspend fun fetchHarnessAgents(client: TalosClient): List<HarnessAgent> =
    withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(client.baseUrl.trimEnd('/') + "/api/opencode/agents")
            .get()
            .auth(client.apiToken)
        val body = runCatching {
            client().newCall(builder.build()).execute().use { it.body?.string() }
        }.getOrNull() ?: return@withContext emptyList()
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext emptyList()
        if (!root.optBoolean("ok", false)) return@withContext emptyList()
        val arr = root.optJSONArray("agents") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val id = row.optString("id")
                if (id.isBlank()) continue
                add(
                    HarnessAgent(
                        id = id,
                        name = row.optString("name").ifBlank { id },
                        description = row.optString("description"),
                        mode = row.optString("mode").ifBlank { "primary" },
                        hidden = row.optBoolean("hidden", false),
                        model = row.optString("model").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

/** The subagents this session summoned, oldest first. */
suspend fun fetchSubagents(client: TalosClient, sessionId: String): List<SubagentRun> =
    withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(client.baseUrl.trimEnd('/') + "/api/opencode/sessions/$sessionId/subagents")
            .get()
            .auth(client.apiToken)
        val body = runCatching {
            client().newCall(builder.build()).execute().use { it.body?.string() }
        }.getOrNull() ?: return@withContext emptyList()
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext emptyList()
        if (!root.optBoolean("ok", false)) return@withContext emptyList()
        val arr = root.optJSONArray("subagents") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val id = row.optString("id")
                if (id.isBlank()) continue
                val tools = row.optJSONArray("tools").let { a ->
                    buildList {
                        if (a != null) {
                            for (j in 0 until a.length()) {
                                a.optString(j).takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                    }
                }
                add(
                    SubagentRun(
                        id = id,
                        title = row.optString("title").ifBlank { "Subagent" },
                        agent = row.optString("agent").takeIf { it.isNotBlank() },
                        model = row.optString("model").takeIf { it.isNotBlank() },
                        toolCalls = row.optInt("tool_calls", 0),
                        tools = tools,
                        reasoningChars = row.optInt("reasoning_chars", 0),
                        text = row.optString("text")
                    )
                )
            }
        }
    }

/**
 * End the turn in flight.
 *
 * The harness queues a prompt sent mid-turn (`delivery: "queue"`) rather than
 * refusing it, which is what lets the composer stay live: typing swaps the stop
 * square for a send arrow, and the message lands as soon as the turn ends.
 */
suspend fun stopRemotePrompt(client: TalosClient, sessionId: String): Boolean =
    withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(client.baseUrl.trimEnd('/') + "/api/opencode/sessions/$sessionId/interrupt")
            .post("".toRequestBody(jsonMedia))
            .auth(client.apiToken)
        runCatching {
            client().newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val body = resp.body?.string() ?: return@use false
                JSONObject(body).optBoolean("ok", false)
            }
        }.getOrDefault(false)
    }

/** One model the harness can run. */
data class HarnessModel(
    val id: String,
    val providerId: String,
    val name: String,
    val vision: Boolean,
    val reasoning: Boolean,
    val context: Int?
)

/** Models the picker can offer. */
suspend fun fetchHarnessModels(client: TalosClient): List<HarnessModel> =
    withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(client.baseUrl.trimEnd('/') + "/api/opencode/models")
            .get()
            .auth(client.apiToken)
        val body = runCatching {
            client().newCall(builder.build()).execute().use { it.body?.string() }
        }.getOrNull() ?: return@withContext emptyList()
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext emptyList()
        if (!root.optBoolean("ok", false)) return@withContext emptyList()
        val arr = root.optJSONArray("models") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val id = row.optString("id")
                if (id.isBlank()) continue
                add(
                    HarnessModel(
                        id = id,
                        providerId = row.optString("provider_id"),
                        name = row.optString("name").ifBlank { id },
                        vision = row.optBoolean("vision", false),
                        reasoning = row.optBoolean("reasoning", false),
                        context = if (row.isNull("context")) null else row.optInt("context")
                    )
                )
            }
        }
    }

/** Pin the session to a model. False means the host refused or could not reach the harness. */
suspend fun setSessionModel(
    client: TalosClient,
    sessionId: String,
    modelId: String,
    providerId: String = "opencode"
): Boolean = withContext(Dispatchers.IO) {
    val payload = JSONObject()
        .put("providerID", providerId)
        .put("modelID", modelId)
        .toString()
        .toRequestBody(jsonMedia)
    val builder = Request.Builder()
        .url(client.baseUrl.trimEnd('/') + "/api/opencode/sessions/$sessionId/model")
        .post(payload)
        .auth(client.apiToken)
    runCatching {
        client().newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use false
            val body = resp.body?.string() ?: return@use false
            JSONObject(body).optBoolean("ok", false)
        }
    }.getOrDefault(false)
}

/** Point the session at a different harness agent. */
suspend fun setSessionAgent(
    client: TalosClient,
    sessionId: String,
    agentId: String
): Boolean = withContext(Dispatchers.IO) {
    val payload = JSONObject().put("agent", agentId).toString().toRequestBody(jsonMedia)
    val builder = Request.Builder()
        .url(client.baseUrl.trimEnd('/') + "/api/opencode/sessions/$sessionId/agent")
        .post(payload)
        .auth(client.apiToken)
    runCatching {
        client().newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use false
            val body = resp.body?.string() ?: return@use false
            JSONObject(body).optBoolean("ok", false)
        }
    }.getOrDefault(false)
}

/** What the session is currently set to: its agent and model. */
data class SessionConfig(val agent: String?, val model: String?, val providerId: String?)

suspend fun fetchSessionConfig(client: TalosClient, sessionId: String): SessionConfig =
    withContext(Dispatchers.IO) {
        val empty = SessionConfig(agent = null, model = null, providerId = null)
        val builder = Request.Builder()
            .url(client.baseUrl.trimEnd('/') + "/api/opencode/sessions/$sessionId/config")
            .get()
            .auth(client.apiToken)
        val body = runCatching {
            client().newCall(builder.build()).execute().use { it.body?.string() }
        }.getOrNull() ?: return@withContext empty
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext empty
        if (!root.optBoolean("ok", false)) return@withContext empty
        SessionConfig(
            agent = root.optString("agent").takeIf { it.isNotBlank() },
            model = root.optString("model").takeIf { it.isNotBlank() },
            providerId = root.optString("provider_id").takeIf { it.isNotBlank() }
        )
    }

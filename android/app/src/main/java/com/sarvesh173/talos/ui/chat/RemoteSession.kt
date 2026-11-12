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
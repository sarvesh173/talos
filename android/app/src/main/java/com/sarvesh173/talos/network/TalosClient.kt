package com.sarvesh173.talos.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.sarvesh173.talos.data.BusEvent
import com.sarvesh173.talos.data.Message
import com.sarvesh173.talos.data.MeshInfo
import com.sarvesh173.talos.data.Role
import com.sarvesh173.talos.data.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.File
import java.util.concurrent.TimeUnit

class TalosClient(private val context: Context, private val scope: CoroutineScope) {
    companion object {
        const val DEFAULT_BASE_URL = "http://100.75.107.79:8000"
        private const val PREFS_NAME = "talos_prefs"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_TOKEN = "api_token"
        private const val TAG = "TalosClient"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    var baseUrl: String = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        private set

    var apiToken: String = prefs.getString(KEY_API_TOKEN, "") ?: ""
        private set

    private var activeWebSocket: WebSocket? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isPaired = MutableStateFlow(apiToken.isNotBlank())
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    private val _currentRoomId = MutableStateFlow("the-council")
    val currentRoomId: StateFlow<String> = _currentRoomId.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isAgentTyping = MutableStateFlow(false)
    val isAgentTyping: StateFlow<Boolean> = _isAgentTyping.asStateFlow()

    private fun loadCachedMessages(roomId: String): List<Message> {
        val raw = prefs.getString("cached_msgs_$roomId", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Message>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCachedMessages(roomId: String, list: List<Message>) {
        try {
            val raw = json.encodeToString(list.takeLast(100))
            prefs.edit().putString("cached_msgs_$roomId", raw).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache messages: ${e.message}")
        }
    }

    fun updateConfig(newUrl: String, newToken: String) {
        baseUrl = newUrl.trimEnd('/')
        apiToken = newToken.trim()
        prefs.edit()
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_API_TOKEN, apiToken)
            .apply()
        _isPaired.value = apiToken.isNotBlank()
        refreshRooms()
        switchRoom(_currentRoomId.value)
    }

    fun switchRoom(roomId: String) {
        if (!isPaired.value) return
        _currentRoomId.value = roomId
        _messages.value = loadCachedMessages(roomId)
        fetchHistory(roomId)
        connectWebSocket(roomId)
    }

    fun refreshRooms() {
        if (!isPaired.value) return
        scope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/api/rooms")
                    .addHeader("Authorization", "Bearer $apiToken")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: "[]"
                        val list = json.decodeFromString<List<Room>>(body)
                        _rooms.value = list
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch rooms: ${e.message}")
            }
        }
    }

    private fun fetchHistory(roomId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/api/rooms/$roomId/messages")
                    .addHeader("Authorization", "Bearer $apiToken")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: "[]"
                        val list = json.decodeFromString<List<Message>>(body)
                        _messages.value = list
                        saveCachedMessages(roomId, list)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch history for $roomId: ${e.message}")
            }
        }
    }

    private fun connectWebSocket(roomId: String) {
        activeWebSocket?.close(1000, "Switching room")
        _isConnected.value = false

        val wsUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://") +
                "/ws/$roomId/android-client?token=$apiToken"

        val req = Request.Builder().url(wsUrl).build()
        activeWebSocket = httpClient.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected to $roomId")
                _isConnected.value = true
                flushOfflineQueue(webSocket, roomId)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleInboundMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleInboundBinaryAudio(bytes)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $reason")
                _isConnected.value = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _isConnected.value = false
            }
        })
    }

    private fun handleInboundMessage(rawText: String) {
        try {
            val event = json.decodeFromString<BusEvent>(rawText)
            when (event.event_type) {
                "message_new" -> {
                    val payload = event.payload ?: return
                    val msg = json.decodeFromJsonElement<Message>(payload)
                    val updated = _messages.value + msg
                    _messages.value = updated
                    saveCachedMessages(_currentRoomId.value, updated)
                    _isAgentTyping.value = false
                }
                "token_delta" -> {
                    val msgId = event.payload?.get("message_id")?.jsonPrimitive?.content ?: ""
                    val delta = event.payload?.get("delta")?.jsonPrimitive?.content ?: ""
                    appendTokenDelta(msgId, delta)
                }
                "thought_delta" -> {
                    val msgId = event.payload?.get("message_id")?.jsonPrimitive?.content ?: ""
                    val delta = event.payload?.get("delta")?.jsonPrimitive?.content ?: ""
                    appendThoughtDelta(msgId, delta)
                }
                "typing_indicator" -> {
                    val isTyping = event.payload?.get("is_typing")?.jsonPrimitive?.content?.toBoolean() ?: false
                    _isAgentTyping.value = isTyping
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing event: ${e.message}")
        }
    }

    private fun handleInboundBinaryAudio(bytes: ByteString) {
        try {
            val audioDir = File(context.cacheDir, "audio_cache").apply { mkdirs() }
            val audioFile = File(audioDir, "memo_${System.currentTimeMillis()}.opus")
            audioFile.writeBytes(bytes.toByteArray())
            val msg = Message(
                id = "audio_${System.currentTimeMillis()}",
                room_id = _currentRoomId.value,
                sender_id = "agent",
                sender_name = "Agent Voice",
                sender_role = Role.agent,
                content = "MEDIA:${audioFile.absolutePath}",
                attachments = listOf(audioFile.absolutePath),
                created_at = System.currentTimeMillis() / 1000.0
            )
            val updated = _messages.value + msg
            _messages.value = updated
            saveCachedMessages(_currentRoomId.value, updated)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle inbound binary audio: ${e.message}")
        }
    }

    private fun appendTokenDelta(messageId: String, delta: String) {
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            val old = currentList[index]
            currentList[index] = old.copy(content = old.content + delta)
            _messages.value = currentList
        } else {
            val newMsg = Message(
                id = messageId,
                room_id = _currentRoomId.value,
                sender_id = "agent",
                sender_name = "Agent",
                sender_role = Role.agent,
                content = delta,
                created_at = System.currentTimeMillis() / 1000.0
            )
            _messages.value = currentList + newMsg
        }
    }

    private fun appendThoughtDelta(messageId: String, delta: String) {
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            val old = currentList[index]
            currentList[index] = old.copy(thought = (old.thought ?: "") + delta)
            _messages.value = currentList
        }
    }

    suspend fun checkMeshStatus(): MeshInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/api/mesh/status")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: "{}"
                        val obj = json.parseToJsonElement(body).jsonObject
                        val active = obj["active"]?.jsonPrimitive?.content?.toBoolean() ?: false
                        val host = obj["hostname"]?.jsonPrimitive?.content ?: ""
                        val ip = obj["self_ip"]?.jsonPrimitive?.content ?: ""
                        val peers = obj["peers_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val peersArr = obj["peers"]?.jsonArray
                        val pName = if (peersArr != null && peersArr.isNotEmpty()) {
                            peersArr[0].jsonObject["hostname"]?.jsonPrimitive?.content ?: ""
                        } else ""
                        MeshInfo(active, host, ip, peers, pName)
                    } else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Mesh check error: ${e.message}")
                null
            }
        }
    }

    suspend fun pairDevice(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/api/auth/pair")
                    .post(ByteArray(0).toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: "{}"
                        val obj = json.parseToJsonElement(body).jsonObject
                        val token = obj["token"]?.jsonPrimitive?.content
                        if (!token.isNullOrBlank()) {
                            updateConfig(baseUrl, token)
                        }
                        token
                    } else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pair device error: ${e.message}")
                null
            }
        }
    }

    suspend fun generateQuickToken(): String? {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/api/auth/generate-quick")
                    .post(ByteArray(0).toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: "{}"
                        val element = json.parseToJsonElement(body)
                        element.jsonObject["token"]?.jsonPrimitive?.content
                    } else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Quick token error: ${e.message}")
                null
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val currentRoom = _currentRoomId.value
        val escaped = json.encodeToString(text)
        val payload = """
            {
                "type": "message",
                "room_id": "$currentRoom",
                "sender_name": "Sarvesh",
                "content": $escaped
            }
        """.trimIndent()
        val sent = activeWebSocket?.send(payload) == true
        if (!sent) {
            enqueueOfflineMessage(currentRoom, text)
            val optimistic = Message(
                id = "temp_${System.currentTimeMillis()}",
                room_id = currentRoom,
                sender_id = "sarvesh",
                sender_name = "Sarvesh",
                sender_role = Role.human,
                content = text,
                created_at = System.currentTimeMillis() / 1000.0
            )
            _messages.value = _messages.value + optimistic
        }
    }

    private fun flushOfflineQueue(ws: WebSocket, currentRoom: String) {
        val queued = loadOfflineQueue()
        if (queued.isEmpty()) return
        val remaining = mutableListOf<QueuedMessage>()
        for (item in queued) {
            if (item.roomId == currentRoom) {
                val escaped = json.encodeToString(item.content)
                val payload = """
                    {
                        "type": "message",
                        "room_id": "${item.roomId}",
                        "sender_name": "Sarvesh",
                        "content": $escaped
                    }
                """.trimIndent()
                val sent = ws.send(payload)
                if (!sent) remaining.add(item)
            } else {
                remaining.add(item)
            }
        }
        saveOfflineQueue(remaining)
    }

    private fun loadOfflineQueue(): List<QueuedMessage> {
        val raw = prefs.getString("offline_msg_queue", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<QueuedMessage>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveOfflineQueue(items: List<QueuedMessage>) {
        try {
            val raw = json.encodeToString(items)
            prefs.edit().putString("offline_msg_queue", raw).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save offline queue: ${e.message}")
        }
    }

    private fun enqueueOfflineMessage(roomId: String, text: String) {
        val list = loadOfflineQueue().toMutableList()
        list.add(QueuedMessage(roomId, text))
        saveOfflineQueue(list)
    }
}

@kotlinx.serialization.Serializable
data class QueuedMessage(
    val roomId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

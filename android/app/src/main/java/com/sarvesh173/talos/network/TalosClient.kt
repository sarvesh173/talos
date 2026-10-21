package com.sarvesh173.talos.network

import android.util.Log
import com.sarvesh173.talos.data.BusEvent
import com.sarvesh173.talos.data.Message
import com.sarvesh173.talos.data.Role
import com.sarvesh173.talos.data.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class TalosClient(private val scope: CoroutineScope) {
    companion object {
        const val DEFAULT_BASE_URL = "http://100.75.107.79:8000"
        private const val TAG = "TalosClient"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for websockets
        .build()

    var baseUrl: String = DEFAULT_BASE_URL
        private set

    private var activeWebSocket: WebSocket? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    private val _currentRoomId = MutableStateFlow("alya-dm")
    val currentRoomId: StateFlow<String> = _currentRoomId.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isAgentTyping = MutableStateFlow(false)
    val isAgentTyping: StateFlow<Boolean> = _isAgentTyping.asStateFlow()

    fun updateBaseUrl(newUrl: String) {
        baseUrl = newUrl.trimEnd('/')
        refreshRooms()
    }

    fun switchRoom(roomId: String) {
        if (_currentRoomId.value == roomId && activeWebSocket != null) return
        _currentRoomId.value = roomId
        _messages.value = emptyList()
        fetchHistory(roomId)
        connectWebSocket(roomId)
    }

    fun refreshRooms() {
        scope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/api/rooms")
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
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: "[]"
                        val list = json.decodeFromString<List<Message>>(body)
                        _messages.value = list
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
                "/ws/$roomId/android-client"

        val req = Request.Builder().url(wsUrl).build()
        activeWebSocket = httpClient.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected to $roomId")
                _isConnected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleInboundMessage(text)
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
                    _messages.value = _messages.value + msg
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

    private fun appendTokenDelta(messageId: String, delta: String) {
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            val old = currentList[index]
            currentList[index] = old.copy(content = old.content + delta)
            _messages.value = currentList
        } else {
            // New streaming message placeholder
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

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val currentRoom = _currentRoomId.value
        val payload = """
            {
                "type": "message",
                "room_id": "$currentRoom",
                "sender_name": "Sarvesh",
                "content": ${json.encodeToString(text)}
            }
        """.trimIndent()
        activeWebSocket?.send(payload)
    }
}

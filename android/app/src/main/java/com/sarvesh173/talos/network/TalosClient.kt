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
import com.sarvesh173.talos.data.Role
import com.sarvesh173.talos.data.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
        // No personal IP baked in: real endpoint comes from MeshDiscovery
        // (UDP probe) or user pairing; loopback covers desktop/emulator runs.
        const val DEFAULT_BASE_URL = "http://127.0.0.1:8000"
        private const val PREFS_NAME = "talos_prefs"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_OVERLAY_URL = "overlay_url"
        private const val KEY_LAN_URL = "lan_url"
        private const val KEY_API_TOKEN = "api_token"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_HAS_LAUNCHED = "has_launched"
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

    /**
     * Separate client for short control-plane probes (mesh status, pairing).
     * The streaming client has no read timeout on purpose - a probe must
     * never hang the UI waiting for a silent server.
     */
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .callTimeout(2000, TimeUnit.MILLISECONDS)
        .build()

    var baseUrl: String = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        private set

    var overlayUrl: String = prefs.getString(KEY_OVERLAY_URL, "") ?: ""
        private set

    var lanUrl: String = prefs.getString(KEY_LAN_URL, "") ?: ""
        private set

    var hasLaunched: Boolean = prefs.getBoolean(KEY_HAS_LAUNCHED, false)
        private set

    fun markLaunched() {
        hasLaunched = true
        prefs.edit().putBoolean(KEY_HAS_LAUNCHED, true).apply()
    }

    var apiToken: String = prefs.getString(KEY_API_TOKEN, "") ?: ""
        private set

    private var activeWebSocket: WebSocket? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isPaired = MutableStateFlow(apiToken.isNotBlank())
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    private val _delegationHierarchy = MutableStateFlow<DelegationHierarchyDto?>(null)
    val delegationHierarchy: StateFlow<DelegationHierarchyDto?> = _delegationHierarchy.asStateFlow()

    fun fetchDelegationHierarchy() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/api/delegation/tree")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val parsed = json.decodeFromString<DelegationHierarchyDto>(body)
                        _delegationHierarchy.value = parsed
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to fetch delegation hierarchy: ${e.message}")
            }
        }
    }

    fun updateDelegateQuota(delegateId: String, newLimit: Int?, reasoningEffort: String?) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val payload = json.encodeToString(UpdateQuotaRequestDto(newLimit, reasoningEffort))
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val req = Request.Builder()
                    .url("$baseUrl/api/delegates/$delegateId/quota")
                    .patch(payload.toRequestBody(mediaType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        fetchDelegationHierarchy()
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to update delegate quota: ${e.message}")
            }
        }
    }

    fun toggleDelegatePause(delegateId: String, pause: Boolean) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val endpoint = if (pause) "pause" else "resume"
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val req = Request.Builder()
                    .url("$baseUrl/api/delegates/$delegateId/$endpoint")
                    .post("{}".toRequestBody(mediaType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        fetchDelegationHierarchy()
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to toggle delegate pause: ${e.message}")
            }
        }
    }

    fun spawnChildSubagent(delegateId: String, name: String, goal: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val payload = json.encodeToString(SpawnSubagentRequestDto(name, goal))
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val req = Request.Builder()
                    .url("$baseUrl/api/delegates/$delegateId/children")
                    .post(payload.toRequestBody(mediaType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        fetchDelegationHierarchy()
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to spawn child subagent: ${e.message}")
            }
        }
    }

    fun checkBrowserStatus(onResult: (BrowserStatusDto?) -> Unit) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/api/browser/status")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val parsed = json.decodeFromString<BrowserStatusDto>(body)
                        withContext(Dispatchers.Main) { onResult(parsed) }
                    } else {
                        withContext(Dispatchers.Main) { onResult(null) }
                    }
                }
            }.onFailure {
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    fun fetchBrowserPage(
        url: String,
        stealth: Boolean = true,
        dumpMode: String = "text",
        onResult: (BrowserFetchResultDto) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val payload = json.encodeToString(BrowserFetchRequestDto(url, stealth, dumpMode))
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val req = Request.Builder()
                    .url("$baseUrl/api/browser/fetch")
                    .post(payload.toRequestBody(mediaType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: "{}"
                    val parsed = json.decodeFromString<BrowserFetchResultDto>(body)
                    withContext(Dispatchers.Main) { onResult(parsed) }
                }
            }.onFailure { e ->
                val fallback = BrowserFetchResultDto(
                    url = url,
                    title = "Error",
                    content = "",
                    duration_ms = 0,
                    success = false,
                    error = e.message ?: "Failed to connect to browser core"
                )
                withContext(Dispatchers.Main) { onResult(fallback) }
            }
        }
    }

    private val _currentRoomId = MutableStateFlow("the-council")
    val currentRoomId: StateFlow<String> = _currentRoomId.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME_MODE, "DARK") ?: "DARK"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        val normalized = if (mode.equals("LIGHT", ignoreCase = true)) "LIGHT" else "DARK"
        _themeMode.value = normalized
        prefs.edit().putString(KEY_THEME_MODE, normalized).apply()
    }

    fun toggleTheme() {
        val next = if (_themeMode.value == "DARK") "LIGHT" else "DARK"
        setThemeMode(next)
    }

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
        val cleanUrl = newUrl.trimEnd('/')
        baseUrl = cleanUrl
        apiToken = newToken.trim()
        val editor = prefs.edit()
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_API_TOKEN, apiToken)

        // Seamless network roaming (Wi-Fi <-> Cellular Mobile Data):
        // Tailscale CGNAT addresses (100.64.0.0/10) work universally on both
        // mobile internet and any Wi-Fi. Store both overlay and local LAN so
        // roaming to cellular never loses the Tailscale connection address.
        if (cleanUrl.contains("100.") || cleanUrl.contains("tailscale") || cleanUrl.contains("ts.net")) {
            overlayUrl = cleanUrl
            editor.putString(KEY_OVERLAY_URL, overlayUrl)
        } else if (cleanUrl.contains("192.168.") || cleanUrl.contains("10.") || cleanUrl.contains("172.")) {
            lanUrl = cleanUrl
            editor.putString(KEY_LAN_URL, lanUrl)
        }
        editor.apply()
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

    fun disconnect() {
        activeWebSocket?.close(1000, "Client disconnect")
        activeWebSocket = null
        _isConnected.value = false
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
            val nanoSalt = (1000..9999).random()
            val audioDir = File(context.cacheDir, "audio_cache").apply { mkdirs() }
            val audioFile = File(audioDir, "memo_${System.currentTimeMillis()}_$nanoSalt.opus")
            audioFile.writeBytes(bytes.toByteArray())
            val msg = Message(
                id = "audio_${System.currentTimeMillis()}_$nanoSalt",
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

    suspend fun checkMeshStatus(endpoint: String = baseUrl): MeshInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("${endpoint.trimEnd('/')}/api/mesh/status")
                    .build()
                probeClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: "{}"
                        val obj = json.parseToJsonElement(body).jsonObject
                        // Only state is read here - never host identity
                        // (hostname, overlay IP, peer names).
                        // The core may report `active` directly, or the older
                        // shape (is_online + wireguard_active). Read both and
                        // never default to false just because a field is
                        // missing - that silently blocked the whole wizard.
                        val explicitActive = obj["active"]?.jsonPrimitive?.content?.toBoolean()
                        val isOnline = obj["is_online"]?.jsonPrimitive?.content?.toBoolean() ?: false
                        val wgActive = obj["wireguard_active"]?.jsonPrimitive?.content?.toBoolean() ?: false
                        val tsEnabled = obj["tailscale_enabled"]?.jsonPrimitive?.content?.toBoolean()
                        val active = explicitActive ?: (isOnline && wgActive)
                        val peers = obj["peers_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        MeshInfo(active, peers, tsEnabled)
                    } else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Mesh check error: ${e.message}")
                null
            }
        }
    }


    /**
     * Find a working core endpoint without ever hardcoding an address.
     * Order: the endpoint we already know -> the same host on the standard
     * TALOS ports (the node may have moved ports) -> LAN auto-discovery.
     */
    suspend fun resolveCore(): MeshInfo? = withContext(Dispatchers.IO) {
        // Fast bounded probe: a silent server must never freeze the gate or wizard.
        withTimeoutOrNull(4_500L) {
            resolveCoreInternal()
        }
    }

    private suspend fun resolveCoreInternal(): MeshInfo? {
        // 1. Try the last known working endpoint.
        val known = checkMeshStatus(baseUrl)
        if (known != null) return known

        // 2. If we have a saved Tailscale overlay URL and it differs from baseUrl,
        //    try it (essential when switching from Wi-Fi to cellular internet / mobile data).
        if (overlayUrl.isNotBlank() && overlayUrl != baseUrl) {
            val status = checkMeshStatus(overlayUrl)
            if (status != null) {
                baseUrl = overlayUrl
                prefs.edit().putString(KEY_BASE_URL, baseUrl).apply()
                return status
            }
        }

        // 3. If we have a saved local LAN URL and it differs from baseUrl, try it.
        if (lanUrl.isNotBlank() && lanUrl != baseUrl) {
            val status = checkMeshStatus(lanUrl)
            if (status != null) {
                baseUrl = lanUrl
                prefs.edit().putString(KEY_BASE_URL, baseUrl).apply()
                return status
            }
        }

        // 4. Try alternate standard TALOS ports (8000, 8010) across all known hosts.
        val hostsToTry = listOfNotNull(
            runCatching { java.net.URI(baseUrl).host }.getOrNull(),
            if (overlayUrl.isNotBlank()) runCatching { java.net.URI(overlayUrl).host }.getOrNull() else null,
            if (lanUrl.isNotBlank()) runCatching { java.net.URI(lanUrl).host }.getOrNull() else null
        ).distinct()

        for (host in hostsToTry) {
            for (port in listOf(8000, 8010)) {
                val candidate = "http://$host:$port"
                val status = checkMeshStatus(candidate)
                if (status != null) {
                    updateConfig(candidate, apiToken)
                    return status
                }
            }
        }

        // 5. LAN auto-discovery (UDP beacon) - only as optional hint, never override Tailscale baseUrl
        if (baseUrl.isBlank()) {
            val found = MeshDiscovery.discover()
            if (found != null) {
                val status = checkMeshStatus(found.baseUrl)
                if (status != null) {
                    lanUrl = found.baseUrl
                    prefs.edit().putString(KEY_LAN_URL, lanUrl).apply()
                    return status
                }
            }
        }
        return null
    }

    /**
     * Check whether a Tailscale or WireGuard encrypted overlay is active locally.
     * Checks TRANSPORT_VPN, LinkProperties, and the 100.64.0.0/10 CGNAT IP range.
     * Works on Mobile Internet (Cellular 4G/5G), any Wi-Fi, or across network switches.
     */
    fun isLocalTailscaleActive(): Boolean {
        // 1. Android ConnectivityManager: check if active network or any network has TRANSPORT_VPN or Tailscale IP
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val activeNet = cm.activeNetwork
                if (activeNet != null) {
                    val caps = cm.getNetworkCapabilities(activeNet)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        return true
                    }
                }
                for (net in cm.allNetworks) {
                    val caps = cm.getNetworkCapabilities(net)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        return true
                    }
                    val lp = cm.getLinkProperties(net)
                    if (lp != null) {
                        for (la in lp.linkAddresses) {
                            val ip = la.address.address
                            if (ip.size == 4 && (ip[0].toInt() and 0xFF) == 100) {
                                val second = ip[1].toInt() and 0xFF
                                if (second in 64..127) return true // Tailscale CGNAT IP range
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "VPN capability check: ${e.message}")
        }

        // 2. NetworkInterface fallback: look for tun / tailscale / wireguard interfaces or 100.64.0.0/10 IPs
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (iface in interfaces) {
                    val name = iface.name.lowercase()
                    if (name.contains("tun") || name.contains("tailscale") || name.contains("wireguard")) {
                        return true
                    }
                    for (addr in iface.inetAddresses) {
                        val ip = addr.address
                        if (ip.size == 4 && (ip[0].toInt() and 0xFF) == 100) {
                            val second = ip[1].toInt() and 0xFF
                            if (second in 64..127) return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network interface check: ${e.message}")
        }
        return false
    }

    suspend fun discoverPeers(): List<MeshPeerInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/api/mesh/peers/discover")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: "{}"
                        val obj = json.parseToJsonElement(body).jsonObject
                        val peersArr = obj["peers"]?.jsonArray ?: return@use emptyList()
                        peersArr.map { elem ->
                            val pObj = elem.jsonObject
                            MeshPeerInfo(
                                hostname = pObj["hostname"]?.jsonPrimitive?.content ?: "Unknown",
                                ip = pObj["ip"]?.jsonPrimitive?.content ?: "",
                                online = pObj["online"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                                os = pObj["os"]?.jsonPrimitive?.content ?: "",
                                direct = pObj["direct"]?.jsonPrimitive?.content?.toBoolean() ?: true
                            )
                        }
                    } else emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Peer discovery error: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun verifyAndConnect(endpoint: String, token: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUrl = endpoint.trim().trimEnd('/')
        val cleanToken = token.trim()

        if (cleanUrl.isBlank()) {
            return@withContext Pair(false, "Please enter your Core Tailscale address (e.g. http://100.x.y.z:8010)")
        }
        if (cleanToken.isBlank()) {
            return@withContext Pair(false, "Please enter the Host API Key generated on your laptop")
        }

        // 1. Health check to test Tailscale reachability
        try {
            val healthReq = Request.Builder()
                .url("$cleanUrl/health")
                .build()
            probeClient.newCall(healthReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Pair(false, "Host responded with HTTP ${resp.code}. Verify Tailscale is active.")
                }
            }
        } catch (e: Exception) {
            return@withContext Pair(false, "Cannot reach host at $cleanUrl (${e.message ?: "Timeout"}). Make sure Tailscale is active on your phone and laptop.")
        }

        // 2. Auth check to verify host-minted token
        try {
            val payload = json.encodeToString(mapOf("token" to cleanToken))
            val authReq = Request.Builder()
                .url("$cleanUrl/api/auth/pair")
                .post(payload.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
            probeClient.newCall(authReq).execute().use { resp ->
                if (resp.code == 200) {
                    updateConfig(cleanUrl, cleanToken)
                    Pair(true, "Host Connected & API Key 200 OK")
                } else if (resp.code == 401) {
                    Pair(false, "Tailscale connected (laptop reached), but Host API Key was rejected (401 Unauthorized). Check key on laptop.")
                } else {
                    Pair(false, "Host returned HTTP ${resp.code} during authorization.")
                }
            }
            } catch (e: Exception) {
            Pair(false, "Authorization request failed: ${e.message}")
            }
            }

            suspend fun pairDevice(): String? {
            return withContext(Dispatchers.IO) {
            try {
            if (apiToken.isBlank()) return@withContext null
            val payload = json.encodeToString(mapOf("token" to apiToken))
            val req = Request.Builder()
                .url("$baseUrl/api/auth/pair")
                .post(payload.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    _isPaired.value = true
                    apiToken
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
            val nanoSalt = (1000..9999).random()
            val optimistic = Message(
                id = "temp_${System.currentTimeMillis()}_$nanoSalt",
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
    val delegates: List<DelegateLeadDto> = emptyList()
)

@kotlinx.serialization.Serializable
data class DelegationHierarchyDto(
    val total_engines: Int,
    val total_delegates: Int,
    val total_active_children: Int,
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

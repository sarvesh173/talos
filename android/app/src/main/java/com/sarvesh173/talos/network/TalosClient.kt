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
        private const val KEY_THINKING_ENABLED = "thinking_enabled"
        private const val KEY_DEVICE_ACTIONS_ENABLED = "device_actions_enabled"
        private const val KEY_LINK_PREVIEWS_ENABLED = "link_previews_enabled"
        private const val KEY_OPEN_LINKS_IN_APP = "open_links_in_app"
        private const val KEY_MEMORY_UPDATES_ENABLED = "memory_updates_enabled"
        private const val KEY_LOADING_INDICATOR_STYLE = "loading_indicator_style"
        private const val KEY_ANIMATE_CHARACTERS = "animate_characters"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        private const val KEY_APP_ICON = "app_icon"
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

    private val _galleryImages = MutableStateFlow<List<GeneratedImageDto>>(emptyList())
    val galleryImages: StateFlow<List<GeneratedImageDto>> = _galleryImages.asStateFlow()

    private val _gitDiff = MutableStateFlow<GitDiffSummaryDto?>(null)
    val gitDiff: StateFlow<GitDiffSummaryDto?> = _gitDiff.asStateFlow()

    fun fetchGitDiff() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/api/git/diff")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val parsed = json.decodeFromString<GitDiffSummaryDto>(body)
                        _gitDiff.value = parsed
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to fetch git diff: ${e.message}")
            }
        }
    }

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

    fun switchDelegateModel(delegateId: String, newModel: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val payload = org.json.JSONObject().apply {
                    put("model", newModel)
                }.toString()
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val req = Request.Builder()
                    .url("$baseUrl/api/delegates/$delegateId/model")
                    .post(payload.toRequestBody(mediaType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        fetchDelegationHierarchy()
                    }
                }
            }.onFailure { e ->
                Log.e(TAG, "switchDelegateModel error: ${e.message}")
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

    fun toggleChildPause(childId: String, isCurrentlyPaused: Boolean) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val action = if (isCurrentlyPaused) "resume" else "pause"
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val req = Request.Builder()
                    .url("$baseUrl/api/children/$childId/$action")
                    .post("{}".toRequestBody(mediaType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        fetchDelegationHierarchy()
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to toggle child subagent pause: ${e.message}")
            }
        }
    }

    fun terminateChildSubagent(childId: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/api/children/$childId")
                    .delete()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        fetchDelegationHierarchy()
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to terminate child subagent: ${e.message}")
            }
        }
    }

    fun fetchImageGallery() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/api/image/gallery")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val parsed = json.decodeFromString<List<GeneratedImageDto>>(body)
                        _galleryImages.value = parsed
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to fetch gallery images: ${e.message}")
            }
        }
    }

    fun generateImage(
        prompt: String,
        style: String = "cinematic",
        aspectRatio: String = "1:1",
        onComplete: ((GeneratedImageDto?) -> Unit)? = null
    ) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val payload = json.encodeToString(GenerateImageRequestDto(prompt, style, aspectRatio))
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val req = Request.Builder()
                    .url("$baseUrl/api/image/generate")
                    .post(payload.toRequestBody(mediaType))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val parsed = json.decodeFromString<GeneratedImageDto>(body)
                        fetchImageGallery()
                        withContext(Dispatchers.Main) { onComplete?.invoke(parsed) }
                    } else {
                        withContext(Dispatchers.Main) { onComplete?.invoke(null) }
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to generate image: ${e.message}")
                withContext(Dispatchers.Main) { onComplete?.invoke(null) }
            }
        }
    }

    fun deleteGalleryImage(id: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/api/image/gallery/$id")
                    .delete()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        fetchImageGallery()
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to delete gallery image: ${e.message}")
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
        val normalized = when (mode.uppercase()) {
            "LIGHT" -> "LIGHT"
            "SYSTEM" -> "SYSTEM"
            else -> "DARK"
        }
        _themeMode.value = normalized
        prefs.edit().putString(KEY_THEME_MODE, normalized).apply()
    }

    fun toggleTheme(isCurrentlyDark: Boolean? = null) {
        val next = if (isCurrentlyDark != null) {
            if (isCurrentlyDark) "LIGHT" else "DARK"
        } else {
            val isSysDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            when (_themeMode.value.uppercase()) {
                "DARK" -> "LIGHT"
                "LIGHT" -> "DARK"
                "SYSTEM" -> if (isSysDark) "LIGHT" else "DARK"
                else -> if (isSysDark) "LIGHT" else "DARK"
            }
        }
        setThemeMode(next)
    }

    private val _themePreset = MutableStateFlow(
        prefs.getString("KEY_THEME_PRESET", "APPLE_AMOLED_BLACK") ?: "APPLE_AMOLED_BLACK"
    )
    val themePreset: StateFlow<String> = _themePreset.asStateFlow()

    fun setThemePreset(presetName: String) {
        _themePreset.value = presetName
        prefs.edit().putString("KEY_THEME_PRESET", presetName).apply()
    }

    private val _accentColor = MutableStateFlow(
        prefs.getString("KEY_APPLE_ACCENT", "BLUE") ?: "BLUE"
    )
    val accentColor: StateFlow<String> = _accentColor.asStateFlow()

    fun setAccentColor(accentNameOrHex: String) {
        _accentColor.value = accentNameOrHex
        prefs.edit().putString("KEY_APPLE_ACCENT", accentNameOrHex).apply()
    }

    private val _fontScale = MutableStateFlow(
        prefs.getString("KEY_FONT_SCALE", "standard") ?: "standard"
    )
    val fontScale: StateFlow<String> = _fontScale.asStateFlow()

    fun setFontScale(scale: String) {
        _fontScale.value = scale
        prefs.edit().putString("KEY_FONT_SCALE", scale).apply()
    }

    // -------------------------------------------------------------------------
    // CHAT PREFERENCES (Official Nous Mobile spec)
    // -------------------------------------------------------------------------
    private val _isThinkingEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_THINKING_ENABLED, true)
    )
    val isThinkingEnabled: StateFlow<Boolean> = _isThinkingEnabled.asStateFlow()

    fun setThinkingEnabled(enabled: Boolean) {
        _isThinkingEnabled.value = enabled
        prefs.edit().putBoolean(KEY_THINKING_ENABLED, enabled).apply()
    }

    private val _isDeviceActionsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_DEVICE_ACTIONS_ENABLED, true)
    )
    val isDeviceActionsEnabled: StateFlow<Boolean> = _isDeviceActionsEnabled.asStateFlow()

    fun setDeviceActionsEnabled(enabled: Boolean) {
        _isDeviceActionsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_DEVICE_ACTIONS_ENABLED, enabled).apply()
    }

    private val _isLinkPreviewsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_LINK_PREVIEWS_ENABLED, true)
    )
    val isLinkPreviewsEnabled: StateFlow<Boolean> = _isLinkPreviewsEnabled.asStateFlow()

    fun setLinkPreviewsEnabled(enabled: Boolean) {
        _isLinkPreviewsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_LINK_PREVIEWS_ENABLED, enabled).apply()
    }

    private val _isOpenLinksInApp = MutableStateFlow(
        prefs.getBoolean(KEY_OPEN_LINKS_IN_APP, true)
    )
    val isOpenLinksInApp: StateFlow<Boolean> = _isOpenLinksInApp.asStateFlow()

    fun setOpenLinksInApp(inApp: Boolean) {
        _isOpenLinksInApp.value = inApp
        prefs.edit().putBoolean(KEY_OPEN_LINKS_IN_APP, inApp).apply()
    }

    private val _isMemoryUpdatesEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_MEMORY_UPDATES_ENABLED, true)
    )
    val isMemoryUpdatesEnabled: StateFlow<Boolean> = _isMemoryUpdatesEnabled.asStateFlow()

    fun setMemoryUpdatesEnabled(enabled: Boolean) {
        _isMemoryUpdatesEnabled.value = enabled
        prefs.edit().putBoolean(KEY_MEMORY_UPDATES_ENABLED, enabled).apply()
    }

    // -------------------------------------------------------------------------
    // LOOK & FEEL PREFERENCES (Loading Style, Characters, Motion, Haptics, Icon)
    // -------------------------------------------------------------------------
    private val _loadingIndicatorStyle = MutableStateFlow(
        prefs.getString(KEY_LOADING_INDICATOR_STYLE, "Dots") ?: "Dots"
    )
    val loadingIndicatorStyle: StateFlow<String> = _loadingIndicatorStyle.asStateFlow()

    fun setLoadingIndicatorStyle(style: String) {
        _loadingIndicatorStyle.value = style
        prefs.edit().putString(KEY_LOADING_INDICATOR_STYLE, style).apply()
    }

    private val _animateCharacters = MutableStateFlow(
        prefs.getBoolean(KEY_ANIMATE_CHARACTERS, true)
    )
    val animateCharacters: StateFlow<Boolean> = _animateCharacters.asStateFlow()

    fun setAnimateCharacters(enabled: Boolean) {
        _animateCharacters.value = enabled
        prefs.edit().putBoolean(KEY_ANIMATE_CHARACTERS, enabled).apply()
    }

    private val _reduceMotion = MutableStateFlow(
        prefs.getBoolean(KEY_REDUCE_MOTION, false)
    )
    val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

    fun setReduceMotion(reduce: Boolean) {
        _reduceMotion.value = reduce
        prefs.edit().putBoolean(KEY_REDUCE_MOTION, reduce).apply()
    }

    private val _isHapticsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_HAPTICS_ENABLED, true).also {
            com.sarvesh173.talos.ui.TalosHaptics.isHapticsEnabled = it
        }
    )
    val isHapticsEnabled: StateFlow<Boolean> = _isHapticsEnabled.asStateFlow()

    fun setHapticsEnabled(enabled: Boolean) {
        _isHapticsEnabled.value = enabled
        com.sarvesh173.talos.ui.TalosHaptics.isHapticsEnabled = enabled
        prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, enabled).apply()
    }

    private val _appIcon = MutableStateFlow(
        prefs.getString(KEY_APP_ICON, "nous") ?: "nous"
    )
    val appIcon: StateFlow<String> = _appIcon.asStateFlow()

    fun setAppIcon(iconKey: String) {
        _appIcon.value = iconKey
        prefs.edit().putString(KEY_APP_ICON, iconKey).apply()
    }

    // --- Nous Portal / Talos Advanced Preferences ---
    private val _requireBiometrics = MutableStateFlow(prefs.getBoolean("KEY_REQUIRE_BIOMETRICS", false))
    val requireBiometrics: StateFlow<Boolean> = _requireBiometrics.asStateFlow()
    fun setRequireBiometrics(enabled: Boolean) {
        _requireBiometrics.value = enabled
        prefs.edit().putBoolean("KEY_REQUIRE_BIOMETRICS", enabled).apply()
    }

    private val _cacheChatList = MutableStateFlow(prefs.getBoolean("KEY_CACHE_CHAT_LIST", true))
    val cacheChatList: StateFlow<Boolean> = _cacheChatList.asStateFlow()
    fun setCacheChatList(enabled: Boolean) {
        _cacheChatList.value = enabled
        prefs.edit().putBoolean("KEY_CACHE_CHAT_LIST", enabled).apply()
    }

    private val _cacheMessages = MutableStateFlow(prefs.getBoolean("KEY_CACHE_MESSAGES", true))
    val cacheMessages: StateFlow<Boolean> = _cacheMessages.asStateFlow()
    fun setCacheMessages(enabled: Boolean) {
        _cacheMessages.value = enabled
        prefs.edit().putBoolean("KEY_CACHE_MESSAGES", enabled).apply()
    }

    fun purgeCache() {
        clearCache()
    }

    fun clearCache(): Long {
        var freedBytes = 0L
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("cached_msgs_") || it == "cached_rooms" }.forEach { key ->
            val v = prefs.all[key]
            if (v is String) freedBytes += v.toByteArray().size
            editor.remove(key)
        }
        editor.apply()
        try {
            val cacheFiles = context.cacheDir.listFiles()
            if (cacheFiles != null) {
                for (file in cacheFiles) {
                    freedBytes += file.length()
                    file.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cache dir clear failed: ${e.message}")
        }
        return freedBytes
    }

    val showThinking: StateFlow<Boolean> get() = _isThinkingEnabled.asStateFlow()
    fun setShowThinking(enabled: Boolean) = setThinkingEnabled(enabled)

    val showDeviceActions: StateFlow<Boolean> get() = _isDeviceActionsEnabled.asStateFlow()
    fun setShowDeviceActions(enabled: Boolean) = setDeviceActionsEnabled(enabled)

    val showLinkPreviews: StateFlow<Boolean> get() = _isLinkPreviewsEnabled.asStateFlow()
    fun setShowLinkPreviews(enabled: Boolean) = setLinkPreviewsEnabled(enabled)

    val openLinksInApp: StateFlow<Boolean> get() = _isOpenLinksInApp.asStateFlow()

    val showMemoryUpdates: StateFlow<Boolean> get() = _isMemoryUpdatesEnabled.asStateFlow()
    fun setShowMemoryUpdates(enabled: Boolean) = setMemoryUpdatesEnabled(enabled)

    private val _notificationsAllowed = MutableStateFlow(prefs.getBoolean("KEY_NOTIFICATIONS_ALLOWED", true))
    val notificationsAllowed: StateFlow<Boolean> = _notificationsAllowed.asStateFlow()
    fun setNotificationsAllowed(enabled: Boolean) {
        _notificationsAllowed.value = enabled
        prefs.edit().putBoolean("KEY_NOTIFICATIONS_ALLOWED", enabled).apply()
    }

    private val _categoryAgentReplies = MutableStateFlow(prefs.getBoolean("KEY_CAT_AGENT_REPLIES", true))
    val categoryAgentReplies: StateFlow<Boolean> = _categoryAgentReplies.asStateFlow()
    fun setCategoryAgentReplies(enabled: Boolean) {
        _categoryAgentReplies.value = enabled
        prefs.edit().putBoolean("KEY_CAT_AGENT_REPLIES", enabled).apply()
    }

    private val _categoryApprovals = MutableStateFlow(prefs.getBoolean("KEY_CAT_APPROVALS", true))
    val categoryApprovals: StateFlow<Boolean> = _categoryApprovals.asStateFlow()
    fun setCategoryApprovals(enabled: Boolean) {
        _categoryApprovals.value = enabled
        prefs.edit().putBoolean("KEY_CAT_APPROVALS", enabled).apply()
    }

    private val _categoryAutomations = MutableStateFlow(prefs.getBoolean("KEY_CAT_AUTOMATIONS", true))
    val categoryAutomations: StateFlow<Boolean> = _categoryAutomations.asStateFlow()
    fun setCategoryAutomations(enabled: Boolean) {
        _categoryAutomations.value = enabled
        prefs.edit().putBoolean("KEY_CAT_AUTOMATIONS", enabled).apply()
    }

    private val _categoryGatewayStatus = MutableStateFlow(prefs.getBoolean("KEY_CAT_GATEWAY_STATUS", true))
    val categoryGatewayStatus: StateFlow<Boolean> = _categoryGatewayStatus.asStateFlow()
    fun setCategoryGatewayStatus(enabled: Boolean) {
        _categoryGatewayStatus.value = enabled
        prefs.edit().putBoolean("KEY_CAT_GATEWAY_STATUS", enabled).apply()
    }

    private val _showWorkingAgents = MutableStateFlow(prefs.getBoolean("KEY_SHOW_WORKING_AGENTS", true))
    val showWorkingAgents: StateFlow<Boolean> = _showWorkingAgents.asStateFlow()
    fun setShowWorkingAgents(enabled: Boolean) {
        _showWorkingAgents.value = enabled
        prefs.edit().putBoolean("KEY_SHOW_WORKING_AGENTS", enabled).apply()
    }

    val hapticsEnabled: StateFlow<Boolean> get() = _isHapticsEnabled.asStateFlow()

    private val _isAgentTyping = MutableStateFlow(false)
    val isAgentTyping: StateFlow<Boolean> = _isAgentTyping.asStateFlow()

    private fun loadCachedMessages(roomId: String): List<Message> {
        if (!_cacheMessages.value) return emptyList()
        val raw = prefs.getString("cached_msgs_$roomId", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Message>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCachedMessages(roomId: String, list: List<Message>) {
        if (!_cacheMessages.value) return
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

    suspend fun setAgentCommandLimit(agentId: String, commandLimit: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val payload = org.json.JSONObject().put("command_limit", commandLimit).toString()
                val req = Request.Builder()
                    .url("$baseUrl/api/agents/$agentId/command-limit")
                    .post(payload.toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                httpClient.newCall(req).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                Log.e(TAG, "setAgentCommandLimit error: ${e.message}")
                false
            }
        }
    }

    suspend fun delegateChildAgent(parentId: String, name: String, task: String, limit: Int = 25): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val payload = org.json.JSONObject().apply {
                    put("name", name)
                    put("task", task)
                    put("command_limit", limit)
                }.toString()
                val req = Request.Builder()
                    .url("$baseUrl/api/agents/$parentId/delegate")
                    .post(payload.toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                httpClient.newCall(req).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                Log.e(TAG, "delegateChildAgent error: ${e.message}")
                false
            }
        }
    }

    suspend fun pauseAgent(agentId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/api/agents/$agentId/pause")
                    .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                val ok = httpClient.newCall(req).execute().use { it.isSuccessful }
                if (ok) fetchDelegationHierarchy()
                ok
            } catch (e: Exception) {
                Log.e(TAG, "pauseAgent error: ${e.message}")
                false
            }
        }
    }

    suspend fun resumeAgent(agentId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/api/agents/$agentId/resume")
                    .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                val ok = httpClient.newCall(req).execute().use { it.isSuccessful }
                if (ok) fetchDelegationHierarchy()
                ok
            } catch (e: Exception) {
                Log.e(TAG, "resumeAgent error: ${e.message}")
                false
            }
        }
    }

    suspend fun pauseAllAgents(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/api/agents/pause-all")
                    .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                val ok = httpClient.newCall(req).execute().use { it.isSuccessful }
                if (ok) fetchDelegationHierarchy()
                ok
            } catch (e: Exception) {
                Log.e(TAG, "pauseAllAgents error: ${e.message}")
                false
            }
        }
    }

    suspend fun resumeAllAgents(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/api/agents/resume-all")
                    .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                val ok = httpClient.newCall(req).execute().use { it.isSuccessful }
                if (ok) fetchDelegationHierarchy()
                ok
            } catch (e: Exception) {
                Log.e(TAG, "resumeAllAgents error: ${e.message}")
                false
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

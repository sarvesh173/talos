package com.sarvesh173.talos.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * SharedPreferences-backed store of per-service desired state.
 *
 * Each value is the user's *desired* state (what the toggle shows), kept
 * separate from the *live* state reported by the host. When the host is
 * reachable the dashboard overwrites the live state with the desired state;
 * when the host is unreachable the toggle still moves, but the live status
 * line says so plainly instead of pretending the change worked.
 *
 * Services and their defaults (first run, nothing stored yet):
 * - "dashboard"         -> ON  (Hermes dashboard is the primary surface)
 * - "opencode-server"   -> OFF (opt-in CLI service)
 * - "commandcode-proxy" -> OFF (opt-in CLI service)
 * - "opencode-bridge"   -> OFF (opt-in CLI service)
 */
object ServiceToggleStore {
    const val SERVICE_DASHBOARD = "dashboard"
    const val SERVICE_OPENCODE_SERVER = "opencode-server"
    const val SERVICE_COMMANDCODE_PROXY = "commandcode-proxy"
    const val SERVICE_OPENCODE_BRIDGE = "opencode-bridge"

    /** Stable order for the settings list. */
    val SERVICE_IDS: List<String> = listOf(
        SERVICE_DASHBOARD,
        SERVICE_OPENCODE_SERVER,
        SERVICE_COMMANDCODE_PROXY,
        SERVICE_OPENCODE_BRIDGE
    )

    private const val PREFS_NAME = "service_toggles"
    private const val KEY_BORDER_TINT_ARGB = "border_tint_argb_long"
    private const val KEY_BORDER_TINT_HEX = "border_tint_hex"

    private val DEFAULTS: Map<String, Boolean> = mapOf(
        SERVICE_DASHBOARD to true,
        SERVICE_OPENCODE_SERVER to false,
        SERVICE_COMMANDCODE_PROXY to false,
        SERVICE_OPENCODE_BRIDGE to false
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Desired state for every known service, using defaults where unset. */
    fun load(context: Context): Map<String, Boolean> {
        val p = prefs(context)
        return SERVICE_IDS.associateWith { id ->
            p.getBoolean(id, DEFAULTS[id] ?: false)
        }
    }

    /** Persist the desired state for one service. */
    fun set(context: Context, serviceId: String, enabled: Boolean) {
        prefs(context).edit().putBoolean(serviceId, enabled).apply()
    }

    /** Desired state for one service, with its documented default. */
    fun resolve(context: Context, serviceId: String): Boolean =
        prefs(context).getBoolean(serviceId, DEFAULTS[serviceId] ?: false)

    // ------------------------------------------------------------------
    // Border tint (user-changeable card borders)
    // ------------------------------------------------------------------

    /** Current border tint, defaulting to white 16%. */
    fun loadBorderTint(context: Context): Color {
        val stored = prefs(context).getLong(KEY_BORDER_TINT_ARGB, Long.MIN_VALUE)
        return if (stored == Long.MIN_VALUE) {
            Color.White.copy(alpha = 0.16f)
        } else {
            Color(stored.toULong())
        }
    }

    /** Persist a border tint. */
    fun setBorderTint(context: Context, color: Color) {
        prefs(context).edit()
            .putLong(KEY_BORDER_TINT_ARGB, color.value.toLong())
            .apply()
    }

    /** Custom hex string the user typed (e.g. "#FFFFFF"), or "" if none. */
    fun loadBorderTintHex(context: Context): String =
        prefs(context).getString(KEY_BORDER_TINT_HEX, "") ?: ""

    /** Persist a custom hex string. */
    fun setBorderTintHex(context: Context, hex: String) {
        prefs(context).edit().putString(KEY_BORDER_TINT_HEX, hex).apply()
    }
}

/** Neutral border presets offered in the UI: white 8% / 16% / 24%. */
val BorderTintPresets: List<Color> = listOf(
    Color.White.copy(alpha = 0.08f),
    Color.White.copy(alpha = 0.16f),
    Color.White.copy(alpha = 0.24f)
)

/**
 * CompositionLocal so any screen can read the user's border tint without
 * threading it through every parameter. Wrap the tree once at the app root;
 * [DashboardControlScreen] does this internally with the stored value.
 */
val LocalBorderTint = staticCompositionLocalOf { Color.White.copy(alpha = 0.16f) }

/**
 * Talks to the host to flip a service on/off.
 *
 * SERVER SIDE IS A SEPARATE TASK: the endpoints below
 * (POST /api/services/{id}/enable and /api/services/{id}/disable) do not
 * exist yet. The orchestrator must implement them on the host (systemctl
 * --user start/stop for hermes-dashboard.service and the CLI units) and
 * wire an [HttpServiceController] into [DashboardControlScreen]. Until
 * then, passing `controller = null` keeps the screen fully usable offline.
 */
fun interface ServiceController {
    suspend fun setEnabled(serviceId: String, enabled: Boolean): Result<Unit>
}

/**
 * HTTP implementation of [ServiceController].
 *
 * @param baseUrl host base URL, e.g. "http://100.75.107.79:9119"
 * @param token bearer token (Authorization: Bearer <token>)
 */
class HttpServiceController(
    private val baseUrl: String,
    private val token: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) : ServiceController {
    override suspend fun setEnabled(serviceId: String, enabled: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            val action = if (enabled) "enable" else "disable"
            val url = baseUrl.trimEnd('/') + "/api/services/" + serviceId + "/" + action
            val request = Request.Builder()
                .url(url)
                .post(ByteArray(0).toRequestBody(null))
                .apply {
                    if (token.isNotBlank()) header("Authorization", "Bearer " + token)
                }
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) Result.success(Unit)
                    else Result.failure(IllegalStateException("HTTP " + response.code))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

/** Parse "#RRGGBB" or "#AARRGGBB" into a Color, or null if invalid. */
fun parseBorderHex(hex: String): Color? {
    val cleaned = hex.trim().removePrefix("#")
    return try {
        when (cleaned.length) {
            6 -> Color((0xFF000000L or cleaned.toLong(16)).toULong())
            8 -> Color(cleaned.toLong(16).toULong())
            else -> null
        }
    } catch (e: NumberFormatException) {
        null
    }
}

/** Format a Color as "#RRGGBB" (alpha ignored). */
fun formatBorderHex(color: Color): String =
    String.format("#%06X", color.toArgb() and 0xFFFFFF)

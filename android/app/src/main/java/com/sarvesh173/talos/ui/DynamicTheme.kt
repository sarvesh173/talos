package com.sarvesh173.talos.ui

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.sarvesh173.talos.network.TalosClient
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Dynamic theme tokens served by the Rust core (`GET /api/theme`).
 *
 * Pattern proven in ReadYou (ReadYouApp/ReadYou): palettes live outside the
 * binary and schemes are rebuilt at runtime, so a palette change never needs
 * an APK rebuild. Here the palette source is talos-core instead of wallpaper.
 */
data class TalosThemeConfig(
    val name: String,
    val dark: Map<String, Color>,
    val light: Map<String, Color>,
) {
    companion object {
        private val REQUIRED_DARK = listOf(
            "primary", "on_primary", "primary_container", "on_primary_container",
            "secondary", "on_secondary", "secondary_container", "on_secondary_container",
            "tertiary", "on_tertiary", "tertiary_container", "on_tertiary_container",
            "error", "on_error", "surface", "on_surface", "on_surface_variant",
            "surface_container_lowest", "surface_container_low", "surface_container",
            "surface_container_high", "surface_container_highest", "outline", "outline_variant",
        )

        fun fromJson(root: JSONObject): TalosThemeConfig? {
            fun roles(obj: JSONObject?): Map<String, Color>? {
                if (obj == null) return null
                val map = HashMap<String, Color>(32)
                for (key in REQUIRED_DARK) {
                    val hex = obj.optString(key, "")
                    val color = parseHex(hex) ?: return null
                    map[key] = color
                }
                return map
            }

            val dark = roles(root.optJSONObject("dark")) ?: return null
            val light = roles(root.optJSONObject("light")) ?: return null
            val name = root.optString("name", "Remote Theme")
            return TalosThemeConfig(name, dark, light)
        }

        fun parseHex(hex: String): Color? {
            val body = hex.removePrefix("#")
            if (body.length != 6) return null
            return body.toLongOrNull(16)?.let { Color(it or 0xFF000000uL.toLong()) }
        }
    }
}

/**
 * Fetch + cache theme tokens. Falls back to the cached copy when the core is
 * unreachable, and to the built-in scheme when nothing is cached.
 */
object ThemeRepository {
    private const val PREFS = "talos_theme"
    private const val KEY_THEME_JSON = "theme_v1"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    /** Try network, then cache. Null = use built-in defaults. */
    fun load(baseUrl: String, context: Context): TalosThemeConfig? {
        fetch(baseUrl)?.let { cfg ->
            cache(context, cfg)
            return cfg
        }
        return cached(context)
    }

    /** GET {base}/api/theme — the Rust core's single source of theme truth. */
    fun fetch(baseUrl: String): TalosThemeConfig? = try {
        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/theme")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            TalosThemeConfig.fromJson(JSONObject(body))
        }
    } catch (_: Exception) {
        null
    }

    fun cache(context: Context, cfg: TalosThemeConfig) {
        runCatching {
            val json = JSONObject().apply {
                put("name", cfg.name)
                put("dark", JSONObject().apply { cfg.dark.forEach { (k, v) -> put(k, toHex(v)) } })
                put("light", JSONObject().apply { cfg.light.forEach { (k, v) -> put(k, toHex(v)) } })
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME_JSON, json.toString()).apply()
        }
    }

    fun cached(context: Context): TalosThemeConfig? = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME_JSON, null) ?: return null
        TalosThemeConfig.fromJson(JSONObject(raw))
    }.getOrNull()

    private fun toHex(c: Color): String {
        val v = (c.value.toULong() shr 32).toULong() and 0xFFFFFFuL
        return "#%06X".format(v.toLong())
    }
}

/** Remote tokens -> Material 3 ColorScheme (same interface ReadYou builds from tonal palettes). */
fun TalosThemeConfig.darkScheme(): ColorScheme = colorSchemeFrom(dark, dark = true)

fun TalosThemeConfig.lightScheme(): ColorScheme = colorSchemeFrom(light, dark = false)

private fun colorSchemeFrom(r: Map<String, Color>, dark: Boolean): ColorScheme {
    fun key(k: String) = r.getValue(k)
    return if (dark) {
        darkColorScheme(
            primary = key("primary"),
            onPrimary = key("on_primary"),
            primaryContainer = key("primary_container"),
            onPrimaryContainer = key("on_primary_container"),
            secondary = key("secondary"),
            onSecondary = key("on_secondary"),
            secondaryContainer = key("secondary_container"),
            onSecondaryContainer = key("on_secondary_container"),
            tertiary = key("tertiary"),
            onTertiary = key("on_tertiary"),
            tertiaryContainer = key("tertiary_container"),
            onTertiaryContainer = key("on_tertiary_container"),
            error = key("error"),
            onError = key("on_error"),
            surface = key("surface"),
            onSurface = key("on_surface"),
            onSurfaceVariant = key("on_surface_variant"),
            surfaceContainerLowest = key("surface_container_lowest"),
            surfaceContainerLow = key("surface_container_low"),
            surfaceContainer = key("surface_container"),
            surfaceContainerHigh = key("surface_container_high"),
            surfaceContainerHighest = key("surface_container_highest"),
            outline = key("outline"),
            outlineVariant = key("outline_variant"),
        )
    } else {
        lightColorScheme(
            primary = key("primary"),
            onPrimary = key("on_primary"),
            primaryContainer = key("primary_container"),
            onPrimaryContainer = key("on_primary_container"),
            secondary = key("secondary"),
            onSecondary = key("on_secondary"),
            secondaryContainer = key("secondary_container"),
            onSecondaryContainer = key("on_secondary_container"),
            tertiary = key("tertiary"),
            onTertiary = key("on_tertiary"),
            tertiaryContainer = key("tertiary_container"),
            onTertiaryContainer = key("on_tertiary_container"),
            error = key("error"),
            onError = key("on_error"),
            surface = key("surface"),
            onSurface = key("on_surface"),
            onSurfaceVariant = key("on_surface_variant"),
            surfaceContainerLowest = key("surface_container_lowest"),
            surfaceContainerLow = key("surface_container_low"),
            surfaceContainer = key("surface_container"),
            surfaceContainerHigh = key("surface_container_high"),
            surfaceContainerHighest = key("surface_container_highest"),
            outline = key("outline"),
            outlineVariant = key("outline_variant"),
        )
    }
}

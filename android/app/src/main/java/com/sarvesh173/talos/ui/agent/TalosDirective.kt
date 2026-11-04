package com.sarvesh173.talos.ui.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Client mirror of the SYSTEM.md `:::talos` directive protocol.
 *
 * The backend parser (core/directives.py) is canonical; this parser applies
 * the same contract on the client so persisted messages render the same way
 * the core validated them: at most three blocks per reply, one permission,
 * malformed JSON dropped without disturbing the prose, an unclosed fence
 * left as prose.
 */
sealed interface TalosDirective {
    val type: String
}

data class ChartSeries(val name: String, val values: List<Float?>)

data class ChartDirective(
    override val type: String = "chart",
    val kind: String,
    val title: String = "",
    val x: List<String>,
    val series: List<ChartSeries>,
    val unit: String = ""
) : TalosDirective

data class SelectOption(val id: String, val label: String, val hint: String = "", val checked: Boolean = false)

data class SelectDirective(
    override val type: String = "select",
    val multi: Boolean = false,
    val title: String = "",
    val options: List<SelectOption>,
    val submitLabel: String = "Submit"
) : TalosDirective

data class ConfirmDirective(
    override val type: String = "confirm",
    val title: String,
    val body: String = "",
    val confirmLabel: String = "Confirm",
    val denyLabel: String = "Deny",
    val deadlineSeconds: Int? = null,
    val defaultOnTimeout: String
) : TalosDirective

data class PermissionDirective(
    override val type: String = "permission",
    val tool: String,
    val command: String = "",
    val reason: String = "",
    val scopes: List<String>
) : TalosDirective

data class GameDirective(
    override val type: String = "game",
    val game: String,
    val title: String = "",
    val prompt: String = "",
    val state: Map<String, Any?> = emptyMap()
) : TalosDirective

data class EmojiDirective(
    override val type: String = "emoji",
    val title: String = "",
    val options: List<String>
) : TalosDirective

data class CardItem(val title: String, val subtitle: String = "", val badge: String = "", val accent: String = "gray")

data class CardsDirective(
    override val type: String = "cards",
    val title: String = "",
    val items: List<CardItem>
) : TalosDirective

sealed interface TalosSegment {
    data class Prose(val text: String) : TalosSegment
    data class Directive(val directive: TalosDirective) : TalosSegment
}

data class ParsedTalosReply(val segments: List<TalosSegment>) {
    val directives: List<TalosDirective>
        get() = segments.filterIsInstance<TalosSegment.Directive>().map { it.directive }

    val prose: String
        get() = segments.filterIsInstance<TalosSegment.Prose>().joinToString("\n") { it.text }.trim()
}

private val FENCE = Regex(":::talos[ \t]*\r?\n?(.*?)\r?\n?:::", RegexOption.DOT_MATCHES_ALL)

private const val MAX_BLOCKS = 3
private const val MAX_PERMISSIONS = 1

object TalosDirectiveParser {

    fun parse(text: String?): ParsedTalosReply {
        if (text.isNullOrEmpty()) return ParsedTalosReply(emptyList())
        val segments = mutableListOf<TalosSegment>()
        var pos = 0
        var kept = 0
        var permissions = 0
        for (match in FENCE.findAll(text)) {
            val head = text.substring(pos, match.range.first)
            if (head.isNotEmpty()) segments += TalosSegment.Prose(head)
            pos = match.range.last + 1
            if (kept >= MAX_BLOCKS) continue
            val body = match.groupValues[1].trim()
            val directive = try {
                if (body.isEmpty()) null else validate(JSONObject(body))
            } catch (_: Exception) {
                null // malformed JSON: drop the block, keep the prose
            } ?: continue
            if (directive is PermissionDirective) {
                if (permissions >= MAX_PERMISSIONS) continue
                permissions++
            }
            segments += TalosSegment.Directive(directive)
            kept++
        }
        val tail = text.substring(pos)
        if (tail.isNotEmpty()) segments += TalosSegment.Prose(tail)
        if (segments.isEmpty()) segments += TalosSegment.Prose(text)
        return ParsedTalosReply(segments)
    }

    fun validate(root: JSONObject): TalosDirective? = when (root.optString("type", "")) {
        "chart" -> validateChart(root)
        "select" -> validateSelect(root)
        "confirm" -> validateConfirm(root)
        "permission" -> validatePermission(root)
        "game" -> validateGame(root)
        "emoji" -> validateEmoji(root)
        "cards" -> validateCards(root)
        else -> null
    }

    private fun validateChart(root: JSONObject): ChartDirective? {
        val kind = root.optString("kind", "")
        if (kind !in setOf("bar", "line", "area")) return null
        val x = root.optJSONArray("x") ?: return null
        if (x.length() !in 1..12) return null
        val labels = (0 until x.length()).map { x.optString(it, "") }
        val rawSeries = root.optJSONArray("series") ?: return null
        if (rawSeries.length() !in 1..4) return null
        val series = (0 until rawSeries.length()).map { i ->
            val entry = rawSeries.optJSONObject(i) ?: return null
            val values = entry.optJSONArray("values") ?: return null
            if (values.length() != x.length()) return null
            val parsed = (0 until values.length()).map { j ->
                val v = values.opt(j)
                when {
                    v == null || v == JSONObject.NULL -> null
                    v is Number -> v.toFloat()
                    else -> return null
                }
            }
            ChartSeries(entry.optString("name", ""), parsed)
        }
        return ChartDirective(kind = kind, title = root.optString("title", ""), x = labels, series = series, unit = root.optString("unit", ""))
    }

    private fun validateSelect(root: JSONObject): SelectDirective? {
        val raw = root.optJSONArray("options") ?: return null
        if (raw.length() !in 2..8) return null
        val options = (0 until raw.length()).map { i ->
            val entry = raw.optJSONObject(i) ?: return null
            val id = entry.optString("id", "")
            val label = entry.optString("label", "")
            if (id.isBlank() || label.isBlank()) return null
            SelectOption(id, label, entry.optString("hint", ""), entry.optBoolean("checked", false))
        }
        return SelectDirective(
            multi = root.optBoolean("multi", false),
            title = root.optString("title", ""),
            options = options,
            submitLabel = root.optString("submit_label", "Submit").ifBlank { "Submit" }
        )
    }

    private fun validateConfirm(root: JSONObject): ConfirmDirective? {
        val title = root.optString("title", "")
        if (title.isBlank()) return null
        val default = root.optString("default_on_timeout", "")
        if (default !in setOf("confirm", "deny")) return null
        val deadline = if (root.has("deadline_seconds")) root.optInt("deadline_seconds", -1) else null
        if (deadline != null && deadline !in 15..600) return null
        return ConfirmDirective(
            title = title,
            body = root.optString("body", ""),
            confirmLabel = root.optString("confirm_label", "Confirm").ifBlank { "Confirm" },
            denyLabel = root.optString("deny_label", "Deny").ifBlank { "Deny" },
            deadlineSeconds = deadline,
            defaultOnTimeout = default
        )
    }

    private fun validatePermission(root: JSONObject): PermissionDirective? {
        val tool = root.optString("tool", "")
        if (tool.isBlank()) return null
        val raw = root.optJSONArray("scopes")
        val scopes = if (raw == null) listOf("once", "session") else (0 until raw.length()).map { raw.optString(it, "") }
        if (scopes.isEmpty() || scopes.any { it !in setOf("once", "session", "full") }) return null
        return PermissionDirective(
            tool = tool,
            command = root.optString("command", ""),
            reason = root.optString("reason", ""),
            scopes = scopes
        )
    }

    private fun validateGame(root: JSONObject): GameDirective? {
        val game = root.optString("game", "")
        if (game !in setOf("rock-paper-scissors", "tic-tac-toe", "guess-number")) return null
        val stateObj = root.optJSONObject("state")
        val state = LinkedHashMap<String, Any?>()
        if (stateObj != null) {
            val keys = stateObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = stateObj.opt(key)
                state[key] = when {
                    value == null || value == JSONObject.NULL -> null
                    value is JSONArray -> (0 until value.length()).map { value.opt(it) }
                    else -> value
                }
            }
        }
        return GameDirective(
            game = game,
            title = root.optString("title", ""),
            prompt = root.optString("prompt", ""),
            state = state
        )
    }

    private fun validateEmoji(root: JSONObject): EmojiDirective? {
        val raw = root.optJSONArray("options") ?: return null
        if (raw.length() !in 4..8) return null
        val options = (0 until raw.length()).map { raw.optString(it, "") }
        if (options.any { it.isBlank() }) return null
        return EmojiDirective(title = root.optString("title", ""), options = options)
    }

    private fun validateCards(root: JSONObject): CardsDirective? {
        val raw = root.optJSONArray("items") ?: return null
        if (raw.length() !in 2..6) return null
        val items = (0 until raw.length()).map { i ->
            val entry = raw.optJSONObject(i) ?: return null
            val title = entry.optString("title", "")
            if (title.isBlank()) return null
            val badge = entry.optString("badge", "")
            if (badge.isNotBlank() && badge.trim().contains(" ")) return null
            val accent = entry.optString("accent", "gray")
            if (accent !in setOf("blue", "green", "orange", "red", "purple", "gray")) return null
            CardItem(title, entry.optString("subtitle", ""), badge, accent)
        }
        return CardsDirective(title = root.optString("title", ""), items = items)
    }
}

package com.sarvesh173.talos.ui.bot

import android.content.Context
import java.io.File

/**
 * Persists user-supplied SVG marks per agent id in app-private storage.
 *
 * Storage: `context.filesDir/logos/<agentId>.svg` (plain files, not
 * SharedPreferences — an SVG can be large, up to [MAX_SVG_BYTES], and belongs
 * in a file). Files under `filesDir` survive process death, app restarts and
 * app updates; they are cleared only by the user (or by system "Clear data").
 *
 * DEPENDENCY (not yet present in the repo at authoring time): another agent is
 * writing `ui/bot/SvgPathParser.kt` with the assumed API
 * `SvgPathParser.parseSvg(source: String): SvgParseResult`, where
 * `SvgParseResult` exposes `contours: List<List<Pt>>` in viewport coords,
 * y-down. This file is coded against exactly that signature; if the parser
 * lands with a different field name, rename `result.contours` in [bakeCustom]
 * and in the sheet's preview.
 */
object CustomLogoStore {

    const val MAX_SVG_BYTES: Int = 512 * 1024 // 512 KB size cap

    private const val DIR_NAME = "logos"
    private const val SVG_EXT = ".svg"
    private const val COLOR_EXT = ".color"
    private const val MAX_ID_LENGTH = 128

    // Allowed filename characters (after lower-casing): keeps every real
    // AgentRoster id ("claude-code", "opencode", ...) valid while making
    // traversal characters impossible by construction.
    private val SAFE_ID = Regex("^[a-z0-9][a-z0-9._-]*$")

    /**
     * Returns the file for [agentId] inside `filesDir/logos/`, or null when
     * [agentId] is unsafe.
     *
     * Sanitisation rules (enforced here, the single choke point for every
     * public API):
     * - blank / empty ids are rejected;
     * - ids containing `/`, `\` or the NUL character (`\u0000`) are rejected;
     * - ids containing the substring `..` are rejected;
     * - ids longer than 128 chars are rejected;
     * - ids not matching `^[a-z0-9][a-z0-9._-]*$` after lower-casing are
     *   rejected (covers `.` / `..`, leading dots, spaces, control chars);
     * - the final canonical path must start with the canonical `logos/` dir.
     */
    fun fileFor(context: Context, agentId: String): File? {
        val safe = safeIdOrNull(agentId) ?: return null
        val dir = logosDir(context)
        val file = File(dir, safe + SVG_EXT)
        val dirCanon = dir.canonicalPath
        val fileCanon = file.canonicalPath
        if (!fileCanon.startsWith(dirCanon + File.separator)) return null
        return file
    }

    /** Save [svg] for [agentId]. Fails (without writing) when the id is
     *  unsafe, the svg is blank, or it exceeds the 512 KB cap. Atomic: the
     *  bytes are written to a sibling temp file and renamed into place. */
    fun save(context: Context, agentId: String, svg: String): Result<Unit> {
        val file = fileFor(context, agentId)
            ?: return Result.failure(
                IllegalArgumentException("Invalid agent id '$agentId' (unsafe filename)")
            )
        if (svg.isBlank()) {
            return Result.failure(IllegalArgumentException("SVG is blank"))
        }
        val bytes = svg.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_SVG_BYTES) {
            return Result.failure(
                IllegalArgumentException(
                    "SVG is ${bytes.size} bytes, exceeds the ${MAX_SVG_BYTES / 1024} KB size cap"
                )
            )
        }
        return try {
            val dir = logosDir(context)
            dir.mkdirs()
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Returns the stored SVG string for [agentId], or null if none / unsafe /
     *  unreadable / over the size cap. */
    fun load(context: Context, agentId: String): String? {
        val file = fileFor(context, agentId) ?: return null
        if (!file.isFile) return null
        return try {
            if (file.length() > MAX_SVG_BYTES) return null
            file.readText(Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /** Removes any stored SVG (and colour override) for [agentId]. */
    fun remove(context: Context, agentId: String) {
        val safe = safeIdOrNull(agentId) ?: return
        val dir = logosDir(context)
        File(dir, safe + SVG_EXT).delete()
        File(dir, safe + COLOR_EXT).delete()
    }

    /** All custom marks: agent id (lowercase, extension stripped) -> file. */
    fun list(context: Context): Map<String, File> {
        val dir = logosDir(context)
        if (!dir.isDirectory) return emptyMap()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(SVG_EXT) }
            ?.associateBy { it.name.removeSuffix(SVG_EXT) }
            ?: emptyMap()
    }

    // --- colour override (sibling file, keeps everything in filesDir) ------

    /** Persist a colour override as `#RRGGBB` in `logos/<agentId>.color`. */
    fun saveColor(context: Context, agentId: String, hex: String): Result<Unit> {
        val safe = safeIdOrNull(agentId)
            ?: return Result.failure(
                IllegalArgumentException("Invalid agent id '$agentId' (unsafe filename)")
            )
        if (!isValidHex(hex)) {
            return Result.failure(IllegalArgumentException("Invalid colour hex '$hex'"))
        }
        return try {
            val dir = logosDir(context)
            dir.mkdirs()
            File(dir, safe + COLOR_EXT).writeText(hex, Charsets.UTF_8)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Returns a stored colour override as `#RRGGBB`, or null if none. */
    fun loadColor(context: Context, agentId: String): String? {
        val safe = safeIdOrNull(agentId) ?: return null
        val file = File(logosDir(context), safe + COLOR_EXT)
        if (!file.isFile) return null
        return try {
            file.readText(Charsets.UTF_8).trim().takeIf { isValidHex(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun clearColor(context: Context, agentId: String) {
        val safe = safeIdOrNull(agentId) ?: return
        File(logosDir(context), safe + COLOR_EXT).delete()
    }

    // --- wire-through to the point-cloud baker -----------------------------

    /**
     * Validate [svg] with `SvgPathParser.parseSvg`, bake it with
     * [AgentLogoBaker.bakeFromContours], and (when [persist] is true) save it
     * via [save] so the mark is the one `cloudFor` callers should now use.
     * Returns the baked cloud; fails with a clear error when the SVG cannot
     * be parsed into any contour or the id/size checks fail. Does NOT edit
     * `AgentLogoCloud.kt` — this is the separate entry point for custom marks.
     */
    fun bakeCustom(context: Context, agentId: String, svg: String, persist: Boolean = false): Result<AgentLogoCloud> {
        val result = try {
            SvgPathParser.parseSvg(svg)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val contours: List<List<Pt>> = result.contours
        val valid = contours.filter { it.size >= 3 }
        if (valid.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("SVG could not be parsed: no usable path contours found")
            )
        }
        if (persist) {
            val saved = save(context, agentId, svg)
            if (saved.isFailure) return Result.failure(saved.exceptionOrNull()!!)
        }
        return Result.success(AgentLogoBaker.bakeFromContours(valid))
    }

    // --- internals ----------------------------------------------------------

    private fun logosDir(context: Context): File = File(context.filesDir, DIR_NAME)

    private fun safeIdOrNull(agentId: String): String? {
        if (agentId.isBlank()) return null
        if (agentId.contains('/') || agentId.contains('\\') || agentId.contains('\u0000')) return null
        if (agentId.contains("..")) return null
        val safe = agentId.trim().lowercase()
        if (safe.length > MAX_ID_LENGTH) return null
        if (!SAFE_ID.matches(safe)) return null
        return safe
    }

    private fun isValidHex(hex: String): Boolean {
        val h = hex.trim()
        return h.length == 7 && h.startsWith("#") &&
            h.substring(1).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }
}

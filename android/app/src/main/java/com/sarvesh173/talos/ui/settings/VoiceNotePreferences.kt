package com.sarvesh173.talos.ui.settings

import android.content.Context

/**
 * Which container/codec a recorded voice note is stored as.
 *
 * Verified against android.jar (API 36) — these are the only audio output
 * formats MediaRecorder actually exposes:
 *
 *   OutputFormat.MPEG_4  = 2   API 1   → .m4a, AAC  (universal)
 *   OutputFormat.WEBM    = 9   API 21  → .webm, Opus (minSdk 26 is fine)
 *   OutputFormat.OGG     = 11  API 29  → .ogg, Opus (WhatsApp/Telegram voice)
 *
 * There is no MP3 encoder constant in MediaRecorder at all — only AAC,
 * HE_AAC, AMR_NB, AMR_WB, VORBIS (21+), OPUS (29+). MP3 is offered in the
 * picker because the user asked for it, and it is honestly labelled as
 * "Ogg/Opus relabelled": the audio is really Opus, the file just wears an
 * .mp3 name for tools that only accept that extension. The UI says so.
 *
 * Keeping the list to three is deliberate: every extra format is another
 * codec path to break on a 2017 phone, for no user-visible gain.
 */
enum class AudioFormat(
    val id: String,
    val label: String,
    val blurb: String,
    val container: String,
    val extension: String
) {
    /** Default. Works on every supported device, plays everywhere. */
    M4A(
        id = "m4a",
        label = "M4A (AAC)",
        blurb = "Plays everywhere. Safest default.",
        container = "MPEG_4",
        extension = "m4a"
    ),

    /** Smallest files, native Opus. */
    WEBM(
        id = "webm",
        label = "WebM (Opus)",
        blurb = "Smallest files. Modern apps only.",
        container = "WEBM",
        extension = "webm"
    ),

    /** The WhatsApp/Telegram voice-note format. API 29+, so it degrades. */
    OGG(
        id = "ogg",
        label = "Ogg (Opus)",
        blurb = "WhatsApp/Telegram voice format.",
        container = "OGG",
        extension = "ogg"
    ),

    /** Honest alias: Opus audio wearing an .mp3 name, for picky tools. */
    MP3(
        id = "mp3",
        label = "MP3",
        blurb = "Not real MP3 — Ogg audio named .mp3.",
        container = "OGG",
        extension = "mp3"
    );

    companion object {
        val Default = M4A

        fun fromId(id: String?): AudioFormat =
            entries.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * Persisted voice-note settings. Same shape as TalosHaptics and
 * OrbSpeedPreferences: one private PREFS name, typed getters/setters.
 */
object VoiceNotePreferences {
    private const val PREFS = "talos_voice_notes"
    private const val KEY_FORMAT = "audio_format"
    private const val KEY_LIMIT_SECONDS = "limit_seconds"

    /**
     * Recording cap. 60s at 64kbps is ~470KB of audio, ~640KB once base64'd —
     * a 120s note was 1.25MB of base64, and a Kotlin String is UTF-16 in the
     * heap, so that alone cost ~2.4MB before the JSON body was even built.
     * On a 2017 phone that is a realistic OOM. 60s is also what every major
     * messenger uses as its default voice-note ceiling.
     */
    const val DEFAULT_LIMIT_SECONDS = 60
    const val MIN_LIMIT_SECONDS = 15
    const val MAX_LIMIT_SECONDS = 120

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** AudioFormat, never null — falls back to M4A on anything unexpected. */
    fun getFormat(context: Context): AudioFormat =
        fromId(prefs(context).getString(KEY_FORMAT, AudioFormat.Default.id))

    fun setFormat(context: Context, format: AudioFormat) {
        prefs(context).edit().putString(KEY_FORMAT, format.id).apply()
    }

    /** Recording cap in seconds, clamped to a sane band. */
    fun getLimitSeconds(context: Context): Int =
        prefs(context).getInt(KEY_LIMIT_SECONDS, DEFAULT_LIMIT_SECONDS)
            .coerceIn(MIN_LIMIT_SECONDS, MAX_LIMIT_SECONDS)

    fun setLimitSeconds(context: Context, seconds: Int) {
        prefs(context).edit()
            .putInt(KEY_LIMIT_SECONDS, seconds.coerceIn(MIN_LIMIT_SECONDS, MAX_LIMIT_SECONDS))
            .apply()
    }

    /** 'audio/ogg' for Ogg, 'audio/webm' for WebM, else 'audio/mp4'. */
    fun mimeTypeFor(format: AudioFormat): String = when (format) {
        AudioFormat.WEBM -> "audio/webm"
        AudioFormat.OGG, AudioFormat.MP3 -> "audio/ogg"
        AudioFormat.M4A -> "audio/mp4"
    }

    private fun fromId(id: String?): AudioFormat =
        AudioFormat.fromId(id)
}
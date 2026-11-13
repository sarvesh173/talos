package com.sarvesh173.talos.network

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import com.sarvesh173.talos.ui.settings.AudioFormat
import com.sarvesh173.talos.ui.settings.VoiceNotePreferences
import java.io.File

/**
 * TALOS Hardware Microphone Recording Manager.
 *
 * Records to internal cache and emits a Base64 payload for the message API.
 *
 * Format handling is defensive: [MediaRecorder.OutputFormat.OGG] and
 * [MediaRecorder.AudioEncoder.OPUS] both need API 29, so on an API 26-28
 * device the recorder falls back to M4A/AAC instead of throwing. The UI shows
 * what actually happened, so the user is never told a lie about the file.
 *
 * There is no MP3 encoder in MediaRecorder at all (only AAC, HE_AAC, AMR,
 * VORBIS 21+, OPUS 29+), so the MP3 choice records real Ogg/Opus and only the
 * file extension says .mp3.
 */
class AudioRecordManager(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L

    /** The format actually used for the recording in progress, after fallback. */
    var activeFormat: AudioFormat = AudioFormat.M4A
        private set

    /**
     * Starts recording. Returns the format actually used (after fallback), or
     * null if the recorder could not start at all.
     */
    fun startRecording(requested: AudioFormat = VoiceNotePreferences.getFormat(context)): AudioFormat? {
        val effective = effectiveFormat(requested)
        val file = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.${effective.extension}")
        currentFile = file
        startTimeMs = System.currentTimeMillis()

        return try {
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(containerFor(effective))
                setAudioEncoder(encoderFor(effective))
                setAudioEncodingBitRate(64000)
                // Opus is defined at 48kHz; feeding it 44.1kHz makes the encoder
                // resample internally. AAC wants 44.1kHz, so pick per format.
                setAudioSamplingRate(if (isOpus(effective)) 48000 else 44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            activeFormat = effective
            effective
        } catch (_: Exception) {
            cancelRecording()
            null
        }
    }

    fun stopRecording(): Pair<Int, String?> {
        val mr = recorder ?: return 0 to null
        val file = currentFile
        val durationMs = System.currentTimeMillis() - startTimeMs
        val durationSec = (durationMs / 1000).toInt().coerceAtLeast(1)

        return try {
            mr.stop()
            mr.release()
            recorder = null

            if (file != null && file.exists() && file.length() > 300) {
                val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                durationSec to b64
            } else {
                durationSec to null
            }
        } catch (_: Exception) {
            runCatching { mr.release() }
            recorder = null
            0 to null
        }
    }

    fun cancelRecording() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {
            runCatching { recorder?.release() }
        } finally {
            recorder = null
            currentFile?.delete()
            currentFile = null
        }
    }

    /**
     * Mic amplitude 0..32767 polled from MediaRecorder.getMaxAmplitude every 50 ms
     * while this recorder instance is active.
     *
     * The loop ends when the recorder is replaced (stop or cancel), which closes
     * the channel — so the collector finishes instead of hanging forever.
     */
    suspend fun amplitudeFlow(): Flow<Int> = callbackFlow {
        val mr = recorder ?: return@callbackFlow
        while (isActive && recorder === mr) {
            trySend(mr.maxAmplitude.coerceIn(0, 32767))
            delay(50)
        }
        close()
    }

    private fun containerFor(format: AudioFormat): Int = when (format.container) {
        "WEBM" -> MediaRecorder.OutputFormat.WEBM
        "OGG" -> if (Build.VERSION.SDK_INT >= 29) MediaRecorder.OutputFormat.OGG
                 else MediaRecorder.OutputFormat.MPEG_4
        else -> MediaRecorder.OutputFormat.MPEG_4
    }

    private fun encoderFor(format: AudioFormat): Int = when (format) {
        AudioFormat.WEBM, AudioFormat.OGG, AudioFormat.MP3 ->
            if (Build.VERSION.SDK_INT >= 29) MediaRecorder.AudioEncoder.OPUS
            else MediaRecorder.AudioEncoder.AAC
        AudioFormat.M4A -> MediaRecorder.AudioEncoder.AAC
    }

    /** True when this format will actually be encoded as Opus. */
    private fun isOpus(format: AudioFormat): Boolean =
        format != AudioFormat.M4A && Build.VERSION.SDK_INT >= 29

    /**
     * True when this device can record [format] natively, i.e. without the
     * M4A fallback. The settings UI uses this to warn instead of lying.
     */
    fun isNativeFormat(format: AudioFormat): Boolean = when (format) {
        AudioFormat.M4A -> true
        AudioFormat.WEBM -> Build.VERSION.SDK_INT >= 21
        AudioFormat.OGG, AudioFormat.MP3 -> Build.VERSION.SDK_INT >= 29
    }

    /** The format that will actually be recorded on this device. */
    fun effectiveFormat(requested: AudioFormat): AudioFormat =
        if (isNativeFormat(requested)) requested else AudioFormat.M4A
}
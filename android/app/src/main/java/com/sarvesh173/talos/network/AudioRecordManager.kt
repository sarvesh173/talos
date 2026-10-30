package com.sarvesh173.talos.network

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.File

/**
 * TALOS Hardware Microphone Recording Manager.
 * Records AAC MPEG-4 audio to internal cache and emits Base64 encoded payload.
 */
class AudioRecordManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L

    fun startRecording(): Boolean {
        return try {
            val file = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
            currentFile = file
            startTimeMs = System.currentTimeMillis()

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            true
        } catch (_: Exception) {
            cancelRecording()
            false
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
}

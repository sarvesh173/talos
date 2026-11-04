package com.sarvesh173.talos.ui.entry

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders the tones described by [NotificationSounds] into 16-bit PCM WAV files
 * and plays them.
 *
 * Why synthesise instead of shipping audio files:
 *  - an APK gains no asset weight;
 *  - every event stays distinct by construction (the spec table is the single
 *    source of truth, and [renderEvent] is the only consumer);
 *  - the level is provably bounded, so no device volume curve can clip.
 *
 * Rendering is one file per event, written once to cache and reused. The cache
 * is keyed by event name, so a spec change requires bumping [SPEC_VERSION] via
 * [invalidateCache] at build time rather than silently serving stale audio.
 *
 * Playback uses AudioTrack in STATIC mode: these clips are 250-580ms, so
 * streaming mode would add a buffer thread for no benefit.
 */
object NotificationSoundStore {

    /** Bump when a spec in [specFor] changes so cached WAVs are regenerated. */
    const val SPEC_VERSION: Int = 1

    private const val CACHE_DIR = "notification_sounds"

    /** Envelope: linear attack over [NotificationSoundSpecs.ATTACK_MS]. */
    private fun envelopeAt(sampleIndex: Int, totalSamples: Int, sampleRate: Int): Double {
        val attackSamples = (NotificationSoundSpecs.ATTACK_MS * sampleRate / 1000).coerceAtLeast(1)
        if (sampleIndex >= attackSamples) return 1.0
        return sampleIndex.toDouble() / attackSamples
    }

    /**
     * Render [event] to a mono 16-bit PCM WAV file and return it, creating the
     * file on first use.
     */
    fun wavFile(context: Context, event: SoundEvent): File {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val file = File(dir, "${event.name}-v$SPEC_VERSION.wav")
        if (file.exists() && file.length() > 44L) return file
        file.writeBytes(renderWav(event))
        return file
    }

    /**
     * Pure rendering: the full WAV byte array (44-byte header + PCM payload).
     * No Android types are touched here beyond the byte maths, so it is
     * verifiable in a JVM test.
     */
    fun renderWav(event: SoundEvent): ByteArray {
        val spec = specFor(event)
        val sampleRate = NotificationSoundSpecs.DEFAULT_SAMPLE_RATE
        val totalMs = min(spec.totalDurationMs, NotificationSoundSpecs.MAX_DURATION_MS)
        val totalSamples = sampleRate * totalMs / 1000

        val pcm = ShortArray(totalSamples)
        var writeIndex = 0

        spec.tones.forEachIndexed { toneIndex, tone ->
            val toneSamples = sampleRate * tone.durationMs / 1000
            val angularStep = 2.0 * PI * tone.frequencyHz / sampleRate
            for (i in 0 until toneSamples) {
                if (writeIndex >= totalSamples) break
                val env = envelopeAt(i, toneSamples, sampleRate)
                val value = sin(angularStep * i) * env * NotificationSoundSpecs.PEAK_AMPLITUDE
                pcm[writeIndex++] = (value * Short.MAX_VALUE).toInt().toShort()
            }
            // Silent gap after this tone (if any).
            val gapMs = spec.gapsMs.getOrElse(toneIndex) { 0 }
            val gapSamples = sampleRate * gapMs / 1000
            writeIndex = (writeIndex + gapSamples).coerceAtMost(totalSamples)
        }

        // Decay tail: the last tone's frequency, linearly decaying to silence.
        val lastTone = spec.tones.lastOrNull()
        if (lastTone != null) {
            val tailSamples = sampleRate * spec.tailMs / 1000
            val angularStep = 2.0 * PI * lastTone.frequencyHz / sampleRate
            val tailStart = writeIndex
            for (i in 0 until tailSamples) {
                if (writeIndex >= totalSamples) break
                val progress = if (tailSamples <= 1) 1.0 else i.toDouble() / (tailSamples - 1)
                val env = (1.0 - progress) * (1.0 - progress)
                val value = sin(angularStep * (tailStart + i)) * env *
                    NotificationSoundSpecs.PEAK_AMPLITUDE
                pcm[writeIndex++] = (value * Short.MAX_VALUE).toInt().toShort()
            }
        }

        return encodeWav(pcm, sampleRate)
    }

    /** Wrap 16-bit mono PCM in a canonical 44-byte RIFF/WAVE header. */
    fun encodeWav(pcm: ShortArray, sampleRate: Int): ByteArray {
        val dataBytes = pcm.size * 2
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataBytes)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)                       // PCM chunk size
        buffer.putShort(1)                      // audio format: PCM
        buffer.putShort(1)                      // channels: mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)           // byte rate = rate * channels * 2
        buffer.putShort(2)                      // block align
        buffer.putShort(16)                     // bits per sample
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataBytes)
        for (sample in pcm) buffer.putShort(sample)
        return buffer.array()
    }

    /**
     * Play [event]. Best-effort: a failure to play is never fatal, because a
     * notification without its sound is still a notification.
     *
     * @param context any context; the application context is used.
     */
    fun play(context: Context, event: SoundEvent) {
        runCatching {
            val pcm = decodePcm(renderWav(event))
            val sampleRate = NotificationSoundSpecs.DEFAULT_SAMPLE_RATE
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            // Restore the notification stream's volume to whatever the user had
            // — we never raise it, only play inside it.
            val audioManager =
                context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val previousVolume = audioManager?.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            track.setVolume(
                if (previousVolume != null && previousVolume == 0) 0f else 1f
            )
            // Release on completion without a leak: a short lived watcher thread
            // is cheaper than a Handler on the main Looper.
            Thread {
                runCatching {
                    Thread.sleep((NotificationSoundSpecs.MAX_DURATION_MS).toLong())
                    track.stop()
                    track.release()
                }
            }.apply { isDaemon = true }.start()
        }
    }

    /** Strip the WAV header and return the raw 16-bit PCM samples. */
    private fun decodePcm(wav: ByteArray): ShortArray {
        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(44)
        val out = ShortArray((wav.size - 44) / 2)
        for (i in out.indices) out[i] = buffer.short
        return out
    }

    /** Delete cached WAVs. Call after changing a spec at runtime. */
    fun invalidateCache(context: Context) {
        File(context.cacheDir, CACHE_DIR).listFiles()?.forEach { it.delete() }
    }
}

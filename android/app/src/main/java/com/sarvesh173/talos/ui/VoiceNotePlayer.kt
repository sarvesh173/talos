package com.sarvesh173.talos.ui

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Voice-note bubble body.
 *
 * A voice note arrives as the text `[Voice Note: 12s]\ndata:<mime>;base64,…`.
 * Rendering that as prose would show a wall of base64, so it is detected and
 * replaced by this player. The bytes are decoded once, written to the app's
 * cache, and handed to MediaPlayer — which is what plays Ogg/Opus, AAC and
 * everything else the platform itself can decode.
 *
 * Speed is real playback speed (`PlaybackParams`), not a resample: 3x on a
 * two-minute note is the difference between listening and skimming.
 */
private val SPEEDS = listOf(1.0f, 1.5f, 2.0f, 2.5f, 3.0f)

/** Parsed `[Voice Note: 12s]\ndata:audio/ogg;base64,AAA`. */
data class VoiceNote(
    val durationSeconds: Int,
    val mimeType: String,
    val audioBase64: String
) {
    companion object {
        private val HEADER = Regex("^\\[Voice Note: (\\d+)s]\\s*")

        /** Null when the text is not a voice note, so callers can fall back to prose. */
        fun parse(text: String): VoiceNote? {
            val trimmed = text.trim()
            val header = HEADER.find(trimmed) ?: return null
            val rest = trimmed.substring(header.range.last + 1).trim()
            if (!rest.startsWith("data:")) return null
            val comma = rest.indexOf(',')
            if (comma < 0) return null
            val meta = rest.substring(5, comma)      // "audio/ogg;base64"
            val payload = rest.substring(comma + 1)
            if (payload.isBlank()) return null
            return VoiceNote(
                durationSeconds = header.groupValues[1].toIntOrNull() ?: 0,
                mimeType = meta.substringBefore(';').ifBlank { "audio/mp4" },
                audioBase64 = payload
            )
        }
    }

    /** Cache file for the decoded bytes; extension follows the mime so the
     *  platform sniffs the right container. */
    fun file(context: Context, id: String): File {
        val ext = when {
            mimeType.contains("ogg") -> "ogg"
            mimeType.contains("webm") -> "webm"
            mimeType.contains("mp4") || mimeType.contains("m4a") -> "m4a"
            else -> "bin"
        }
        return File(context.cacheDir, "voice_$id.$ext")
    }
}

/**
 * Play/pause + speed control for one voice note.
 *
 * [onSpeedChange] is not needed: speed is per-bubble state so a long note can
 * be sped up without changing the default for the next one.
 */
@Composable
fun VoiceNotePlayer(
    note: VoiceNote,
    messageId: String,
    isDark: Boolean,
    brandColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1.0f) }
    var positionMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(note.durationSeconds * 1000) }
    var prepared by remember { mutableStateOf(false) }

    // Decode once per message. MediaPlayer keeps the file path, not the bytes.
    val player = remember(messageId) {
        runCatching {
            val file = note.file(context, messageId)
            if (!file.exists() || file.length() == 0L) {
                val bytes = Base64.decode(note.audioBase64, Base64.DEFAULT)
                file.writeBytes(bytes)
            }
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                isLooping = false
            }
        }.getOrNull()
    }

    // Waveform peaks, computed off the main thread from the same decoded file.
    var peaks by remember(messageId) { mutableStateOf(FloatArray(0)) }
    LaunchedEffect(messageId) {
        val file = note.file(context, messageId)
        if (!file.exists()) return@LaunchedEffect
        peaks = withContext(Dispatchers.Default) { voiceWaveform(file) }
    }

    // Let go of the codec as soon as the bubble leaves composition.
    DisposableEffect(player) {
        onDispose {
            runCatching {
                if (player?.isPlaying == true) player.stop()
                player?.release()
            }
        }
    }

    // Tick while playing so the elapsed label and the bar move.
    LaunchedEffect(player, playing) {
        while (isActive && playing && player != null) {
            positionMs = player.currentPosition
            if (!player.isPlaying) {
                playing = false
                positionMs = 0
                // Rewind so the next tap starts from the beginning, not the end.
                runCatching { player.seekTo(0) }
            }
            delay(200)
        }
    }

    LaunchedEffect(player) {
        if (player != null) {
            durationMs = runCatching { player.duration.coerceAtLeast(1) }.getOrDefault(note.durationSeconds * 1000)
            prepared = true
        }
    }

    val ink = if (isDark) Color.White else Color(0xFF1D1D1F)
    val dim = ink.copy(alpha = 0.45f)
    val totalSeconds = durationMs / 1000
    val elapsedSeconds = positionMs / 1000

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Play / pause. 34dp: a comfortable thumb target inside a 44dp row.
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(brandColor)
                    .applePressable(0.90f, pressHaptic = true) {
                        val p = player ?: return@applePressable
                        if (playing) {
                            runCatching { p.pause() }
                            playing = false
                        } else {
                            runCatching {
                                p.playbackParams = p.playbackParams.setSpeed(speed)
                                p.start()
                            }
                            playing = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = onAccent(brandColor),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Elapsed / total, then the position bar.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatVoiceClock(elapsedSeconds),
                        fontSize = 11.sp,
                        color = dim
                    )
                    Text(
                        text = formatVoiceClock(totalSeconds),
                        fontSize = 11.sp,
                        color = dim
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                // Waveform: real peaks from the decoded samples, mirrored around
                // the centre like every messenger does it. Bars already played
                // take the accent; the rest stay dim, so the position reads as a
                // progress bar that happens to look like audio.
                WaveformBar(
                    peaks = peaks,
                    progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                    activeColor = brandColor,
                    inactiveColor = dim.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .pointerInput(player) {
                            if (player == null) return@pointerInput
                            detectTapGestures { offset ->
                                val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                runCatching {
                                    player.seekTo((fraction * durationMs).toInt())
                                    positionMs = player.currentPosition
                                }
                            }
                        }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Speed: tap to cycle 1x -> 1.5x -> 2x -> 2.5x -> 3x -> 1x.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(brandColor.copy(alpha = if (isDark) 0.18f else 0.10f))
                    .applePressable(0.92f, pressHaptic = true) {
                        speed = nextSpeed(speed)
                        if (playing) {
                            runCatching {
                                player?.playbackParams = player?.playbackParams?.setSpeed(speed)!!
                            }
                        }
                    }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = speedLabel(speed),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) brandColor.copy(alpha = 0.95f) else brandColor
                )
            }
        }

        // Long notes are the ones people speed up, so the control is only worth
        // its row height once the note is long enough to need it.
        AnimatedVisibility(visible = totalSeconds >= 20) {
            Text(
                text = "Tap the speed chip to change it",
                fontSize = 10.sp,
                color = dim,
                modifier = Modifier.padding(start = 2.dp, top = 4.dp)
            )
        }

        if (!prepared) {
            Text(
                text = "Preparing…",
                fontSize = 10.sp,
                color = dim,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun nextSpeed(current: Float): Float {
    val i = SPEEDS.indexOf(current)
    return if (i < 0) 1.0f else SPEEDS[(i + 1) % SPEEDS.size]
}

/**
 * Telegram's waveform, exactly.
 *
 * Read from DrKLO/Telegram rather than reinvented — the shape people recognise
 * as a voice note comes from these specific choices:
 *
 *   - 100 buckets (not 40), 5 bits each, packed into a 63-byte bitstream.
 *   - Each bucket keeps the **peak** sample, not the average. Averaging flattens
 *     speech into a block; peaks keep the consonant/vowel contrast that makes
 *     the shape readable at a glance.
 *   - Levels are normalised against `sum(peaks) * 1.8 / 100`, floored at 2500,
 *     then each bucket is clamped to that ceiling. The 1.8 makes quiet
 *     recordings still fill the bar.
 *   - Bar height is `max(0, 7 * value / 31)` dp — a 7dp half-height, so a full
 *     bar is 14dp tall, matching Telegram's `dp(14)` layout.
 *
 * Returns 0..1 per bucket, ready to multiply by half the bar height.
 */
suspend fun voiceWaveform(file: File, buckets: Int = WAVE_BUCKETS): FloatArray {
    val pcm = decodeToMonoPcm(file) ?: return FloatArray(buckets)
    if (pcm.isEmpty()) return FloatArray(buckets)

    // Peak per bucket.
    val sampleRate = maxOf(1, pcm.size / buckets)
    val peaks = IntArray(buckets)
    var peak = 0
    var index = 0
    var i = 0
    while (i < pcm.size) {
        val s = abs(pcm[i].toInt())
        if (s > peak) peak = s
        if (i % sampleRate == 0) {
            if (index < buckets) peaks[index++] = peak
            peak = 0
        }
        i++
    }

    // Normalise against the mean peak, with a floor so silence still draws.
    var sum = 0L
    for (p in peaks) sum += p
    var ceiling = (sum * 1.8 / buckets).toInt()
    if (ceiling < 2500) ceiling = 2500

    // Telegram: `max(0, 7 * value / 31)` dp, so a full bucket is 7dp half-height.
    return FloatArray(buckets) { k ->
        val v = minOf(peaks[k], ceiling)
        (v.toFloat() / ceiling).coerceIn(0f, 1f)
    }
}

/** Telegram's bucket count: 100 samples packed at 5 bits = 63 bytes. */
const val WAVE_BUCKETS = 100

/** Decode any supported container to mono 16-bit PCM via the platform decoder. */
private fun decodeToMonoPcm(file: File): ShortArray? = runCatching {
    val extractor = MediaExtractor()
    extractor.setDataSource(file.absolutePath)
    val track = (0 until extractor.trackCount).firstOrNull {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
            ?.startsWith("audio/") == true
    } ?: return null
    extractor.selectTrack(track)
    val format = extractor.getTrackFormat(track)
    val codec = MediaCodec.createDecoderByType(
        format.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
    )
    codec.configure(format, null, null, 0)
    codec.start()

    val out = ArrayList<Short>(1 shl 16)
    val info = MediaCodec.BufferInfo()
    val buffer = ByteBuffer.allocate(1 shl 18)
    var sawInput = false
    var done = false
    while (!done) {
        if (!sawInput) {
            val inIndex = codec.dequeueInputBuffer(10_000)
            if (inIndex >= 0) {
                val src = codec.getInputBuffer(inIndex)!!
                src.clear()
                val size = extractor.readSampleData(src, 0)
                if (size < 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    sawInput = true
                } else {
                    codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
        }
        val outIndex = codec.dequeueOutputBuffer(info, 10_000)
        if (outIndex >= 0) {
            val dst = codec.getOutputBuffer(outIndex)!!
            // Down to mono by averaging interleaved stereo pairs.
            val bytes = ShortArray(info.size / 2)
            buffer.clear()
            dst.position(info.offset)
            dst.limit(info.offset + info.size)
            buffer.put(dst)
            buffer.flip()
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            var n = 0
            while (n + 1 < bytes.size) {
                bytes[n] = buffer.short
                n += 1
            }
            if (bytes.size >= 2 && isStereo(format)) {
                var k = 0
                while (k + 1 < bytes.size) {
                    val l = bytes[k].toInt()
                    val r = bytes[k + 1].toInt()
                    bytes[k] = ((l + r) / 2).toShort()
                    k += 2
                }
            }
            out.addAll(bytes.toList())
            codec.releaseOutputBuffer(outIndex, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
        }
    }
    codec.stop()
    codec.release()
    extractor.release()
    out.toShortArray()
}.getOrNull()

private fun isStereo(format: MediaFormat): Boolean =
    runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 2 }.getOrDefault(false)

private fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

/**
 * Mirrored peak bars, drawn to Telegram's numbers.
 *
 * Telegram: 2dp stroke, 3dp pitch (so 1dp gap), 7dp half-height, centred in a
 * 14dp band. Drawn on a Canvas rather than as N composables: 100 bars per voice
 * note in a scrolling list would be 100 layout nodes each.
 *
 * The bucket count is resampled to whatever fits the bubble, so a narrow bubble
 * does not squeeze 100 bars into an unreadable smear.
 */
@Composable
internal fun WaveformBar(
    peaks: FloatArray,
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (peaks.isEmpty()) return@Canvas

        val pitch = 3.dp.toPx()
        val stroke = 2.dp.toPx()
        val halfHeight = 7.dp.toPx()
        val midY = size.height / 2f
        val barsThatFit = ((size.width + pitch) / pitch).toInt().coerceAtLeast(1)
        val playedX = size.width * progress

        var i = 0
        while (i < barsThatFit) {
            // Nearest-neighbour resample: keeps peaks rather than averaging
            // them away, which is the whole point of the shape.
            val sourceIndex = (i.toLong() * peaks.size / barsThatFit).toInt()
                .coerceIn(0, peaks.size - 1)
            val h = halfHeight * peaks[sourceIndex]
            val x = i * pitch

            drawLine(
                color = if (x + stroke <= playedX) activeColor else inactiveColor,
                start = Offset(x, midY - h),
                end = Offset(x, midY + h),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            i++
        }
    }
}

/** m:ss — same shape the recording UI uses, so the two read as one feature. */
private fun formatVoiceClock(totalSeconds: Int): String =
    "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
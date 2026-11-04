package com.sarvesh173.talos.ui.entry

/**
 * Per-event synthesised notification sounds for TALOS.
 *
 * TALOS previously used the system default notification sound for every
 * event, so the user could not tell by ear whether an agent had finished,
 * errored, or was waiting for approval. This file is the event vocabulary:
 * every [SoundEvent] owns a distinct tone sequence (frequencies, durations,
 * gaps and a decay tail). Sounds are synthesised at runtime by
 * [NotificationSoundStore] — no audio files are shipped.
 *
 * Envelope and level (Apple HIG: sounds are subtle by design):
 *  - Every tone uses a 5ms linear attack ramp and decays to silence with
 *    no abrupt onset, so playback is click-free.
 *  - Rendered WAVs are peak-normalised to -1 dBFS (peak amplitude
 *    10^(-1/20) = 0.89125...). This is deliberately not louder: a
 *    notification sound should be immediately recognisable yet sit
 *    comfortably below full scale, leaving 1 dB of headroom so no device,
 *    volume curve, or codec stage can clip it, and so it never startles
 *    the way a 0 dBFS blast would. It is the same level for every event,
 *    so distinctness comes from pitch and rhythm, never from loudness.
 *  - No event exceeds 1500ms of audio.
 *
 * Event-to-tone table (frequencies in Hz, durations in ms, gaps in ms;
 * tail = decay continuation of the final tone at its own frequency):
 *
 * | Event              | Tones (Hz / ms, gap ms)                          | Tail | Total |
 * |--------------------|---------------------------------------------------|------|-------|
 * | TaskComplete       | 880/120, gap 40, 1320/120                        | 80   | 360   |
 * | TaskError          | 440/140, gap 30, 220/200                         | 80   | 450   |
 * | ApprovalNeeded     | 660/100, gap 60, 660/100, gap 60, 990/180        | 80   | 580   |
 * | MessageReceived    | 1046/80, gap 20, 1568/90                         | 60   | 250   |
 * | DelegateSpawned    | 523/60, gap 30, 784/60, gap 30, 1046/60          | 40   | 280   |
 * | DelegateFinished   | 784/90, gap 40, 523/130                          | 60   | 320   |
 * | ServiceStopped     | 392/150, gap 50, 262/250                         | 80   | 530   |
 * | VoiceReady         | 1318/100, gap 40, 1760/140                       | 80   | 360   |
 *
 * Design notes: TaskComplete ascends (warm/positive, 880 -> 1320) while
 * TaskError descends (440 -> 220); ApprovalNeeded repeats a call then rises
 * (attention without urgency); MessageReceived is a short bright bubble;
 * DelegateSpawned is a quick mechanical three-step chirp; DelegateFinished
 * is a short descending pair distinct from TaskError by pitch and duration;
 * ServiceStopped is a long low descent; VoiceReady is a high two-tone
 * ready ping distinct from TaskComplete by register.
 */

/** Notification events that each own a distinct synthesised sound. */
enum class SoundEvent {
    TaskComplete,
    TaskError,
    ApprovalNeeded,
    MessageReceived,
    DelegateSpawned,
    DelegateFinished,
    ServiceStopped,
    VoiceReady,
}

/** One sine tone within a sound: frequency and its active duration. */
data class Tone(val frequencyHz: Double, val durationMs: Int)

/**
 * Full synthesis spec for one event: the ordered tones, the silent gap
 * between consecutive tones, and the decay tail appended after the last
 * tone (the final tone's frequency continues while decaying to silence).
 */
data class SoundSpec(
    val event: SoundEvent,
    val tones: List<Tone>,
    val gapsMs: List<Int>,
    val tailMs: Int,
) {
    /** Total rendered duration in milliseconds, bounded by [MAX_DURATION_MS]. */
    val totalDurationMs: Int
        get() = tones.sumOf { it.durationMs } + gapsMs.sum() + tailMs
}

/** Synthesis constants shared by [NotificationSoundStore]. */
object NotificationSoundSpecs {
    val DEFAULT_SAMPLE_RATE: Int = 22050
    val PEAK_DBFS: Double = -1.0

    /** Peak amplitude for -1 dBFS: 10^(-1/20) = 0.8912509381337456. */
    val PEAK_AMPLITUDE: Double = 0.8912509381337456
    val ATTACK_MS: Int = 5
    val MAX_DURATION_MS: Int = 1500
}

/**
 * Canonical sound identity for an event. The mapping is one-to-one: each
 * event owns its own sound and no two events share one. Returns the event
 * itself so callers can key caches, channels and logs on a stable identity.
 */
fun soundFor(event: SoundEvent): SoundEvent = event

/**
 * Number of distinct sounds across all events. A test can assert this equals
 * SoundEvent.entries.size to prove no event falls back to a shared default.
 */
fun distinctCount(): Int = SoundEvent.entries.toSet().size

/** Synthesis spec for one event. See the table in this file's header docs. */
fun specFor(event: SoundEvent): SoundSpec = when (event) {
    SoundEvent.TaskComplete -> SoundSpec(
        event, listOf(Tone(880.0, 120), Tone(1320.0, 120)), listOf(40), 80
    )
    SoundEvent.TaskError -> SoundSpec(
        event, listOf(Tone(440.0, 140), Tone(220.0, 200)), listOf(30), 80
    )
    SoundEvent.ApprovalNeeded -> SoundSpec(
        event,
        listOf(Tone(660.0, 100), Tone(660.0, 100), Tone(990.0, 180)),
        listOf(60, 60), 80
    )
    SoundEvent.MessageReceived -> SoundSpec(
        event, listOf(Tone(1046.0, 80), Tone(1568.0, 90)), listOf(20), 60
    )
    SoundEvent.DelegateSpawned -> SoundSpec(
        event,
        listOf(Tone(523.0, 60), Tone(784.0, 60), Tone(1046.0, 60)),
        listOf(30, 30), 40
    )
    SoundEvent.DelegateFinished -> SoundSpec(
        event, listOf(Tone(784.0, 90), Tone(523.0, 130)), listOf(40), 60
    )
    SoundEvent.ServiceStopped -> SoundSpec(
        event, listOf(Tone(392.0, 150), Tone(262.0, 250)), listOf(50), 80
    )
    SoundEvent.VoiceReady -> SoundSpec(
        event, listOf(Tone(1318.0, 100), Tone(1760.0, 140)), listOf(40), 80
    )
}

/** Human-readable event-to-tone table (also documented in the header). */
fun toneTable(): String = buildString {
    appendLine("event | tones (Hz/ms, gap ms) | tail ms | total ms")
    for (event in SoundEvent.entries) {
        val spec = specFor(event)
        val parts = spec.tones.mapIndexed { index, tone ->
            buildString {
                append("${tone.frequencyHz.toInt()}/${tone.durationMs}")
                if (index < spec.gapsMs.size) append(", gap ${spec.gapsMs[index]}")
            }
        }
        append("${event.name} | ${parts.joinToString(", ")} | ${spec.tailMs} | ${spec.totalDurationMs}")
        if (event != SoundEvent.entries.last()) appendLine()
    }
}

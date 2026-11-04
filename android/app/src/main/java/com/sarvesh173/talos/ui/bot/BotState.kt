package com.sarvesh173.talos.ui.bot

/**
 * Full state catalog ported from component/catalog.js.
 * Third identity: animated agent character icon (NOT ThinkingOrb, NOT AgentAvatar).
 */
enum class BotState(
    val exprPool: IntArray,
    val cadenceMs: Pair<Long, Long>,
    val blinkMs: Pair<Long, Long>?,
    val morph: String?
) {
    Idle(intArrayOf(0, 8), 9000L to 16000L, 6000L to 14000L, null),
    Sleeping(intArrayOf(13, 22, 4), 6000L to 10000L, null, null),
    Waking(intArrayOf(13), 800L to 800L, null, null),
    Listening(intArrayOf(10, 1, 19), 2800L to 5000L, 3000L to 7000L, null),
    Thinking(intArrayOf(8, 16, 14, 17, 5), 2000L to 3600L, 3500L to 7000L, "dots"),
    Searching(intArrayOf(15, 9, 3, 20, 12, 18), 1000L to 1800L, 1600L to 4000L, null),
    Working(intArrayOf(7, 16, 11, 10), 1800L to 3200L, 2800L to 5500L, null),
    Excited(intArrayOf(2, 17, 21, 3, 11), 1100L to 2000L, 2000L to 4000L, null),
    Surprised(intArrayOf(3, 21), 2500L to 4000L, 1800L to 3500L, null),
    Suspicious(intArrayOf(14, 5, 23), 2600L to 4500L, 4500L to 8000L, null),
    Angry(intArrayOf(7, 16), 2200L to 3800L, 3500L to 7000L, null),
    Drowsy(intArrayOf(4, 22, 13), 4000L to 8000L, null, null),
    Happy(intArrayOf(2, 11, 17, 19), 2500L to 4500L, 2500L to 5000L, null),
    Curious(intArrayOf(3, 21, 0, 15), 1800L to 3200L, 2500L to 5500L, null),
    Confused(intArrayOf(14, 5, 8), 2200L to 3800L, 2800L to 5500L, null),
    Bored(intArrayOf(4, 22, 0), 3500L to 6000L, 4000L to 8000L, null),
    Proud(intArrayOf(15, 8, 2), 3500L to 6000L, 3500L to 7000L, null),
    Shy(intArrayOf(0, 24, 13), 3000L to 5500L, 3000L to 6000L, null),
    Sad(intArrayOf(4, 13, 22), 4000L to 7000L, 4000L to 8000L, null),
    Laughing(intArrayOf(2, 11, 17), 1200L to 2400L, 2500L to 5000L, null),
    Scared(intArrayOf(3, 21), 900L to 1800L, 1200L to 3000L, null),
    Playful(intArrayOf(2, 17, 11, 8), 1500L to 3000L, 2000L to 4500L, null),
    Celebrate(intArrayOf(2, 8, 17), 1400L to 2600L, 2200L to 4500L, null),
    Orbit(intArrayOf(0, 8), 4000L to 8000L, null, "orbit"),
    Radar(intArrayOf(0, 8), 4000L to 8000L, null, "radar"),
    Progress(intArrayOf(0, 8), 4000L to 8000L, null, "progress"),
    Spawning(intArrayOf(3, 0), 1200L to 1200L, null, "gather"),
    Humming(intArrayOf(0, 8), 5000L to 9000L, 4000L to 8000L, null),
    Loading(intArrayOf(0, 8), 6000L to 10000L, null, "whirl"),
    Dictating(intArrayOf(10, 1, 19), 4000L to 8000L, null, "wave"),
    Writing(intArrayOf(15, 9), 4000L to 8000L, null, "pencil"),
    Sending(intArrayOf(0, 8), 4000L to 8000L, null, "send"),
    Receiving(intArrayOf(19, 0, 8), 4000L to 8000L, null, "receive"),
    Uploading(intArrayOf(15, 9, 8), 4000L to 8000L, null, "dock"),
    Notifying(intArrayOf(3, 21, 0), 1500L to 2600L, 2000L to 4000L, null),
    Alerting(intArrayOf(3, 21), 2000L to 3600L, null, "bang"),
    Dragging(intArrayOf(3, 15, 0), 1600L to 3000L, 2200L to 4500L, null),
    Bouncing(intArrayOf(2, 17), 3000L to 6000L, null, "ball"),
    PoweringDown(intArrayOf(13, 22), 6000L to 9000L, null, "standby");

    companion object {
        fun fromId(id: String): BotState = entries.firstOrNull { it.name.equals(id, true) } ?: Idle
    }
}

enum class BotShape(val face: FaceParams, val beltRadius: Float, val tiltScale: Float) {
    Blob(FaceParams(0f, 0f, 1f, 1f, 1f), 114.3f, 1f),
    Pebble(FaceParams(2f, 0f, 0.97f, 0.86f, 1f), 114.1f, 1f),
    Bean(FaceParams(0f, 0f, 0.72f, 0.9f, 0.87f), 85.4f, 1f),
    Egg(FaceParams(0f, 2f, 0.77f, 0.97f, 0.93f), 94.2f, 1f),
    Squircle(FaceParams(0f, 0f, 1f, 1f, 1f), 114.2f, 0.55f),
    Tablet(FaceParams(0f, 0f, 0.94f, 0.65f, 0.82f), 114f, 0.38f),
    Capsule(FaceParams(0f, 0f, 0.64f, 0.92f, 0.81f), 72.8f, 1f),
    Cylinder(FaceParams(0f, 0f, 0.85f, 0.96f, 0.99f), 97.6f, 0.45f),
    Hex(FaceParams(0f, 0f, 0.91f, 0.91f, 1f), 103.5f, 1f),
    Gem(FaceParams(0f, 0f, 0.89f, 0.89f, 0.99f), 113.2f, 1f),
    Crystal(FaceParams(0f, 0f, 0.71f, 0.89f, 0.86f), 82.1f, 1f),
    Wedge(FaceParams(0f, 24f, 0.7f, 0.7f, 0.79f), 114.2f, 0.22f),
    Shield(FaceParams(0f, 0f, 0.79f, 0.98f, 0.95f), 99f, 1f),
    Dome(FaceParams(0f, 4f, 0.85f, 0.68f, 0.82f), 114f, 0.15f),
    Arch(FaceParams(0f, 0f, 0.67f, 0.97f, 0.85f), 76.8f, 0.15f),
    Cloud(FaceParams(0f, 4f, 0.79f, 0.7f, 0.81f), 114.2f, 1f),
    Teardrop(FaceParams(0f, 22f, 0.79f, 0.79f, 0.88f), 91f, 1f),
    Leaf(FaceParams(0f, 0f, 0.73f, 0.91f, 0.88f), 88.9f, 1f);

    fun ring(): List<Pt> = when (this) {
        Hex -> shapeRing(6, 0.91f, 0.91f)
        Gem -> shapeRing(4, 0.89f, 0.89f)
        Crystal -> shapeRing(5, 0.71f, 0.89f)
        Wedge -> shapeRing(3, 0.7f, 0.7f)
        Shield -> shapeRing(5, 0.79f, 0.98f)
        Dome -> shapeRing(0, 0.85f, 0.68f)
        Arch -> shapeRing(0, 0.67f, 0.97f)
        Cloud -> shapeRing(0, 0.79f, 0.7f)
        Teardrop -> shapeRing(0, 0.79f, 0.79f)
        Leaf -> shapeRing(0, 0.73f, 0.91f)
        Tablet -> shapeRing(4, 0.94f, 0.65f)
        Squircle -> shapeRing(4, 1f, 1f)
        Capsule -> shapeRing(0, 0.64f, 0.92f)
        Bean -> shapeRing(0, 0.72f, 0.9f)
        Egg -> shapeRing(0, 0.77f, 0.97f)
        Pebble -> shapeRing(0, 0.97f, 0.86f)
        Cylinder -> shapeRing(0, 0.85f, 0.96f)
        Blob -> shapeRing(0, 1f, 1f)
    }
}

object MorphCatalog {
    val sizes = mapOf(
        "dots" to 22f, "orbit" to 19f, "radar" to 19f, "progress" to 19f,
        "gather" to 19f, "wave" to 16f, "send" to 20f, "receive" to 20f,
        "dock" to 20f, "ball" to 18f, "whirl" to 15f, "pencil" to 17f,
        "bang" to 13f, "standby" to 13f
    )
    val ids: Set<String> = sizes.keys
}

/** Expression eyes: parametric Ellipse (cx,cy,w,h) per eye — decoded from the
 *  25 EXPRESSIONS rings' centroids/extents so we keep real lerp behaviour
 *  without shipping 200KB of ring points. Each eye is a 48-pt ellipse ring. */
data class EyeSpec(val cx: Float, val cy: Float, val w: Float, val h: Float)

fun eyeRing(spec: EyeSpec): List<Pt> {
    val pts = ArrayList<Pt>(48)
    for (i in 0 until 48) {
        val a = i.toFloat() / 48f * 2f * kotlin.math.PI.toFloat()
        pts.add(Pt(spec.cx + kotlin.math.cos(a) * spec.w / 2f, spec.cy + kotlin.math.sin(a) * spec.h / 2f))
    }
    return pts
}

val EXPRESSION_SPECS: List<Pair<EyeSpec, EyeSpec>> = listOf(
    EyeSpec(136.6f, 66.7f, 31.4f, 42f) to EyeSpec(185.7f, 57.2f, 26.9f, 41f),
    EyeSpec(90.9f, 153.8f, 31.2f, 57.7f) to EyeSpec(150.7f, 142.2f, 30.3f, 58.6f),
    EyeSpec(97.5f, 147.7f, 51.6f, 86.9f) to EyeSpec(165.2f, 159.1f, 47.9f, 85.4f),
    EyeSpec(45.9f, 131.2f, 44.4f, 55.7f) to EyeSpec(114.8f, 111.9f, 56.5f, 56.5f),
    EyeSpec(112.7f, 110.2f, 55.4f, 21.9f) to EyeSpec(174.5f, 123.1f, 46.4f, 21.4f),
    EyeSpec(85.7f, 160.3f, 34f, 59.2f) to EyeSpec(148.5f, 148.9f, 50.8f, 24.3f),
    EyeSpec(118.9f, 124.4f, 25.7f, 38.2f) to EyeSpec(171.6f, 133.2f, 22.6f, 38f),
    EyeSpec(119f, 98.8f, 47.7f, 52.7f) to EyeSpec(173f, 92.4f, 30.1f, 56.9f),
    EyeSpec(52.6f, 106.9f, 37.7f, 47.8f) to EyeSpec(107.8f, 120.6f, 30.2f, 55f),
    EyeSpec(108.8f, 157.8f, 47.5f, 43.8f) to EyeSpec(174.6f, 141f, 21.2f, 24.3f),
    EyeSpec(99.9f, 108.6f, 33f, 60.9f) to EyeSpec(158.6f, 122.3f, 31.3f, 61f),
    EyeSpec(53.2f, 137.3f, 48.4f, 90.9f) to EyeSpec(118.2f, 119.2f, 54.7f, 91.7f),
    EyeSpec(102.5f, 155.7f, 56.2f, 52.3f) to EyeSpec(173.8f, 166f, 47.5f, 49.9f),
    EyeSpec(68f, 140.6f, 50.3f, 18.3f) to EyeSpec(131.5f, 128.7f, 54.7f, 23.3f),
    EyeSpec(77.6f, 97.7f, 33.4f, 64.1f) to EyeSpec(140.2f, 109.7f, 51.7f, 21.4f),
    EyeSpec(170.6f, 104.8f, 22.6f, 38.3f) to EyeSpec(209.7f, 97.2f, 15.2f, 38.1f),
    EyeSpec(44.5f, 134.8f, 27.8f, 60.3f) to EyeSpec(97f, 150.4f, 47.1f, 49.1f),
    EyeSpec(109.5f, 115.1f, 30.2f, 55f) to EyeSpec(166.5f, 102.4f, 39.4f, 48.4f),
    EyeSpec(91f, 99.9f, 46.5f, 47.3f) to EyeSpec(158f, 114.6f, 23.1f, 25.1f),
    EyeSpec(63.2f, 142.2f, 30.3f, 59.8f) to EyeSpec(121.3f, 131.7f, 33f, 60.4f),
    EyeSpec(106.2f, 161.5f, 52.8f, 81.9f) to EyeSpec(172.4f, 168.9f, 47.5f, 80.5f),
    EyeSpec(55f, 139.4f, 47.7f, 55f) to EyeSpec(126.6f, 123.4f, 56.2f, 56.4f),
    EyeSpec(91.5f, 88.8f, 54.4f, 19.2f) to EyeSpec(155.7f, 102f, 51.5f, 23.9f),
    EyeSpec(109.4f, 147.3f, 33.2f, 61.8f) to EyeSpec(170.6f, 134.4f, 46.2f, 24f),
    EyeSpec(98.5f, 140.2f, 24.7f, 37.3f) to EyeSpec(153.7f, 145.9f, 23.5f, 37.1f)
)

fun expressionRings(index: Int): Pair<List<Pt>, List<Pt>> {
    val i = index.coerceIn(EXPRESSION_SPECS.indices)
    return eyeRing(EXPRESSION_SPECS[i].first) to eyeRing(EXPRESSION_SPECS[i].second)
}

/** Maps live agent telemetry (phase/tool flags) to a BotState. Pure function. */
fun BotState.Companion.fromTelemetry(
    phase: String?,
    isTyping: Boolean = false,
    isListening: Boolean = false,
    isSearching: Boolean = false,
    hasError: Boolean = false,
    hasResult: Boolean = false,
    isLoading: Boolean = false,
    isSending: Boolean = false,
    isReceiving: Boolean = false,
    isWriting: Boolean = false,
    isUploading: Boolean = false,
    isNotifying: Boolean = false
): BotState {
    if (hasError) return BotState.Alerting
    if (isNotifying) return BotState.Notifying
    if (isUploading) return BotState.Uploading
    if (isSending) return BotState.Sending
    if (isReceiving) return BotState.Receiving
    if (isWriting) return BotState.Writing
    if (isLoading) return BotState.Loading
    if (isSearching) return BotState.Searching
    if (isListening) return BotState.Listening
    if (isTyping) return BotState.Dictating
    return when (phase?.lowercase()) {
        "thinking", "solving", "reasoning" -> BotState.Thinking
        "working", "tool", "executing" -> BotState.Working
        "done", "complete", "happy" -> BotState.Happy
        "error", "failed" -> BotState.Alerting
        "sleeping", "idle" -> BotState.Sleeping
        else -> if (hasResult) BotState.Happy else BotState.Idle
    }
}

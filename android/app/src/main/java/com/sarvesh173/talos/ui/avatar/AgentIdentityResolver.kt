package com.sarvesh173.talos.ui.avatar

/**
 * Resolves [AgentIdentity] for an agent or group.
 *
 * Seed algorithm: FNV-1a 64-bit (NOT String.hashCode(), whose values for
 * some inputs vary across JVM versions and which collides freely):
 *   h = 14695981039346656037 (0xCBF29CE484222325, FNV offset basis)
 *   for each UTF-8 byte b of the id:
 *     h = h XOR b
 *     h = h * 1099511628211 (0x100000001B3, FNV prime)
 * All arithmetic is modulo 2^64 (Kotlin Long two's-complement wrap-around is
 * fully specified, so this is identical on every JVM/Android runtime).
 * The returned Long's bit pattern is the unsigned 64-bit hash.
 *
 * Form rule ([preferredForm]): let u = (seed mod 1000) / 1000 in [0, 1).
 * - Group (isGroup = true):  u < 0.60 -> Blob, u < 0.80 -> Orb, else Character.
 * - Member (isGroup = false): u < 0.60 -> Character, u < 0.80 -> Blob, else Orb.
 * Groups deterministically favour Blob (round, shared, "room" feel) while
 * members favour Character (expressive, individual). Photo is never
 * auto-assigned — it is only ever set via a user override.
 *
 * Palette rule ([paletteFor]): palettes are picked from [PALETTE_IDS] by
 * (seed mod paletteCount). For groups, if the picked palette is already used
 * by any member, advance to the next unused palette (cyclically). If every
 * palette is taken by members, the group gets the base palette with a "-g"
 * suffix, which no member can ever hold — so a group palette NEVER equals
 * any of its members' palettes.
 *
 * Inheritance: when [resolve] receives a non-null groupId, the member's seed
 * is fnv1a(groupId + "\u0000" + agentId) — a delegate of opencode looks
 * related to opencode's room (same salt) but stays distinct from its
 * siblings and from the group itself.
 */
object AgentIdentityResolver {

    /** Palette keys. Members pick from these; groups are bumped off them. */
    val PALETTE_IDS: List<String> = listOf(
        "amber", "teal", "coral", "violet",
        "mint", "rose", "sky", "slate"
    )

    private const val FNV_OFFSET_BASIS: Long = -3750763034362895579L // 0xCBF29CE484222325
    private const val FNV_PRIME: Long = 1099511628211L // 0x100000001B3
    private const val GROUP_BLOB_BIAS: Long = 600L // u < 0.60
    private const val MEMBER_CHARACTER_BIAS: Long = 600L // u < 0.60
    private const val SECOND_BIAS: Long = 800L // u < 0.80 for both

    /**
     * Stable 64-bit FNV-1a hash of [agentId] over its UTF-8 bytes.
     * Same id -> same seed on every run, device and JVM version. This must
     * match tests/test_identity_resolver.py exactly.
     */
    fun seedFor(agentId: String): Long {
        var h = FNV_OFFSET_BASIS
        val bytes = agentId.toByteArray(charset = Charsets.UTF_8)
        for (b in bytes) {
            h = h xor (b.toLong() and 0xFFL)
            h *= FNV_PRIME
        }
        return h
    }

    /** Pure function of (agentId, isGroup) following the documented rule. */
    fun preferredForm(agentId: String, isGroup: Boolean): IdentityForm {
        val u = Math.floorMod(seedFor(agentId), 1000L) // always 0..999
        return if (isGroup) {
            when {
                u < GROUP_BLOB_BIAS -> IdentityForm.Blob
                u < SECOND_BIAS -> IdentityForm.Orb
                else -> IdentityForm.Character
            }
        } else {
            when {
                u < MEMBER_CHARACTER_BIAS -> IdentityForm.Character
                u < SECOND_BIAS -> IdentityForm.Blob
                else -> IdentityForm.Orb
            }
        }
    }

    /**
     * Deterministic palette for [agentId]. Pass [memberPaletteIds] (the
     * palettes already taken by this group's members) when resolving a group
     * to guarantee the group palette differs from every member's.
     */
    fun paletteFor(
        agentId: String,
        isGroup: Boolean,
        memberPaletteIds: List<String> = emptyList()
    ): String {
        val base = Math.floorMod(seedFor(agentId), PALETTE_IDS.size.toLong()).toInt()
        if (!isGroup) return PALETTE_IDS[base]
        // Group: advance past any member-taken palette.
        for (i in PALETTE_IDS.indices) {
            val candidate = PALETTE_IDS[(base + i) % PALETTE_IDS.size]
            if (candidate !in memberPaletteIds) return candidate
        }
        // Every palette is taken: use a suffixed variant no member can hold.
        return PALETTE_IDS[base] + "-g"
    }

    /**
     * Full identity for [agentId]. A user override in [overrides] always wins
     * (returned as-is). Otherwise the identity is derived deterministically;
     * [groupId] salts the seed for parent-inheritance (see class docs).
     * [memberPaletteIds] should be the palettes of the group's members when
     * resolving the group itself.
     */
    fun resolve(
        agentId: String,
        overrides: Map<String, AgentIdentity> = emptyMap(),
        isGroup: Boolean = false,
        groupId: String? = null,
        memberPaletteIds: List<String> = emptyList()
    ): AgentIdentity {
        overrides[agentId]?.let { return it }

        val effectiveId = if (groupId != null) "$groupId\u0000$agentId" else agentId
        val seed = seedFor(effectiveId)
        val displayName = agentId
        return AgentIdentity(
            agentId = agentId,
            displayName = displayName,
            form = preferredForm(effectiveId, isGroup),
            seed = seed,
            paletteId = paletteFor(effectiveId, isGroup, memberPaletteIds),
            blobParams = blobParamsFromSeed(seed),
            orbParams = orbParamsFromSeed(seed),
            characterParams = characterParamsFromSeed(seed),
            photoPath = null,
            groupId = groupId,
            isGroup = isGroup
        )
    }

    // SplitMix64: deterministic PRNG from a seed, all values in-range.
    private fun nextUnit(seed: Long, salt: Long): Double {
        var z = seed + salt
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return (z ushr 11).toDouble() / 9007199254740992.0 // 2^53
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    /** Deterministic params in the documented ranges for [BlobParams]. */
    fun blobParamsFromSeed(seed: Long): BlobParams = BlobParams(
        lobeCount = 3 + (Math.floorMod(seed, 6L)).toInt(), // 3..8
        lobeAsymmetry = nextUnit(seed, 1L).toFloat(), // 0f..1f
        surfaceRoughness = nextUnit(seed, 2L).toFloat(), // 0f..1f
        wobbleAmplitude = lerp(0.0, 0.30, nextUnit(seed, 3L)).toFloat(), // 0f..0.30f
        wobbleFrequency = lerp(0.1, 4.0, nextUnit(seed, 4L)).toFloat(), // 0.1f..4f
        viscousness = nextUnit(seed, 5L).toFloat(), // 0f..1f
        squashStretch = nextUnit(seed, 6L).toFloat(), // 0f..1f
        rimLightStrength = nextUnit(seed, 7L).toFloat(), // 0f..1f
        innerShadowStrength = nextUnit(seed, 8L).toFloat() // 0f..1f
    )

    /** Deterministic params in the documented ranges for [OrbParams]. */
    fun orbParamsFromSeed(seed: Long): OrbParams = OrbParams(
        stateCount = 2 + (Math.floorMod(seed ushr 8, 4L)).toInt(), // 2..5
        dotCount = 4 + (Math.floorMod(seed ushr 16, 61L)).toInt(), // 4..64
        dotRadius = lerp(0.02, 0.20, nextUnit(seed, 11L)).toFloat(), // 0.02f..0.20f
        innerRotationSpeed = lerp(-3.0, 3.0, nextUnit(seed, 12L)).toFloat(), // -3f..3f
        outerRotationSpeed = lerp(-3.0, 3.0, nextUnit(seed, 13L)).toFloat(), // -3f..3f
        shellDepth = nextUnit(seed, 14L).toFloat(), // 0f..1f
        meridianDensity = 1 + (Math.floorMod(seed ushr 24, 12L)).toInt(), // 1..12
        scanSpeed = lerp(0.0, 3.0, nextUnit(seed, 15L)).toFloat() // 0f..3f
    )

    /** Deterministic params in the documented ranges for [CharacterParams]. */
    fun characterParamsFromSeed(seed: Long): CharacterParams = CharacterParams(
        bodyProportion = lerp(0.5, 1.5, nextUnit(seed, 21L)).toFloat(), // 0.5f..1.5f
        eyeSize = lerp(0.5, 1.8, nextUnit(seed, 22L)).toFloat(), // 0.5f..1.8f
        eyeSpacing = lerp(0.6, 1.6, nextUnit(seed, 23L)).toFloat(), // 0.6f..1.6f
        blinkRate = nextUnit(seed, 24L).toFloat(), // 0f..1f
        mouthCurve = lerp(-1.0, 1.0, nextUnit(seed, 25L)).toFloat(), // -1f..1f
        hasAntenna = Math.floorMod(seed ushr 32, 2L) == 0L,
        hasEars = Math.floorMod(seed ushr 40, 2L) == 0L,
        particleAuraDensity = nextUnit(seed, 26L).toFloat() // 0f..1f
    )
}

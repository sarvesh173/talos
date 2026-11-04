package com.sarvesh173.talos.ui.avatar

/**
 * Visual identity model for a TALOS agent (member) or room/group (CLI tool).
 *
 * This file is the single source of truth for every customization screen:
 * a user can change any field below and the renderer must honour it.
 *
 * Mental model (WhatsApp groups): the room/session is the group profile photo,
 * each member agent inside it has its own photo. Groups and members are both
 * [AgentIdentity]; groups simply have [AgentIdentity.isGroup] = true and a
 * different default form bias (see [AgentIdentityResolver.preferredForm]).
 *
 * Determinism: every identity is derived from [AgentIdentity.seed] via
 * [AgentIdentityResolver.seedFor], which is FNV-1a over the agentId bytes.
 * The same agentId always yields the same seed, form, palette and parameters
 * on every device and every app restart. No Random(), no UUIDs, no
 * String.hashCode() (which is unspecified across JVM versions for some
 * inputs and can collide across strings).
 */
enum class IdentityForm {
    /** A cute primitive character (body + face). Default for members. */
    Character,
    /** A deforming metaball blob. Default for groups. */
    Blob,
    /** A dotted meridian orb. Neutral prestige form. */
    Orb,
    /** A user-picked photo (file path). Never auto-assigned; override only. */
    Photo
}

/**
 * Blob physics/appearance parameters.
 *
 * Ranges (validated by [AgentIdentityResolver] when generating defaults;
 * customization screens should clamp user input to these ranges):
 * - [lobeCount]: Int, 3..8, default 5. Number of metaball lobes.
 * - [lobeAsymmetry]: Float, 0f..1f, default 0.35f. 0 = perfectly symmetric
 *   lobes, 1 = lobes vary wildly in size/placement.
 * - [surfaceRoughness]: Float, 0f..1f, default 0.4f. 0 = glass-smooth,
 *   1 = lumpy/jagged silhouette (perturbs the radius function).
 * - [wobbleAmplitude]: Float, 0f..0.30f, default 0.08f. Fraction of the base
 *   radius the idle wobble displaces the silhouette (0 = statue stiff).
 * - [wobbleFrequency]: Float, 0.1f..4f, default 1.0f. Wobble cycles/sec.
 * - [viscousness]: Float, 0f..1f, default 0.5f. 0 = watery/sloshy (fast
 *   deformation), 1 = thick honey (very slow, heavy deformation).
 * - [squashStretch]: Float, 0f..1f, default 0.3f. Strength of squash-and-
 *   stretch secondary motion tied to the wobble.
 * - [rimLightStrength]: Float, 0f..1f, default 0.6f. Rim-light intensity on
 *   the silhouette edge.
 * - [innerShadowStrength]: Float, 0f..1f, default 0.45f. Inner ambient
 *   occlusion / depth shadow intensity.
 */
data class BlobParams(
    val lobeCount: Int = 5,
    val lobeAsymmetry: Float = 0.35f,
    val surfaceRoughness: Float = 0.4f,
    val wobbleAmplitude: Float = 0.08f,
    val wobbleFrequency: Float = 1.0f,
    val viscousness: Float = 0.5f,
    val squashStretch: Float = 0.3f,
    val rimLightStrength: Float = 0.6f,
    val innerShadowStrength: Float = 0.45f
)

/**
 * Orb (dotted meridian sphere) parameters.
 *
 * Ranges:
 * - [stateCount]: Int, 2..5, default 3. Number of visual states the orb cycles
 *   through (e.g. idle/working/error). 2 = minimal, 5 = full set.
 * - [dotCount]: Int, 4..64, default 24. Number of surface dots.
 * - [dotRadius]: Float, 0.02f..0.20f, default 0.08f. Dot radius as a fraction
 *   of the orb radius.
 * - [innerRotationSpeed]: Float, -3f..3f (rad/s), default 0.8f. Sign sets
 *   rotation direction.
 * - [outerRotationSpeed]: Float, -3f..3f (rad/s), default -0.5f. Counter-
 *   rotating shell; sign sets direction.
 * - [shellDepth]: Float, 0f..1f, default 0.5f. 0 = flat disc of dots,
 *   1 = full 3D spherical shell (meridian distribution).
 * - [meridianDensity]: Int, 1..12, default 4. Longitude lines the dots orbit
 *   along.
 * - [scanSpeed]: Float, 0f..3f (rad/s), default 1.2f. Speed of the latitude
 *   scan highlight sweeping the orb.
 */
data class OrbParams(
    val stateCount: Int = 3,
    val dotCount: Int = 24,
    val dotRadius: Float = 0.08f,
    val innerRotationSpeed: Float = 0.8f,
    val outerRotationSpeed: Float = -0.5f,
    val shellDepth: Float = 0.5f,
    val meridianDensity: Int = 4,
    val scanSpeed: Float = 1.2f
)

/**
 * Character (primitive mascot) parameters.
 *
 * Ranges:
 * - [bodyProportion]: Float, 0.5f..1.5f, default 1.0f. Body height multiplier
 *   (0.5 = squat, 1.5 = lanky, 1.0 = balanced).
 * - [eyeSize]: Float, 0.5f..1.8f, default 1.0f. Eye radius scale.
 * - [eyeSpacing]: Float, 0.6f..1.6f, default 1.0f. Eye center distance scale.
 * - [blinkRate]: Float, 0f..1f, default 0.35f. Blinks/sec (0 = never blinks).
 * - [mouthCurve]: Float, -1f..1f, default 0.25f. -1 = full frown, 0 = flat,
 *   +1 = full smile.
 * - [hasAntenna]: Boolean, default true. Antenna toggle.
 * - [hasEars]: Boolean, default false. Ear toggle (mutually exclusive in the
 *   renderer; both true renders antenna + ears).
 * - [particleAuraDensity]: Float, 0f..1f, default 0.4f. Density of orbiting
 *   sparkle particles around the character.
 */
data class CharacterParams(
    val bodyProportion: Float = 1.0f,
    val eyeSize: Float = 1.0f,
    val eyeSpacing: Float = 1.0f,
    val blinkRate: Float = 0.35f,
    val mouthCurve: Float = 0.25f,
    val hasAntenna: Boolean = true,
    val hasEars: Boolean = false,
    val particleAuraDensity: Float = 0.4f
)

/**
 * Complete identity for one agent or group.
 *
 * Fields:
 * - [agentId]: stable identity key (e.g. "opencode", "hermes",
 *   "claude-code", "delegate-7f3a"). Feeds [AgentIdentityResolver.seedFor].
 * - [displayName]: human label shown in UI chrome.
 * - [form]: which renderer to use.
 * - [seed]: deterministic seed from [AgentIdentityResolver.seedFor]; the same
 *   agent always looks the same across restarts/devices.
 * - [paletteId]: palette key into the app's palette table; groups never share
 *   a palette with their members (enforced by the resolver).
 * - [photoPath]: only meaningful when [form] is [IdentityForm.Photo];
 *   null for all other forms.
 * - [groupId]: id of the owning group for members; null for groups.
 * - [isGroup]: true for the room/group identity, false for members.
 */
data class AgentIdentity(
    val agentId: String,
    val displayName: String,
    val form: IdentityForm,
    val seed: Long,
    val paletteId: String,
    val blobParams: BlobParams = BlobParams(),
    val orbParams: OrbParams = OrbParams(),
    val characterParams: CharacterParams = CharacterParams(),
    val photoPath: String? = null,
    val groupId: String? = null,
    val isGroup: Boolean = false
)

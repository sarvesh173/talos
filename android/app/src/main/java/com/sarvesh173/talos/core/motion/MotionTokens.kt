package com.sarvesh173.talos.core.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * TALOS Motion Tokens — the single source of truth for every animation in the app.
 *
 * Lineage (measured 2026-10-28 from live sources, see docs/MOTION_SPEC.md):
 *  - Telegram Web K CSS:  --transition-standard-easing cubic-bezier(.4,0,.2,1)
 *                          --transition-standard-in-time  .2s / .3s
 *                          --transition-standard-out-time .15s / .25s
 *                          --transition-time .4s / --transition-time-move .3s
 *  - Telegram Android:    CubicBezierInterpolator DEFAULT/EASE_OUT/EASE_OUT_BACK,
 *                          EmphasizedDecelerate (0.05,0.7,0.1,1),
 *                          EmphasizedAccelerate (0.3,0,0.8,0.15),
 *                          BottomSheet present/dismiss = 250 ms
 *  - WhatsApp Web CSS:    durations .1 .15 .167 .2 .25 .28 .3 .333 .417 .45 .5 s,
 *                          easings incl. cubic-bezier(.22,1,.36,1) popup reveal
 *  - M3 Expressive:       spatial fast 0.6/800, default 0.8/380, slow 0.8/200;
 *                          effects 1.0/3800, 1.0/1600, 1.0/800
 *
 * RULES (do not break):
 *  1. Spatial springs for position / size / shape / rotation.
 *  2. Critically damped effects springs for color / alpha / state layers.
 *     A spatial spring on alpha overshoots and reads as a glitch.
 *  3. Infinite loops NEVER use springs — keyframes on [EmphasizedDecelerate].
 *  4. Never mix families without a reason.
 */
object MotionTokens {

    // ---------------------------------------------------------------------
    // Curves (shared lineage across Telegram / WhatsApp)
    // ---------------------------------------------------------------------

    /** Telegram Web K standard in/out curve. */
    val Standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** Telegram Android emphasized decelerate — entrances, reveals, expansion. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Telegram Android emphasized accelerate — exits, dismissals. */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Telegram Android StandardDecelerate — very long settle. */
    val StandardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** Telegram Android EASE_OUT_QUINT — sharp decelerate for value changes. */
    val QuintDecelerate: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** Telegram + WhatsApp shared popup / menu reveal curve. */
    val PopupReveal: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** Telegram Android EASE_OUT_BACK — spring-like overshoot, used sparingly. */
    val BackOut: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    // ---------------------------------------------------------------------
    // Durations (ms)
    // ---------------------------------------------------------------------

    /** Telegram Web K enter band: .2s–.3s. */
    const val ENTER_DURATION_MS: Int = 260

    /** Telegram Web K exit band: .15s–.25s. */
    const val EXIT_DURATION_MS: Int = 200

    /** Telegram Android BottomSheet present/dismiss. */
    const val SHEET_DURATION_MS: Int = 250

    /** Telegram Web K `--transition-time` (long, full-surface moves). */
    const val LONG_DURATION_MS: Int = 400

    /** Telegram Web K `--transition-time-move` (layout moves). */
    const val MOVE_DURATION_MS: Int = 300

    /** WhatsApp Web typing-dot cadence. */
    const val TYPING_CADENCE_MS: Int = 333

    /** WhatsApp Web stagger step for list entrances. */
    const val STAGGER_STEP_MS: Int = 40

    /** WhatsApp Web quick state flip (chips, toggles). */
    const val QUICK_DURATION_MS: Int = 150

    // ---------------------------------------------------------------------
    // Springs
    // ---------------------------------------------------------------------

    /** Fast spatial — buttons, chips, switches, small controls. */
    fun <T> fastSpatial(): SpringSpec<T> =
        spring(dampingRatio = 0.6f, stiffness = 800f)

    /** Default spatial — cards, sheets, partial-screen surfaces. */
    fun <T> defaultSpatial(): SpringSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Slow spatial — full-screen transitions, hero reveals. */
    fun <T> slowSpatial(): SpringSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 200f)

    /** Fast effects — icon tints, selection highlights, state-layer fade. */
    fun <T> fastEffects(): SpringSpec<T> =
        spring(dampingRatio = 1.0f, stiffness = 3800f)

    /** Default effects — container color transitions. */
    fun <T> defaultEffects(): SpringSpec<T> =
        spring(dampingRatio = 1.0f, stiffness = 1600f)

    /** Slow effects — long fades. */
    fun <T> slowEffects(): SpringSpec<T> =
        spring(dampingRatio = 1.0f, stiffness = 800f)

    // ---------------------------------------------------------------------
    // Tween helpers (timed curves only — never use for interruptible motion)
    // ---------------------------------------------------------------------

    /** Enter tween: emphasized decelerate, [ENTER_DURATION_MS]. */
    fun <T> enterTween(): FiniteAnimationSpec<T> =
        tween(durationMillis = ENTER_DURATION_MS, easing = EmphasizedDecelerate)

    /** Exit tween: emphasized accelerate, [EXIT_DURATION_MS]. */
    fun <T> exitTween(): FiniteAnimationSpec<T> =
        tween(durationMillis = EXIT_DURATION_MS, easing = EmphasizedAccelerate)

    /** Sheet tween: standard curve, [SHEET_DURATION_MS] (Telegram BottomSheet). */
    fun <T> sheetTween(): FiniteAnimationSpec<T> =
        tween(durationMillis = SHEET_DURATION_MS, easing = Standard)

    /** Quick state flip: standard curve, [QUICK_DURATION_MS]. */
    fun <T> quickTween(): FiniteAnimationSpec<T> =
        tween(durationMillis = QUICK_DURATION_MS, easing = Standard)

    // ---------------------------------------------------------------------
    // Composite interaction specs used across the metallic kit
    // ---------------------------------------------------------------------

    /** Press feedback scale for every metallic control. */
    const val PRESS_SCALE: Float = 0.96f

    /** Press feedback scale for large primary surfaces. */
    const val PRESS_SCALE_LARGE: Float = 0.94f

    /** Message bubble entrance offset (Telegram/WhatsApp list entry behaviour). */
    val BUBBLE_ENTER_OFFSET_DP: Int = 12

    /** Message bubble entrance start scale. */
    const val BUBBLE_ENTER_SCALE: Float = 0.96f

    /** Mention popup entrance start scale. */
    const val POPUP_ENTER_SCALE: Float = 0.92f

    /**
     * Per-item stagger delay for list entrances.
     * @param index zero-based item index
     * @param maxItems how many items participate in the stagger (rest appear at once)
     */
    fun staggerDelayMillis(index: Int, maxItems: Int = 12): Int =
        if (index >= maxItems) 0 else index * STAGGER_STEP_MS
}

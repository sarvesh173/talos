package com.sarvesh173.talos.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * TALOS Apple Motion Discipline
 *
 * Reduced motion is not "no feedback" — it is a gentler, non-vestibular equivalent.
 * Per Apple's accessibility guidance and Emil Kowalski's motion discipline:
 *  - System animation scale 0 => replace slides, springs and parallax with short
 *    opacity cross-fades. Never remove feedback entirely.
 *  - System animation scale < 1 => honour the user's chosen speed by shortening
 *    durations, never by changing the spatial path.
 *
 * Font / display size scaling is handled by Compose's sp units, so layout follows
 * Dynamic Type automatically as long as we do not hardcode dp heights around text.
 */
object TalosMotion {

    /** Reads the OS animator duration scale (0f when animations are turned off). */
    fun animationScale(context: Context): Float = try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
    } catch (_: Throwable) {
        1f
    }

    /** True when the user has disabled system animations. */
    fun isReducedMotion(context: Context): Boolean = animationScale(context) == 0f
}

/**
 * Remembers the current reduced-motion state for a composition.
 * Recomposes only when the value is read again on a new measurement pass; Android
 * does not broadcast animator-scale changes, so we sample once per screen entry —
 * which matches how the platform itself behaves for already-running animations.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { TalosMotion.isReducedMotion(context) }
}

/**
 * Spatial spring that collapses to an instant settle under reduced motion.
 * Use for anything that translates or scales on screen.
 */
fun <T> spatialSpring(reducedMotion: Boolean): FiniteAnimationSpec<T> =
    if (reducedMotion) tween(durationMillis = 0)
    else spring(dampingRatio = 0.82f, stiffness = 420f)

/**
 * Effects spring (colour / opacity). Effects never overshoot, and under reduced
 * motion they degrade to a short cross-fade rather than disappearing.
 */
fun <T> effectsSpring(reducedMotion: Boolean): FiniteAnimationSpec<T> =
    if (reducedMotion) tween(durationMillis = 120)
    else spring(dampingRatio = 1f, stiffness = 900f)

/**
 * Duration for opacity-only transitions. Reduced motion keeps a short fade so the
 * change still reads as a change rather than an instant snap.
 */
fun fadeMillis(reducedMotion: Boolean, normalMillis: Int = 220): Int =
    if (reducedMotion) 120 else normalMillis

/**
 * Press scale target. Under reduced motion the element still responds instantly to
 * touch, but without the elastic overshoot that can cause discomfort.
 */
fun pressScale(reducedMotion: Boolean, normalScale: Float = 0.97f): Float =
    if (reducedMotion) 0.99f else normalScale

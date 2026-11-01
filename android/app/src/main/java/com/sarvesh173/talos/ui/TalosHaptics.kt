package com.sarvesh173.talos.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

object TalosHaptics {
    var isHapticsEnabled: Boolean = true
    private val WAVEFORM = longArrayOf(0, 30, 50, 30)

    /**
     * Crisp, distinct tactile feedback for primary user actions (message send, chamber enter).
     * Uses VibratorManager (API 31+) with waveform [0, 30, 50, 30] via VibrationEffect.createWaveform.
     * Falls back to Vibrator / HapticFeedback on older Android versions or devices without VibratorManager.
     */
    fun sendHaptic(
        context: Context,
        composeHaptic: HapticFeedback? = null
    ) {
        if (!isHapticsEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                if (vibrator != null && vibrator.hasVibrator()) {
                    val effect = VibrationEffect.createWaveform(WAVEFORM, -1)
                    vibrator.vibrate(effect)
                    return
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (vibrator != null && vibrator.hasVibrator()) {
                    val effect = VibrationEffect.createWaveform(WAVEFORM, -1)
                    vibrator.vibrate(effect)
                    return
                }
            }
        } catch (_: Throwable) {
            // Fall through to HapticFeedback fallback if vibrator call fails
        }

        // Fallback to HapticFeedback on older Android versions or missing vibrator hardware
        try {
            composeHaptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Throwable) {}

        try {
            val activity = findActivity(context)
            activity?.window?.decorView?.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        } catch (_: Throwable) {
            try {
                val activity = findActivity(context)
                activity?.window?.decorView?.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP
                )
            } catch (_: Throwable) {}
        }
    }

    private fun findActivity(context: Context?): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}

/**
 * Top-level helper function: fun sendHaptic(context)
 */
fun sendHaptic(
    context: Context,
    composeHaptic: HapticFeedback? = null
) {
    TalosHaptics.sendHaptic(context, composeHaptic)
}

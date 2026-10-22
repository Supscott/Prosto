package com.jestk.browser.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App-wide vibration feedback.
 *
 * Compose's `HapticFeedback` is routed through the system "touch feedback" setting and silently
 * does nothing on many devices, so the app drives the platform vibrator directly.
 */
interface VibrationFeedback {
    /** Light pulse for discrete gestures (drag swap, carousel slot change). */
    fun tick()

    /** Stronger pulse for confirmations (long press). */
    fun confirm()
}

/** Fallback used when no composition local is provided (previews, isolated tests). */
object NoVibrationFeedback : VibrationFeedback {
    override fun tick() = Unit

    override fun confirm() = Unit
}

val LocalVibrationFeedback: ProvidableCompositionLocal<VibrationFeedback> =
    staticCompositionLocalOf { NoVibrationFeedback }

/**
 * [VibrationFeedback] backed by the platform vibrator.
 *
 * [isEnabled] is read on every pulse instead of being cached: the instance stays stable for the
 * whole app lifetime, so gesture callbacks captured at composition time always observe the
 * current user preference without being restarted.
 */
class VibratorVibrationFeedback internal constructor(
    private val vibrator: Vibrator?,
    private val isEnabled: () -> Boolean,
) : VibrationFeedback {
    constructor(context: Context, isEnabled: () -> Boolean) : this(resolveVibrator(context), isEnabled)

    private val canVibrate: Boolean = vibrator?.hasVibrator() == true

    override fun tick() = vibrate(TICK_DURATION_MS)

    override fun confirm() = vibrate(CONFIRM_DURATION_MS)

    private fun vibrate(durationMs: Long) {
        if (!isEnabled() || !canVibrate) return
        // One-shot effects are supported on every device; predefined effects are not.
        vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private companion object {
        const val TICK_DURATION_MS = 20L
        const val CONFIRM_DURATION_MS = 50L

        fun resolveVibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
    }
}

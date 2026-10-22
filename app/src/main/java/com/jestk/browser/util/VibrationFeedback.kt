package com.jestk.browser.util

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App-wide vibration feedback.
 *
 * Compose's `HapticFeedback` is routed through the system "touch feedback" setting and silently
 * does nothing on many devices, so the app drives the platform vibrator directly with hardware
 * feedback attributes.
 */
interface VibrationFeedback {
    /** Light pulse for discrete gestures (drag swap, carousel slot change, tab close swipe). */
    fun tick()

    /** Stronger pulse for confirmations (long press, pull-to-refresh threshold, settings toggle). */
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

    override fun tick() =
        vibrate(
            predefinedEffect = VibrationEffect.EFFECT_TICK,
            fallbackDurationMs = TICK_DURATION_MS,
        )

    override fun confirm() =
        vibrate(
            predefinedEffect = VibrationEffect.EFFECT_CLICK,
            fallbackDurationMs = CONFIRM_DURATION_MS,
        )

    private fun vibrate(predefinedEffect: Int?, fallbackDurationMs: Long) {
        if (!isEnabled() || !canVibrate) return
        val effect =
            try {
                if (predefinedEffect != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    VibrationEffect.createPredefined(predefinedEffect)
                } else {
                    VibrationEffect.createOneShot(fallbackDurationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                }
            } catch (_: Throwable) {
                VibrationEffect.createOneShot(fallbackDurationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attributes =
                    VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)
                        .build()
                vibrator?.vibrate(effect, attributes)
            } else {
                val attributes =
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build()
                @Suppress("DEPRECATION")
                vibrator?.vibrate(effect, attributes)
            }
        } catch (_: Throwable) {
            try {
                val fallbackEffect = VibrationEffect.createOneShot(fallbackDurationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator?.vibrate(fallbackEffect)
            } catch (_: Throwable) {
                // Ignore device-specific vibrator exceptions
            }
        }
    }

    internal companion object {
        const val TICK_DURATION_MS = 35L
        const val CONFIRM_DURATION_MS = 60L

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

package com.jestk.browser.util

import android.content.Context
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VibrationFeedbackTest {
    private lateinit var vibrator: Vibrator
    private lateinit var shadow: org.robolectric.shadows.ShadowVibrator

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        shadow = shadowOf(vibrator)
        shadow.setHasVibrator(true)
    }

    @Test
    fun tick_and_confirm_vibrateWhenEnabled() {
        VibratorVibrationFeedback(vibrator) { true }.tick()
        assertThat(shadow.isVibrating).isTrue()

        VibratorVibrationFeedback(vibrator) { true }.confirm()
        assertThat(shadow.isVibrating).isTrue()
    }

    @Test
    fun disabled_feedbackDoesNotVibrate() {
        val feedback = VibratorVibrationFeedback(vibrator) { false }

        feedback.tick()
        feedback.confirm()

        assertThat(shadow.isVibrating).isFalse()
    }

    @Test
    fun deviceWithoutVibrator_doesNotVibrate() {
        shadow.setHasVibrator(false)

        VibratorVibrationFeedback(vibrator) { true }.tick()

        assertThat(shadow.isVibrating).isFalse()
    }

    @Test
    fun fallbackDuration_constantsAreHealthy() {
        assertThat(VibratorVibrationFeedback.TICK_DURATION_MS).isEqualTo(35L)
        assertThat(VibratorVibrationFeedback.CONFIRM_DURATION_MS).isEqualTo(60L)
    }

    @Test
    fun vibration_usesHardwareFeedbackAttributes() {
        VibratorVibrationFeedback(vibrator) { true }.tick()

        val attrs = shadow.vibrationAttributesFromLastVibration as? android.os.VibrationAttributes
        assertThat(attrs?.usage).isEqualTo(android.os.VibrationAttributes.USAGE_HARDWARE_FEEDBACK)
    }

    @Test
    @Config(sdk = [32])
    fun vibration_belowApi33_vibratesWithoutThrowing() {
        VibratorVibrationFeedback(vibrator) { true }.tick()

        assertThat(shadow.isVibrating).isTrue()
    }

    @Test
    fun enabledPredicate_isReadOnEveryPulse() {
        var reads = 0
        val feedback =
            VibratorVibrationFeedback(vibrator) {
                reads++
                true
            }

        feedback.tick()
        feedback.confirm()

        assertThat(reads).isEqualTo(2)
    }

    @Test
    fun enabledPredicate_isReReadOnEveryPulse() {
        var enabled = true
        val feedback = VibratorVibrationFeedback(vibrator) { enabled }

        feedback.tick()
        assertThat(shadow.isVibrating).isTrue()

        enabled = false
        org.robolectric.shadows.ShadowVibrator.reset()
        shadow.setHasVibrator(true)
        feedback.confirm()

        // The second pulse observes the flipped preference and stays silent.
        assertThat(shadow.isVibrating).isFalse()
    }
}

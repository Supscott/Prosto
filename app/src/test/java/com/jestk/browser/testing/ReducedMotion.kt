package com.jestk.browser.testing

import android.content.Context
import android.provider.Settings

/**
 * Sets Android's animator duration scale, which Compose reads when a `ComposeView` attaches to its
 * window. At 0 every animation snaps to its end value and an infinite one (indeterminate progress
 * indicators, blinking dots) stops asking for frames. Otherwise a preview that holds such an
 * animation keeps the main looper producing frames, and `CoilIdleTracker.awaitIdle` can run into
 * its watchdog (PRO-193). [CoilPreviewRule] turns it off for every preview test that uses it.
 *
 * Call before `setContent`: a view that is already attached keeps the scale it read.
 */
object ReducedMotion {
    fun set(context: Context, scale: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    fun disableAnimations(context: Context) = set(context, 0f)
}

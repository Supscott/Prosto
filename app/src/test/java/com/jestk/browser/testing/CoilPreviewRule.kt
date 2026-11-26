package com.jestk.browser.testing

import android.view.View
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment

/**
 * Rule for Robolectric preview tests that draw a `ComposeView` by hand: it installs a
 * [CoilIdleTracker] for the test, and [awaitIdle] before the draw waits for the `AsyncImage`
 * loads (engine badges, site favicons) that Coil decodes off the main thread (PRO-144, PRO-157).
 *
 * ```
 * @get:Rule val coil = CoilPreviewRule()
 * ...
 * coil.awaitIdle(composeView, width, height)
 * composeView.draw(canvas)
 * ```
 */
class CoilPreviewRule : ExternalResource() {
    private var tracker: CoilIdleTracker? = null

    override fun before() {
        // Animations snap to their end state: a static capture wants that, and an infinite one (a
        // loading spinner) would keep the frame clock busy while awaitIdle waits (PRO-193).
        ReducedMotion.disableAnimations(RuntimeEnvironment.getApplication())
        tracker = CoilIdleTracker.install(RuntimeEnvironment.getApplication())
    }

    override fun after() {
        ReducedMotion.set(RuntimeEnvironment.getApplication(), 1f)
        CoilIdleTracker.uninstall()
        tracker = null
    }

    fun awaitIdle(view: View, width: Int, height: Int) {
        checkNotNull(tracker) { "CoilPreviewRule is not active" }.awaitIdle(view, width, height)
    }
}

package com.jestk.browser.testing

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.ComposeView
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * An indeterminate spinner must not keep the frame clock busy in a preview test (PRO-193): with the
 * animator scale at 0 no frame stays queued, so `idleFor` returns. At the default scale the same
 * spinner makes `idleFor` spin until the 240 s test watchdog, which is why there is no control test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReducedMotionTest {
    @get:Rule
    val coil = CoilPreviewRule()

    @Test
    fun previewRuleLeavesNoFrameQueuedForAnInfiniteAnimation() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity).apply { setContent { CircularProgressIndicator() } }
        activity.setContentView(composeView)
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, 100, 100)

        val looper = shadowOf(Looper.getMainLooper())
        looper.idleFor(Duration.ofMillis(500))

        assertThat(looper.nextScheduledTaskTime).isEqualTo(Duration.ZERO)
    }
}

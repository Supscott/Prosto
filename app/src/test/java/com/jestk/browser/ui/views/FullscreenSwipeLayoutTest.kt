package com.jestk.browser.ui.views

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FullscreenSwipeLayoutTest {

    private fun createMotionEvent(action: Int, x: Float, y: Float): MotionEvent {
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(now, now, action, x, y, 0)
    }

    @Test
    fun swipeDown_onInterceptTouchEvent_triggersCallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var swipeTriggered = false
        val layout = FullscreenSwipeLayout(context) {
            swipeTriggered = true
        }

        // Down at y = 100
        val down = createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 100f)
        layout.onInterceptTouchEvent(down)

        // Drag down significantly (dy = 250 > threshold)
        val move = createMotionEvent(MotionEvent.ACTION_MOVE, 105f, 350f)
        val intercepted = layout.onInterceptTouchEvent(move)

        assertThat(swipeTriggered).isTrue()
        assertThat(intercepted).isTrue()
    }

    @Test
    fun swipeHorizontal_doesNotTriggerCallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var swipeTriggered = false
        val layout = FullscreenSwipeLayout(context) {
            swipeTriggered = true
        }

        val down = createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 100f)
        layout.onInterceptTouchEvent(down)

        // Move horizontally (dx = 250, dy = 10)
        val move = createMotionEvent(MotionEvent.ACTION_MOVE, 350f, 110f)
        val intercepted = layout.onInterceptTouchEvent(move)

        assertThat(swipeTriggered).isFalse()
        assertThat(intercepted).isFalse()
    }

    @Test
    fun swipeUp_doesNotTriggerCallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var swipeTriggered = false
        val layout = FullscreenSwipeLayout(context) {
            swipeTriggered = true
        }

        val down = createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 300f)
        layout.onInterceptTouchEvent(down)

        // Move upwards
        val move = createMotionEvent(MotionEvent.ACTION_MOVE, 100f, 100f)
        val intercepted = layout.onInterceptTouchEvent(move)

        assertThat(swipeTriggered).isFalse()
        assertThat(intercepted).isFalse()
    }

    @Test
    fun swipeDown_onTouchEvent_triggersCallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var swipeTriggered = false
        val layout = FullscreenSwipeLayout(context) {
            swipeTriggered = true
        }

        val down = createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 100f)
        layout.onTouchEvent(down)

        val move = createMotionEvent(MotionEvent.ACTION_MOVE, 105f, 350f)
        layout.onTouchEvent(move)

        assertThat(swipeTriggered).isTrue()
    }

    @Test
    fun swipeDown_dispatchTouchEvent_withDisallowIntercept_triggersCallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var swipeTriggered = false
        val layout = FullscreenSwipeLayout(context) {
            swipeTriggered = true
        }

        // Child view disallows intercept
        layout.requestDisallowInterceptTouchEvent(true)

        val down = createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 100f)
        layout.dispatchTouchEvent(down)

        val move = createMotionEvent(MotionEvent.ACTION_MOVE, 105f, 350f)
        val handled = layout.dispatchTouchEvent(move)

        assertThat(swipeTriggered).isTrue()
        assertThat(handled).isTrue()
    }
}

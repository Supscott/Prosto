package com.jestk.browser.engine

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.ui.views.JestkSwipeRefreshLayout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PullToRefreshGestureTest {

    private fun obtainMotionEvent(
        action: Int,
        x: Float,
        y: Float,
        downTime: Long = SystemClock.uptimeMillis(),
        eventTime: Long = SystemClock.uptimeMillis()
    ): MotionEvent {
        return MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
    }

    @Test
    fun nestedWebView_initialState_eligibleAtTop() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webView = NestedWebView(context)

        assertThat(webView.isPullToRefreshEligible()).isTrue()
    }

    @Test
    fun nestedWebView_horizontalSwipe_locksDirectionAndBlocksPullToRefresh() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webView = NestedWebView(context)

        val downTime = SystemClock.uptimeMillis()
        webView.onTouchEvent(obtainMotionEvent(MotionEvent.ACTION_DOWN, 100f, 100f, downTime, downTime))
        assertThat(webView.isPullToRefreshEligible()).isTrue()

        // Move horizontally right by 60px, slightly down by 5px
        webView.onTouchEvent(obtainMotionEvent(MotionEvent.ACTION_MOVE, 160f, 105f, downTime, downTime + 50))
        // Horizontal motion should lock out pull-to-refresh eligibility
        assertThat(webView.isPullToRefreshEligible()).isFalse()

        // Even if the gesture subsequently moves significantly downward, it remains locked
        webView.onTouchEvent(obtainMotionEvent(MotionEvent.ACTION_MOVE, 180f, 250f, downTime, downTime + 100))
        assertThat(webView.isPullToRefreshEligible()).isFalse()

        // Releasing touch resets the lock
        webView.onTouchEvent(obtainMotionEvent(MotionEvent.ACTION_UP, 180f, 250f, downTime, downTime + 150))
        assertThat(webView.isPullToRefreshEligible()).isTrue()
    }

    @Test
    fun jestkSwipeRefreshLayout_horizontalSwipe_doesNotInterceptTouch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val swipeRefresh = JestkSwipeRefreshLayout(context)
        val child = NestedWebView(context)
        swipeRefresh.addView(child)

        val downTime = SystemClock.uptimeMillis()
        val down = obtainMotionEvent(MotionEvent.ACTION_DOWN, 100f, 100f, downTime, downTime)
        swipeRefresh.onInterceptTouchEvent(down)

        // Move horizontally right by 80px, down by 10px
        val move = obtainMotionEvent(MotionEvent.ACTION_MOVE, 180f, 110f, downTime, downTime + 50)
        val intercepted = swipeRefresh.onInterceptTouchEvent(move)

        assertThat(intercepted).isFalse()
    }

    @Test
    fun jestkSwipeRefreshLayout_verticalPullDown_canInterceptWhenAppropriate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val swipeRefresh = JestkSwipeRefreshLayout(context)
        val child = NestedWebView(context)
        swipeRefresh.addView(child)

        val downTime = SystemClock.uptimeMillis()
        val down = obtainMotionEvent(MotionEvent.ACTION_DOWN, 100f, 100f, downTime, downTime)
        swipeRefresh.onInterceptTouchEvent(down)

        // Move purely vertically down by 80px
        val move = obtainMotionEvent(MotionEvent.ACTION_MOVE, 100f, 180f, downTime, downTime + 50)
        // Should not be rejected by our horizontal guard
        swipeRefresh.onInterceptTouchEvent(move)
    }

    @Test
    fun nestedWebView_verticalSwipe_maintainsPullToRefreshEligibility() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webView = NestedWebView(context)

        val downTime = SystemClock.uptimeMillis()
        webView.onTouchEvent(obtainMotionEvent(MotionEvent.ACTION_DOWN, 100f, 100f, downTime, downTime))
        assertThat(webView.isPullToRefreshEligible()).isTrue()

        // Move purely down by 50px
        webView.onTouchEvent(obtainMotionEvent(MotionEvent.ACTION_MOVE, 100f, 150f, downTime, downTime + 50))
        assertThat(webView.isPullToRefreshEligible()).isTrue()

        webView.onTouchEvent(obtainMotionEvent(MotionEvent.ACTION_UP, 100f, 150f, downTime, downTime + 100))
        assertThat(webView.isPullToRefreshEligible()).isTrue()
    }
}

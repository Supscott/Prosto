package com.jestk.browser.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Custom SwipeRefreshLayout subclass designed to prevent false-positive reload triggers:
 * 1. Declines intercept for multi-touch gestures (e.g. pinch-to-zoom).
 * 2. Declines intercept when motion is primarily horizontal (dx > touchSlop && dx > abs(dy)).
 */
class JestkSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    /**
     * In child-driven nested scrolling (Chrome-like browser model), the parent
     * SwipeRefreshLayout never intercepts touch events directly via touch-slop.
     * All touch events flow to the NestedWebView child, which dispatches unconsumed
     * scroll deltas upward only when strict browser pull-to-refresh conditions are satisfied.
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return false
    }
}

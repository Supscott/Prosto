package com.jestk.browser.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.jestk.browser.engine.NestedWebView

/**
 * Custom SwipeRefreshLayout subclass designed to prevent false-positive reload triggers:
 * 1. Declines intercept for multi-touch gestures (e.g. pinch-to-zoom).
 * 2. Declines intercept when motion is primarily horizontal (dx > touchSlop && dx > abs(dy)).
 * 3. Enforces child scrollability and NestedWebView pull-to-refresh eligibility in canChildScrollUp().
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

    override fun canChildScrollUp(): Boolean {
        if (super.canChildScrollUp()) {
            return true
        }
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is NestedWebView) {
                return !child.isPullToRefreshEligible() || child.canScrollVertically(-1) || child.scrollY > 0
            }
        }
        val target: View? = getChildAt(0)
        return target != null && (target.canScrollVertically(-1) || target.scrollY > 0)
    }
}

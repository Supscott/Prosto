package com.jestk.browser.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.jestk.browser.engine.NestedWebView
import kotlin.math.abs
import kotlin.math.max

/**
 * Custom SwipeRefreshLayout subclass designed to prevent false-positive reload triggers:
 * 1. Declines intercept for multi-touch gestures (e.g. pinch-to-zoom).
 * 2. Declines intercept when motion is primarily horizontal (dx > touchSlop && dx > abs(dy)).
 * 3. Enforces child scrollability and NestedWebView pull-to-refresh eligibility in canChildScrollUp().
 * 4. Dispatches haptic feedback exactly when pull distance reaches the refresh trigger threshold.
 */
class JestkSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    /** Callback invoked when pull distance reaches the threshold where releasing triggers refresh. */
    var onRefreshTriggerReached: (() -> Unit)? = null

    private var currentUnconsumed: Float = 0f
    private var hasTriggeredRefreshHaptic: Boolean = false

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
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is NestedWebView) {
                return !child.isPullToRefreshEligible() || child.canScrollVertically(-1) || child.scrollY > 0
            }
        }
        val target: View? = getChildAt(0)
        return target != null && (target.canScrollVertically(-1) || target.scrollY > 0)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, consumed)
        handleNestedScrollForHaptic(dyUnconsumed)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int
    ) {
        super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type)
        handleNestedScrollForHaptic(dyUnconsumed)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        super.onNestedPreScroll(target, dx, dy, consumed, type)
        if (dy > 0 && currentUnconsumed > 0f) {
            currentUnconsumed = max(0f, currentUnconsumed - dy)
            if (currentUnconsumed < refreshThreshold) {
                hasTriggeredRefreshHaptic = false
            }
        }
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        super.onStopNestedScroll(target, type)
        resetHapticState()
    }

    override fun onStopNestedScroll(target: View) {
        super.onStopNestedScroll(target)
        resetHapticState()
    }

    private val refreshThreshold: Float
        get() = progressViewEndOffset.toFloat()

    private fun handleNestedScrollForHaptic(dyUnconsumed: Int) {
        if (dyUnconsumed < 0 && !canChildScrollUp()) {
            currentUnconsumed += abs(dyUnconsumed)
            if (currentUnconsumed >= refreshThreshold && !hasTriggeredRefreshHaptic) {
                hasTriggeredRefreshHaptic = true
                onRefreshTriggerReached?.invoke()
            }
        }
    }

    private fun resetHapticState() {
        currentUnconsumed = 0f
        hasTriggeredRefreshHaptic = false
    }
}

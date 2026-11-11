package com.jestk.browser.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
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

    /**
     * The container of the tab WebViews. Found by type, never by index: the spinner circle is a
     * child too, added first, and SwipeRefreshLayout moves it with bringToFront() only once the
     * spinner shows. Index 0 is the circle until then.
     */
    val tabsContainer: FrameLayout?
        get() = (0 until childCount).map { getChildAt(it) }.firstOrNull { it is FrameLayout } as? FrameLayout

    /** Callback invoked when pull distance reaches the threshold where releasing triggers refresh. */
    var onRefreshTriggerReached: (() -> Unit)? = null

    private var currentUnconsumed: Float = 0f
    private var hasTriggeredRefreshHaptic: Boolean = false

    private val indicatorState = PullRefreshIndicatorState()
    private val reloadStartTimeout = Runnable {
        if (indicatorState.onReloadStartTimeout()) isRefreshing = false
    }

    /** Installs [onRefresh] so that only the pull gesture shows the spinner. */
    fun setOnPullRefreshListener(onRefresh: () -> Unit) {
        setOnRefreshListener {
            indicatorState.onPullRefresh()
            removeCallbacks(reloadStartTimeout)
            postDelayed(reloadStartTimeout, PullRefreshIndicatorState.RELOAD_START_TIMEOUT_MS)
            onRefresh()
        }
    }

    /**
     * Mirrors the page loading state. Page loads never show the spinner on their own; a spinner
     * shown by a pull hides once the reload it started has finished.
     */
    fun syncWithPageLoading(isLoading: Boolean) {
        if (isLoading) removeCallbacks(reloadStartTimeout)
        if (indicatorState.onPageLoadingChanged(isLoading) && isRefreshing) {
            isRefreshing = false
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(reloadStartTimeout)
        super.onDetachedFromWindow()
    }

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

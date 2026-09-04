package com.jestk.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import kotlin.math.abs
import kotlin.math.max

/**
 * A WebView subclass implementing NestedScrollingChild3.
 * Enables AndroidView to seamlessly dispatch scroll deltas to Compose nestedScroll
 * connections and pull-to-refresh parent layouts.
 */
class NestedWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr), NestedScrollingChild3 {

    companion object {
        const val PULL_TO_REFRESH_SLOP_DP = 40f
    }

    var loadedUrl: String? = null

    override fun loadUrl(url: String) {
        loadedUrl = url
        super.loadUrl(url)
    }

    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        loadedUrl = url
        super.loadUrl(url, additionalHttpHeaders)
    }

    private val childHelper: NestedScrollingChildHelper = NestedScrollingChildHelper(this).apply {
        isNestedScrollingEnabled = true
    }

    private var initialDownX: Float = 0f
    private var initialDownY: Float = 0f
    private var lastMotionY: Int = 0
    private var isLockedHorizontal: Boolean = false
    private var isPullToRefreshActive: Boolean = false
    private var accumulatedPullDistance: Float = 0f

    private val touchSlop: Int by lazy {
        ViewConfiguration.get(context).scaledTouchSlop
    }

    private val pullThresholdPx: Float by lazy {
        PULL_TO_REFRESH_SLOP_DP * resources.displayMetrics.density
    }

    fun isPullToRefreshEligible(): Boolean {
        return !isLockedHorizontal && !canScrollVertically(-1) && scrollY <= 0
    }

    private val scrollConsumed = IntArray(2)
    private val scrollOffset = IntArray(2)

    init {
        isNestedScrollingEnabled = true
        isFocusable = true
        isFocusableInTouchMode = true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val y = event.y.toInt()

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (!hasFocus()) {
                    requestFocus()
                }
                initialDownX = event.x
                initialDownY = event.y
                lastMotionY = y
                isLockedHorizontal = false
                isPullToRefreshActive = false
                accumulatedPullDistance = 0f
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount > 1) {
                    isLockedHorizontal = true
                    isPullToRefreshActive = false
                    accumulatedPullDistance = 0f
                }
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) {
                    isLockedHorizontal = true
                    isPullToRefreshActive = false
                    accumulatedPullDistance = 0f
                }

                val totalDx = event.x - initialDownX
                val totalDy = event.y - initialDownY
                val absDx = abs(totalDx)
                val absDy = abs(totalDy)

                if (!isLockedHorizontal && !isPullToRefreshActive) {
                    if (absDx > touchSlop && absDx > absDy) {
                        isLockedHorizontal = true
                    }
                }

                var deltaY = lastMotionY - y

                scrollConsumed[0] = 0
                scrollConsumed[1] = 0
                scrollOffset[0] = 0
                scrollOffset[1] = 0

                // Step 1: Dispatch pre-scroll to Compose parent / SwipeRefreshLayout
                if (dispatchNestedPreScroll(0, deltaY, scrollConsumed, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                    deltaY -= scrollConsumed[1]
                }
                lastMotionY = y - scrollOffset[1]

                // If parent consumed upward motion while pull-to-refresh was active, reduce accumulated distance
                if (scrollConsumed[1] > 0 && accumulatedPullDistance > 0f) {
                    accumulatedPullDistance = max(0f, accumulatedPullDistance - scrollConsumed[1])
                    if (accumulatedPullDistance < pullThresholdPx) {
                        isPullToRefreshActive = false
                    }
                }

                val oldScrollY = scrollY
                val handled = super.onTouchEvent(event)
                val newScrollY = scrollY
                val dyConsumed = newScrollY - oldScrollY
                val dyUnconsumed = deltaY - dyConsumed

                val canScrollUp = canScrollVertically(-1)
                val canScrollDown = canScrollVertically(1)

                if (canScrollUp || scrollY > 0) {
                    accumulatedPullDistance = 0f
                    isPullToRefreshActive = false
                }

                // Step 2: Dispatch downward unconsumed scroll ONLY when at the very top of webpage (!canScrollVertically(-1))
                // and ONLY after a longer swipe down threshold has been reached, and gesture is not horizontal.
                var unconsumedToDispatch = 0
                if (!canScrollUp && dyUnconsumed < 0) {
                    if (!isLockedHorizontal) {
                        val pullDelta = -dyUnconsumed.toFloat()
                        accumulatedPullDistance += pullDelta
                        if (accumulatedPullDistance >= pullThresholdPx) {
                            if (!isPullToRefreshActive) {
                                isPullToRefreshActive = true
                                val excess = accumulatedPullDistance - pullThresholdPx
                                unconsumedToDispatch = -(excess.toInt().coerceAtLeast(1))
                            } else {
                                unconsumedToDispatch = dyUnconsumed
                            }
                        }
                    }
                } else if (!canScrollDown && dyUnconsumed > 0) {
                    unconsumedToDispatch = dyUnconsumed
                } else if (dyUnconsumed > 0 && accumulatedPullDistance > 0f) {
                    accumulatedPullDistance = max(0f, accumulatedPullDistance - dyUnconsumed)
                    if (accumulatedPullDistance < pullThresholdPx) {
                        isPullToRefreshActive = false
                    }
                }

                if (unconsumedToDispatch != 0) {
                    if (dispatchNestedScroll(0, dyConsumed, 0, unconsumedToDispatch, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                        lastMotionY -= scrollOffset[1]
                    }
                } else if (dyConsumed != 0) {
                    dispatchNestedScroll(0, dyConsumed, 0, 0, scrollOffset, ViewCompat.TYPE_TOUCH)
                }

                return handled
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = super.onTouchEvent(event)
                isLockedHorizontal = false
                isPullToRefreshActive = false
                accumulatedPullDistance = 0f
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
                return handled
            }
            else -> {
                return super.onTouchEvent(event)
            }
        }
    }

    // NestedScrollingChild3 delegations
    override fun startNestedScroll(axes: Int, type: Int): Boolean =
        childHelper.startNestedScroll(axes, type)

    override fun stopNestedScroll(type: Int) =
        childHelper.stopNestedScroll(type)

    override fun hasNestedScrollingParent(type: Int): Boolean =
        childHelper.hasNestedScrollingParent(type)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean = childHelper.dispatchNestedScroll(
        dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type
    )

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean = childHelper.dispatchNestedPreScroll(
        dx, dy, consumed, offsetInWindow, type
    )

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
        consumed: IntArray
    ) {
        childHelper.dispatchNestedScroll(
            dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed
        )
    }

    // NestedScrollingChild2 / NestedScrollingChild backward-compatible delegations
    override fun setNestedScrollingEnabled(enabled: Boolean) {
        childHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean =
        childHelper.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int): Boolean =
        childHelper.startNestedScroll(axes)

    override fun stopNestedScroll() =
        childHelper.stopNestedScroll()

    override fun hasNestedScrollingParent(): Boolean =
        childHelper.hasNestedScrollingParent()

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?
    ): Boolean = childHelper.dispatchNestedScroll(
        dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow
    )

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?
    ): Boolean = childHelper.dispatchNestedPreScroll(
        dx, dy, consumed, offsetInWindow
    )

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean =
        childHelper.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean =
        childHelper.dispatchNestedPreFling(velocityX, velocityY)
}

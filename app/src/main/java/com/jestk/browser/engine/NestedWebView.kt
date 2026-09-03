package com.jestk.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat

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

    private var lastMotionY: Int = 0
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
                lastMotionY = y
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                var deltaY = lastMotionY - y

                scrollConsumed[0] = 0
                scrollConsumed[1] = 0
                scrollOffset[0] = 0
                scrollOffset[1] = 0

                // Step 1: Dispatch pre-scroll to Compose parent
                if (dispatchNestedPreScroll(0, deltaY, scrollConsumed, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                    deltaY -= scrollConsumed[1]
                }
                lastMotionY = y - scrollOffset[1]

                val oldScrollY = scrollY
                val handled = super.onTouchEvent(event)
                val newScrollY = scrollY
                val dyConsumed = newScrollY - oldScrollY
                val dyUnconsumed = deltaY - dyConsumed

                // Step 2: Dispatch downward unconsumed scroll ONLY when at the very top of webpage (!canScrollVertically(-1))
                // This prevents pull-to-refresh from hijacking scrolls in the middle of a web page
                val canScrollUp = canScrollVertically(-1)
                val canScrollDown = canScrollVertically(1)

                val unconsumedToDispatch = when {
                    !canScrollUp && dyUnconsumed < 0 -> dyUnconsumed
                    !canScrollDown && dyUnconsumed > 0 -> dyUnconsumed
                    else -> 0
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

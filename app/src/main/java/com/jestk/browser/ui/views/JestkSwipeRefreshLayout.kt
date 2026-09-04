package com.jestk.browser.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.abs

/**
 * Custom SwipeRefreshLayout subclass designed to prevent false-positive reload triggers:
 * 1. Declines intercept for multi-touch gestures (e.g. pinch-to-zoom).
 * 2. Declines intercept when motion is primarily horizontal (dx > touchSlop && dx > abs(dy)).
 */
class JestkSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    private var initialDownX = 0f
    private var initialDownY = 0f
    private var isDeclined = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (childCount == 0) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialDownX = ev.x
                initialDownY = ev.y
                isDeclined = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ev.pointerCount > 1) {
                    isDeclined = true
                    return false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDeclined) return false
                val dx = abs(ev.x - initialDownX)
                val dy = ev.y - initialDownY
                // If moving horizontally or upwards, do not intercept
                if (dx > touchSlop && dx > abs(dy)) {
                    isDeclined = true
                    return false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDeclined = false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}

package com.jestk.browser.ui.views

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

class FullscreenSwipeLayout(
    context: Context,
    private val onSwipeDown: () -> Unit
) : FrameLayout(context) {

    private var startX = 0f
    private var startY = 0f
    private var hasTriggered = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val swipeThreshold = 60f * context.resources.displayMetrics.density

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Do not allow child views to disallow intercepting downward swipe gestures
        super.requestDisallowInterceptTouchEvent(false)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                hasTriggered = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                if (!hasTriggered && dy > swipeThreshold && dy > abs(dx) * 1.2f) {
                    hasTriggered = true
                    onSwipeDown()
                    val cancelEvent = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancelEvent)
                    cancelEvent.recycle()
                    return true
                }
                if (hasTriggered) {
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                hasTriggered = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                hasTriggered = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                if (!hasTriggered && dy > touchSlop && dy > abs(dx) * 1.2f) {
                    if (dy > swipeThreshold) {
                        hasTriggered = true
                        onSwipeDown()
                        return true
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                hasTriggered = false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                hasTriggered = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                if (!hasTriggered && dy > swipeThreshold && dy > abs(dx) * 1.2f) {
                    hasTriggered = true
                    onSwipeDown()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                hasTriggered = false
            }
        }
        return true
    }
}

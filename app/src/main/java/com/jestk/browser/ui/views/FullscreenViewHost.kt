package com.jestk.browser.ui.views

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Holds the view that Chromium hands to `WebChromeClient.onShowCustomView`.
 *
 * Chromium detaches the page from the WebView before it calls onShowCustomView, so the page is
 * reported as hidden until the fullscreen view is attached to a window. Players such as YouTube
 * tear down their MediaSource on that `visibilitychange`, and the video reloads and pauses.
 * [attach] therefore runs synchronously inside the callback (like DuckDuckGo's goFullScreen),
 * into a [container] that the UI keeps attached at all times.
 */
class FullscreenViewHost(context: Context) {
    val container: FrameLayout = FrameLayout(context)

    fun attach(view: View) {
        if (view.parent === container) return
        (view.parent as? ViewGroup)?.removeView(view)
        container.removeAllViews()
        container.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        view.keepScreenOn = true
        if (view is ViewGroup) {
            view.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        view.requestFocus()
    }

    fun detach() {
        container.removeAllViews()
    }
}

/** Implemented by the Activity that shows tab WebViews, so WebView callbacks can reach the host. */
interface FullscreenViewHostOwner {
    val fullscreenViewHost: FullscreenViewHost
}

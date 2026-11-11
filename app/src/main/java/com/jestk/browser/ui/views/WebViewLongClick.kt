package com.jestk.browser.ui.views

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.webkit.WebView
import com.jestk.browser.engine.NestedWebView
import com.jestk.browser.ui.viewmodel.BrowserUiEvent

/**
 * Opens the link or image context menu on a long press inside [this] WebView, anchored at the
 * touch position.
 */
internal fun WebView.installLongClickContextMenu(onEvent: (BrowserUiEvent) -> Unit) {
    val wv = this
    wv.isLongClickable = true
    var fallbackTouchX = 0f
    var fallbackTouchY = 0f
    var fallbackRawX = 0f
    var fallbackRawY = 0f

    @SuppressLint("ClickableViewAccessibility")
    wv.setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
            fallbackTouchX = event.x
            fallbackTouchY = event.y
            fallbackRawX = event.rawX
            fallbackRawY = event.rawY
        }
        false
    }

    wv.setOnLongClickListener { v ->
        val targetWebView = v as? WebView ?: return@setOnLongClickListener false
        val hitTest = targetWebView.hitTestResult ?: return@setOnLongClickListener false
        val nested = targetWebView as? NestedWebView

        val location = IntArray(2)
        targetWebView.getLocationOnScreen(location)
        val relX = nested?.lastTouchX?.takeIf { it > 0f } ?: fallbackTouchX
        val relY = nested?.lastTouchY?.takeIf { it > 0f } ?: fallbackTouchY
        val anchorX = if (relX > 0f) location[0] + relX else fallbackRawX.takeIf { it > 0f }
        val anchorY = if (relY > 0f) location[1] + relY else fallbackRawY.takeIf { it > 0f }

        when (hitTest.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                val url = hitTest.extra
                if (!url.isNullOrBlank()) {
                    onEvent(BrowserUiEvent.ShowLinkContextMenu(url = url, anchorX = anchorX, anchorY = anchorY))
                    true
                } else {
                    val handler = object : Handler(Looper.getMainLooper()) {
                        override fun handleMessage(msg: Message) {
                            val nodeUrl = msg.data?.getString("url")
                            if (!nodeUrl.isNullOrBlank()) {
                                onEvent(BrowserUiEvent.ShowLinkContextMenu(url = nodeUrl, anchorX = anchorX, anchorY = anchorY))
                            }
                        }
                    }
                    targetWebView.requestFocusNodeHref(handler.obtainMessage())
                    true
                }
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                val imageUrl = hitTest.extra
                if (!imageUrl.isNullOrBlank()) {
                    onEvent(BrowserUiEvent.ShowImageContextMenu(imageUrl = imageUrl, anchorX = anchorX, anchorY = anchorY))
                    true
                } else false
            }
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val fallbackUrl = hitTest.extra
                val handler = object : Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message) {
                        val imageUrl = msg.data?.getString("src")
                        val nodeUrl = msg.data?.getString("url")
                        val targetLink = if (!nodeUrl.isNullOrBlank()) nodeUrl else fallbackUrl
                        if (!imageUrl.isNullOrBlank()) {
                            onEvent(
                                BrowserUiEvent.ShowImageContextMenu(
                                    imageUrl = imageUrl,
                                    linkUrl = targetLink,
                                    anchorX = anchorX,
                                    anchorY = anchorY
                                )
                            )
                        } else if (!targetLink.isNullOrBlank()) {
                            onEvent(BrowserUiEvent.ShowLinkContextMenu(url = targetLink, anchorX = anchorX, anchorY = anchorY))
                        }
                    }
                }
                targetWebView.requestFocusNodeHref(handler.obtainMessage())
                true
            }
            1 -> { // Deprecated HitTestResult.ANCHOR_TYPE
                val url = hitTest.extra
                if (!url.isNullOrBlank()) {
                    onEvent(BrowserUiEvent.ShowLinkContextMenu(url = url, anchorX = anchorX, anchorY = anchorY))
                    true
                } else false
            }
            6 -> { // Deprecated HitTestResult.IMAGE_ANCHOR_TYPE
                val fallbackUrl = hitTest.extra
                val handler = object : Handler(Looper.getMainLooper()) {
                    override fun handleMessage(msg: Message) {
                        val imageUrl = msg.data?.getString("src")
                        val nodeUrl = msg.data?.getString("url")
                        val targetLink = if (!nodeUrl.isNullOrBlank()) nodeUrl else fallbackUrl
                        if (!imageUrl.isNullOrBlank()) {
                            onEvent(
                                BrowserUiEvent.ShowImageContextMenu(
                                    imageUrl = imageUrl,
                                    linkUrl = targetLink,
                                    anchorX = anchorX,
                                    anchorY = anchorY
                                )
                            )
                        } else if (!targetLink.isNullOrBlank()) {
                            onEvent(BrowserUiEvent.ShowLinkContextMenu(url = targetLink, anchorX = anchorX, anchorY = anchorY))
                        }
                    }
                }
                targetWebView.requestFocusNodeHref(handler.obtainMessage())
                true
            }
            else -> false
        }
    }
}

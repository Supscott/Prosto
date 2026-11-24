package com.jestk.browser.ui.views

import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.ui.viewmodel.BrowserUiState

/**
 * True while a full-screen surface (Home, Settings, Downloads, Tabs Overview, Workspaces overview,
 * Search overlay, clean video player) is drawn over the WebView page. The page is then invisible
 * to the user, so it must also be invisible to accessibility services.
 *
 * Partial overlays (pills, dock prompt cards, menus, the first-paint snapshot) leave the page
 * on screen and are deliberately not listed.
 */
fun isPageCoveredByOverlay(uiState: BrowserUiState): Boolean =
    uiState.activeOverlay != OverlayType.NONE ||
        uiState.isHomeContentVisible ||
        uiState.isSettingsVisible ||
        uiState.isDownloadsVisible ||
        uiState.cleanPlayerVideoId != null

/**
 * Drops the child nodes from this container's accessibility node. `NO_HIDE_DESCENDANTS` alone is
 * not enough: a fetch that includes not-important views (`uiautomator dump` without `--compressed`,
 * services with FLAG_INCLUDE_NOT_IMPORTANT_VIEWS) skips the importance check and still walks the
 * children, so the WebView's nodes stayed listed under Settings and Tabs Overview.
 */
private val hideChildrenDelegate =
    object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfo,
        ) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            val group = host as? ViewGroup ?: return
            for (i in 0 until group.childCount) info.removeChild(group.getChildAt(i))
        }
    }

/**
 * Hides or exposes every WebView below this container for accessibility. Set on the container, not
 * on a WebView, so a pooled WebView that is reparented here never keeps a stale flag.
 */
fun View.applyPageCoverage(covered: Boolean) {
    val mode =
        if (covered) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
    if (importantForAccessibility != mode) importantForAccessibility = mode
    // The setter only stores the reference; reading it back needs API 29, so assign every time.
    setAccessibilityDelegate(if (covered) hideChildrenDelegate else null)
}

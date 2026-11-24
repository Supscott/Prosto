package com.jestk.browser.ui.views

import android.view.View
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
}

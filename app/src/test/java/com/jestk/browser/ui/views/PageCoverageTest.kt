package com.jestk.browser.ui.views

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PageCoverageTest {
    private val pageShown = BrowserUiState(currentUrl = "https://example.com/")

    @Test
    fun pageShownWithoutOverlayIsNotCovered() {
        assertThat(isPageCoveredByOverlay(pageShown)).isFalse()
    }

    @Test
    fun fullScreenOverlaysCoverThePage() {
        listOf(
            OverlayType.HOME,
            OverlayType.SEARCH,
            OverlayType.TAB_GRID,
            OverlayType.SETTINGS,
            OverlayType.DOWNLOADS,
            OverlayType.WORKSPACES,
        ).forEach { overlay ->
            assertThat(isPageCoveredByOverlay(pageShown.copy(activeOverlay = overlay))).isTrue()
        }
    }

    @Test
    fun settingsAndDownloadsScreensCoverThePage() {
        assertThat(isPageCoveredByOverlay(pageShown.copy(activeScreen = ScreenType.SETTINGS))).isTrue()
        assertThat(isPageCoveredByOverlay(pageShown.copy(activeScreen = ScreenType.DOWNLOADS))).isTrue()
    }

    @Test
    fun homeAndCleanPlayerCoverThePage() {
        assertThat(isPageCoveredByOverlay(BrowserUiState())).isTrue()
        assertThat(isPageCoveredByOverlay(pageShown.copy(cleanPlayerVideoId = "abc"))).isTrue()
    }

    @Test
    fun partialOverlaysLeaveThePageExposed() {
        val withPills =
            pageShown.copy(
                isMenuExpanded = true,
                showUndoTabCloseSnackbar = true,
                showDownloadSnackbar = true,
            )
        assertThat(isPageCoveredByOverlay(withPills)).isFalse()
    }

    @Test
    fun applyPageCoverageTogglesAccessibilityMode() {
        val view = FrameLayout(ApplicationProvider.getApplicationContext())
        view.applyPageCoverage(true)
        assertThat(view.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
        view.applyPageCoverage(false)
        assertThat(view.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)
    }
}

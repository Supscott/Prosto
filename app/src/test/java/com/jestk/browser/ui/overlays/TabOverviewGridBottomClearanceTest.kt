package com.jestk.browser.ui.overlays

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** At scroll end the last row of tiles must end above the floating Close All / Done bar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabOverviewGridBottomClearanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun assertLastRowClearsBar(fontScale: Float) {
        val count = 10
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                    TabOverviewGrid(
                        uiState =
                            BrowserUiState(
                                tabs =
                                    (1..count).map {
                                        Tab(id = "$it", url = "https://example.com/$it", title = "Tab $it", isActive = it == 1)
                                    },
                                activeTabId = "1",
                            ),
                        onEvent = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("overview_grid").performScrollToIndex(count - 1)
        composeRule.waitForIdle()

        val lastCard = composeRule.onNodeWithTag("tab_card_$count").getBoundsInRoot()
        val bar = composeRule.onNodeWithTag(TAB_OVERVIEW_BOTTOM_BAR_TAG).getBoundsInRoot()
        assertThat(lastCard.bottom.value).isAtMost(bar.top.value - TAB_OVERVIEW_BOTTOM_BAR_GAP.value)
    }

    @Test
    fun lastRowClearsTheBottomBar() = assertLastRowClearsBar(fontScale = 1.0f)

    @Test
    fun lastRowClearsTheBottomBarAtLargeFont() = assertLastRowClearsBar(fontScale = 1.3f)
}

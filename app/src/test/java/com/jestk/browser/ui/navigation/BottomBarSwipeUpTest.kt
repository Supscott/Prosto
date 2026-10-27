package com.jestk.browser.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.VibrationFeedback
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class BottomBarSwipeUpTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<BrowserUiEvent>()
    private val testVibration = object : VibrationFeedback {
        override fun tick() {}
        override fun confirm() {}
    }

    @Test
    fun swipeUpOnDock_triggersOpenSearchEvent() {
        events.clear()
        val sampleTabs = listOf(
            Tab(id = "tab1", url = "https://example.com", title = "Example", isActive = true),
            Tab(id = "tab2", url = "https://duckduckgo.com", title = "DDG", isActive = false),
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalVibrationFeedback provides testVibration) {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        UnifiedBottomBar(
                            uiState = BrowserUiState(
                                tabs = sampleTabs,
                                activeTabId = "tab1",
                                currentUrl = "https://example.com",
                                pageTitle = "Example",
                            ),
                            onEvent = { events += it },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("dock_capsule").performTouchInput {
            swipeUp()
        }

        val searchEvents = events.filterIsInstance<BrowserUiEvent.OpenSearch>()
        assertThat(searchEvents).isNotEmpty()
        assertThat(searchEvents.first().openInNewTab).isTrue()
    }
}

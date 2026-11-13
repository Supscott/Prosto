package com.jestk.browser.ui.views

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.R
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.HomeUiEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class HomeTutorialCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun show(
        state: BrowserUiState,
        events: MutableList<BrowserUiEvent> = mutableListOf(),
    ) {
        composeRule.setContent {
            JestkBrowserTheme {
                HomeScreen(uiState = state, onEvent = { events.add(it) })
            }
        }
    }

    @Test
    fun freshInstall_showsTutorialCardAndNoSearchCapsule() {
        val events = mutableListOf<BrowserUiEvent>()
        show(BrowserUiState(isDefaultBrowser = false), events)

        composeRule.onNodeWithTag("tutorial_card").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_tutorial_card_action)).performClick()
        assertThat(events.last()).isEqualTo(HomeUiEvent.OpenTutorial)

        composeRule.onNodeWithContentDescription(context.getString(R.string.dismiss)).performClick()
        assertThat(events.last()).isEqualTo(HomeUiEvent.DismissTutorialCard)
    }

    @Test
    fun completedTutorial_hidesCard() {
        show(BrowserUiState(settings = BrowserSettings(isTutorialCompleted = true)))

        composeRule.onNodeWithTag("tutorial_card").assertDoesNotExist()
    }

    @Test
    fun protectionCard_isHiddenWhileStatsAreZero() {
        show(BrowserUiState())

        composeRule.onNodeWithText(context.getString(R.string.home_protection_active)).assertDoesNotExist()
    }

    @Test
    fun protectionCard_showsOnceThereAreBlockedRequests() {
        show(BrowserUiState(blockedAdsCount = 12L))

        composeRule.onNodeWithText(context.getString(R.string.home_protection_active)).assertIsDisplayed()
    }

    @Test
    fun protectionCard_staysHiddenWhenDisabledInSettings() {
        show(BrowserUiState(blockedAdsCount = 12L, settings = BrowserSettings(isHomePrivacyCardEnabled = false)))

        composeRule.onNodeWithText(context.getString(R.string.home_protection_active)).assertDoesNotExist()
    }
}

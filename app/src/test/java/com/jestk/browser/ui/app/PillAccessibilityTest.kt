package com.jestk.browser.ui.app

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jestk.browser.R
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.overlays.PopupBlockedPill
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * A notice pill is one node in the accessibility tree that carries its text and is a polite live
 * region, so TalkBack announces it and `uiautomator dump` lists it (PRO-190).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PillAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private val politeLiveRegion =
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    @Test
    fun favoriteAddedPillIsALiveRegionWithItsText() {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FavoriteNoticePill(added = true, isDark = false, accent = null, swipeKey = 1, onDismiss = {})
            }
        }

        composeRule.onNode(hasText(string(R.string.favorite_added_pill))).assert(politeLiveRegion)
    }

    @Test
    fun favoriteRemovedPillIsALiveRegionWithItsText() {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                FavoriteNoticePill(added = false, isDark = false, accent = null, swipeKey = 1, onDismiss = {})
            }
        }

        composeRule.onNode(hasText(string(R.string.favorite_removed_pill))).assert(politeLiveRegion)
    }

    @Test
    fun pillWithAButtonAnnouncesTheMessageAndKeepsTheButtonReachable() {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                PopupBlockedPill(isDark = false, promptKey = 1, accent = null, onAllow = {}, onDismiss = {})
            }
        }

        val pill = composeRule.onNodeWithText(string(R.string.popup_blocked_message))
        pill.assert(politeLiveRegion)
        pill.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        composeRule.onNodeWithText(string(R.string.popup_blocked_allow_site)).assertHasClickAction()
    }
}

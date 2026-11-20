package com.jestk.browser.ui.overlays

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The last story closes only through Done or by making Prosto the default browser. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class TutorialStoriesScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var completed = 0
    private var closed = 0

    private fun show(isDefaultBrowser: Boolean = false) {
        composeRule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                TutorialStoriesScreen(
                    isDark = false,
                    isDefaultBrowser = isDefaultBrowser,
                    onDefaultBrowserChanged = {},
                    onClose = { closed++ },
                    onComplete = { completed++ },
                )
            }
        }
    }

    private fun tapForward(times: Int) {
        repeat(times) {
            composeRule.onNodeWithTag("tutorial_stories").performTouchInput {
                click(androidx.compose.ui.geometry.Offset(width * 0.8f, height * 0.5f))
            }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun incognitoStoryRendersAtLargeFontScale() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(3f, 1.3f),
            ) {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    TutorialStoriesScreen(false, false, {}, {}, {})
                }
            }
        }
        // Stories auto-advance with the clock, so step it by hand: tap to the story, then run it to its menu.
        repeat(TutorialSlides.indexOfFirst { it.title == com.jestk.browser.R.string.tutorial_incognito_title }) {
            composeRule.onNodeWithTag("tutorial_stories").performTouchInput {
                click(androidx.compose.ui.geometry.Offset(width * 0.8f, height * 0.5f))
            }
            composeRule.mainClock.advanceTimeBy(100)
        }
        composeRule.mainClock.advanceTimeBy(3000)
        val view = composeRule.activity.window.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file = java.io.File("build/tutorial_incognito_story_large_font.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun lastStoryIgnoresTapsSwipesAndHasNoCloseButton() {
        show()
        tapForward(TutorialSlides.lastIndex + 2)
        composeRule.onNodeWithTag("tutorial_stories").performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertThat(completed).isEqualTo(0)
        assertThat(closed).isEqualTo(0)
        composeRule.onNodeWithContentDescription("Close tutorial").assertDoesNotExist()
        composeRule.onNodeWithTag("tutorial_make_default").assertIsDisplayed()
    }

    @Test
    fun doneCompletesTheTour() {
        show()
        tapForward(TutorialSlides.lastIndex)
        composeRule.onNodeWithTag("tutorial_done").performClick()
        assertThat(completed).isEqualTo(1)
    }

    @Test
    fun earlierStoriesStillCloseWithTheCloseButton() {
        // Hold the clock: with auto-advance the stories would play through to the last one.
        composeRule.mainClock.autoAdvance = false
        show()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription("Close tutorial").performClick()
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun alreadyDefaultShowsOnlyDone() {
        show(isDefaultBrowser = true)
        tapForward(TutorialSlides.lastIndex)
        composeRule.onNodeWithTag("tutorial_make_default").assertDoesNotExist()
        composeRule.onNodeWithTag("tutorial_done").assertIsDisplayed()
    }
}

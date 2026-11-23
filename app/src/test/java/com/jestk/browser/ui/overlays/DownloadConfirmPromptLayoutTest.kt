package com.jestk.browser.ui.overlays

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.components.LocalDockPromptAnimateEnter
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The details line of the download card (size, separator, host) at the font scales the user's phone
 * uses: the size must stay whole on one line, and only the host may be cut.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadConfirmPromptLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val size = "128.0 MB"
    private val host = "very-long-subdomain.downloads.some-long-host.example.organization.test"

    private fun setCard(fontScale: Float) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalDockPromptAnimateEnter provides false,
            ) {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    DownloadConfirmPrompt(
                        fileName = "quarterly-financial-report-final-version-with-all-appendices-2026-11-22.pdf",
                        size = size,
                        host = host,
                        isDark = false,
                        onDownload = {},
                        onCancel = {},
                    )
                }
            }
        }
    }

    // The details row clears its semantics (one TalkBack stop), so its Texts exist only unmerged.
    private fun node(text: String) = composeRule.onNodeWithText(text, useUnmergedTree = true)

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertThat(action).isNotNull()
        assertThat(action!!(results)).isTrue()
        return results.single()
    }

    private fun assertSizeWholeAndHostCutOnOneLine(fontScale: Float) {
        setCard(fontScale)
        composeRule.waitForIdle()

        val sizeNode = node(size)
        val hostNode = node(host)
        val sizeLayout = sizeNode.layout()
        val hostLayout = hostNode.layout()

        // The size: one line, no ellipsis, nothing cut, and the node is as wide as its text.
        assertThat(sizeLayout.lineCount).isEqualTo(1)
        assertThat(sizeLayout.isLineEllipsized(0)).isFalse()
        // didOverflowWidth is not asserted: with softWrap = false it is true even for a whole line.
        assertThat(sizeLayout.didOverflowHeight).isFalse()
        assertThat(sizeNode.fetchSemanticsNode().size.width).isAtLeast(sizeLayout.size.width)

        // The host: still on one line, and still cut (the card is narrower than the host).
        assertThat(hostLayout.lineCount).isEqualTo(1)
        assertThat(hostLayout.isLineEllipsized(0)).isTrue()

        // Size and host share one line and do not overlap.
        val sizeBounds = sizeNode.getBoundsInRoot()
        val hostBounds = hostNode.getBoundsInRoot()
        assertThat(hostBounds.top).isEqualTo(sizeBounds.top)
        assertThat(hostBounds.left).isAtLeast(sizeBounds.right)
    }

    @Test
    fun sizeStaysWholeAndHostIsCutAtFontScale1_15() = assertSizeWholeAndHostCutOnOneLine(1.15f)

    @Test
    fun sizeStaysWholeAndHostIsCutAtFontScale1_3() = assertSizeWholeAndHostCutOnOneLine(1.3f)
}

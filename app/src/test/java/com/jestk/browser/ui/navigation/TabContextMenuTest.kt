package com.jestk.browser.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.util.LocalVibrationFeedback
import com.jestk.browser.util.VibrationFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabContextMenuTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val testVibration = object : VibrationFeedback {
        override fun tick() {}
        override fun confirm() {}
    }

    @Test
    fun testTabContextMenuPositionProvider_activePillAndInactiveCircleAlignAtSameElevation() {
        val density = Density(3f)
        val provider = TabContextMenuPositionProvider(density = density)

        val windowSize = IntSize(1233, 2673)
        val popupContentSize = IntSize(516, 600)

        // Active pill (height 42dp = 126px), top at 2529px
        val pillAnchor = IntRect(100, 2529, 600, 2655)
        val pillPos = provider.calculatePosition(
            anchorBounds = pillAnchor,
            windowSize = windowSize,
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = popupContentSize,
        )
        val pillBottom = pillPos.y + popupContentSize.height

        // Inactive circle (height 38dp = 114px, centered in 50dp dock, so top is 2535px, 6px lower)
        val circleAnchor = IntRect(700, 2535, 814, 2649)
        val circlePos = provider.calculatePosition(
            anchorBounds = circleAnchor,
            windowSize = windowSize,
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = popupContentSize,
        )
        val circleBottom = circlePos.y + popupContentSize.height

        // Both must align to the exact same bottom elevation above the dock
        assertEquals("Active pill and inactive circle context menus should have identical bottom Y", pillBottom, circleBottom)
        // Expected targetBottomY = 2529 - (14 * 3) = 2487
        assertEquals(2487, pillBottom)
    }

    @Test
    fun testTabContextMenuPositionProvider_clampsHorizontalBoundsWithinWindow() {
        val density = Density(3f)
        val provider = TabContextMenuPositionProvider(density = density, margin = 12.dp)

        val windowSize = IntSize(1233, 2673)
        val popupContentSize = IntSize(516, 600)
        val marginPx = (12 * 3) // 36px

        // Anchor at far left edge
        val leftAnchor = IntRect(0, 2529, 100, 2655)
        val leftPos = provider.calculatePosition(leftAnchor, windowSize, LayoutDirection.Ltr, popupContentSize)
        assertEquals(marginPx, leftPos.x)

        // Anchor at far right edge
        val rightAnchor = IntRect(1200, 2529, 1233, 2655)
        val rightPos = provider.calculatePosition(rightAnchor, windowSize, LayoutDirection.Ltr, popupContentSize)
        assertEquals(windowSize.width - popupContentSize.width - marginPx, rightPos.x)
    }

    @Test
    fun tabContextMenu_whenOpenedOnDockTab_showsMenuItems() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVibrationFeedback provides testVibration) {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ActiveTabPill(
                                title = "Example",
                                domain = "example.com",
                                url = "https://example.com",
                                faviconUrl = null,
                                isHome = false,
                                backgroundColor = androidx.compose.ui.graphics.Color.White,
                                isDark = false,
                                onOpenSearch = {},
                                onClose = {},
                                onAddFavorite = { _, _ -> },
                                onShare = {},
                                menuExpanded = true,
                                onMenuDismiss = {},
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Close tab").assertExists()
        composeTestRule.onNodeWithText("Copy URL").assertExists()
        composeTestRule.onNodeWithText("Add to Favorites").assertExists()
    }

    @Test
    fun tabContextMenu_whenReloadVisible_showsReloadAndTriggersCallback() {
        var reloadCalled = false
        var dismissCalled = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalVibrationFeedback provides testVibration) {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    TabContextMenu(
                        expanded = true,
                        onDismissRequest = { dismissCalled = true },
                        isDark = false,
                        onCloseTab = {},
                        onCopyUrl = {},
                        onAddFavorite = {},
                        isReloadVisible = true,
                        onReload = { reloadCalled = true },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Reload").assertExists()
        composeTestRule.onNodeWithText("Reload").performClick()

        assertTrue("Expected onReload callback to be invoked", reloadCalled)
        assertTrue("Expected onDismissRequest to be invoked when clicked", dismissCalled)
    }

    @Test
    fun tabContextMenu_whenReloadNotVisible_doesNotShowReload() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVibrationFeedback provides testVibration) {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    TabContextMenu(
                        expanded = true,
                        onDismissRequest = {},
                        isDark = false,
                        onCloseTab = {},
                        onCopyUrl = {},
                        onAddFavorite = {},
                        isReloadVisible = false,
                        onReload = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Reload").assertDoesNotExist()
        composeTestRule.onNodeWithText("Close tab").assertExists()
    }

    @Test
    fun isReloadVisible_evaluatesCorrectlyForWebpageVsHome() {
        fun computeIsReloadVisible(isHome: Boolean, url: String): Boolean =
            !isHome && url.isNotBlank() && !url.startsWith("about:")

        // Active webpage tab
        assertTrue(computeIsReloadVisible(isHome = false, url = "https://example.com"))

        // Home tab or blank
        assertFalse(computeIsReloadVisible(isHome = true, url = "https://example.com"))
        assertFalse(computeIsReloadVisible(isHome = false, url = ""))
        assertFalse(computeIsReloadVisible(isHome = false, url = "about:blank"))
    }

    @Test
    fun tabContextMenu_whenBackPressed_triggersDismiss() {
        var dismissCalled = false

        composeTestRule.setContent {
            CompositionLocalProvider(LocalVibrationFeedback provides testVibration) {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    TabContextMenu(
                        expanded = true,
                        onDismissRequest = { dismissCalled = true },
                        isDark = false,
                        onCloseTab = {},
                        onCopyUrl = {},
                        onAddFavorite = {},
                    )
                }
            }
        }

        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()

        assertTrue("Expected onDismissRequest to be called on back press", dismissCalled)
    }

    @Test
    fun testTabContextMenuPositionProvider_verticalOffsetAlignsWithActionDockBaseline() {
        val density = Density(3f)
        val provider = TabContextMenuPositionProvider(density = density)
        val windowSize = IntSize(1080, 2400)
        val popupContentSize = IntSize(500, 400)

        // Pill anchor placed near dock
        val anchor = IntRect(200, 2200, 600, 2326)
        val pos = provider.calculatePosition(anchor, windowSize, LayoutDirection.Ltr, popupContentSize)
        val bottomY = pos.y + popupContentSize.height

        // 14.dp at density 3 = 42px offset above anchor.top
        assertEquals(2200 - 42, bottomY)
    }

    @Test
    fun testDockShadowUnderClipShape_clipsExactlyAtDockTopAndAllowsHorizontalVerticalHeadroom() {
        val shape = DockShadowUnderClipShape(dockGapPx = 30f, overflowPx = 96f)
        val size = Size(516f, 600f)
        val outline = shape.createOutline(size, LayoutDirection.Ltr, Density(3f))

        assertTrue("Expected Outline.Rectangle", outline is Outline.Rectangle)
        val rect = (outline as Outline.Rectangle).rect

        assertEquals(-96f, rect.left, 0.001f)
        assertEquals(-96f, rect.top, 0.001f)
        assertEquals(516f + 96f, rect.right, 0.001f)
        // Bottom must stop precisely at size.height + dockGapPx (600 + 30 = 630f)
        // so no shadow overlays onto the dock surface below
        assertEquals(630f, rect.bottom, 0.001f)
    }
}

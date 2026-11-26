package com.jestk.browser.ui.overlays

import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.testing.renderComposePreview
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Renders the grouping hint (scrim + folder icon) on the target cards, light and dark (PRO-203). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabOverviewGroupingHintPreviewTest {
    @Test
    fun renderGroupingHintLightPreview() = render("tab_overview_grouping_hint_light.png", ThemeMode.LIGHT, 1.0f)

    @Test
    fun renderGroupingHintDarkPreview() = render("tab_overview_grouping_hint_dark.png", ThemeMode.DARK, 1.0f)

    @Test
    fun renderGroupingHintLargeFontPreview() = render("tab_overview_grouping_hint_large_font.png", ThemeMode.DARK, 1.3f)

    private fun render(
        fileName: String,
        themeMode: ThemeMode,
        fontScale: Float,
    ) {
        renderComposePreview(fileName, width = 412, height = 360, fontScale = fontScale) {
            JestkBrowserTheme(themeMode = themeMode) {
                TabOverviewGroupingHintSample(isDark = themeMode == ThemeMode.DARK)
            }
        }
    }
}

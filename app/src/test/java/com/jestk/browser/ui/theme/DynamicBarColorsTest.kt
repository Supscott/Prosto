package com.jestk.browser.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DynamicBarColorsTest {

    @Test
    fun resolve_homeScreen_lightTheme_usesProstoCanvasFloor() {
        val result = DynamicBarColors.resolve(
            isDark = false,
            isHomeOrNeutralScreen = true,
            topThemeColor = 0xFFFF0000.toInt(),
            bottomThemeColor = 0xFF00FF00.toInt(),
        )

        assertThat(result.statusBarColor).isEqualTo(ProstoCanvasFloor)
        assertThat(result.underDockColor).isEqualTo(ProstoCanvasFloor)
        assertThat(result.navigationBarColor).isEqualTo(ProstoCanvasFloor)
        assertThat(result.isAppearanceLightStatusBars).isTrue()
        assertThat(result.isAppearanceLightNavigationBars).isTrue()
    }

    @Test
    fun resolve_homeScreen_darkTheme_usesDarkBackground() {
        val result = DynamicBarColors.resolve(
            isDark = true,
            isHomeOrNeutralScreen = true,
            topThemeColor = 0xFFFF0000.toInt(),
            bottomThemeColor = 0xFF00FF00.toInt(),
        )

        assertThat(result.statusBarColor).isEqualTo(DarkBackground)
        assertThat(result.underDockColor).isEqualTo(DarkBackground)
        assertThat(result.navigationBarColor).isEqualTo(DarkBackground)
        assertThat(result.isAppearanceLightStatusBars).isFalse()
        assertThat(result.isAppearanceLightNavigationBars).isFalse()
    }

    @Test
    fun resolve_lightTheme_allowsAnyColorsAndSplitsTopAndBottom() {
        val red = Color(0xFFFF0000).toArgb()
        val yellow = Color(0xFFFFFF00).toArgb()

        val result = DynamicBarColors.resolve(
            isDark = false,
            isHomeOrNeutralScreen = false,
            topThemeColor = red,
            bottomThemeColor = yellow,
        )

        assertThat(result.statusBarColor).isEqualTo(Color(red))
        assertThat(result.underDockColor).isEqualTo(Color(yellow))
        assertThat(result.navigationBarColor).isEqualTo(Color(yellow))
        // Yellow luminance > 0.5f -> dark icons (isAppearanceLight = true)
        assertThat(result.isAppearanceLightNavigationBars).isTrue()
        // Red luminance = 0.21f < 0.5f -> light icons (isAppearanceLight = false)
        assertThat(result.isAppearanceLightStatusBars).isFalse()
    }

    @Test
    fun resolve_darkTheme_brightColorsFallbackToDarkBackground() {
        val pureWhite = Color.White.toArgb()
        val pureYellow = Color.Yellow.toArgb()

        val result = DynamicBarColors.resolve(
            isDark = true,
            isHomeOrNeutralScreen = false,
            topThemeColor = pureWhite,
            bottomThemeColor = pureYellow,
        )

        // Since both White and Yellow have luminance >= 0.3f, both must fallback to DarkBackground
        assertThat(result.statusBarColor).isEqualTo(DarkBackground)
        assertThat(result.underDockColor).isEqualTo(DarkBackground)
        assertThat(result.navigationBarColor).isEqualTo(DarkBackground)
        assertThat(result.isAppearanceLightStatusBars).isFalse()
        assertThat(result.isAppearanceLightNavigationBars).isFalse()
    }

    @Test
    fun resolve_darkTheme_darkColorsAreAllowed() {
        // Very dark slate: #0F172A (luminance is ~0.015 < 0.3)
        val darkSlate = Color(0xFF0F172A).toArgb()
        val darkTeal = Color(0xFF06201D).toArgb()

        val result = DynamicBarColors.resolve(
            isDark = true,
            isHomeOrNeutralScreen = false,
            topThemeColor = darkSlate,
            bottomThemeColor = darkTeal,
        )

        assertThat(result.statusBarColor).isEqualTo(Color(darkSlate))
        assertThat(result.underDockColor).isEqualTo(Color(darkTeal))
        assertThat(result.navigationBarColor).isEqualTo(Color(darkTeal))
        assertThat(result.isAppearanceLightStatusBars).isFalse()
        assertThat(result.isAppearanceLightNavigationBars).isFalse()
    }

    @Test
    fun resolve_bottomColorFallbackToTopColorWhenBottomIsNull() {
        val cyan = Color(0xFF00BCD4).toArgb()

        val result = DynamicBarColors.resolve(
            isDark = false,
            isHomeOrNeutralScreen = false,
            topThemeColor = cyan,
            bottomThemeColor = null,
        )

        assertThat(result.statusBarColor).isEqualTo(Color(cyan))
        assertThat(result.underDockColor).isEqualTo(Color(cyan))
        assertThat(result.navigationBarColor).isEqualTo(Color(cyan))
    }

    @Test
    fun resolve_nullColorsFallbackToDefaultBg() {
        val resultLight = DynamicBarColors.resolve(
            isDark = false,
            isHomeOrNeutralScreen = false,
            topThemeColor = null,
            bottomThemeColor = null,
        )
        assertThat(resultLight.statusBarColor).isEqualTo(ProstoCanvasFloor)
        assertThat(resultLight.underDockColor).isEqualTo(ProstoCanvasFloor)

        val resultDark = DynamicBarColors.resolve(
            isDark = true,
            isHomeOrNeutralScreen = false,
            topThemeColor = null,
            bottomThemeColor = null,
        )
        assertThat(resultDark.statusBarColor).isEqualTo(DarkBackground)
        assertThat(resultDark.underDockColor).isEqualTo(DarkBackground)
    }
}

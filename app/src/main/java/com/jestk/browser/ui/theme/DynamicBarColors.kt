package com.jestk.browser.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

data class ResolvedBarColors(
    val statusBarColor: Color,
    val underDockColor: Color,
    val navigationBarColor: Color,
    val isAppearanceLightStatusBars: Boolean,
    val isAppearanceLightNavigationBars: Boolean,
)

object DynamicBarColors {
    /**
     * In Dark Theme, only website colors darker than this threshold are allowed.
     * Brighter website colors fallback to DarkBackground to prevent glaring contrasts.
     */
    const val DARK_MODE_LUMINANCE_THRESHOLD: Float = 0.3f

    /**
     * Resolves the adaptive colors and icon contrast for status bar, area under dock,
     * and system navigation bar based on the active theme mode and webpage sampled colors.
     */
    fun resolve(
        isDark: Boolean,
        isHomeOrNeutralScreen: Boolean,
        topThemeColor: Int?,
        bottomThemeColor: Int?,
    ): ResolvedBarColors {
        val defaultBg = if (isDark) DarkBackground else ProstoCanvasFloor

        if (isHomeOrNeutralScreen) {
            val isLight = defaultBg.luminance() > 0.5f
            return ResolvedBarColors(
                statusBarColor = defaultBg,
                underDockColor = defaultBg,
                navigationBarColor = defaultBg,
                isAppearanceLightStatusBars = isLight,
                isAppearanceLightNavigationBars = isLight,
            )
        }

        // 1. Top color for status bar
        val resolvedTopColor =
            if (topThemeColor != null) {
                sanitizeColor(topThemeColor, isDark)
            } else {
                defaultBg
            }

        // 2. Bottom color for area under dock and navigation bar (fallback to topThemeColor, then defaultBg)
        val resolvedBottomColor =
            when {
                bottomThemeColor != null -> sanitizeColor(bottomThemeColor, isDark)
                topThemeColor != null -> sanitizeColor(topThemeColor, isDark)
                else -> defaultBg
            }

        return ResolvedBarColors(
            statusBarColor = resolvedTopColor,
            underDockColor = resolvedBottomColor,
            navigationBarColor = resolvedBottomColor,
            isAppearanceLightStatusBars = resolvedTopColor.luminance() > 0.5f,
            isAppearanceLightNavigationBars = resolvedBottomColor.luminance() > 0.5f,
        )
    }

    private fun sanitizeColor(colorInt: Int, isDark: Boolean): Color {
        val color = Color(colorInt)
        return if (isDark) {
            if (color.luminance() < DARK_MODE_LUMINANCE_THRESHOLD) color else DarkBackground
        } else {
            color
        }
    }
}

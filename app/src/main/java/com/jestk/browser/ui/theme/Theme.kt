package com.jestk.browser.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jestk.browser.domain.model.ThemeMode

// ==========================================
// Prosto Canvas Color Schemes
// ==========================================

val LightColorScheme =
    lightColorScheme(
        primary = ProstoPrimary,
        onPrimary = ProstoOnPrimary,
        primaryContainer = ProstoPrimaryContainer,
        onPrimaryContainer = ProstoOnPrimaryContainer,
        secondary = ProstoSecondary,
        onSecondary = ProstoOnSecondary,
        secondaryContainer = ProstoSecondaryContainer,
        onSecondaryContainer = ProstoOnSecondaryContainer,
        tertiary = ProstoTertiary,
        onTertiary = ProstoOnTertiary,
        tertiaryContainer = ProstoTertiaryContainer,
        onTertiaryContainer = ProstoOnTertiaryContainer,
        surface = ProstoSurface,
        onSurface = ProstoOnSurface,
        surfaceVariant = ProstoSurfaceContainerHighest,
        onSurfaceVariant = ProstoOnSurfaceVariant,
        background = ProstoCanvasFloor,
        onBackground = ProstoOnSurface,
        outline = ProstoOutline,
        outlineVariant = ProstoOutlineVariant,
        error = ProstoError,
        onError = ProstoOnError,
        errorContainer = ProstoErrorContainer,
        onErrorContainer = ProstoOnErrorContainer,
        inverseSurface = ProstoInverseSurface,
        inverseOnSurface = ProstoInverseOnSurface,
        inversePrimary = ProstoInversePrimary,
    )

val DarkColorScheme =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = Color(0xFF003732),
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = Color(0xFFA5F2E9),
        secondary = Color(0xFFB1CCC8),
        onSecondary = Color(0xFF1D3532),
        secondaryContainer = Color(0xFF334B48),
        onSecondaryContainer = Color(0xFFCCE8E3),
        tertiary = Color(0xFFA8CBE1),
        onTertiary = Color(0xFF0D3449),
        tertiaryContainer = Color(0xFF284A61),
        onTertiaryContainer = Color(0xFFC4E8FF),
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        background = DarkBackground,
        onBackground = DarkOnSurface,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        error = DarkError,
        onError = DarkOnError,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer,
        inverseSurface = DarkInverseSurface,
        inverseOnSurface = DarkInverseOnSurface,
        inversePrimary = ProstoPrimary,
    )

val LocalIsDarkTheme = compositionLocalOf { false }

@Composable
fun isAppDarkTheme(themeMode: ThemeMode = ThemeMode.SYSTEM): Boolean =
    when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

/**
 * Top-level application theme applying Prosto Canvas color schemes and the
 * Manrope title / Inter body typography.
 */
@Composable
fun JestkBrowserTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = isAppDarkTheme(themeMode)
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalProstoTypography provides ProstoTextStyles,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ProstoTypography,
            content = content,
        )
    }
}

// ==========================================
// Prosto Shapes Hierarchy (DESIGN.md)
// ==========================================

object ProstoShapes {
    /** 8dp for small chips, badges, and tags */
    val Sm = RoundedCornerShape(8.dp)

    /** 16dp for medium cards and grouped list containers */
    val Md = RoundedCornerShape(16.dp)

    /** 24dp for elevated card modules, quick dials, dialogs */
    val Card = RoundedCornerShape(24.dp)

    /** 28dp for bottom modal sheets */
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** 32dp for dock floating capsule */
    val Dock = RoundedCornerShape(32.dp)

    /** Continuous 9999px pill shape for omnibox, tab switchers, action buttons */
    val Pill = CircleShape

    val ButtonPill = CircleShape
    val Capsule = CircleShape
}

// ==========================================
// Prosto Borders & Rim Highlights
// ==========================================

object ProstoBorders {
    /**
     * Level 1 micro-border: 1px solid rgba(226, 232, 240, 0.8) in light mode,
     * subtle slate border in dark mode.
     */
    fun cardBorder(
        isDark: Boolean = false,
        width: Dp = 1.dp,
    ): BorderStroke {
        val color =
            if (isDark) {
                Color(0xFF33413E).copy(alpha = 0.80f)
            } else {
                Color(0xFFDCE6E3).copy(alpha = 0.80f)
            }
        return BorderStroke(width, color)
    }

    /**
     * Hairline micro-border: 1px subtle boundary for Prosto Canvas surfaces and stat chips.
     */
    fun hairlineBorder(
        isDark: Boolean = false,
        width: Dp = 1.dp,
    ): BorderStroke = cardBorder(isDark = isDark, width = width)

    /**
     * Level 2 floating rim highlight: 1px solid rgba(255, 255, 255, 0.8) vertical gradient in light mode,
     * subtle slate rim in dark mode.
     */
    fun floatingBorder(
        isDark: Boolean = false,
        width: Dp = 1.dp,
    ): BorderStroke {
        val brush =
            if (isDark) {
                Brush.verticalGradient(
                    colors =
                        listOf(
                            Color(0xFF3E4D4A).copy(alpha = 0.70f),
                            Color(0xFF2A3A37).copy(alpha = 0.40f),
                        ),
                )
            } else {
                Brush.verticalGradient(
                    colors =
                        listOf(
                            Color.White.copy(alpha = 0.85f),
                            Color.White.copy(alpha = 0.40f),
                        ),
                )
            }
        return BorderStroke(width, brush)
    }

    fun frostedBorder(
        isDark: Boolean,
        alpha: Float? = null,
        width: Dp = 1.dp,
    ): BorderStroke = cardBorder(isDark = isDark, width = width)

    fun specularBorder(
        isDark: Boolean,
        width: Dp = 1.dp,
    ): BorderStroke = floatingBorder(isDark = isDark, width = width)
}

// ==========================================
// Prosto Surfaces (Crisp Solid Surfaces, No Blur)
// ==========================================

/**
 * Surface Layer (Level 1 - Cards & Lists): Pure white #FFFFFF (or dark slate) with an
 * ambient diffused dispersion shadow and subtle micro-border. NO BLUR.
 */
fun Modifier.prostoCardSurface(
    shape: Shape = ProstoShapes.Card,
    isDark: Boolean = false,
    backgroundColor: Color? = null,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 2.dp,
): Modifier {
    val bg = backgroundColor ?: if (isDark) DarkSurface else ProstoCardSurface
    val border = ProstoBorders.cardBorder(isDark = isDark, width = borderWidth)
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color(0x0F0F172A),
            spotColor = if (isDark) Color.Black.copy(alpha = 0.25f) else Color(0x0A0F172A),
        ).background(color = bg, shape = shape)
        .border(border = border, shape = shape)
        .clip(shape)
}

/**
 * Floating Controls (Level 2 - Omnibox, Modals, Action Dock): Solid crisp surface
 * with deep cerulean-tinted atmospheric shadow and rim highlight border. NO BLUR.
 */
fun Modifier.prostoFloatingSurface(
    shape: Shape = ProstoShapes.Dock,
    isDark: Boolean = false,
    backgroundColor: Color? = null,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 10.dp,
    clip: Boolean = true,
): Modifier {
    val bg = backgroundColor ?: if (isDark) DarkSurface else Color.White
    val border = ProstoBorders.floatingBorder(isDark = isDark, width = borderWidth)
    val base = this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = if (isDark) Color.Black else Color.Black.copy(alpha = 0.85f),
            spotColor = Color.Black,
        ).background(color = bg, shape = shape)
        .border(border = border, shape = shape)
    return if (clip) base.clip(shape) else base
}

/**
 * Solid surface modifier with zero blur and no haze dependencies.
 * Used for translucent tiles that must let the content behind them show through.
 */
fun Modifier.prostoSolidSurface(
    shape: Shape = ProstoShapes.Card,
    isDark: Boolean = false,
    tintColor: Color? = null,
    alpha: Float? = null,
    borderColor: Color? = null,
    borderBrush: Brush? = null,
    borderWidth: Dp = 1.dp,
): Modifier {
    val bg = tintColor ?: if (isDark) DarkSurface else ProstoCardSurface
    val resolvedBg = if (alpha != null) bg.copy(alpha = alpha) else bg
    val resolvedBorder =
        when {
            borderBrush != null -> BorderStroke(borderWidth, borderBrush)
            borderColor != null -> BorderStroke(borderWidth, borderColor)
            else -> ProstoBorders.cardBorder(isDark = isDark, width = borderWidth)
        }
    return this
        .shadow(
            elevation = 3.dp,
            shape = shape,
            clip = false,
            ambientColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color(0x0F0F172A),
            spotColor = if (isDark) Color.Black.copy(alpha = 0.25f) else Color(0x0A0F172A),
        ).background(color = resolvedBg, shape = shape)
        .border(border = resolvedBorder, shape = shape)
        .clip(shape)
}

package com.jestk.browser.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
// Serene Canvas Color Schemes
// ==========================================

val LightColorScheme =
    lightColorScheme(
        primary = SerenePrimary,
        onPrimary = SereneOnPrimary,
        primaryContainer = SerenePrimaryContainer,
        onPrimaryContainer = SereneOnPrimaryContainer,
        secondary = SereneSecondary,
        onSecondary = SereneOnSecondary,
        secondaryContainer = SereneSecondaryContainer,
        onSecondaryContainer = SereneOnSecondaryContainer,
        tertiary = SereneTertiary,
        onTertiary = SereneOnTertiary,
        tertiaryContainer = SereneTertiaryContainer,
        onTertiaryContainer = SereneOnTertiaryContainer,
        surface = SereneSurface,
        onSurface = SereneOnSurface,
        surfaceVariant = SereneSurfaceContainerHighest,
        onSurfaceVariant = SereneOnSurfaceVariant,
        background = SereneCanvasFloor,
        onBackground = SereneOnSurface,
        outline = SereneOutline,
        outlineVariant = SereneOutlineVariant,
        error = SereneError,
        onError = SereneOnError,
        errorContainer = SereneErrorContainer,
        onErrorContainer = SereneOnErrorContainer,
        inverseSurface = SereneInverseSurface,
        inverseOnSurface = SereneInverseOnSurface,
        inversePrimary = SereneInversePrimary,
    )

val DarkColorScheme =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = Color(0xFF003258),
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = Color(0xFFD1E4FF),
        secondary = Color(0xFFA4C9FF),
        onSecondary = Color(0xFF00315B),
        secondaryContainer = Color(0xFF004883),
        onSecondaryContainer = Color(0xFFD4E3FF),
        tertiary = Color(0xFFC1C7CC),
        onTertiary = Color(0xFF2B3135),
        tertiaryContainer = Color(0xFF41484C),
        onTertiaryContainer = Color(0xFFDDE3E8),
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
        inversePrimary = SerenePrimary,
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
 * Top-level application theme applying Serene Canvas color schemes and Plus Jakarta Sans typography.
 */
@Composable
fun JestkBrowserTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    hazeState: Any? = null,
    content: @Composable () -> Unit,
) {
    val darkTheme = isAppDarkTheme(themeMode)
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalSereneTypography provides SereneTextStyles,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SereneTypography,
            content = content,
        )
    }
}

// ==========================================
// Serene Shapes Hierarchy (DESIGN.md)
// ==========================================

object SereneShapes {
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

/**
 * Backward compatibility alias for LiquidShapes.
 */
object LiquidShapes {
    val Card = RoundedCornerShape(18.dp)
    val ButtonPill = RoundedCornerShape(22.dp)
    val Capsule = RoundedCornerShape(24.dp)
    val BottomSheet = SereneShapes.BottomSheet
    val Dock = RoundedCornerShape(29.dp)
}

// ==========================================
// Serene Borders & Rim Highlights
// ==========================================

object SereneBorders {
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
                Color(0xFF334155).copy(alpha = 0.80f)
            } else {
                Color(0xFFE2E8F0).copy(alpha = 0.80f)
            }
        return BorderStroke(width, color)
    }

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
                            Color(0xFF475569).copy(alpha = 0.70f),
                            Color(0xFF334155).copy(alpha = 0.40f),
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

val LiquidBorders = SereneBorders

// ==========================================
// Serene Surfaces (Crisp Solid Surfaces, No Blur)
// ==========================================

/**
 * Surface Layer (Level 1 - Cards & Lists): Pure white #FFFFFF (or dark slate) with an
 * ambient diffused dispersion shadow and subtle micro-border. NO BLUR.
 */
fun Modifier.sereneCardSurface(
    shape: Shape = SereneShapes.Card,
    isDark: Boolean = false,
    backgroundColor: Color? = null,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 2.dp,
): Modifier {
    val bg = backgroundColor ?: if (isDark) DarkSurface else SereneCardSurface
    val border = SereneBorders.cardBorder(isDark = isDark, width = borderWidth)
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color(0x0F0F172A),
            spotColor = if (isDark) Color.Black.copy(alpha = 0.25f) else Color(0x0A0F172A),
        )
        .background(color = bg, shape = shape)
        .border(border = border, shape = shape)
        .clip(shape)
}

/**
 * Floating Controls (Level 2 - Omnibox, Modals, Action Dock): Solid crisp surface
 * with deep cerulean-tinted atmospheric shadow and rim highlight border. NO BLUR.
 */
fun Modifier.sereneFloatingSurface(
    shape: Shape = SereneShapes.Dock,
    isDark: Boolean = false,
    backgroundColor: Color? = null,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 10.dp,
): Modifier {
    val bg = backgroundColor ?: if (isDark) DarkSurface else Color.White
    val border = SereneBorders.floatingBorder(isDark = isDark, width = borderWidth)
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = if (isDark) Color.Black else Color.Black.copy(alpha = 0.85f),
            spotColor = Color.Black,
        )
        .background(color = bg, shape = shape)
        .border(border = border, shape = shape)
        .clip(shape)
}

/**
 * Backward compatibility alias for [liquidFrostedGlass].
 * Implemented as a solid Level 1 / Level 2 surface with zero blur and no Haze dependencies.
 */
fun Modifier.liquidFrostedGlass(
    shape: Shape = SereneShapes.Card,
    isDark: Boolean = false,
    blurRadius: Dp = 0.dp,
    tintColor: Color? = null,
    alpha: Float? = null,
    borderColor: Color? = null,
    borderBrush: Brush? = null,
    borderWidth: Dp = 1.dp,
    hazeState: Any? = null,
): Modifier {
    val bg = tintColor ?: if (isDark) DarkSurface else SereneCardSurface
    val resolvedBg = if (alpha != null) bg.copy(alpha = alpha) else bg
    val resolvedBorder =
        when {
            borderBrush != null -> BorderStroke(borderWidth, borderBrush)
            borderColor != null -> BorderStroke(borderWidth, borderColor)
            else -> SereneBorders.cardBorder(isDark = isDark, width = borderWidth)
        }
    return this
        .shadow(
            elevation = 3.dp,
            shape = shape,
            clip = false,
            ambientColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color(0x0F0F172A),
            spotColor = if (isDark) Color.Black.copy(alpha = 0.25f) else Color(0x0A0F172A),
        )
        .background(color = resolvedBg, shape = shape)
        .border(border = resolvedBorder, shape = shape)
        .clip(shape)
}

/**
 * Solid container composable replacing frosted glass box with clean, solid Serene styling.
 */
@Composable
fun LiquidFrostedGlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = SereneShapes.Card,
    isDark: Boolean = LocalIsDarkTheme.current,
    blurRadius: Dp = 0.dp,
    tintColor: Color? = null,
    alpha: Float? = null,
    borderColor: Color? = null,
    borderBrush: Brush? = null,
    borderWidth: Dp = 1.dp,
    hazeState: Any? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier.liquidFrostedGlass(
                shape = shape,
                isDark = isDark,
                blurRadius = blurRadius,
                tintColor = tintColor,
                alpha = alpha,
                borderColor = borderColor,
                borderBrush = borderBrush,
                borderWidth = borderWidth,
                hazeState = hazeState,
            ),
    ) {
        content()
    }
}
